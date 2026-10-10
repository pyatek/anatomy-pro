package com.ptk.anatomypro.feature.onboarding

import com.ptk.anatomypro.core.data.NotBuiltPackRepository
import com.ptk.anatomypro.core.data.fake.FakePackRepository
import kotlin.test.Test
import kotlin.test.assertFalse
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
}
