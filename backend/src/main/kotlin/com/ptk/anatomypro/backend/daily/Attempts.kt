package com.ptk.anatomypro.backend.daily

import com.ptk.anatomypro.api.DailyAnswer
import com.ptk.anatomypro.api.LeaderboardRow
import com.ptk.anatomypro.backend.Settings
import com.ptk.anatomypro.backend.transaction
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.sql.Connection
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import javax.sql.DataSource

class Attempt(
    val date: LocalDate,
    val locale: String,
    val startedAt: Instant,
    val submittedAt: Instant?,
    val correct: Int?,
    val elapsedMillis: Long?,
    val ranked: Boolean,
)

class Board(val entries: List<LeaderboardRow>, val me: LeaderboardRow?, val totalPlayers: Int)

/**
 * Attempts at the daily, and the boards read from them (backend spec §6.2, §6.3, §7).
 *
 * Every write here can be repeated: a second start changes nothing and a second submit
 * changes nothing, which is what lets a client retry a request it cannot tell the fate of.
 */
class Attempts(private val database: DataSource, private val settings: Settings) {

    private val answerList = ListSerializer(DailyAnswer.serializer())

    fun find(account: UUID, date: LocalDate): Attempt? = database.connection.use { read(it, account, date) }

    /** Starts the attempt, or returns the one already started with its original clock. */
    fun start(account: UUID, date: LocalDate, locale: String, timed: Boolean, now: Instant): Attempt = database.transaction { connection ->
        connection.prepareStatement(
            "INSERT INTO daily_attempt (account_id, date, locale, timed, started_at) VALUES (?, ?, ?, ?, ?) ON CONFLICT DO NOTHING",
        ).use {
            it.setObject(1, account)
            it.setObject(2, date)
            it.setString(3, locale)
            it.setBoolean(4, timed)
            it.setTimestamp(5, Timestamp.from(now))
            it.executeUpdate()
        }
        read(connection, account, date)!!
    }

    /**
     * Scores and records the attempt, once. A second call finds it already submitted and
     * records nothing, whatever answers it brings.
     *
     * @return the attempt as it stands, or null if it was never started.
     */
    fun submit(account: UUID, set: DailySet, answers: List<DailyAnswer>, now: Instant): Attempt? = database.transaction { connection ->
        val started = read(connection, account, set.date) ?: return@transaction null
        if (started.submittedAt != null) return@transaction started

        val chosen = answers.associate { it.questionId to it.chosen }
        // The right option is the highlighted structure; anything else, or nothing, is wrong.
        val correct = set.questions.count { chosen[it.id] == it.highlighted }
        val elapsed = Duration.between(started.startedAt, now).toMillis()
        val timed = connection.prepareStatement("SELECT timed FROM daily_attempt WHERE account_id = ? AND date = ?").use { query ->
            query.setObject(1, account)
            query.setObject(2, set.date)
            query.executeQuery().use { it.next(); it.getBoolean(1) }
        }
        val deadline = set.date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).plus(Duration.ofMinutes(settings.lateSubmitGraceMinutes))
        val ranked = timed &&
            elapsed >= settings.speedFloorMillisPerQuestion * set.questions.size &&
            !now.isAfter(deadline)

        val recorded = connection.prepareStatement(
            """
            UPDATE daily_attempt SET submitted_at = ?, answers = ?::jsonb, correct = ?, elapsed_millis = ?, ranked = ?
            WHERE account_id = ? AND date = ? AND submitted_at IS NULL
            """.trimIndent(),
        ).use {
            it.setTimestamp(1, Timestamp.from(now))
            it.setString(2, Json.encodeToString(answerList, answers))
            it.setInt(3, correct)
            it.setLong(4, elapsed)
            it.setBoolean(5, ranked)
            it.setObject(6, account)
            it.setObject(7, set.date)
            it.executeUpdate()
        }
        // Only the submit that recorded the attempt counts the day.
        if (recorded == 1) countDay(connection, account, set.date)
        read(connection, account, set.date)
    }

    /** A completed daily extends the streak, ranked or not: only the board's place is the timer's. */
    private fun countDay(connection: Connection, account: UUID, date: LocalDate) {
        connection.prepareStatement(
            """
            UPDATE account SET
                streak_days = CASE
                    WHEN streak_last >= ? THEN streak_days
                    WHEN streak_last = ? THEN streak_days + 1
                    ELSE 1
                END,
                streak_last = CASE WHEN streak_last >= ? THEN streak_last ELSE ? END
            WHERE id = ?
            """.trimIndent(),
        ).use {
            it.setObject(1, date)
            it.setObject(2, date.minusDays(1))
            it.setObject(3, date)
            it.setObject(4, date)
            it.setObject(5, account)
            it.executeUpdate()
        }
    }

    private fun read(connection: Connection, account: UUID, date: LocalDate): Attempt? = connection.prepareStatement(
        "SELECT locale, started_at, submitted_at, correct, elapsed_millis, ranked FROM daily_attempt WHERE account_id = ? AND date = ?",
    ).use { query ->
        query.setObject(1, account)
        query.setObject(2, date)
        query.executeQuery().use { rows ->
            if (!rows.next()) null else Attempt(
                date = date,
                locale = rows.getString(1),
                startedAt = rows.getTimestamp(2).toInstant(),
                submittedAt = rows.getTimestamp(3)?.toInstant(),
                correct = rows.getObject(4) as Int?,
                elapsedMillis = rows.getObject(5) as Long?,
                ranked = rows.getBoolean(6),
            )
        }
    }

    // --- the day's board ------------------------------------------------------------------

    /** How many ranked attempts the day has. */
    fun rankedCount(date: LocalDate): Int = database.connection.use { connection ->
        connection.prepareStatement("SELECT count(*) FROM daily_attempt WHERE date = ? AND ranked").use { query ->
            query.setObject(1, date)
            query.executeQuery().use { it.next(); it.getInt(1) }
        }
    }

    /**
     * [account]'s place on [date]'s board, or null when its attempt is not ranked.
     *
     * One more than the ranked attempts that are better: more right, or as many in less
     * time, or the same in the same time and submitted first (§9.2). Counted on read; the
     * board's index is this order.
     */
    fun rankOf(account: UUID, date: LocalDate): Int? = database.connection.use { connection ->
        connection.prepareStatement(
            """
            SELECT (
                SELECT count(*) FROM daily_attempt o
                WHERE o.date = m.date AND o.ranked AND (
                    o.correct > m.correct
                    OR (o.correct = m.correct AND o.elapsed_millis < m.elapsed_millis)
                    OR (o.correct = m.correct AND o.elapsed_millis = m.elapsed_millis
                        AND (o.submitted_at, o.account_id) < (m.submitted_at, m.account_id))
                )
            ) + 1
            FROM daily_attempt m WHERE m.account_id = ? AND m.date = ? AND m.ranked
            """.trimIndent(),
        ).use { query ->
            query.setObject(1, account)
            query.setObject(2, date)
            query.executeQuery().use { rows -> if (rows.next()) rows.getInt(1) else null }
        }
    }

    fun dailyBoard(account: UUID, date: LocalDate): Board {
        val entries = database.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT a.id, a.nickname, d.correct, d.elapsed_millis FROM daily_attempt d
                JOIN account a ON a.id = d.account_id
                WHERE d.date = ? AND d.ranked
                ORDER BY d.correct DESC, d.elapsed_millis, d.submitted_at, d.account_id
                LIMIT ?
                """.trimIndent(),
            ).use { query ->
                query.setObject(1, date)
                query.setInt(2, settings.boardSize)
                query.executeQuery().use { rows ->
                    buildList {
                        while (rows.next()) {
                            val id = rows.getObject(1, UUID::class.java)
                            add(LeaderboardRow(rank = size + 1, nickname = rows.getString(2), score = rows.getInt(3), elapsedMillis = rows.getLong(4), isMe = id == account))
                        }
                    }
                }
            }
        }
        // The caller's row is read apart, so it is there even below the listed rows.
        val me = entries.firstOrNull { it.isMe } ?: rankOf(account, date)?.let { rank ->
            database.connection.use { connection ->
                connection.prepareStatement(
                    "SELECT a.nickname, d.correct, d.elapsed_millis FROM daily_attempt d JOIN account a ON a.id = d.account_id WHERE d.account_id = ? AND d.date = ?",
                ).use { query ->
                    query.setObject(1, account)
                    query.setObject(2, date)
                    query.executeQuery().use { rows ->
                        rows.next()
                        LeaderboardRow(rank, rows.getString(1), rows.getInt(2), rows.getLong(3), isMe = true)
                    }
                }
            }
        }
        return Board(entries, me, rankedCount(date))
    }

    // --- the streak board -----------------------------------------------------------------

    /**
     * Accounts whose streak is alive on [today]: it ended today or yesterday. Longest
     * first; among equals, the one kept up most recently, then by nickname so the order
     * is the same every time it is read.
     */
    fun streakBoard(account: UUID, today: LocalDate): Board = database.connection.use { connection ->
        val alive = "streak_days > 0 AND streak_last >= ?"
        val since = today.minusDays(1)
        val entries = connection.prepareStatement(
            "SELECT id, nickname, streak_days FROM account WHERE $alive ORDER BY streak_days DESC, streak_last DESC, nickname_key LIMIT ?",
        ).use { query ->
            query.setObject(1, since)
            query.setInt(2, settings.boardSize)
            query.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) {
                        add(LeaderboardRow(rank = size + 1, nickname = rows.getString(2), score = rows.getInt(3), isMe = rows.getObject(1, UUID::class.java) == account))
                    }
                }
            }
        }
        val me = entries.firstOrNull { it.isMe } ?: connection.prepareStatement(
            """
            SELECT m.nickname, m.streak_days, (
                SELECT count(*) FROM account o WHERE o.streak_days > 0 AND o.streak_last >= ? AND (
                    o.streak_days > m.streak_days
                    OR (o.streak_days = m.streak_days AND o.streak_last > m.streak_last)
                    OR (o.streak_days = m.streak_days AND o.streak_last = m.streak_last AND o.nickname_key < m.nickname_key)
                )
            ) + 1
            FROM account m WHERE m.id = ? AND m.streak_days > 0 AND m.streak_last >= ?
            """.trimIndent(),
        ).use { query ->
            query.setObject(1, since)
            query.setObject(2, account)
            query.setObject(3, since)
            query.executeQuery().use { rows ->
                if (!rows.next()) null else LeaderboardRow(rank = rows.getInt(3), nickname = rows.getString(1), score = rows.getInt(2), isMe = true)
            }
        }
        val total = connection.prepareStatement("SELECT count(*) FROM account WHERE $alive").use { query ->
            query.setObject(1, since)
            query.executeQuery().use { it.next(); it.getInt(1) }
        }
        Board(entries, me, total)
    }
}
