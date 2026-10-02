package com.ptk.anatomypro.core.model

import kotlin.jvm.JvmInline

private val QUIZ_SLUG = Regex("^[a-z0-9]+(-[a-z0-9]+)*$")

private fun requireQuizSlug(value: String, kind: String): String {
    require(value.matches(QUIZ_SLUG)) {
        "$kind must be a lowercase kebab-case slug, was: '$value'"
    }
    return value
}

/** A system or region a quiz can be scoped to (spec §8.2). */
@JvmInline
value class QuizTopicId(val value: String) {
    init { requireQuizSlug(value, "QuizTopicId") }
}

/** One run through a topic. Generated at runtime, still a slug. */
@JvmInline
value class QuizSessionId(val value: String) {
    init { requireQuizSlug(value, "QuizSessionId") }
}

@JvmInline
value class QuizQuestionId(val value: String) {
    init { requireQuizSlug(value, "QuizQuestionId") }
}
