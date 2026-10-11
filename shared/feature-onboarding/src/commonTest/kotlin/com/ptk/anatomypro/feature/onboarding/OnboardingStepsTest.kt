package com.ptk.anatomypro.feature.onboarding

import com.ptk.anatomypro.core.data.NotBuiltPackRepository
import com.ptk.anatomypro.core.data.fake.FakePackRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OnboardingStepsTest {

    @Test
    fun with_a_pack_repository_there_is_a_download_step() {
        assertTrue(onboardingHasDownloadStep(FakePackRepository()))
    }

    @Test
    fun without_one_the_download_step_is_left_out_rather_than_left_to_crash() {
        // Production has no PackRepository yet: NotBuiltPackRepository throws on first use
        // (all-screens spec §6), and the pack is bundled in the app. Showing screen 03 there
        // would crash a first run.
        assertFalse(onboardingHasDownloadStep(NotBuiltPackRepository))
    }

    // --- the order of the steps ---------------------------------------------------------

    @Test
    fun with_the_atlas_shipped_in_the_app_the_goals_come_before_the_download() {
        // The prototype's order, 01 to 03.
        assertEquals(
            listOf(OnboardingStep.LANGUAGE, OnboardingStep.GOALS, OnboardingStep.DOWNLOAD),
            onboardingSteps(hasDownload = true, atlasArrivesByDownload = false),
        )
        assertEquals(
            listOf(OnboardingStep.LANGUAGE, OnboardingStep.GOALS),
            onboardingSteps(hasDownload = false, atlasArrivesByDownload = false),
        )
    }

    @Test
    fun where_the_download_is_what_brings_the_atlas_it_comes_before_the_goals() {
        // The goals are a choice among the atlas's systems: there are none to offer before it.
        assertEquals(
            listOf(OnboardingStep.LANGUAGE, OnboardingStep.DOWNLOAD, OnboardingStep.GOALS),
            onboardingSteps(hasDownload = true, atlasArrivesByDownload = true),
        )
    }

    @Test
    fun each_step_leads_to_the_next_and_the_last_to_the_end() {
        val steps = onboardingSteps(hasDownload = true, atlasArrivesByDownload = true)

        assertEquals(OnboardingStep.DOWNLOAD, stepAfter(OnboardingStep.LANGUAGE, steps, atlasInstalled = false))
        assertEquals(OnboardingStep.GOALS, stepAfter(OnboardingStep.DOWNLOAD, steps, atlasInstalled = true))
        assertNull(stepAfter(OnboardingStep.GOALS, steps, atlasInstalled = true))
    }

    @Test
    fun a_download_walked_past_ends_onboarding_rather_than_offering_goals_from_no_atlas() {
        val steps = onboardingSteps(hasDownload = true, atlasArrivesByDownload = true)

        assertNull(stepAfter(OnboardingStep.DOWNLOAD, steps, atlasInstalled = false))
    }

    @Test
    fun the_goals_are_still_shown_while_a_shipped_atlas_is_being_read_in() {
        // Shipped in the app, the atlas is a moment away, and the step says so and can be passed.
        val steps = onboardingSteps(hasDownload = true, atlasArrivesByDownload = false)

        assertEquals(OnboardingStep.GOALS, stepAfter(OnboardingStep.LANGUAGE, steps, atlasInstalled = false))
    }
}
