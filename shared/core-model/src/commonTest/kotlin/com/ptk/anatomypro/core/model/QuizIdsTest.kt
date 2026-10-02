package com.ptk.anatomypro.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class QuizIdsTest {

    @Test
    fun a_topic_id_is_a_slug_like_every_other_id() {
        assertEquals("skeletal-thorax", QuizTopicId("skeletal-thorax").value)
    }

    @Test
    fun a_session_id_generated_at_runtime_is_still_a_slug() {
        assertEquals("session-1a2b3c", QuizSessionId("session-1a2b3c").value)
    }

    @Test
    fun a_question_id_rejects_uppercase_so_ids_cannot_drift_apart_by_case() {
        assertFailsWith<IllegalArgumentException> { QuizQuestionId("Q1") }
    }

    @Test
    fun all_three_reject_blank() {
        assertFailsWith<IllegalArgumentException> { QuizTopicId("") }
        assertFailsWith<IllegalArgumentException> { QuizSessionId("") }
        assertFailsWith<IllegalArgumentException> { QuizQuestionId("") }
    }
}
