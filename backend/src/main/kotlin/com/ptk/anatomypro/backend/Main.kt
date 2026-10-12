package com.ptk.anatomypro.backend

import com.ptk.anatomypro.backend.content.Content
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

/**
 * Configuration is the environment's, from a file on the server that is not in the
 * repository (backend spec §11.1). A missing value stops the server at start, by name.
 */
fun main(args: Array<String>) {
    fun env(name: String): String = System.getenv(name) ?: error("$name is not set")

    val database = Database.open(
        DatabaseConfig(
            jdbcUrl = env("DATABASE_URL"),
            user = env("DATABASE_USER"),
            password = env("DATABASE_PASSWORD"),
        ),
    )
    // `backend ingest …` and `backend verify …` do their work and end.
    contentCommand(args.toList(), Content(database))?.let { done ->
        println(done)
        return
    }

    val blocklist = object {}.javaClass.getResourceAsStream("/nickname-blocklist.txt")!!
        .bufferedReader().readLines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .toSet()
    val settings = Settings(
        nicknameBlocklist = blocklist,
        trustForwardedHeaders = System.getenv("TRUST_FORWARDED_HEADERS") == "true",
        // Until purchases are verified nobody is a subscriber, unless this says otherwise.
        everyoneIsSubscribed = System.getenv("EVERYONE_IS_SUBSCRIBED") == "true",
    )

    embeddedServer(Netty, port = (System.getenv("PORT") ?: "8080").toInt()) { backend(database, settings) }.start(wait = true)
}
