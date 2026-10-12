package com.ptk.anatomypro.backend

import com.ptk.anatomypro.backend.accounts.NicknameRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Backend spec §5.2. */
class NicknamesTest {

    private val rules = NicknameRules(blocklist = setOf("kurwa", "admin"))

    @Test
    fun three_to_twenty_letters_digits_underscores_and_hyphens() {
        assertTrue(rules.allows("ala"))
        assertTrue(rules.allows("Żaneta_92-b"))
        assertTrue(rules.allows("a".repeat(20)))

        assertFalse(rules.allows("al"))
        assertFalse(rules.allows("a".repeat(21)))
        assertFalse(rules.allows("ala ma"))
        assertFalse(rules.allows("ala@kot"))
        assertFalse(rules.allows(""))
    }

    @Test
    fun a_blocked_word_is_refused_however_it_is_cased_or_dressed() {
        assertFalse(rules.allows("Admin"))
        assertFalse(rules.allows("xX_KURWA_Xx"))
        assertFalse(rules.allows("k-u-r-w-a"))
    }

    @Test
    fun two_nicknames_that_differ_only_in_case_are_the_same_nickname() {
        assertEquals(rules.keyOf("Żaneta"), rules.keyOf("żANETA"))
    }

    @Test
    fun a_generated_nickname_is_one_the_rules_allow() {
        repeat(50) { assertTrue(rules.allows(rules.generate()), "generated a nickname its own rules refuse") }
    }
}
