package com.ptk.anatomypro.backend

import com.ptk.anatomypro.backend.content.VerificationFile
import com.ptk.anatomypro.backend.content.VerificationRecord
import com.ptk.anatomypro.core.data.ManifestStructure
import com.ptk.anatomypro.core.data.PackManifest
import com.ptk.anatomypro.core.data.quiz.QuizNode
import com.ptk.anatomypro.core.data.quiz.SAMPLE_QUIZ_NODES
import com.ptk.anatomypro.core.model.Laterality
import kotlinx.serialization.json.Json

/** The generator's sample atlas, as the files the server is given: a manifest and a review. */
object SampleContent {
    const val PACK = "skeletal-body"

    fun manifest(nodes: List<QuizNode> = SAMPLE_QUIZ_NODES, pack: String = PACK): String = Json.encodeToString(
        PackManifest.serializer(),
        PackManifest(
            packId = pack,
            structures = nodes.map { node ->
                ManifestStructure(
                    structureId = node.id.value,
                    english = "${node.name} (en)",
                    latin = node.name,
                    system = node.systemId,
                    region = node.regionId,
                    parentId = node.parentId?.value,
                    laterality = when (node.laterality) {
                        Laterality.LEFT -> "L"
                        Laterality.RIGHT -> "R"
                        else -> "M"
                    },
                    isGroup = node.isGroup,
                )
            },
        ),
    )

    /** A review of every node the sample marks verified, in [locales]. */
    fun verifications(nodes: List<QuizNode> = SAMPLE_QUIZ_NODES, locales: List<String> = listOf("la")): String = Json.encodeToString(
        VerificationFile.serializer(),
        VerificationFile(
            nodes.filter { it.verified }.flatMap { node ->
                locales.map { locale ->
                    val name = if (locale == "la") node.name else "${node.name} (en)"
                    VerificationRecord(node.id.value, locale, "VERIFIED", PackManifest.contentHash(name), "2026-10-01T10:00:00Z", "dr-k")
                }
            },
        ),
    )
}
