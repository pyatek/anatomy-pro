package com.ptk.anatomypro.core.network

import io.ktor.client.HttpClient

/** The HTTP client for this platform: HttpURLConnection on Android, URLSession on iOS. */
expect fun platformHttpClient(): HttpClient
