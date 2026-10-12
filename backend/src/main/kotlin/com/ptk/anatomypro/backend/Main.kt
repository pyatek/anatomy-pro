package com.ptk.anatomypro.backend

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

/**
 * Configuration is the environment's, from a file on the server that is not in the
 * repository (backend spec §11.1). A missing value stops the server at start, by name.
 */
fun main() {
    fun env(name: String): String = System.getenv(name) ?: error("$name is not set")

    val database = Database.open(
        DatabaseConfig(
            jdbcUrl = env("DATABASE_URL"),
            user = env("DATABASE_USER"),
            password = env("DATABASE_PASSWORD"),
        ),
    )
    val blocklist = object {}.javaClass.getResourceAsStream("/nickname-blocklist.txt")!!
        .bufferedReader().readLines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .toSet()
    val settings = Settings(
        nicknameBlocklist = blocklist,
        trustForwardedHeaders = System.getenv("TRUST_FORWARDED_HEADERS") == "true",
    )

    embeddedServer(Netty, port = (System.getenv("PORT") ?: "8080").toInt()) { backend(database, settings) }.start(wait = true)
}
