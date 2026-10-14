package com.ptk.anatomypro

import androidx.compose.runtime.Composable

/**
 * Production: Room for what exists, refusals for the rest (all-screens spec §6). Packs are
 * downloaded where the build was given an index to download them from, and the daily and
 * the boards are real where it was given a server, and Google sign-in is offered where it was
 * given a client id.
 */
@Composable
internal fun appEntry(): AppEntry = rememberAppEntry(
    packIndexUrl = BuildConfig.PACK_INDEX_URL,
    apiBaseUrl = BuildConfig.API_BASE_URL,
    signIn = rememberGoogleSignIn(BuildConfig.GOOGLE_SERVER_CLIENT_ID),
)
