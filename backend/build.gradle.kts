plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    application
}

// The server (backend spec, 2026-10-11). It shares the wire types and the quiz generator
// with the app rather than restating them.
application {
    mainClass.set("com.ptk.anatomypro.backend.MainKt")
}

dependencies {
    implementation(project(":shared:api-contract"))
    implementation(project(":shared:core-quiz"))
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.contentNegotiation)
    implementation(libs.ktor.server.statusPages)
    implementation(libs.ktor.server.callLogging)
    implementation(libs.ktor.server.rateLimit)
    implementation(libs.ktor.server.forwardedHeader)
    implementation(libs.ktor.serialization.json)
    implementation(libs.hikari)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)
    implementation(libs.postgresql)
    implementation(libs.logback)

    testImplementation(libs.kotlin.test)
    testImplementation(project(":shared:core-quiz-fixtures"))
    testImplementation(project(":shared:api-client"))
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.server.testHost)
    testImplementation(libs.ktor.client.contentNegotiation)
    // A real PostgreSQL, run as a process: ranking, uniqueness and the account merge are
    // things the database does, and an in-memory stand-in would not do them (design §15).
    testImplementation(libs.embeddedPostgres)
    testImplementation(platform(libs.embeddedPostgres.bom))
    testRuntimeOnly(libs.embeddedPostgres.darwinArm64)
    testRuntimeOnly(libs.embeddedPostgres.linuxArm64)
}

tasks.test {
    useJUnitPlatform()
}

// The server on a laptop with nothing to install. See DevServer.kt for what it fakes.
tasks.register<JavaExec>("devServer") {
    group = "application"
    description = "Runs the server over a throwaway PostgreSQL with fixture reviews, on port 8080."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.ptk.anatomypro.backend.dev.DevServerKt")
    workingDir = rootProject.projectDir
}
