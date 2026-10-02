package com.ptk.anatomypro.core.data.fake

import com.ptk.anatomypro.core.data.model.PackFailure
import com.ptk.anatomypro.core.data.model.PackStatus
import com.ptk.anatomypro.core.data.model.PurchaseOutcome
import com.ptk.anatomypro.core.model.PackId
import com.ptk.anatomypro.core.model.SystemId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FakeCommerceTest {

    @Test
    fun a_board_carries_the_users_own_row_so_your_position_is_not_a_second_call() = runTest {
        val board = FakeLeaderboardRepository().streaks()

        assertTrue(board.entries.isNotEmpty())
        assertEquals(true, board.me?.isMe)
    }

    @Test
    fun an_unranked_user_has_no_row_rather_than_a_row_of_zeroes() = runTest {
        val board = FakeLeaderboardRepository(ranked = false).streaks()

        assertEquals(null, board.me)
    }

    @Test
    fun the_board_is_ordered_by_rank() = runTest {
        val ranks = FakeLeaderboardRepository().streaks().entries.map { it.rank }

        assertEquals(ranks.sorted(), ranks)
    }

    @Test
    fun skeletal_is_playable_before_any_purchase() = runTest {
        val entitlements = FakeEntitlementRepository().entitlements.first()

        assertTrue(entitlements.allows(SystemId("skeletal")))
        assertTrue(!entitlements.allows(SystemId("muscular")))
    }

    @Test
    fun a_purchase_unlocks_everything_and_is_observed_by_the_flow() = runTest {
        val repository = FakeEntitlementRepository()
        val plan = repository.plans().first()

        val outcome = repository.purchase(plan)

        assertIs<PurchaseOutcome.Succeeded>(outcome)
        assertTrue(repository.entitlements.first().allows(SystemId("muscular")))
    }

    @Test
    fun a_purchase_can_be_made_to_fail_so_screen_18_has_an_error_state() = runTest {
        val repository = FakeEntitlementRepository(purchaseSucceeds = false)
        val plan = repository.plans().first()

        assertIs<PurchaseOutcome.Failed>(repository.purchase(plan))
    }

    @Test
    fun the_free_pack_is_installed_and_a_paid_one_is_merely_available() = runTest {
        val packs = FakePackRepository().packs.first()

        assertIs<PackStatus.Installed>(packs.first { it.id == PackId("skeletal-body") }.status)
        assertIs<PackStatus.Available>(packs.first { it.id == PackId("muscular-body") }.status)
    }

    @Test
    fun downloading_a_pack_installs_it_and_deleting_it_makes_it_available_again() = runTest {
        val repository = FakePackRepository()

        repository.download(PackId("muscular-body"))
        assertIs<PackStatus.Installed>(repository.packs.first().first { it.id == PackId("muscular-body") }.status)

        repository.delete(PackId("muscular-body"))
        assertIs<PackStatus.Available>(repository.packs.first().first { it.id == PackId("muscular-body") }.status)
    }

    @Test
    fun no_score_on_either_board_is_negative_including_the_users_own() = runTest {
        val repository = FakeLeaderboardRepository()

        for (board in listOf(repository.daily(LocalDate(2026, 9, 24)), repository.streaks())) {
            val scores = board.entries.map { it.score } + listOfNotNull(board.me?.score)
            assertTrue(scores.all { it >= 0 }, "negative score on a board: $scores")
        }
    }

    @Test
    fun a_user_ranked_below_the_listed_rows_does_not_outscore_them() = runTest {
        val board = FakeLeaderboardRepository().streaks()
        val me = board.me!!

        assertTrue(me.rank > board.entries.last().rank)
        assertTrue(me.score <= board.entries.last().score)
    }

    @Test
    fun a_download_is_observed_passing_through_progress_before_it_installs() = runTest {
        // Screen 03 draws a progress bar from this flow. A StateFlow keeps only its latest
        // value, so progress set without yielding would never reach a collector.
        val repository = FakePackRepository()
        val seen = mutableListOf<PackStatus>()
        backgroundScope.launch {
            repository.packs.collect { packs -> seen += packs.first { it.id == PackId("muscular-body") }.status }
        }
        runCurrent()

        repository.download(PackId("muscular-body"))
        runCurrent()

        assertTrue(seen.any { it is PackStatus.Downloading }, "no progress was observable: $seen")
        assertIs<PackStatus.Installed>(seen.last())
    }

    @Test
    fun a_download_is_queued_before_any_bytes_arrive() = runTest {
        // Screen 03's first state: the request is accepted, nothing is transferring yet.
        val repository = FakePackRepository()
        val seen = mutableListOf<PackStatus>()
        backgroundScope.launch {
            repository.packs.collect { packs -> seen += packs.first { it.id == PackId("muscular-body") }.status }
        }
        runCurrent()

        repository.download(PackId("muscular-body"))
        runCurrent()

        val queued = seen.indexOfFirst { it is PackStatus.Queued }
        val downloading = seen.indexOfFirst { it is PackStatus.Downloading }
        assertTrue(queued in 0 until downloading, "expected Queued before Downloading: $seen")
    }

    @Test
    fun a_download_can_be_made_to_fail_part_way_so_screen_03_can_offer_a_resume() = runTest {
        val repository = FakePackRepository(downloadFailure = PackFailure.NETWORK)

        repository.download(PackId("muscular-body"))

        val status = repository.packs.first().first { it.id == PackId("muscular-body") }.status
        assertIs<PackStatus.Failed>(status)
        assertEquals(PackFailure.NETWORK, status.reason)
        assertTrue(status.resumable)
    }
}
