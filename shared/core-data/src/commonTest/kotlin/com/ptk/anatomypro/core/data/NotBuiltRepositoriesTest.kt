package com.ptk.anatomypro.core.data

import com.ptk.anatomypro.core.data.model.AppSettings
import com.ptk.anatomypro.core.data.model.SearchHit
import com.ptk.anatomypro.core.data.model.StructureDetail
import com.ptk.anatomypro.core.data.model.StructureSummary
import com.ptk.anatomypro.core.data.repository.AtlasRepository
import com.ptk.anatomypro.core.data.repository.SettingsRepository
import com.ptk.anatomypro.core.model.StructureId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** Empty, but real: the point is that it answers rather than throws. */
private class StandInAtlasRepository : AtlasRepository {
    override suspend fun roots(locale: String) = emptyList<StructureSummary>()
    override suspend fun children(parent: StructureId, locale: String) = emptyList<StructureSummary>()
    override suspend fun summary(id: StructureId, locale: String): StructureSummary? = null
    override suspend fun detail(id: StructureId, locale: String): StructureDetail? = null
    override suspend fun search(query: String, limit: Int) = emptyList<SearchHit>()
    override suspend fun systems() = emptyList<com.ptk.anatomypro.core.model.SystemId>()
    override suspend fun structuresIn(system: com.ptk.anatomypro.core.model.SystemId) = emptySet<StructureId>()
    override suspend fun allStructures() = emptySet<StructureId>()
}

private class StandInSettingsRepository : SettingsRepository {
    private val state = MutableStateFlow(AppSettings())
    override val settings: Flow<AppSettings> = state.asStateFlow()
    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        state.value = transform(state.value)
    }
}

/**
 * The production path must not be able to serve invented data.
 *
 * A fabricated leaderboard reaching a real user is worse than a crash, so the six
 * repositories Phase 3 has not built yet throw rather than answer.
 */
class NotBuiltRepositoriesTest {

    @Test
    fun the_quiz_is_not_built() = runTest {
        assertFailsWith<NotImplementedError> { NotBuiltQuizRepository.topics("pl") }
        assertFailsWith<NotImplementedError> { NotBuiltQuizRepository.finish(SESSION) }
    }

    @Test
    fun progress_is_not_built() = runTest {
        assertFailsWith<NotImplementedError> { NotBuiltProgressRepository.masteryBySystem("pl") }
        assertFailsWith<NotImplementedError> { NotBuiltProgressRepository.streak.first() }
    }

    @Test
    fun the_daily_quiz_is_not_built() = runTest {
        assertFailsWith<NotImplementedError> { NotBuiltDailyRepository.today() }
    }

    @Test
    fun the_leaderboard_is_not_built() = runTest {
        assertFailsWith<NotImplementedError> { NotBuiltLeaderboardRepository.streaks() }
    }

    @Test
    fun entitlements_are_not_built() = runTest {
        assertFailsWith<NotImplementedError> { NotBuiltEntitlementRepository.plans() }
    }

    @Test
    fun packs_are_not_built() = runTest {
        assertFailsWith<NotImplementedError> { NotBuiltPackRepository.packs.first() }
    }

    @Test
    fun the_two_repositories_that_do_exist_are_carried_through_untouched() = runTest {
        val atlas = StandInAtlasRepository()
        val settings = StandInSettingsRepository()

        val dependencies = notBuiltAppDependencies(atlas, settings)

        assertFailsWith<NotImplementedError> { dependencies.quiz.topics("pl") }
        // The atlas is the real one, so it answers.
        dependencies.atlas!!.roots("pl")
    }

    private companion object {
        val SESSION = com.ptk.anatomypro.core.model.QuizSessionId("session-0")
    }
}
