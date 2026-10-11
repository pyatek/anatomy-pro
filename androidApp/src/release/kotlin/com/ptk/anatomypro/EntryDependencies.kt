package com.ptk.anatomypro

import androidx.compose.runtime.Composable

/**
 * Production: Room for what exists, refusals for the rest (all-screens spec §6). Packs are
 * downloaded where the build was given an index to download them from.
 */
@Composable
internal fun appEntry(): AppEntry = rememberAppEntry(BuildConfig.PACK_INDEX_URL)
