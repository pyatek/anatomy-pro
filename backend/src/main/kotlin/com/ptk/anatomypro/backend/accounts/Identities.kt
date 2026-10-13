package com.ptk.anatomypro.backend.accounts

import com.ptk.anatomypro.api.IdentityProvider
import com.ptk.anatomypro.backend.transaction
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.sql.DataSource

/** The account the caller now speaks for, and whether getting there merged another into it. */
class Linked(val account: UUID, val merged: Boolean)

/**
 * Signing in (backend spec §5.3): an identity is attached to the calling account, or — when
 * it already opens another account — the calling account is merged into that one.
 */
class Identities(private val database: DataSource) {

    fun link(caller: UUID, provider: IdentityProvider, subject: String, now: Instant): Linked = database.transaction { connection ->
        // Claimed if nobody has it. Two devices signing in at once both get here; one
        // insert wins, and both then read whose it is.
        connection.prepareStatement(
            "INSERT INTO identity (provider, subject, account_id, linked_at) VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING",
        ).use {
            it.setString(1, provider.name)
            it.setString(2, subject)
            it.setObject(3, caller)
            it.setTimestamp(4, Timestamp.from(now))
            it.executeUpdate()
        }
        val owner = connection.prepareStatement("SELECT account_id FROM identity WHERE provider = ? AND subject = ?").use { query ->
            query.setString(1, provider.name)
            query.setString(2, subject)
            query.executeQuery().use { it.next(); it.getObject(1, UUID::class.java) }
        }
        if (owner == caller) {
            Linked(caller, merged = false)
        } else {
            merge(connection, from = caller, into = owner, now = now)
            Linked(owner, merged = true)
        }
    }

    /**
     * Folds [from] into [into] and deletes it, all in the caller's transaction: any failure
     * leaves both accounts exactly as they were.
     *
     * What survives (§5.3): every device and identity of either account now opens [into];
     * one attempt per date, and where both played the same date the one submitted first,
     * because that is the one the board has been showing; [into]'s nickname; and a streak
     * counted again from the attempts that are left.
     */
    private fun merge(connection: Connection, from: UUID, into: UUID, now: Instant) {
        // Both rows are locked in one order, so two merges cannot wait on each other.
        connection.prepareStatement("SELECT id FROM account WHERE id IN (?, ?) ORDER BY id FOR UPDATE").use {
            it.setObject(1, from)
            it.setObject(2, into)
            it.executeQuery().close()
        }
        // Where both played a date and [from]'s attempt is the one to keep, [into]'s gives way…
        connection.prepareStatement(
            """
            DELETE FROM daily_attempt kept_out USING daily_attempt better
            WHERE kept_out.account_id = ? AND better.account_id = ? AND better.date = kept_out.date
              AND better.submitted_at IS NOT NULL
              AND (kept_out.submitted_at IS NULL OR better.submitted_at < kept_out.submitted_at)
            """.trimIndent(),
        ).use {
            it.setObject(1, into)
            it.setObject(2, from)
            it.executeUpdate()
        }
        // …and every attempt of [from]'s on a date [into] has none for moves across. The
        // rest of [from]'s go with it when it is deleted.
        connection.prepareStatement(
            """
            UPDATE daily_attempt SET account_id = ?
            WHERE account_id = ? AND date NOT IN (SELECT date FROM daily_attempt WHERE account_id = ?)
            """.trimIndent(),
        ).use {
            it.setObject(1, into)
            it.setObject(2, from)
            it.setObject(3, into)
            it.executeUpdate()
        }
        for (table in listOf("device_token", "identity")) {
            connection.prepareStatement("UPDATE $table SET account_id = ? WHERE account_id = ?").use {
                it.setObject(1, into)
                it.setObject(2, from)
                it.executeUpdate()
            }
        }
        recountStreak(connection, into)
        connection.prepareStatement("DELETE FROM account WHERE id = ?").use {
            it.setObject(1, from)
            it.executeUpdate()
        }
        connection.prepareStatement("INSERT INTO account_merge (surviving_account, merged_account, merged_at) VALUES (?, ?, ?)").use {
            it.setObject(1, into)
            it.setObject(2, from)
            it.setTimestamp(3, Timestamp.from(now))
            it.executeUpdate()
        }
    }

    /** The run of consecutive completed days that ends at the last one. */
    private fun recountStreak(connection: Connection, account: UUID) {
        val days = connection.prepareStatement(
            "SELECT date FROM daily_attempt WHERE account_id = ? AND submitted_at IS NOT NULL ORDER BY date DESC",
        ).use { query ->
            query.setObject(1, account)
            query.executeQuery().use { rows -> buildList { while (rows.next()) add(rows.getObject(1, LocalDate::class.java)) } }
        }
        val run = days.withIndex().takeWhile { (index, day) -> day == days.first().minusDays(index.toLong()) }.size
        connection.prepareStatement("UPDATE account SET streak_days = ?, streak_last = ? WHERE id = ?").use {
            it.setInt(1, run)
            it.setObject(2, days.firstOrNull())
            it.setObject(3, account)
            it.executeUpdate()
        }
    }
}
