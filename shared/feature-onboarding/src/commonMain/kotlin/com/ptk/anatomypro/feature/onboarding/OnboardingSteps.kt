package com.ptk.anatomypro.feature.onboarding

import com.ptk.anatomypro.core.data.NotBuiltPackRepository
import com.ptk.anatomypro.core.data.repository.PackRepository

/**
 * Whether onboarding has a download step.
 *
 * It does only where there is something to download with. Production has no PackRepository
 * yet — the one it is given throws on first use, by design (all-screens spec §6) — and the
 * pack ships inside the app, so there onboarding is two steps. When a real PackRepository
 * is built this returns true everywhere and can be deleted.
 */
fun onboardingHasDownloadStep(packs: PackRepository): Boolean = packs !is NotBuiltPackRepository

enum class OnboardingStep { LANGUAGE, GOALS, DOWNLOAD }

/**
 * The steps, in the order they are walked.
 *
 * The prototype's order is language, goals, download, and that holds where the atlas ships
 * inside the app. Where the download is what brings the atlas ([atlasArrivesByDownload]) it
 * comes first: the goals are a choice among the atlas's systems, and before it arrives
 * there are none to offer.
 */
fun onboardingSteps(hasDownload: Boolean, atlasArrivesByDownload: Boolean): List<OnboardingStep> = when {
    !hasDownload -> listOf(OnboardingStep.LANGUAGE, OnboardingStep.GOALS)
    atlasArrivesByDownload -> listOf(OnboardingStep.LANGUAGE, OnboardingStep.DOWNLOAD, OnboardingStep.GOALS)
    else -> listOf(OnboardingStep.LANGUAGE, OnboardingStep.GOALS, OnboardingStep.DOWNLOAD)
}

/**
 * The step that follows [step], or null when onboarding is over.
 *
 * A download that was walked past leaves no atlas, and goals that would follow it have
 * nothing to choose from: onboarding ends there instead. Goals that come before the
 * download are shown either way, because a shipped atlas is only a moment from being read.
 */
fun stepAfter(step: OnboardingStep, steps: List<OnboardingStep>, atlasInstalled: Boolean): OnboardingStep? {
    val next = steps.getOrNull(steps.indexOf(step) + 1) ?: return null
    val goalsFromNoAtlas = next == OnboardingStep.GOALS && step == OnboardingStep.DOWNLOAD && !atlasInstalled
    return if (goalsFromNoAtlas) stepAfter(next, steps, atlasInstalled) else next
}

