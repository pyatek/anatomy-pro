package com.ptk.anatomypro.backend

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import javax.sql.DataSource

data class DatabaseConfig(
    val jdbcUrl: String,
    val user: String,
    val password: String,
    /** Tests give each test a schema of its own; production uses the default one. */
    val schema: String? = null,
    val poolSize: Int = 10,
)

object Database {
    /**
     * Opens the pool and brings the schema up to date.
     *
     * A migration that fails throws, and the server does not start: serving from a schema
     * that is half one version and half another is worse than not serving (spec §11.1).
     */
    fun open(config: DatabaseConfig): DataSource {
        val pool = HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = config.jdbcUrl
                username = config.user
                password = config.password
                maximumPoolSize = config.poolSize
                config.schema?.let { schema = it }
            },
        )
        Flyway.configure()
            .dataSource(pool)
            .apply { config.schema?.let { schemas(it) } }
            .load()
            .migrate()
        return pool
    }
}

/** Runs [block] on one connection, as one transaction. */
fun <T> DataSource.transaction(block: (java.sql.Connection) -> T): T = connection.use { connection ->
    connection.autoCommit = false
    try {
        block(connection).also { connection.commit() }
    } catch (failure: Throwable) {
        connection.rollback()
        throw failure
    }
}
