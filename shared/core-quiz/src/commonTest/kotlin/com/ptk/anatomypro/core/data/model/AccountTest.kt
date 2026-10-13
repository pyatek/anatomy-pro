package com.ptk.anatomypro.core.data.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccountTest {

    @Test
    fun a_nickname_is_three_to_twenty_letters_digits_underscores_and_hyphens() {
        // Backend spec §5.2.
        assertTrue(nicknameIsWellFormed("abc"))
        assertTrue(nicknameIsWellFormed("student-4821"))
        assertTrue(nicknameIsWellFormed("os_hyoideum"))
        assertTrue(nicknameIsWellFormed("a".repeat(20)))
        // A learner's own alphabet counts as letters.
        assertTrue(nicknameIsWellFormed("żebro"))
    }

    @Test
    fun a_nickname_too_short_too_long_or_with_anything_else_in_it_is_not_one() {
        assertFalse(nicknameIsWellFormed("ab"))
        assertFalse(nicknameIsWellFormed("a".repeat(21)))
        assertFalse(nicknameIsWellFormed("two words"))
        assertFalse(nicknameIsWellFormed("costa.vii"))
        assertFalse(nicknameIsWellFormed(""))
    }
}
