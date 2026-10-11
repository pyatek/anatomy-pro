package com.ptk.anatomypro.feature.onboarding

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.Flow
import com.ptk.anatomypro.core.data.repository.PackRepository
import com.ptk.anatomypro.core.data.model.PackState
import com.ptk.anatomypro.core.data.fake.FakeBehaviour
import com.ptk.anatomypro.core.data.fake.FakePackRepository
import com.ptk.anatomypro.core.data.model.PackFailure
import com.ptk.anatomypro.core.data.model.PackStatus
import com.ptk.anatomypro.core.model.PackId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FirstDownloadViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    // --- §9's four states, as the mapping the screen draws from ----------------------

    @Test
    fun a_pack_asked_for_and_not_yet_transferring_is_queued() {
        assertEquals(DownloadPhase.Queued, phaseOf(PackStatus.Queued, requestFailed = false))
        // Available is the instant before the request goes out; showing "queued" then is
        // truer than flashing some fifth state.
        assertEquals(DownloadPhase.Queued, phaseOf(PackStatus.Available, requestFailed = false))
    }

    @Test
    fun a_transferring_pack_reports_how_far_it_has_got() {
        val phase = phaseOf(PackStatus.Downloading(bytesDone = 12_000_000, bytesTotal = 48_000_000), requestFailed = false)

        assertEquals(DownloadPhase.Progress(12_000_000, 48_000_000), phase)
        assertEquals(0.25f, (phase as DownloadPhase.Progress).fraction)
    }

    @Test
    fun a_network_failure_is_one_to_resume_from() {
        val phase = phaseOf(PackStatus.Failed(PackFailure.NETWORK, resumable = true), requestFailed = false)

        assertEquals(DownloadPhase.Failed(PackFailure.NETWORK, resumable = true), phase)
    }

    @Test
    fun a_checksum_failure_is_not_offered_as_a_resume() {
        // The bytes held are wrong, so there is nothing to resume: the screen offers to start over.
        val phase = phaseOf(PackStatus.Failed(PackFailure.CHECKSUM, resumable = false), requestFailed = false)

        assertEquals(DownloadPhase.Failed(PackFailure.CHECKSUM, resumable = false), phase)
    }

    @Test
    fun an_installed_pack_is_done() {
        assertEquals(DownloadPhase.Done, phaseOf(PackStatus.Installed, requestFailed = false))
    }

    @Test
    fun a_request_that_threw_reads_as_a_resumable_failure_until_the_repository_says_otherwise() {
        assertEquals(DownloadPhase.Failed(PackFailure.NETWORK, resumable = true), phaseOf(PackStatus.Available, requestFailed = true))
        // Once bytes are moving the earlier failure is history.
        assertIs<DownloadPhase.Progress>(phaseOf(PackStatus.Downloading(1, 2), requestFailed = true))
    }

    @Test
    fun a_pack_the_repository_does_not_have_is_unavailable() {
        assertEquals(DownloadPhase.Unavailable, phaseOf(null, requestFailed = false))
    }

    // --- the ViewModel ---------------------------------------------------------------

    @Test
    fun the_download_starts_by_itself_and_ends_done() = runTest(dispatcher) {
        val repository = FakePackRepository(initiallyInstalled = emptySet())

        val model = FirstDownloadViewModel(repository)
        advanceUntilIdle()

        assertEquals(DownloadPhase.Done, model.state.value.phase)
        assertTrue(model.state.value.canContinue)
        assertEquals(1, repository.downloadRequests)
    }

    @Test
    fun the_state_names_the_pack_and_its_size() = runTest(dispatcher) {
        val model = FirstDownloadViewModel(FakePackRepository(initiallyInstalled = emptySet()))
        advanceUntilIdle()

        assertEquals("Układ kostny", model.state.value.label)
        assertEquals(48_300_000L, model.state.value.byteSize)
    }

    @Test
    fun a_pack_already_installed_is_done_without_a_download() = runTest(dispatcher) {
        // A reinstall over existing data, or a pack bundled with the app.
        val repository = FakePackRepository()

        val model = FirstDownloadViewModel(repository)
        advanceUntilIdle()

        assertEquals(DownloadPhase.Done, model.state.value.phase)
        assertEquals(0, repository.downloadRequests)
    }

    @Test
    fun a_download_that_fails_can_be_skipped_but_not_continued_as_if_done() = runTest(dispatcher) {
        val model = FirstDownloadViewModel(
            FakePackRepository(downloadFailure = PackFailure.NETWORK, initiallyInstalled = emptySet()),
        )
        advanceUntilIdle()

        assertEquals(DownloadPhase.Failed(PackFailure.NETWORK, resumable = true), model.state.value.phase)
        assertTrue(model.state.value.canSkip)
        assertFalse(model.state.value.canContinue)
    }

    @Test
    fun retrying_a_failed_download_finishes_it() = runTest(dispatcher) {
        val repository = FakePackRepository(
            downloadFailure = PackFailure.NETWORK,
            failingAttempts = 1,
            initiallyInstalled = emptySet(),
        )
        val model = FirstDownloadViewModel(repository)
        advanceUntilIdle()
        assertIs<DownloadPhase.Failed>(model.state.value.phase)

        model.onRetry()
        advanceUntilIdle()

        assertEquals(DownloadPhase.Done, model.state.value.phase)
        assertEquals(2, repository.downloadRequests)
    }

    @Test
    fun a_request_that_throws_is_a_resumable_failure_not_a_crash() = runTest(dispatcher) {
        // Review Focus 2. Offline at the moment of asking: download() throws before any
        // status is written.
        val offline = FakePackRepository(
            behaviour = FakeBehaviour(failure = { IllegalStateException("offline") }),
            initiallyInstalled = emptySet(),
        )

        val model = FirstDownloadViewModel(offline)
        advanceUntilIdle()

        assertEquals(DownloadPhase.Failed(PackFailure.NETWORK, resumable = true), model.state.value.phase)
        assertTrue(model.state.value.canSkip)
    }

    @Test
    fun a_pack_the_repository_does_not_have_can_be_passed() = runTest(dispatcher) {
        // Review Focus 3.
        val repository = FakePackRepository(initiallyInstalled = emptySet())

        val model = FirstDownloadViewModel(repository, pack = PackId("no-such-pack"))
        advanceUntilIdle()

        assertEquals(DownloadPhase.Unavailable, model.state.value.phase)
        assertTrue(model.state.value.canContinue)
        assertEquals(0, repository.downloadRequests)
    }

    @Test
    fun opening_the_screen_again_after_it_finished_asks_for_nothing_more() = runTest(dispatcher) {
        // Review Focus 4. Back to the goals and forward again builds a second ViewModel over
        // the same repository; only a pack that is still Available is ever requested.
        val repository = FakePackRepository(initiallyInstalled = emptySet())
        FirstDownloadViewModel(repository)
        advanceUntilIdle()

        FirstDownloadViewModel(repository)
        advanceUntilIdle()

        assertEquals(1, repository.downloadRequests)
    }

    @Test
    fun a_failure_is_not_retried_just_because_the_screen_was_reopened() = runTest(dispatcher) {
        // Reopening must not turn into a retry loop against a network that is down.
        val repository = FakePackRepository(downloadFailure = PackFailure.NETWORK, initiallyInstalled = emptySet())
        FirstDownloadViewModel(repository)
        advanceUntilIdle()

        val reopened = FirstDownloadViewModel(repository)
        advanceUntilIdle()

        assertIs<DownloadPhase.Failed>(reopened.state.value.phase)
        assertEquals(1, repository.downloadRequests)
    }

    // --- outside onboarding: the atlas tab with no pack ---------------------------------

    @Test
    fun a_pack_someone_removed_is_offered_not_fetched_again_behind_their_back() = runTest(dispatcher) {
        val repository = FakePackRepository(initiallyInstalled = emptySet())

        val model = FirstDownloadViewModel(repository, autoStart = false)
        advanceUntilIdle()

        assertEquals(DownloadPhase.Offered, model.state.value.phase)
        assertEquals(0, repository.downloadRequests)

        model.onStart()
        advanceUntilIdle()

        assertEquals(DownloadPhase.Done, model.state.value.phase)
        assertEquals(1, repository.downloadRequests)
    }

    @Test
    fun a_download_that_failed_earlier_is_still_a_failure_to_retry_when_it_is_only_offered() = runTest(dispatcher) {
        val repository = FakePackRepository(downloadFailure = PackFailure.NETWORK, failingAttempts = 1, initiallyInstalled = emptySet())
        FirstDownloadViewModel(repository)
        advanceUntilIdle()

        val later = FirstDownloadViewModel(repository, autoStart = false)
        advanceUntilIdle()
        assertEquals(DownloadPhase.Failed(PackFailure.NETWORK, resumable = true), later.state.value.phase)

        later.onRetry()
        advanceUntilIdle()
        assertEquals(DownloadPhase.Done, later.state.value.phase)
    }

    // --- a list of packs that cannot be read --------------------------------------------

    @Test
    fun a_list_that_cannot_be_read_is_a_failure_to_try_again_not_a_build_with_nothing_to_fetch() = runTest(dispatcher) {
        // No connection on first launch: there is no index, so there is no list.
        val inner = FakePackRepository(initiallyInstalled = emptySet())
        var online = false
        val repository = object : PackRepository by inner {
            override val packs: Flow<List<PackState>> = flow {
                if (!online) throw IllegalStateException("no index")
                emitAll(inner.packs)
            }
        }

        val model = FirstDownloadViewModel(repository)
        advanceUntilIdle()

        assertEquals(DownloadPhase.Failed(PackFailure.NETWORK, resumable = false), model.state.value.phase)
        assertTrue(model.state.value.canSkip)

        online = true
        model.onRetry()
        advanceUntilIdle()

        assertEquals(DownloadPhase.Done, model.state.value.phase)
    }
}
