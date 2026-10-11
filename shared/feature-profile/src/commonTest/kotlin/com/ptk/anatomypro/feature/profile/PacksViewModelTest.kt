package com.ptk.anatomypro.feature.profile

import com.ptk.anatomypro.core.data.fake.FakePackRepository
import com.ptk.anatomypro.core.data.model.PackFailure
import com.ptk.anatomypro.core.data.model.PackState
import com.ptk.anatomypro.core.data.model.PackStatus
import com.ptk.anatomypro.core.data.repository.PackRepository
import com.ptk.anatomypro.core.model.PackId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PacksViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val skeletal = PackId("skeletal-body")
    private val muscular = PackId("muscular-body")

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun PacksUiState.status(id: PackId): PackStatus = packs.first { it.id == id }.status

    @Test
    fun installed_and_available_packs_are_both_listed() = runTest(dispatcher) {
        // §9, screen 19: installed; available.
        val model = PacksViewModel(FakePackRepository())
        advanceUntilIdle()

        assertEquals(3, model.state.value.packs.size)
        assertEquals(PackStatus.Installed, model.state.value.status(skeletal))
        assertEquals(PackStatus.Available, model.state.value.status(muscular))
        assertFalse(model.state.value.isLoading)
    }

    @Test
    fun what_is_installed_is_totalled() = runTest(dispatcher) {
        val model = PacksViewModel(FakePackRepository())
        advanceUntilIdle()

        assertEquals(48_300_000L, model.state.value.installedBytes)
    }

    @Test
    fun a_download_is_seen_in_progress_and_ends_installed() = runTest(dispatcher) {
        // §9, screen 19: downloading.
        val model = PacksViewModel(FakePackRepository())
        advanceUntilIdle()
        val seen = mutableListOf<PackStatus>()
        val watching = backgroundScope.launchWatching(model, muscular, seen)

        model.onDownload(muscular)
        advanceUntilIdle()
        watching.cancel()

        assertTrue(seen.any { it is PackStatus.Downloading }, "no progress was seen: $seen")
        assertEquals(PackStatus.Installed, model.state.value.status(muscular))
    }

    @Test
    fun a_download_can_be_cancelled_and_the_pack_is_available_again() = runTest(dispatcher) {
        val model = PacksViewModel(FakePackRepository())
        advanceUntilIdle()

        model.onDownload(muscular)
        runCurrent()
        model.onCancel(muscular)
        advanceUntilIdle()

        // The fake's download has no real duration, so this proves the request reaches the
        // repository and its answer reaches the screen — not that a transfer was interrupted.
        // FakeCommerceTest covers stopping one half way.
        assertEquals(PackStatus.Available, model.state.value.status(muscular))
    }

    @Test
    fun deleting_asks_first_and_deletes_nothing_until_it_is_confirmed() = runTest(dispatcher) {
        // §9, screen 19: delete confirm. §12: a destructive action is confirmed.
        val model = PacksViewModel(FakePackRepository())
        advanceUntilIdle()

        model.onDeleteRequested(skeletal)
        advanceUntilIdle()

        assertEquals(skeletal, model.state.value.confirmingDelete)
        assertEquals(PackStatus.Installed, model.state.value.status(skeletal))
    }

    @Test
    fun confirming_deletes_the_pack_that_was_asked_about() = runTest(dispatcher) {
        val model = PacksViewModel(FakePackRepository())
        advanceUntilIdle()
        model.onDeleteRequested(skeletal)

        model.onDeleteConfirmed()
        advanceUntilIdle()

        assertEquals(PackStatus.Available, model.state.value.status(skeletal))
        assertNull(model.state.value.confirmingDelete)
    }

    @Test
    fun dismissing_keeps_the_pack() = runTest(dispatcher) {
        val model = PacksViewModel(FakePackRepository())
        advanceUntilIdle()
        model.onDeleteRequested(skeletal)

        model.onDeleteDismissed()
        advanceUntilIdle()

        assertEquals(PackStatus.Installed, model.state.value.status(skeletal))
        assertNull(model.state.value.confirmingDelete)
    }

    @Test
    fun only_an_installed_pack_can_be_asked_about() = runTest(dispatcher) {
        val model = PacksViewModel(FakePackRepository())
        advanceUntilIdle()

        model.onDeleteRequested(muscular)

        assertNull(model.state.value.confirmingDelete)
    }

    @Test
    fun a_confirmation_is_dropped_when_its_pack_is_no_longer_there_to_delete() = runTest(dispatcher) {
        // Review Focus 3. The list changes under the question.
        val listed = MutableStateFlow(
            listOf(PackState(skeletal, 1, "Układ kostny", 48_300_000, "sha256:2f1c0a", status = PackStatus.Installed)),
        )
        val repository = object : PackRepository {
            override val packs: Flow<List<PackState>> = listed
            override suspend fun download(id: PackId) = Unit
            override suspend fun cancel(id: PackId) = Unit
            override suspend fun delete(id: PackId) = Unit
        }
        val model = PacksViewModel(repository)
        advanceUntilIdle()
        model.onDeleteRequested(skeletal)

        listed.value = emptyList()
        advanceUntilIdle()

        assertNull(model.state.value.confirmingDelete)
    }

    @Test
    fun a_download_that_fails_is_shown_as_failed_and_can_be_tried_again() = runTest(dispatcher) {
        // Review Focus 4.
        val model = PacksViewModel(FakePackRepository(downloadFailure = PackFailure.NETWORK, failingAttempts = 1))
        advanceUntilIdle()

        model.onDownload(muscular)
        advanceUntilIdle()
        val failed = assertIs<PackStatus.Failed>(model.state.value.status(muscular))
        assertTrue(failed.resumable)

        model.onDownload(muscular)
        advanceUntilIdle()
        assertEquals(PackStatus.Installed, model.state.value.status(muscular))
    }

    @Test
    fun a_list_that_cannot_be_read_is_a_failure_not_an_empty_manager() = runTest(dispatcher) {
        val repository = object : PackRepository {
            override val packs: Flow<List<PackState>> = flow { throw IllegalStateException("no list") }
            override suspend fun download(id: PackId) = Unit
            override suspend fun cancel(id: PackId) = Unit
            override suspend fun delete(id: PackId) = Unit
        }

        val model = PacksViewModel(repository)
        advanceUntilIdle()

        assertTrue(model.state.value.failed)
        assertFalse(model.state.value.isLoading)
    }

    @Test
    fun an_action_that_throws_does_not_take_the_manager_down() = runTest(dispatcher) {
        val real = FakePackRepository()
        val repository = object : PackRepository by real {
            override suspend fun download(id: PackId) = throw IllegalStateException("offline")
        }
        val model = PacksViewModel(repository)
        advanceUntilIdle()

        model.onDownload(muscular)
        advanceUntilIdle()

        assertEquals(3, model.state.value.packs.size)
        assertFalse(model.state.value.failed)
    }
}

/** Records every status [pack] passes through in [model]'s state. */
private fun CoroutineScope.launchWatching(
    model: PacksViewModel,
    pack: PackId,
    into: MutableList<PackStatus>,
) = launch {
    model.state.collect { state -> state.packs.firstOrNull { it.id == pack }?.let { into += it.status } }
}
