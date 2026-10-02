package com.ptk.anatomypro.core.designsystem

import android.os.LocaleList
import androidx.compose.runtime.Composable
import java.util.Locale

/**
 * compose-resources 1.11.1 resolves against `androidx.compose.ui.text.intl.Locale.current`,
 * which on Android is the process default locale — not `LocalConfiguration`, which was tried
 * first and verified on device to have no effect. So the default is set here, before
 * [content] composes; [ProvideAppLocale]'s `key` is what makes the lookup run again.
 *
 * This is process-wide. Number and date formatting follow the interface locale too, which is
 * what a user who chose it expects.
 */
@Composable
internal actual fun PlatformLocale(locale: String, content: @Composable () -> Unit) {
    val target = Locale.forLanguageTag(locale)
    if (Locale.getDefault() != target) {
        Locale.setDefault(target)
        LocaleList.setDefault(LocaleList(target))
    }
    content()
}
