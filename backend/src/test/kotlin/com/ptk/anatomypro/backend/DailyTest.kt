package com.ptk.anatomypro.backend

import com.ptk.anatomypro.api.ApiErrorCode
import com.ptk.anatomypro.api.DailyResultResponse
import com.ptk.anatomypro.api.DailyState
import com.ptk.anatomypro.core.data.model.QuizFormat
import com.ptk.anatomypro.core.data.model.QuizQuestion
import com.ptk.anatomypro.core.data.quiz.QuizGraph
import com.ptk.anatomypro.core.data.quiz.SAMPLE_QUIZ_NODES
import com.ptk.anatomypro.core.model.QuizTopicId
import com.ptk.anatomypro.api.ApiPaths
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Backend spec §6, and the integrity list of §12. */
class DailyTest {

    private val harness = DailyHarness()

    private fun storedSet(date: String): Pair<String, Long> = harness.database.connection.use { connection ->
        connection.prepareStatement("SELECT topic_id, seed FROM daily_set WHERE date = ?::date").use { query ->
            query.setString(1, date)
            query.executeQuery().use { it.next(); it.getString(1) to it.getLong(2) }
        }
    }

    // --- the day's set (§6.1) -----------------------------------------------------------

    @Test
    fun a_day_not_yet_played_says_how_many_questions_it_has() = harness.run {
        val day = player().today()

        assertEquals(harness.today, day.date)
        assertEquals(DailyState.NOT_STARTED, day.state)
        assertTrue(day.questionCount in 4..5, "the sample's topics have four or five names")
    }

    @Test
    fun the_questions_carry_names_and_do_not_say_which_is_right() = harness.run {
        val response = player().startResponse()

        assertEquals(HttpStatusCode.OK, response.status)
        assertFalse("correct" in response.bodyAsText())
        val attempt = player().start()
        assertEquals(harness.today, attempt.date)
        assertEquals(attempt.questions.map { it.id }.distinct(), attempt.questions.map { it.id })
        for (question in attempt.questions) {
            assertEquals(4, question.options.size)
            assertTrue(question.options.any { it.id == question.highlighted })
            assertTrue(question.options.all { it.nameLocale == "la" && it.name.isNotBlank() })
        }
    }

    @Test
    fun the_stored_set_is_what_the_apps_own_generator_gives_for_that_days_seed() = harness.run {
        val attempt = player().start()
        val (topic, seed) = storedSet(harness.today)

        val expected = QuizGraph(SAMPLE_QUIZ_NODES)
            .questions(QuizTopicId(topic), QuizFormat.NAME_THE_HIGHLIGHTED, 10, seed)
            .map { it as QuizQuestion.NameTheHighlighted }

        assertEquals(expected.map { it.highlighted.value }, attempt.questions.map { it.highlighted })
        assertEquals(expected.map { q -> q.options.map { it.id.value } }, attempt.questions.map { q -> q.options.map { it.id } })
        assertEquals(expected.map { q -> q.options.map { it.name } }, attempt.questions.map { q -> q.options.map { it.name } })
    }

    @Test
    fun everyone_gets_the_same_set() = harness.run {
        assertEquals(player().start().questions, player().start().questions)
    }

    @Test
    fun a_content_update_during_the_day_does_not_change_that_days_questions() = harness.run {
        val morning = player().start()

        harness.content.ingest(SampleContent.manifest(SAMPLE_QUIZ_NODES.map { it.copy(name = "${it.name} bis") }))
        val afternoon = player().start()

        assertEquals(morning.questions, afternoon.questions)
    }

    @Test
    fun only_reviewed_names_are_asked_about_or_offered() = harness.run {
        val unverified = SAMPLE_QUIZ_NODES.filter { !it.verified }.map { it.id.value }.toSet()
        repeat(4) {
            val attempt = player().start()
            assertTrue(attempt.questions.flatMap { q -> q.options.map { it.id } }.none { it in unverified })
            harness.nextDay()
        }
    }

    @Test
    fun a_topic_is_not_asked_again_while_there_are_others_to_ask() = harness.run {
        // Three topics in the sample: three days running are three different ones, and the
        // fourth is not either of the two before it.
        val topics = (1..4).map {
            player().start()
            storedSet(harness.today).first.also { harness.nextDay() }
        }

        assertEquals(3, topics.take(3).toSet().size)
        assertTrue(topics[3] != topics[2] && topics[3] != topics[1])
    }

    @Test
    fun with_nothing_reviewed_there_is_no_daily_and_it_says_so() {
        val bare = DailyHarness(seed = { it.ingest(SampleContent.manifest()) })
        bare.run {
            val player = player()

            assertEquals(DailyState.UNAVAILABLE, player.today().state)
            val start = player.startResponse()
            assertEquals(HttpStatusCode.Conflict, start.status)
            assertEquals(ApiErrorCode.DAILY_UNAVAILABLE, start.errorCode())
        }
    }

    @Test
    fun a_name_must_be_reviewed_in_every_language_the_daily_is_offered_in() {
        // Reviewed in Latin only, and the daily is offered in Latin and English: nothing is eligible.
        val twoLanguages = DailyHarness(Settings(everyoneIsSubscribed = true, dailyLocales = listOf("la", "en")))
        twoLanguages.run { assertEquals(DailyState.UNAVAILABLE, player().today().state) }

        val bothReviewed = DailyHarness(Settings(everyoneIsSubscribed = true, dailyLocales = listOf("la", "en"))) {
            it.ingest(SampleContent.manifest())
            it.verify(SampleContent.verifications(locales = listOf("la", "en")))
        }
        bothReviewed.run {
            val english = player().start(locale = "en")
            assertTrue(english.questions.all { q -> q.options.all { it.nameLocale == "en" && it.name.endsWith("(en)") && it.latinName != null } })
        }
    }

    @Test
    fun a_language_the_daily_is_not_offered_in_is_answered_in_latin_and_says_so() = harness.run {
        val attempt = player().start(locale = "pl")

        assertTrue(attempt.questions.all { q -> q.options.all { it.nameLocale == "la" } })
    }

    // --- the flow (§6.2) ----------------------------------------------------------------

    @Test
    fun starting_twice_returns_the_first_attempt_and_its_clock() = harness.run {
        val player = player()
        val first = player.start()
        harness.pass(Duration.ofSeconds(40))

        val second = player.start()

        assertEquals(first, second)
        assertEquals(Instant.parse("2026-10-12T10:00:00Z").toEpochMilli(), second.startedAtEpochMillis)
    }

    @Test
    fun a_day_is_in_progress_between_start_and_submit_and_completed_after() = harness.run {
        val player = player()
        val attempt = player.start()

        val during = player.today()
        assertEquals(DailyState.IN_PROGRESS, during.state)
        assertEquals(attempt, during.attempt)

        val result = player.play()
        val after = player.today()
        assertEquals(DailyState.COMPLETED, after.state)
        assertEquals(result, after.result)
        assertNull(after.attempt)
    }

    @Test
    fun only_todays_daily_can_be_started() = harness.run {
        val player = player()

        for (date in listOf("2026-10-11", "2026-10-13", "not-a-date")) {
            val response = player.startResponse(date = date)
            assertEquals(HttpStatusCode.Conflict, response.status, date)
            assertEquals(ApiErrorCode.WRONG_DATE, response.errorCode(), date)
        }
    }

    @Test
    fun a_submit_for_a_day_never_started_is_refused() = harness.run {
        val response = player().submitResponse(harness.today, emptyList())

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals(ApiErrorCode.NOT_STARTED, response.errorCode())
    }

    // --- scoring (§6.3) -----------------------------------------------------------------

    @Test
    fun the_server_counts_the_right_answers_and_times_the_attempt_itself() = harness.run {
        val player = player()
        val total = player.today().questionCount

        val result = player.play(right = 3, thinking = Duration.ofMillis(41_500))

        assertEquals(3, result.correct)
        assertEquals(total, result.total)
        assertEquals(41_500, result.elapsedMillis)
        assertTrue(result.ranked)
        assertEquals(1, result.rank)
        assertEquals(1, result.totalPlayers)
    }

    @Test
    fun a_question_left_unanswered_or_answered_with_something_not_offered_is_wrong() = harness.run {
        val player = player()
        val attempt = player.start()
        harness.pass(Duration.ofSeconds(60))

        val answers = attempt.questions.mapIndexed { index, question ->
            when (index) {
                0 -> player.answer(question, rightly = true)
                1 -> player.answer(question, rightly = true).copy(chosen = null)
                2 -> player.answer(question, rightly = true).copy(chosen = "not-an-option")
                else -> player.answer(question, rightly = false)
            }
        }
        val result = player.submitResponse(attempt.date, answers).body<DailyResultResponse>()

        assertEquals(1, result.correct)
    }

    @Test
    fun submitting_twice_returns_the_first_result_whatever_the_second_says() = harness.run {
        val player = player()
        val attempt = player.start()
        harness.pass(Duration.ofSeconds(60))
        val first = player.submitResponse(attempt.date, attempt.questions.map { player.answer(it, rightly = false) }).body<DailyResultResponse>()
        harness.pass(Duration.ofSeconds(60))

        val second = player.submitResponse(attempt.date, attempt.questions.map { player.answer(it, rightly = true) }).body<DailyResultResponse>()

        assertEquals(first, second)
        assertEquals(0, second.correct)
    }

    @Test
    fun an_attempt_faster_than_a_second_a_question_is_kept_but_not_ranked() = harness.run {
        val player = player()

        val result = player.play(thinking = Duration.ofMillis(900))

        assertFalse(result.ranked)
        assertNull(result.rank)
        assertEquals(result.total, result.correct)
        assertEquals(0, result.totalPlayers)
        assertEquals(DailyState.COMPLETED, player.today().state)
    }

    @Test
    fun an_untimed_attempt_is_scored_and_not_ranked() = harness.run {
        val result = player().play(timed = false)

        assertEquals(result.total, result.correct)
        assertFalse(result.ranked)
        assertNull(result.rank)
    }

    @Test
    fun a_submit_just_past_midnight_is_still_ranked_and_one_long_after_is_not() = harness.run {
        val prompt = player()
        val late = player()
        val day = harness.today
        val promptAttempt = prompt.start()
        val lateAttempt = late.start()

        harness.nextDay(at = "00:10:00")
        val inTime = prompt.submitResponse(day, promptAttempt.questions.map { prompt.answer(it, true) }).body<DailyResultResponse>()
        harness.pass(Duration.ofMinutes(10))
        val tooLate = late.submitResponse(day, lateAttempt.questions.map { late.answer(it, true) }).body<DailyResultResponse>()

        assertTrue(inTime.ranked)
        assertEquals(day, inTime.date)
        assertFalse(tooLate.ranked)
        assertEquals(tooLate.total, tooLate.correct)
    }

    @Test
    fun attempts_submitted_at_the_same_moment_are_all_recorded() = harness.run {
        val players = List(8) { player() }
        val attempts = players.map { it.start() }
        harness.pass(Duration.ofSeconds(60))

        val results = coroutineScope {
            players.zip(attempts).map { (player, attempt) ->
                async { player.submitResponse(attempt.date, attempt.questions.map { player.answer(it, true) }).body<DailyResultResponse>() }
            }.awaitAll()
        }

        assertTrue(results.all { it.ranked })
        assertEquals(8, players.first().dailyBoard().totalPlayers)
    }

    // --- who may play (§8.2) ------------------------------------------------------------

    @Test
    fun the_daily_and_the_boards_are_for_subscribers_and_the_gate_is_shut_unless_opened() {
        // The default settings: nobody is a subscriber until stage 7 says who is.
        DailyHarness(Settings()).run {
            val player = player()

            val refusals = listOf(
                player.todayResponse(),
                player.startResponse(),
                player.submitResponse(harness.today, emptyList()),
                player.dailyBoardResponse(),
                player.streakBoardResponse(),
            )

            for (response in refusals) {
                assertEquals(HttpStatusCode.Forbidden, response.status)
                assertEquals(ApiErrorCode.NOT_SUBSCRIBED, response.errorCode())
            }
        }
    }

    @Test
    fun nothing_about_the_daily_is_said_to_a_caller_with_no_token() = harness.run {
        assertEquals(HttpStatusCode.Unauthorized, client.get(ApiPaths.DAILY_TODAY).status)
    }

    @Test
    fun the_set_is_not_the_same_from_one_day_to_the_next() = harness.run {
        val monday = player().start()
        harness.nextDay()
        val tuesday = player().start()

        assertNotEquals(monday.questions, tuesday.questions)
        assertNotNull(storedSet(monday.date))
    }

    @Test
    fun the_first_requests_of_a_day_arriving_together_all_get_one_set() = harness.run {
        val players = List(8) { player() }

        val attempts = coroutineScope { players.map { async { it.start() } }.awaitAll() }

        assertEquals(1, attempts.map { it.questions }.toSet().size)
    }

    // --- leaving (§5.4) -----------------------------------------------------------------

    @Test
    fun a_deleted_account_takes_its_attempts_and_its_place_on_the_board_with_it() = harness.run {
        val stays = player()
        val leaves = player()
        leaves.play(thinking = Duration.ofSeconds(20))
        stays.play(thinking = Duration.ofSeconds(40))

        client.delete(ApiPaths.ACCOUNT) { bearerAuth(leaves.token) }

        val board = stays.dailyBoard()
        assertEquals(listOf(stays.nickname), board.entries.map { it.nickname })
        assertEquals(1, board.me?.rank)
        assertEquals(listOf(stays.nickname), stays.streakBoard().entries.map { it.nickname })
    }
}
