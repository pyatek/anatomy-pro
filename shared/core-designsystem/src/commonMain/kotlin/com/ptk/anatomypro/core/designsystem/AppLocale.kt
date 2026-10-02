package com.ptk.anatomypro.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.key

/** Matches AppSettings.interfaceLocale's default: the app starts in Polish (spec §8). */
const val DEFAULT_APP_LOCALE = "pl"

/** English is the base bundle, so it is what an unknown locale degrades to (spec §8). */
const val BASE_APP_LOCALE = "en"

/** The locales a UI string bundle exists for. Latin is a study locale, never a UI one. */
val SUPPORTED_UI_LOCALES = setOf("en", "pl")

fun resolveAppLocale(requested: String): String =
    if (requested in SUPPORTED_UI_LOCALES) requested else BASE_APP_LOCALE

/**
 * The interface locale, independent of the system's.
 *
 * §13 makes the interface language an app setting: a student may read Polish UI while
 * being examined in Latin. compose-resources resolves against the system locale, so the
 * active locale has to be carried explicitly rather than read from the platform.
 *
 * Reading this is how app code learns the interface locale. It is not what makes
 * stringResource() follow it — see [ProvideAppLocale].
 */
val LocalAppLocale: ProvidableCompositionLocal<String> =
    compositionLocalOf { DEFAULT_APP_LOCALE }

/**
 * Makes everything inside [content] — including compose-resources lookups — use [locale].
 *
 * Providing [LocalAppLocale] alone is not enough: compose-resources never reads it, and was
 * verified on device to keep resolving the system locale (plan Task 2, 2026-10-02). So each
 * platform also overrides the locale compose-resources does read, and [key] rebuilds the
 * subtree so string lookups it has already cached are made again.
 */
@Composable
fun ProvideAppLocale(locale: String, content: @Composable () -> Unit) {
    val resolved = resolveAppLocale(locale)
    CompositionLocalProvider(LocalAppLocale provides resolved) {
        PlatformLocale(resolved) {
            key(resolved) { content() }
        }
    }
}

/** Overrides the locale compose-resources resolves against on this platform. */
@Composable
internal expect fun PlatformLocale(locale: String, content: @Composable () -> Unit)
