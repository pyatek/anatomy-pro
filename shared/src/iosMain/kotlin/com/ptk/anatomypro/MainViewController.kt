package com.ptk.anatomypro

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import platform.Foundation.NSBundle

/**
 * The production entry point. iOS has no debug/release source split, so what protects a
 * release build is that this path never constructs a fake (all-screens spec §5, §6).
 *
 * `AnatomyProPackIndexURL` in Info.plist is where packs are downloaded from (backend spec
 * §9). Without it the app draws the pack shipped inside it. `AnatomyProApiURL` is the
 * server behind the daily, the boards and entitlements; without it their tabs keep their
 * placeholders.
 */
fun MainViewController() = ComposeUIViewController {
    val packIndexUrl = remember { NSBundle.mainBundle.objectForInfoDictionaryKey("AnatomyProPackIndexURL") as? String }
    val apiBaseUrl = remember { NSBundle.mainBundle.objectForInfoDictionaryKey("AnatomyProApiURL") as? String }
    val entry = rememberAppEntry(packIndexUrl, apiBaseUrl)
    App(entry)
}
