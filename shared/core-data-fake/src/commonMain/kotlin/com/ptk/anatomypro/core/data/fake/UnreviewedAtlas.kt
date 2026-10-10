package com.ptk.anatomypro.core.data.fake

import com.ptk.anatomypro.core.data.quiz.QuizGraphSource
import com.ptk.anatomypro.core.data.quiz.QuizNode

/**
 * A harness: the installed atlas with every name treated as reviewed.
 *
 * **It switches §7 off.** No structure has been verified yet, so the real generator has
 * nothing to ask, and a debug build needs questions to look at: tiers, sides, the two
 * formats, on the model actually drawn. It lives in the fake module and cannot reach a
 * release build, where the generator sees verification as it is.
 */
class UnreviewedAtlas(private val real: QuizGraphSource) : QuizGraphSource {
    override suspend fun nodes(locale: String): List<QuizNode> = real.nodes(locale).map { it.copy(verified = true) }
}
