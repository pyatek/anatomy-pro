package com.ptk.anatomypro.backend.daily

import com.ptk.anatomypro.backend.Settings
import com.ptk.anatomypro.backend.content.Content
import com.ptk.anatomypro.backend.transaction
import com.ptk.anatomypro.core.data.model.QuizFormat
import com.ptk.anatomypro.core.data.model.QuizQuestion
import com.ptk.anatomypro.core.data.quiz.QuizGraph
import com.ptk.anatomypro.core.model.QuizTopicId
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import javax.sql.DataSource
import kotlin.random.Random

/** A name as it was when the set was made. [latin] is beside it for the app's Latin line. */
@Serializable
data class StoredName(val name: String, val latin: String? = null)

/** An option, named in every language the daily is offered in. */
@Serializable
data class StoredOption(val id: String, val names: Map<String, StoredName>)

/** The highlighted structure is the right answer; the app knows as much (spec §4.1). */
@Serializable
data class StoredQuestion(val id: String, val highlighted: String, val options: List<StoredOption>)

class DailySet(val date: LocalDate, val topic: String, val seed: Long, val questions: List<StoredQuestion>)

/**
 * The day's set (backend spec §6.1): made by the app's own generator the first time anyone
 * asks for the date, stored, and read from then on.
 */
class DailySets(private val database: DataSource, private val content: Content, private val settings: Settings) {

    private val questionList = ListSerializer(StoredQuestion.serializer())

    /**
     * The set for [date], or null when nothing is eligible to be asked about (§4.2).
     *
     * No connection is held while the set is generated. Generating reads the atlas on
     * connections of its own, and a request that held one while waiting for another would
     * stop the server dead as soon as a pool's worth of first requests arrived together.
     */
    fun forDate(date: LocalDate, now: Instant): DailySet? {
        database.connection.use { read(it, date) }?.let { return it }
        val made = generate(date) ?: return null
        return database.transaction { connection ->
            // Two first requests can both get here. One insert wins, and both read the winner.
            connection.prepareStatement(
                "INSERT INTO daily_set (date, topic_id, seed, questions, created_at) VALUES (?, ?, ?, ?::jsonb, ?) ON CONFLICT (date) DO NOTHING",
            ).use {
                it.setObject(1, date)
                it.setString(2, made.topic)
                it.setLong(3, made.seed)
                it.setString(4, Json.encodeToString(questionList, made.questions))
                it.setTimestamp(5, Timestamp.from(now))
                it.executeUpdate()
            }
            read(connection, date)
        }
    }

    private fun read(connection: Connection, date: LocalDate): DailySet? =
        connection.prepareStatement("SELECT topic_id, seed, questions FROM daily_set WHERE date = ?").use { query ->
            query.setObject(1, date)
            query.executeQuery().use { rows ->
                if (!rows.next()) null else DailySet(date, rows.getString(1), rows.getLong(2), Json.decodeFromString(questionList, rows.getString(3)))
            }
        }

    private fun generate(date: LocalDate): DailySet? {
        val locales = settings.dailyLocales
        val inLocale = locales.associateWith { content.nodes(settings.dailyPack, it) }
        // Reviewed in every language on offer, each in its own right: a name shown in Latin
        // for want of a translation is not a reviewed translation.
        val reviewedEverywhere = locales
            .map { locale -> inLocale.getValue(locale).filter { it.verified && it.nameLocale == locale }.mapTo(mutableSetOf()) { it.id } }
            .reduce { all, next -> all.apply { retainAll(next) } }
        val graph = QuizGraph(inLocale.getValue(locales.first()).map { it.copy(verified = it.id in reviewedEverywhere) })

        val eligible = graph.topics().map { it.id.value }.sorted()
        if (eligible.isEmpty()) return null

        val seed = date.toEpochDay()
        val topic = restedTopics(eligible, recentTopics(date)).let { it[Random(seed).nextInt(it.size)] }
        val questions = graph.questions(QuizTopicId(topic), QuizFormat.NAME_THE_HIGHLIGHTED, settings.dailyQuestionCount, seed)

        val names = inLocale.mapValues { (_, nodes) -> nodes.associateBy { it.id } }
        return DailySet(
            date = date,
            topic = topic,
            seed = seed,
            questions = questions.map { question ->
                question as QuizQuestion.NameTheHighlighted
                StoredQuestion(
                    id = question.id.value,
                    highlighted = question.highlighted.value,
                    options = question.options.map { option ->
                        StoredOption(
                            id = option.id.value,
                            names = locales.associateWith { locale ->
                                names.getValue(locale).getValue(option.id).let { StoredName(it.name, it.latinName) }
                            },
                        )
                    },
                )
            },
        )
    }

    /** The topics of the days before [date], most recent first. */
    private fun recentTopics(date: LocalDate): List<String> = database.connection.use { connection ->
        connection.prepareStatement("SELECT topic_id FROM daily_set WHERE date < ? AND date >= ? ORDER BY date DESC").use { query ->
            query.setObject(1, date)
            query.setObject(2, date.minusDays(settings.topicRestDays.toLong()))
            query.executeQuery().use { rows -> buildList { while (rows.next()) add(rows.getString(1)) } }
        }
    }

    /**
     * [eligible] without the topics asked most recently — as many of them as can be left
     * out while something remains. With fewer topics than rest days, the one asked longest
     * ago comes round again.
     */
    private fun restedTopics(eligible: List<String>, recent: List<String>): List<String> {
        for (resting in recent.size downTo 0) {
            val rested = eligible - recent.take(resting).toSet()
            if (rested.isNotEmpty()) return rested
        }
        return eligible
    }
}
