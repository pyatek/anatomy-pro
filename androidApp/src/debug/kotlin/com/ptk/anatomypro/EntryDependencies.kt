package com.ptk.anatomypro

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.ptk.anatomypro.core.data.AppDependencies
import com.ptk.anatomypro.core.data.fake.fakeAppDependencies

/**
 * Debug builds run entirely on fakes, so every screen is reachable without a database,
 * a pack on disk, or a backend.
 */
@Composable
internal fun appDependencies(): AppDependencies = remember { fakeAppDependencies() }
