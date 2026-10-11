package com.ptk.anatomypro.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android

// The platform's own HTTP stack. Ktor's OkHttp engine pulls in an OkHttp that needs a newer
// compile SDK than the Android Gradle plugin this project is on supports.
actual fun platformHttpClient(): HttpClient = HttpClient(Android)
