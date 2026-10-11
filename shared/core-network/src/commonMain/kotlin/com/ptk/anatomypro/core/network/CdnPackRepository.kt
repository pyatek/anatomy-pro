package com.ptk.anatomypro.core.network

import com.ptk.anatomypro.core.data.model.PackFailure
import com.ptk.anatomypro.core.data.model.PackState
import com.ptk.anatomypro.core.data.model.PackStatus
import com.ptk.anatomypro.core.data.repository.InstalledPacks
import com.ptk.anatomypro.core.data.repository.PackRepository
import com.ptk.anatomypro.core.model.PackId
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path

/** `index.json` at the root of the bucket: every pack on offer (backend spec §9.1). */
@Serializable
internal data class PackIndex(val packs: List<PackIndexEntry> = emptyList())

@Serializable
internal data class PackIndexEntry(
    val id: String,
    val version: Int,
    val labels: Map<String, String> = emptyMap(),
    val byteSize: Long,
    val sha256: String,
    val manifestSha256: String,
    val meshUrl: String,
    val manifestUrl: String,
)

/** The index could not be fetched, and none was ever kept. */
class PackIndexUnavailable(cause: Throwable? = null) : Exception("the pack index could not be read", cause)

/**
 * Packs from a public bucket, with no server of ours involved (backend spec §9).
 *
 * A download belongs to [scope], not to whoever asked for it: a screen asks from its own
 * scope, and leaving the screen must not stop the transfer. [cancel] is what stops one.
 *
 * A pack becomes visible to the rest of the app when [installed] is told of it, which
 * happens last: the mesh is already whole, checked and in place by then. Removal runs the
 * other way round.
 *
 * A pack installed at an older version than the index offers is listed as installed. Nothing
 * here offers an update yet.
 */
class CdnPackRepository(
    private val indexUrl: String,
    private val client: HttpClient,
    private val files: FileSystem,
    /** Where packs are kept; each version of each pack gets a directory of its own under it. */
    private val directory: Path,
    private val installed: InstalledPacks,
    private val scope: CoroutineScope,
    /** The language packs are named in on screen, Latin where the index has no such name. */
    private val locale: suspend () -> String,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : PackRepository {

    private val downloader = PackDownloader(client, files, io)
    private val json = Json { ignoreUnknownKeys = true }
    private val state = MutableStateFlow<List<PackState>?>(null)
    private var entries: Map<PackId, PackIndexEntry> = emptyMap()
    private val loading = Mutex()
    private val jobs = mutableMapOf<PackId, Job>()
    private val jobsLock = Mutex()

    override val packs: Flow<List<PackState>> = flow {
        load()
        emitAll(state.filterNotNull())
    }

    private suspend fun load() = loading.withLock {
        if (state.value != null) return@withLock
        val index = readIndex()
        val onDevice = installed.all().mapTo(mutableSetOf()) { it.id }
        val language = locale()
        entries = index.packs.associateBy { PackId(it.id) }
        state.value = index.packs.map { entry ->
            PackState(
                id = PackId(entry.id),
                version = entry.version,
                label = entry.labels[language] ?: entry.labels["la"] ?: entry.id,
                byteSize = entry.byteSize,
                checksum = entry.sha256,
                status = if (PackId(entry.id) in onDevice) PackStatus.Installed else PackStatus.Available,
            )
        }
    }

    /** The index as the bucket has it now, or as it was when last fetched. */
    private suspend fun readIndex(): PackIndex = withContext(io) {
        val cache = directory / "index.json"
        val text = try {
            val response = client.get(indexUrl)
            if (response.status != HttpStatusCode.OK) error("index answered ${response.status}")
            response.bodyAsText().also { fetched ->
                json.decodeFromString<PackIndex>(fetched)
                files.createDirectories(directory)
                files.write(cache) { writeUtf8(fetched) }
            }
        } catch (failure: Exception) {
            currentCoroutineContext().ensureActive()
            if (!files.exists(cache)) throw PackIndexUnavailable(failure)
            files.read(cache) { readUtf8() }
        }
        json.decodeFromString<PackIndex>(text)
    }

    private fun setStatus(id: PackId, status: PackStatus) =
        state.update { all -> all?.map { if (it.id == id) it.copy(status = status) else it } }

    private fun statusOf(id: PackId): PackStatus? = state.value?.firstOrNull { it.id == id }?.status

    private fun home(entry: PackIndexEntry): Path = directory / entry.id / entry.version.toString()
    private fun meshOf(entry: PackIndexEntry): Path = home(entry) / "pack.glb"
    private fun manifestOf(entry: PackIndexEntry): Path = home(entry) / "manifest.json"

    override suspend fun download(id: PackId) {
        load()
        val entry = requireNotNull(entries[id]) { "the index offers no pack ${id.value}" }
        if (statusOf(id) == PackStatus.Installed) return
        val job = jobsLock.withLock {
            jobs[id]?.takeIf { it.isActive } ?: scope.launch { transfer(entry) }.also { jobs[id] = it }
        }
        job.join()
    }

    private suspend fun transfer(entry: PackIndexEntry) {
        val id = PackId(entry.id)
        setStatus(id, PackStatus.Queued)
        try {
            val manifest = manifestOf(entry)
            val mesh = meshOf(entry)
            if (!files.exists(manifest)) downloader.fetch(entry.manifestUrl, manifest, entry.manifestSha256, byteSize = null)
            if (!files.exists(mesh)) {
                downloader.fetch(entry.meshUrl, mesh, entry.sha256, entry.byteSize) { done, total ->
                    setStatus(id, PackStatus.Downloading(done, total))
                }
            }
            val manifestJson = withContext(io) { files.read(manifest) { readUtf8() } }
            installed.install(manifestJson, entry.version.toLong(), "file://$mesh")
            setStatus(id, PackStatus.Installed)
        } catch (failure: PackDownloadException) {
            setStatus(id, PackStatus.Failed(failure.reason, failure.resumable))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Everything was fetched and reading it in failed: what is held can be tried again.
            setStatus(id, PackStatus.Failed(PackFailure.NETWORK, resumable = true))
        }
    }

    private suspend fun stop(id: PackId) {
        jobsLock.withLock { jobs.remove(id) }?.cancelAndJoin()
    }

    override suspend fun cancel(id: PackId) {
        val entry = entries[id] ?: return
        if (statusOf(id) == PackStatus.Installed) return
        stop(id)
        withContext(io) {
            downloader.discard(meshOf(entry))
            downloader.discard(manifestOf(entry))
            files.delete(meshOf(entry))
            files.delete(manifestOf(entry))
        }
        setStatus(id, PackStatus.Available)
    }

    override suspend fun delete(id: PackId) {
        if (id !in entries) return
        stop(id)
        // The database first: after this nothing is handed the mesh that is about to go.
        installed.uninstall(id)
        withContext(io) { files.deleteRecursively(directory / id.value) }
        setStatus(id, PackStatus.Available)
    }
}
