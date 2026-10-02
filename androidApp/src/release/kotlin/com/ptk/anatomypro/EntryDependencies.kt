package com.ptk.anatomypro

import androidx.compose.runtime.Composable
import com.ptk.anatomypro.core.data.AppDependencies

/** Production: Room for what exists, refusals for the rest (all-screens spec §6). */
@Composable
internal fun appDependencies(): AppDependencies = rememberAppDependencies()
