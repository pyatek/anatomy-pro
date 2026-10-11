package com.ptk.anatomypro

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import platform.Foundation.NSBundle

/**
 * The production entry point. iOS has no debug/release source split, so what protects a
 * release build is that this path never constructs a fake (all-screens spec §5, §6).
 *
 * `AnatomyProPackIndexURL` in Info.plist is where packs are downloaded from (backend spec
 * §9). Without it the app draws the pack shipped inside it.
 */
fun MainViewController() = ComposeUIViewController {
    val packIndexUrl = remember { NSBundle.mainBundle.objectForInfoDictionaryKey("AnatomyProPackIndexURL") as? String }
    val entry = rememberAppEntry(packIndexUrl)
    App(entry.dependencies, entry.pack)
}
