package com.ptk.anatomypro.core.network

import com.ptk.anatomypro.core.data.model.PackFailure
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Backend spec §9.2: resumable, checksum-verified, and never visible half-written. */
class PackDownloaderTest {

    private val files = FakeFileSystem()
    private val target = "/packs/skeletal-body/1/pack.glb".toPath()
    private val part = "/packs/skeletal-body/1/pack.glb.part".toPath()
    private val body = ByteArray(100_000) { (it * 31 % 251).toByte() }
    private val sha256 = body.toByteString().sha256().hex()

    /** The `Range` header of every request made, null where there was none. */
    private val ranges = mutableListOf<String?>()

    private fun downloader(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        PackDownloader(
            client = HttpClient(MockEngine { request ->
                ranges += request.headers[HttpHeaders.Range]
                handler(request)
            }),
            files = files,
        )

    /** A server that honours `Range`, and sends only the first [limit] bytes of what it owes. */
    private fun MockRequestHandleScope.serve(request: HttpRequestData, limit: Int = Int.MAX_VALUE): HttpResponseData {
        val from = request.headers[HttpHeaders.Range]?.removePrefix("bytes=")?.removeSuffix("-")?.toInt()
        val owed = body.copyOfRange(from ?: 0, body.size)
        return respond(
            content = owed.copyOf(minOf(owed.size, limit)),
            status = if (from == null) HttpStatusCode.OK else HttpStatusCode.PartialContent,
            headers = headersOf(HttpHeaders.ContentLength, owed.size.toString()),
        )
    }

    private fun read(path: okio.Path) = files.read(path) { readByteArray() }

    @Test
    fun a_download_lands_whole_at_its_target_and_leaves_nothing_else_behind() = runTest {
        val progress = mutableListOf<Pair<Long, Long>>()

        downloader { serve(it) }.fetch("https://cdn/pack.glb", target, sha256, body.size.toLong()) { done, total ->
            progress += done to total
        }

        assertContentEquals(body, read(target))
        assertFalse(files.exists(part))
        assertEquals(body.size.toLong() to body.size.toLong(), progress.last())
        assertEquals(progress.sortedBy { it.first }, progress)
    }

    @Test
    fun an_interrupted_download_keeps_what_arrived_and_shows_nothing_at_the_target() = runTest {
        val failure = assertFailsWith<PackDownloadException> {
            downloader { serve(it, limit = 30_000) }.fetch("https://cdn/pack.glb", target, sha256, body.size.toLong())
        }

        assertEquals(PackFailure.NETWORK, failure.reason)
        assertTrue(failure.resumable)
        assertFalse(files.exists(target))
        assertContentEquals(body.copyOf(30_000), read(part))
    }

    @Test
    fun a_resumed_download_asks_only_for_the_bytes_it_does_not_hold() = runTest {
        assertFailsWith<PackDownloadException> {
            downloader { serve(it, limit = 30_000) }.fetch("https://cdn/pack.glb", target, sha256, body.size.toLong())
        }
        ranges.clear()
        val progress = mutableListOf<Long>()

        downloader { serve(it) }.fetch("https://cdn/pack.glb", target, sha256, body.size.toLong()) { done, _ -> progress += done }

        assertEquals(listOf<String?>("bytes=30000-"), ranges)
        assertContentEquals(body, read(target))
        // Progress counts the whole pack, not this request: the bar must not start over.
        assertTrue(progress.first() > 30_000)
    }

    @Test
    fun a_server_that_ignores_the_range_and_sends_everything_is_taken_from_the_start() = runTest {
        files.createDirectories(part.parent!!)
        files.write(part) { write(body, 0, 30_000) }

        downloader { respond(body, HttpStatusCode.OK) }.fetch("https://cdn/pack.glb", target, sha256, body.size.toLong())

        assertContentEquals(body, read(target))
    }

    @Test
    fun bytes_that_do_not_match_the_checksum_are_thrown_away_and_cannot_be_resumed() = runTest {
        val wrong = body.copyOf().also { it[500] = (it[500] + 1).toByte() }

        val failure = assertFailsWith<PackDownloadException> {
            downloader { respond(wrong, HttpStatusCode.OK) }.fetch("https://cdn/pack.glb", target, sha256, body.size.toLong())
        }

        assertEquals(PackFailure.CHECKSUM, failure.reason)
        assertFalse(failure.resumable)
        assertFalse(files.exists(target))
        assertFalse(files.exists(part))
    }

    @Test
    fun a_connection_that_fails_is_a_network_failure_that_can_be_tried_again() = runTest {
        val failure = assertFailsWith<PackDownloadException> {
            downloader { throw kotlinx.io.IOException("no route to host") }
                .fetch("https://cdn/pack.glb", target, sha256, body.size.toLong())
        }

        assertEquals(PackFailure.NETWORK, failure.reason)
        assertTrue(failure.resumable)
    }

    @Test
    fun a_refusal_from_the_server_is_a_network_failure_and_writes_nothing() = runTest {
        val failure = assertFailsWith<PackDownloadException> {
            downloader { respond("gone", HttpStatusCode.NotFound) }
                .fetch("https://cdn/pack.glb", target, sha256, body.size.toLong())
        }

        assertEquals(PackFailure.NETWORK, failure.reason)
        assertFalse(files.exists(target))
        assertFalse(files.exists(part))
    }

    @Test
    fun a_download_that_had_finished_but_was_never_moved_into_place_is_not_fetched_again() = runTest {
        files.createDirectories(part.parent!!)
        files.write(part) { write(body) }

        downloader { error("nothing should be asked for") }.fetch("https://cdn/pack.glb", target, sha256, body.size.toLong())

        assertEquals(emptyList(), ranges)
        assertContentEquals(body, read(target))
    }

    @Test
    fun a_file_of_unknown_size_is_always_fetched_from_the_start() = runTest {
        // The manifest: small, and the index does not say how long it is.
        files.createDirectories(part.parent!!)
        files.write(part) { write(body, 0, 10) }

        downloader { serve(it) }.fetch("https://cdn/manifest.json", target, sha256, byteSize = null)

        assertNull(ranges.single())
        assertContentEquals(body, read(target))
    }
}
