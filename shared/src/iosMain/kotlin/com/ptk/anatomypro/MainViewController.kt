package com.ptk.anatomypro

import androidx.compose.ui.window.ComposeUIViewController

/**
 * The production entry point. iOS has no debug/release source split, so what protects a
 * release build is that this path never constructs a fake (all-screens spec §5, §6).
 */
fun MainViewController() = ComposeUIViewController { App(rememberAppDependencies()) }