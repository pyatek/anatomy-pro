package com.ptk.anatomypro.feature.daily

import com.ptk.anatomypro.core.data.NotBuiltDailyRepository
import com.ptk.anatomypro.core.data.NotBuiltLeaderboardRepository
import com.ptk.anatomypro.core.data.fake.FakeDailyRepository
import com.ptk.anatomypro.core.data.fake.FakeLeaderboardRepository
import com.ptk.anatomypro.core.data.model.Entitlements
import com.ptk.anatomypro.core.data.model.QuizTopic
import com.ptk.anatomypro.core.model.QuizTopicId
import com.ptk.anatomypro.core.model.SystemId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PracticeTest {

    private fun topic(id: String, system: String) =
        QuizTopic(QuizTopicId(id), title = id, system = SystemId(system), structureCount = 8, mastery = 0f)

    private val ribs = topic("costae", "skeletal-system")
    private val chest = topic("musculi-thoracis", "muscular-system")
    private val nerves = topic("nervi-craniales", "nervous-system")

    private val free = Entitlements(subscribed = false, ownedSystems = emptySet())
    private val subscribed = Entitlements(subscribed = true, ownedSystems = emptySet())

    @Test
    fun practice_is_on_a_system_the_student_said_they_study() {
        // All-screens spec §15.4. Order in the list is not what decides.
        assertEquals(chest, practiceTopic(listOf(ribs, chest, nerves), studied = setOf("muscular-system"), subscribed))
    }

    @Test
    fun a_studied_system_the_student_may_not_be_quizzed_on_is_passed_over() {
        assertEquals(ribs, practiceTopic(listOf(chest, ribs), studied = setOf("muscular-system"), free))
    }

    @Test
    fun with_nothing_studied_practice_is_on_the_skeleton() {
        assertEquals(ribs, practiceTopic(listOf(chest, ribs), studied = emptySet(), subscribed))
    }

    @Test
    fun with_no_skeletal_topic_it_is_any_topic_allowed() {
        assertEquals(chest, practiceTopic(listOf(chest, nerves), studied = emptySet(), subscribed))
    }

    @Test
    fun with_no_topic_allowed_there_is_no_practice() {
        assertNull(practiceTopic(listOf(chest, nerves), studied = setOf("muscular-system"), free))
        assertNull(practiceTopic(emptyList(), studied = emptySet(), subscribed))
    }

    @Test
    fun the_daily_is_not_built_where_either_repository_refuses() {
        // All-screens spec §6.
        assertFalse(dailyIsBuilt(NotBuiltDailyRepository, FakeLeaderboardRepository()))
        assertFalse(dailyIsBuilt(FakeDailyRepository(), NotBuiltLeaderboardRepository))
        assertTrue(dailyIsBuilt(FakeDailyRepository(), FakeLeaderboardRepository()))
    }
}
