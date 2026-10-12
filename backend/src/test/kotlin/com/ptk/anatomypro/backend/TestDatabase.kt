package com.ptk.anatomypro.backend

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource

/**
 * One PostgreSQL for the whole test run, and a schema of its own for each test.
 *
 * The server is started once because starting it is the slow part; the schemas keep tests
 * from seeing each other's rows, and each is migrated from nothing, so every test also
 * proves the migrations run on an empty database.
 */
object TestDatabase {
    // Every test opens a pool of its own and none is closed before the run ends, so the
    // server is told to expect a crowd and each pool is kept small.
    private val postgres: EmbeddedPostgres by lazy {
        EmbeddedPostgres.builder().setServerConfig("max_connections", "1000").start()
    }
    private val counter = AtomicInteger()

    fun fresh(): DataSource {
        val schema = "test_${counter.incrementAndGet()}"
        postgres.postgresDatabase.connection.use { it.createStatement().execute("CREATE SCHEMA $schema") }
        return Database.open(
            DatabaseConfig(
                jdbcUrl = postgres.getJdbcUrl("postgres", "postgres") + "&currentSchema=$schema",
                user = "postgres",
                password = "postgres",
                schema = schema,
                poolSize = 4,
            ),
        )
    }
}
