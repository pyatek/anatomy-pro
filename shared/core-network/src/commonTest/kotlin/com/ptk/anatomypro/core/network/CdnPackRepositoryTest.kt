package com.ptk.anatomypro.core.network

import com.ptk.anatomypro.core.data.model.PackFailure
import com.ptk.anatomypro.core.data.model.PackStatus
import com.ptk.anatomypro.core.data.repository.InstalledPack
import com.ptk.anatomypro.core.data.repository.InstalledPacks
import com.ptk.anatomypro.core.model.PackId
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Backend spec §9.2: the pack contract, served from a bucket with no server behind it. */
@OptIn(ExperimentalCoroutinesApi::class)
class CdnPackRepositoryTest {

    private val skeleton = PackId("skeletal-body")
    private val files = FakeFileSystem()
    private val directory = "/app/packs".toPath()
    private val meshPath = directory / "skeletal-body" / "2" / "pack.glb"

    private val mesh = ByteArray(200_000) { (it * 7 % 253).toByte() }
    private val manifest = """{"pack_id":"skeletal-body","structures":[]}""".encodeToByteArray()
    private var index = """
        {"packs":[
          {"id":"skeletal-body","version":2,"labels":{"la":"Systema skeletale","pl":"Układ szkieletowy"},
           "byteSize":${mesh.size},"sha256":"${mesh.toByteString().sha256().hex()}",
           "manifestSha256":"${manifest.toByteString().sha256().hex()}",
           "meshUrl":"https://cdn/packs/skeletal-body/2/pack.glb",
           "manifestUrl":"https://cdn/packs/skeletal-body/2/manifest.json","structureCount":0,
           "somethingALaterPublisherAdds":true},
          {"id":"muscular-body","version":1,"labels":{"la":"Systema musculare"},
           "byteSize":10,"sha256":"00","manifestSha256":"00",
           "meshUrl":"https://cdn/packs/muscular-body/1/pack.glb",
           "manifestUrl":"https://cdn/packs/muscular-body/1/manifest.json","structureCount":0}
        ]}
    """.trimIndent()

    /** What the bucket does when asked for the mesh. */
    private var meshResponse: suspend () -> ByteArray = { mesh }
    private var online = true
    private val requested = mutableListOf<String>()

    private val installed = object : InstalledPacks {
        val packs = mutableMapOf<PackId, InstalledPack>()
        val manifests = mutableListOf<String>()
        /** Whether the mesh was in place at the moment the database was told about it. */
        var meshWasThere: Boolean? = null

        override suspend fun all() = packs.values.toList()
        override suspend fun install(manifestJson: String, version: Long, meshUri: String) {
            meshWasThere = files.exists(meshUri.removePrefix("file://").toPath())
            manifests += manifestJson
            packs[PackId("skeletal-body")] = InstalledPack(PackId("skeletal-body"), version, meshUri)
        }
        override suspend fun uninstall(id: PackId) { packs.remove(id) }
    }

    private fun TestScope.repository(locale: String = "pl") = CdnPackRepository(
        indexUrl = "https://cdn/index.json",
        // Everything on the test's own scheduler: a transfer is then exactly where the test
        // left it, and not wherever another thread had got to.
        client = HttpClient(MockEngine.create {
            dispatcher = UnconfinedTestDispatcher(testScheduler)
            addHandler { request ->
                val url = request.url.toString()
                requested += url
                when {
                    !online -> throw kotlinx.io.IOException("offline")
                    url.endsWith("index.json") -> respond(index)
                    url.endsWith("skeletal-body/2/manifest.json") -> respond(manifest)
                    url.endsWith("skeletal-body/2/pack.glb") -> respond(meshResponse())
                    else -> respondError(HttpStatusCode.NotFound)
                }
            }
        }),
        files = files,
        directory = directory,
        installed = installed,
        scope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
        locale = { locale },
        io = UnconfinedTestDispatcher(testScheduler),
    )

    private suspend fun CdnPackRepository.statusOf(id: PackId): PackStatus = packs.first().first { it.id == id }.status

    // --- the listing ------------------------------------------------------------------

    @Test
    fun lists_what_the_index_offers_named_in_the_readers_language() = runTest {
        val packs = repository(locale = "pl").packs.first()

        assertEquals(listOf("skeletal-body", "muscular-body"), packs.map { it.id.value })
        val pack = packs.first()
        assertEquals("Układ szkieletowy", pack.label)
        assertEquals(2, pack.version)
        assertEquals(mesh.size.toLong(), pack.byteSize)
        assertEquals(PackStatus.Available, pack.status)
    }

    @Test
    fun a_pack_with_no_name_in_the_readers_language_is_named_in_latin() = runTest {
        assertEquals("Systema musculare", repository(locale = "pl").packs.first().last().label)
    }

    @Test
    fun a_pack_the_database_has_is_listed_as_installed() = runTest {
        installed.packs[skeleton] = InstalledPack(skeleton, 2, "file:///somewhere/pack.glb")

        assertEquals(PackStatus.Installed, repository().statusOf(skeleton))
    }

    @Test
    fun with_no_connection_the_last_index_fetched_is_what_is_listed() = runTest {
        repository().packs.first()
        online = false

        assertEquals(2, repository().packs.first().size)
    }

    @Test
    fun with_no_connection_and_no_index_ever_fetched_the_listing_fails() = runTest {
        online = false

        assertFailsWith<PackIndexUnavailable> { repository().packs.first() }
    }

    // --- downloading ------------------------------------------------------------------

    @Test
    fun a_download_ends_with_the_mesh_in_place_and_the_manifest_in_the_database() = runTest {
        val repository = repository()
        val seen = mutableListOf<PackStatus>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.packs.collect { all -> seen += all.first { it.id == skeleton }.status }
        }

        repository.download(skeleton)

        assertEquals(PackStatus.Installed, seen.last())
        assertTrue(seen.any { it is PackStatus.Downloading && it.bytesTotal == mesh.size.toLong() })
        assertEquals(PackStatus.Queued, seen[1])
        assertEquals(mesh.toList(), files.read(meshPath) { readByteArray() }.toList())
        assertEquals(listOf(manifest.decodeToString()), installed.manifests)
        assertEquals(InstalledPack(skeleton, 2, "file://$meshPath"), installed.packs[skeleton])
    }

    @Test
    fun the_database_hears_of_a_pack_only_once_its_mesh_is_whole_and_in_place() = runTest {
        repository().download(skeleton)

        assertEquals(true, installed.meshWasThere)
    }

    @Test
    fun a_download_that_loses_its_connection_can_be_resumed() = runTest {
        val repository = repository()
        meshResponse = { mesh.copyOf(50_000) }

        repository.download(skeleton)

        assertEquals(PackStatus.Failed(PackFailure.NETWORK, resumable = true), repository.statusOf(skeleton))
        assertTrue(installed.packs.isEmpty())

        meshResponse = { mesh }
        repository.download(skeleton)

        assertEquals(PackStatus.Installed, repository.statusOf(skeleton))
    }

    @Test
    fun a_mesh_that_does_not_match_its_checksum_is_never_installed() = runTest {
        val repository = repository()
        meshResponse = { mesh.copyOf().also { it[0] = 99 } }

        repository.download(skeleton)

        assertEquals(PackStatus.Failed(PackFailure.CHECKSUM, resumable = false), repository.statusOf(skeleton))
        assertTrue(installed.packs.isEmpty())
        assertFalse(files.exists(meshPath))
    }

    @Test
    fun a_download_goes_on_when_the_screen_that_asked_for_it_has_gone() = runTest {
        // The screen asks from its own scope, and leaving the screen ends that scope.
        val repository = repository()
        val held = CompletableDeferred<Unit>()
        meshResponse = { held.await(); mesh }
        val screen = launch(UnconfinedTestDispatcher(testScheduler)) { repository.download(skeleton) }
        assertEquals(PackStatus.Queued, repository.statusOf(skeleton))

        screen.cancel()
        held.complete(Unit)
        testScheduler.advanceUntilIdle()

        assertEquals(PackStatus.Installed, repository.statusOf(skeleton))
    }

    @Test
    fun asking_twice_for_a_pack_that_is_downloading_fetches_it_once() = runTest {
        val repository = repository()
        val held = CompletableDeferred<Unit>()
        meshResponse = { held.await(); mesh }
        val first = launch(UnconfinedTestDispatcher(testScheduler)) { repository.download(skeleton) }
        val second = launch(UnconfinedTestDispatcher(testScheduler)) { repository.download(skeleton) }

        held.complete(Unit)
        first.join()
        second.join()

        assertEquals(1, requested.count { it.endsWith("pack.glb") })
        assertEquals(PackStatus.Installed, repository.statusOf(skeleton))
    }

    // --- cancelling and deleting ------------------------------------------------------

    @Test
    fun a_cancelled_download_leaves_nothing_installed_and_nothing_on_disk() = runTest {
        val repository = repository()
        val held = CompletableDeferred<Unit>()
        meshResponse = { held.await(); mesh }
        val asking = launch(UnconfinedTestDispatcher(testScheduler)) { repository.download(skeleton) }
        assertEquals(PackStatus.Queued, repository.statusOf(skeleton))
        assertTrue(requested.any { it.endsWith("pack.glb") }, "the transfer had not started")

        repository.cancel(skeleton)
        asking.join()

        assertEquals(PackStatus.Available, repository.statusOf(skeleton))
        assertTrue(installed.packs.isEmpty())
        assertEquals(emptyList(), files.allPaths.filter { files.metadata(it).isRegularFile && it.name != "index.json" })
    }

    @Test
    fun a_deleted_pack_is_forgotten_by_the_database_and_its_files_are_gone() = runTest {
        val repository = repository()
        repository.download(skeleton)

        repository.delete(skeleton)

        assertEquals(PackStatus.Available, repository.statusOf(skeleton))
        assertTrue(installed.packs.isEmpty())
        assertFalse(files.exists(meshPath))
    }

    @Test
    fun a_pack_the_index_does_not_offer_cannot_be_asked_for() = runTest {
        assertFailsWith<IllegalArgumentException> { repository().download(PackId("no-such-pack")) }
    }
}
