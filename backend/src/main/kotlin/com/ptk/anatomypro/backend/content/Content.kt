package com.ptk.anatomypro.backend.content

import com.ptk.anatomypro.backend.transaction
import com.ptk.anatomypro.core.data.PackManifest
import com.ptk.anatomypro.core.data.quiz.QuizNameRow
import com.ptk.anatomypro.core.data.quiz.QuizNode
import com.ptk.anatomypro.core.data.quiz.QuizStructureRow
import com.ptk.anatomypro.core.data.quiz.quizNodesOf
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource

/**
 * What the server reads about reviews (backend spec §10). Where the file comes from is the
 * reviewer tool's business; this is only its shape.
 *
 * [nameHash] is `PackManifest.contentHash` of the name that was looked at, and [verifiedAt]
 * an ISO-8601 instant.
 */
@Serializable
data class VerificationRecord(
    val structureId: String,
    val locale: String,
    val state: String,
    val nameHash: String,
    val verifiedAt: String,
    val verifiedBy: String,
)

@Serializable
data class VerificationFile(val verifications: List<VerificationRecord>)

/** The atlas as the server holds it: packs' graphs and names, and the reviews of those names. */
class Content(private val database: DataSource) {

    /**
     * Replaces the pack the manifest describes. Reviews are left as they are: one of a name
     * that came through unchanged still counts, and one of a name that changed stops
     * counting by itself, because its hash no longer matches.
     *
     * @return how many structures the pack now has.
     */
    fun ingest(manifestJson: String): Int {
        val manifest = PackManifest.parse(manifestJson)
        database.transaction { connection ->
            connection.prepareStatement("DELETE FROM structure WHERE pack_id = ?").use {
                it.setString(1, manifest.packId)
                it.executeUpdate()
            }
            connection.prepareStatement(
                "INSERT INTO structure (id, pack_id, parent_id, system_id, region_id, laterality, is_group) VALUES (?, ?, ?, ?, ?, ?, ?)",
            ).use { insert ->
                for (structure in manifest.structures) {
                    insert.setString(1, structure.structureId)
                    insert.setString(2, manifest.packId)
                    insert.setString(3, structure.parentId)
                    insert.setString(4, structure.system)
                    insert.setString(5, structure.region)
                    insert.setString(6, structure.laterality)
                    insert.setBoolean(7, structure.isGroup)
                    insert.addBatch()
                }
                insert.executeBatch()
            }
            connection.prepareStatement(
                "INSERT INTO structure_text (structure_id, locale, name, content_hash) VALUES (?, ?, ?, ?)",
            ).use { insert ->
                for (structure in manifest.structures) {
                    for ((locale, name) in structure.names) {
                        insert.setString(1, structure.structureId)
                        insert.setString(2, locale)
                        insert.setString(3, name)
                        insert.setString(4, PackManifest.contentHash(name))
                        insert.addBatch()
                    }
                }
                insert.executeBatch()
            }
        }
        return manifest.structures.size
    }

    /**
     * Records reviews, each replacing any earlier review of the same name in the same
     * language. The whole file or none of it.
     *
     * @return how many were recorded.
     */
    fun verify(verificationsJson: String): Int {
        val file = Json.decodeFromString(VerificationFile.serializer(), verificationsJson)
        database.transaction { connection ->
            connection.prepareStatement(
                """
                INSERT INTO structure_verification (structure_id, locale, state, verified_text_hash, verified_at, verified_by)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (structure_id, locale) DO UPDATE SET
                    state = EXCLUDED.state,
                    verified_text_hash = EXCLUDED.verified_text_hash,
                    verified_at = EXCLUDED.verified_at,
                    verified_by = EXCLUDED.verified_by
                """.trimIndent(),
            ).use { insert ->
                for (record in file.verifications) {
                    insert.setString(1, record.structureId)
                    insert.setString(2, record.locale)
                    insert.setString(3, record.state)
                    insert.setString(4, record.nameHash)
                    insert.setTimestamp(5, Timestamp.from(Instant.parse(record.verifiedAt)))
                    insert.setString(6, record.verifiedBy)
                    insert.addBatch()
                }
                insert.executeBatch()
            }
        }
        return file.verifications.size
    }

    /**
     * [pack] as the quiz generator reads it, named in [locale]. A node is verified by a
     * review that is an approval and whose hash is still the hash of the name (design §7).
     */
    fun nodes(pack: String, locale: String): List<QuizNode> = database.connection.use { connection ->
        val structures = connection.prepareStatement(
            "SELECT id, parent_id, system_id, region_id, laterality, is_group FROM structure WHERE pack_id = ? ORDER BY id",
        ).use { query ->
            query.setString(1, pack)
            query.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) {
                        add(QuizStructureRow(rows.getString(1), rows.getString(2), rows.getString(3), rows.getString(4), rows.getString(5), rows.getBoolean(6)))
                    }
                }
            }
        }
        val names = connection.prepareStatement(
            """
            SELECT t.structure_id, t.locale, t.name FROM structure_text t
            JOIN structure s ON s.id = t.structure_id
            WHERE s.pack_id = ?
            """.trimIndent(),
        ).use { query ->
            query.setString(1, pack)
            query.executeQuery().use { rows ->
                buildList { while (rows.next()) add(QuizNameRow(rows.getString(1), rows.getString(2), rows.getString(3))) }
            }
        }
        val verified = connection.prepareStatement(
            """
            SELECT v.structure_id, v.locale FROM structure_verification v
            JOIN structure_text t ON t.structure_id = v.structure_id AND t.locale = v.locale
            JOIN structure s ON s.id = t.structure_id
            WHERE s.pack_id = ? AND v.state = 'VERIFIED' AND v.verified_text_hash = t.content_hash
            """.trimIndent(),
        ).use { query ->
            query.setString(1, pack)
            query.executeQuery().use { rows ->
                buildSet { while (rows.next()) add(rows.getString(1) to rows.getString(2)) }
            }
        }
        quizNodesOf(structures, names, verified, locale)
    }
}
