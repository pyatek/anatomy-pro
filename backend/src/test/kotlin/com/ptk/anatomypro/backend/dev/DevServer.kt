package com.ptk.anatomypro.backend.dev

import com.ptk.anatomypro.backend.Database
import com.ptk.anatomypro.backend.DatabaseConfig
import com.ptk.anatomypro.backend.SampleContent
import com.ptk.anatomypro.backend.Settings
import com.ptk.anatomypro.backend.backend
import com.ptk.anatomypro.backend.content.Content
import com.ptk.anatomypro.backend.content.VerificationFile
import com.ptk.anatomypro.backend.content.VerificationRecord
import com.ptk.anatomypro.core.data.PackManifest
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant

/**
 * The server on a laptop, with nothing to install: `./gradlew :backend:devServer`.
 *
 * It runs PostgreSQL as a process, loads the skeleton the pipeline built (or the tests'
 * sample atlas where there is none), and listens on port 8080. Everything in it is gone
 * when it stops.
 *
 * **Two things here are false on purpose, and must never be true of a real server.** Every
 * Latin name is marked reviewed, by nobody, so that the daily has something to ask; and
 * every account is treated as a subscriber. That is why this lives with the tests.
 */
fun main() {
    val postgres = EmbeddedPostgres.builder().start()
    val database = Database.open(DatabaseConfig(postgres.getJdbcUrl("postgres", "postgres"), "postgres", "postgres"))

    val built = File("pipeline/build/packs/skeletal-body/manifest.json")
    val manifest = if (built.isFile) built.readText() else SampleContent.manifest()
    val content = Content(database)
    val structures = content.ingest(manifest)
    val reviews = content.verify(
        Json.encodeToString(
            VerificationFile.serializer(),
            VerificationFile(
                PackManifest.parse(manifest).structures.mapNotNull { structure ->
                    structure.latin?.let {
                        VerificationRecord(structure.structureId, "la", "VERIFIED", PackManifest.contentHash(it), Instant.now().toString(), "dev-fixture")
                    }
                },
            ),
        ),
    )
    println("dev server: $structures structures from ${if (built.isFile) built.path else "the sample atlas"}, $reviews names marked reviewed BY NOBODY")
    println("dev server: every account is treated as a subscriber; listening on http://localhost:8080")

    embeddedServer(Netty, port = 8080) { backend(database, Settings(everyoneIsSubscribed = true)) }.start(wait = true)
}
