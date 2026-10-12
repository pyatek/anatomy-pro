package com.ptk.anatomypro.backend.accounts

import com.ptk.anatomypro.backend.transaction
import java.security.MessageDigest
import java.security.SecureRandom
import java.sql.SQLException
import java.time.LocalDate
import java.util.Base64
import java.util.UUID
import javax.sql.DataSource

data class Account(val id: UUID, val nickname: String, val nicknameLockedUntil: LocalDate?)

/** A new account and the token for the device that made it. The token exists only here. */
class NewAccount(val account: Account, val deviceToken: String)

sealed interface RenameResult {
    data class Renamed(val account: Account) : RenameResult
    data object Taken : RenameResult
}

/**
 * Accounts and the tokens that speak for them (backend spec §5.1).
 *
 * The queries are the design, so they are written out: there is no mapper between this and
 * the tables in `V1__accounts.sql`.
 */
class Accounts(private val database: DataSource, private val nicknames: NicknameRules) {

    private val secureRandom = SecureRandom()

    fun createAnonymous(): NewAccount {
        val token = ByteArray(TOKEN_BYTES).also(secureRandom::nextBytes)
            .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
        // A generated nickname can collide with one already taken; another is drawn.
        repeat(NICKNAME_ATTEMPTS) {
            val account = Account(UUID.randomUUID(), nicknames.generate(), nicknameLockedUntil = null)
            try {
                database.transaction { connection ->
                    connection.prepareStatement("INSERT INTO account (id, nickname, nickname_key) VALUES (?, ?, ?)").use {
                        it.setObject(1, account.id)
                        it.setString(2, account.nickname)
                        it.setString(3, nicknames.keyOf(account.nickname))
                        it.executeUpdate()
                    }
                    connection.prepareStatement("INSERT INTO device_token (token_hash, account_id) VALUES (?, ?)").use {
                        it.setBytes(1, hashOf(token))
                        it.setObject(2, account.id)
                        it.executeUpdate()
                    }
                }
                return NewAccount(account, token)
            } catch (failure: SQLException) {
                if (failure.sqlState != UNIQUE_VIOLATION) throw failure
            }
        }
        error("no free generated nickname in $NICKNAME_ATTEMPTS attempts")
    }

    /** The account [token] speaks for, or null when nobody was given it. */
    fun byToken(token: String): Account? = database.connection.use { connection ->
        connection.prepareStatement(
            """
            SELECT a.id, a.nickname, a.nickname_locked_until
            FROM device_token t JOIN account a ON a.id = t.account_id
            WHERE t.token_hash = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setBytes(1, hashOf(token))
            statement.executeQuery().use { rows ->
                if (!rows.next()) null else Account(
                    id = rows.getObject(1, UUID::class.java),
                    nickname = rows.getString(2),
                    nicknameLockedUntil = rows.getObject(3, LocalDate::class.java),
                )
            }
        }
    }

    /** The unique index decides who has a nickname; nothing is read first to be raced. */
    fun rename(account: Account, nickname: String): RenameResult = try {
        database.transaction { connection ->
            connection.prepareStatement("UPDATE account SET nickname = ?, nickname_key = ? WHERE id = ?").use {
                it.setString(1, nickname)
                it.setString(2, nicknames.keyOf(nickname))
                it.setObject(3, account.id)
                it.executeUpdate()
            }
        }
        RenameResult.Renamed(account.copy(nickname = nickname))
    } catch (failure: SQLException) {
        if (failure.sqlState != UNIQUE_VIOLATION) throw failure
        RenameResult.Taken
    }

    /** The account and everything that hangs from it (§5.4); the tables cascade. */
    fun delete(account: Account) {
        database.transaction { connection ->
            connection.prepareStatement("DELETE FROM account WHERE id = ?").use {
                it.setObject(1, account.id)
                it.executeUpdate()
            }
        }
    }

    private fun hashOf(token: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(token.toByteArray())

    private companion object {
        const val TOKEN_BYTES = 32
        const val NICKNAME_ATTEMPTS = 20
        const val UNIQUE_VIOLATION = "23505"
    }
}
