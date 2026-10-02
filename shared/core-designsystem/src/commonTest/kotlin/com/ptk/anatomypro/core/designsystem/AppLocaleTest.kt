package com.ptk.anatomypro.core.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals

class AppLocaleTest {

    @Test
    fun the_default_is_the_apps_default_interface_locale_not_the_systems() {
        assertEquals("pl", DEFAULT_APP_LOCALE)
    }

    @Test
    fun a_supported_locale_is_kept() {
        assertEquals("en", resolveAppLocale("en"))
        assertEquals("pl", resolveAppLocale("pl"))
    }

    @Test
    fun an_unsupported_locale_falls_back_to_english_because_english_is_the_base_bundle() {
        assertEquals("en", resolveAppLocale("de"))
        assertEquals("en", resolveAppLocale(""))
    }
}
