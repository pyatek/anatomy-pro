package com.ptk.anatomypro.core.network

import com.ptk.anatomypro.core.data.model.PackFailure
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.HashingSource
import okio.Path
import okio.Path.Companion.toPath
import okio.blackholeSink
import okio.buffer
import okio.use

/** Why a download did not finish, and whether what was fetched so far is worth keeping. */
class PackDownloadException(
    val reason: PackFailure,
    val resumable: Boolean,
    cause: Throwable? = null,
) : Exception("pack download failed: $reason", cause)

/**
 * Fetches one file of a pack (backend spec §9.2).
 *
 * Bytes are written beside the target, to `<target>.part`, and the file is moved into place
 * only once the whole of it matches its checksum: nothing ever reads a pack that is half
 * there. An interrupted download leaves its part file, and the next attempt asks the server
 * only for what is missing.
 */
class PackDownloader(
    private val client: HttpClient,
    private val files: FileSystem,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    /**
     * [byteSize] is the file's whole length where the index gives one. Without it the file
     * is fetched from the start every time: there is no telling a finished part from a
     * short one.
     *
     * [onProgress] counts bytes of the whole file, so a resumed download does not start its
     * bar over.
     *
     * @throws PackDownloadException with what went wrong.
     */
    suspend fun fetch(
        url: String,
        target: Path,
        sha256: String,
        byteSize: Long?,
        onProgress: (bytesDone: Long, bytesTotal: Long) -> Unit = { _, _ -> },
    ) = withContext(io) {
        val part = partOf(target)
        try {
            target.parent?.let(files::createDirectories)
            var held = files.metadataOrNull(part)?.size ?: 0L
            if (byteSize == null || held > byteSize) held = 0L
            if (held != byteSize) receive(url, part, held, byteSize, onProgress)
        } catch (failure: PackDownloadException) {
            throw failure
        } catch (failure: Exception) {
            // A cancelled download is not a failed one.
            currentCoroutineContext().ensureActive()
            if (failure is CancellationException) throw failure
            throw PackDownloadException(PackFailure.NETWORK, resumable = true, cause = failure)
        }

        val length = files.metadataOrNull(part)?.size ?: 0L
        if (byteSize != null && length < byteSize) {
            // The connection closed early without saying why.
            throw PackDownloadException(PackFailure.NETWORK, resumable = true)
        }
        if (!hashOf(part).equals(sha256, ignoreCase = true)) {
            files.delete(part)
            throw PackDownloadException(PackFailure.CHECKSUM, resumable = false)
        }
        files.atomicMove(part, target)
    }

    /** Throws away what a download had fetched so far. */
    suspend fun discard(target: Path) = withContext(io) { files.delete(partOf(target)) }

    private suspend fun receive(url: String, part: Path, held: Long, byteSize: Long?, onProgress: (Long, Long) -> Unit) {
        client.prepareGet(url) {
            if (held > 0) header(HttpHeaders.Range, "bytes=$held-")
        }.execute { response ->
            val resumed = response.status == HttpStatusCode.PartialContent
            if (!resumed && response.status != HttpStatusCode.OK) {
                throw PackDownloadException(PackFailure.NETWORK, resumable = true)
            }
            // A server that answers a range with the whole file is taken from the start.
            var done = if (resumed) held else 0L
            val total = byteSize ?: response.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: 0L
            val channel = response.bodyAsChannel()
            val buffer = ByteArray(CHUNK)
            (if (resumed) files.appendingSink(part) else files.sink(part)).buffer().use { sink ->
                while (true) {
                    val read = channel.readAvailable(buffer, 0, buffer.size)
                    if (read < 0) break
                    if (read == 0) continue
                    sink.write(buffer, 0, read)
                    done += read
                    onProgress(done, total)
                }
            }
        }
    }

    private fun hashOf(path: Path): String = files.source(path).use { source ->
        val hashing = HashingSource.sha256(source)
        hashing.buffer().use { it.readAll(blackholeSink()) }
        hashing.hash.hex()
    }

    private fun partOf(target: Path): Path = "$target.part".toPath()

    private companion object {
        const val CHUNK = 64 * 1024
    }
}
