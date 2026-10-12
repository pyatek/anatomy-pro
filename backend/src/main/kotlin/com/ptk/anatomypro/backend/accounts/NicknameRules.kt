package com.ptk.anatomypro.backend.accounts

import kotlin.random.Random

/**
 * Backend spec §5.2: 3 to 20 characters; letters, digits, `_` and `-`; unique without
 * regard to case; nothing from the blocklist.
 *
 * A blocked word is looked for inside the nickname once everything but letters and digits
 * is gone, so spacing a word out with hyphens does not get it through. That also refuses an
 * innocent nickname that happens to contain one, which is the cheaper mistake of the two.
 */
class NicknameRules(blocklist: Set<String>, private val random: Random = Random.Default) {

    private val blocked = blocklist.map { it.lowercase() }.filter { it.isNotBlank() }

    fun allows(nickname: String): Boolean {
        if (nickname.length !in MIN_LENGTH..MAX_LENGTH) return false
        if (!nickname.all { it.isLetterOrDigit() || it == '_' || it == '-' }) return false
        val bare = nickname.filter { it.isLetterOrDigit() }.lowercase()
        return blocked.none { it in bare }
    }

    /** The nickname as it is compared for uniqueness. */
    fun keyOf(nickname: String): String = nickname.lowercase()

    /** A nickname for an account that has not chosen one, so it can be ranked at once. */
    fun generate(): String = "student-${random.nextInt(1_000, 1_000_000)}"

    private companion object {
        const val MIN_LENGTH = 3
        const val MAX_LENGTH = 20
    }
}
