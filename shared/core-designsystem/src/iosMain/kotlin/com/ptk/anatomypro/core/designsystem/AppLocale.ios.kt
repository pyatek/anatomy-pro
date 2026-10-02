package com.ptk.anatomypro.core.designsystem

import androidx.compose.runtime.Composable
import platform.Foundation.NSUserDefaults

private const val APPLE_LANGUAGES = "AppleLanguages"

/**
 * compose-resources resolves against `androidx.compose.ui.text.intl.Locale.current`, which on
 * iOS follows `NSLocale.preferredLanguages`, which reads the app's `AppleLanguages` default.
 * Verified on the simulator with a Polish system language: the bundle follows this setting
 * live, without a relaunch. Persistence across launches is not relied on — the interface
 * locale comes from AppSettings on every start.
 */
@Composable
internal actual fun PlatformLocale(locale: String, content: @Composable () -> Unit) {
    NSUserDefaults.standardUserDefaults.setObject(listOf(locale), forKey = APPLE_LANGUAGES)
    content()
}
