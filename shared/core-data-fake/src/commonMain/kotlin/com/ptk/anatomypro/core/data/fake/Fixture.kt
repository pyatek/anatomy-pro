package com.ptk.anatomypro.core.data.fake

import com.ptk.anatomypro.core.data.model.StructureDetail
import com.ptk.anatomypro.core.data.model.StructureSummary
import com.ptk.anatomypro.core.model.Laterality
import com.ptk.anatomypro.core.model.StructureId
import com.ptk.anatomypro.core.model.VerificationState

/**
 * One structure in the fake atlas.
 *
 * Verification is tracked per locale, matching VerificationState's own rule: a Latin name
 * may be correct while its Polish translation is wrong.
 */
data class FixtureStructure(
    val id: StructureId,
    val parent: StructureId?,
    val names: Map<String, String>,
    val definition: String?,
    val isGroup: Boolean,
    /** The system id, in the form real packs use. */
    val system: String = "skeletal-system",
    val laterality: Laterality = Laterality.MEDIAN,
    val verification: Map<String, VerificationState> =
        mapOf("la" to VerificationState.VERIFIED, "pl" to VerificationState.VERIFIED, "en" to VerificationState.VERIFIED),
)

private fun roman(index: Int): String =
    listOf("i", "ii", "iii", "iv", "v", "vi", "vii", "viii", "ix", "x", "xi", "xii")[index - 1]

private fun romanUpper(index: Int): String = roman(index).uppercase()

/**
 * The shared fake atlas: one system, two group nodes, and two real sibling sets.
 *
 * Ribs and cervical vertebrae are here because §8.1's hard tier draws distractors from
 * siblings under the same parent, and a fixture of unrelated structures would let a
 * generator look correct while producing questions no student would find hard.
 */
object AtlasFixture {

    private val skeletal = FixtureStructure(
        id = StructureId("skeletal"),
        parent = null,
        names = mapOf("la" to "Systema skeletale", "pl" to "Układ kostny", "en" to "Skeletal system"),
        definition = "The bones of the body, and the joints between them.",
        isGroup = true,
    )

    private val costae = FixtureStructure(
        id = StructureId("costae"),
        parent = StructureId("skeletal"),
        names = mapOf("la" to "Costae", "pl" to "Żebra", "en" to "Ribs"),
        definition = "Twelve paired bones forming the thoracic cage.",
        isGroup = true,
    )

    private val cervicales = FixtureStructure(
        id = StructureId("vertebrae-cervicales"),
        parent = StructureId("skeletal"),
        names = mapOf("la" to "Vertebrae cervicales", "pl" to "Kręgi szyjne", "en" to "Cervical vertebrae"),
        definition = "The seven vertebrae of the neck.",
        isGroup = true,
    )

    private val ribs: List<FixtureStructure> = (1..12).map { index ->
        FixtureStructure(
            id = StructureId("costa-${roman(index)}"),
            parent = costae.id,
            names = mapOf(
                "la" to "Costa ${romanUpper(index)}",
                "pl" to "Żebro ${romanUpper(index)}",
                "en" to "Rib ${romanUpper(index)}",
            ),
            definition = "Rib ${romanUpper(index)}.",
            isGroup = false,
            laterality = Laterality.MEDIAN,
            // Costa XII is deliberately unverified: §7 forbids it as a quiz answer, and a
            // fixture with nothing unverified would let that rule pass untested.
            verification = if (index == 12) {
                mapOf("la" to VerificationState.UNVERIFIED, "pl" to VerificationState.UNVERIFIED, "en" to VerificationState.UNVERIFIED)
            } else {
                mapOf("la" to VerificationState.VERIFIED, "pl" to VerificationState.VERIFIED, "en" to VerificationState.VERIFIED)
            },
        )
    }

    private val cervicalVertebrae: List<FixtureStructure> = (1..7).map { index ->
        FixtureStructure(
            id = StructureId("vertebra-cervicalis-${roman(index)}"),
            parent = cervicales.id,
            names = mapOf(
                "la" to "Vertebra cervicalis ${romanUpper(index)}",
                "pl" to "Kręg szyjny ${romanUpper(index)}",
                "en" to "Cervical vertebra ${romanUpper(index)}",
            ),
            definition = "Cervical vertebra ${romanUpper(index)}.",
            isGroup = false,
        )
    }

    private val muscular = FixtureStructure(
        id = StructureId("muscular"),
        parent = null,
        names = mapOf("la" to "Systema musculare", "pl" to "Układ mięśniowy", "en" to "Muscular system"),
        definition = "The muscles of the body.",
        isGroup = true,
        system = "muscular-system",
    )

    private val thoracicMuscles = FixtureStructure(
        id = StructureId("musculi-thoracis"),
        parent = StructureId("muscular"),
        names = mapOf("la" to "Musculi thoracis", "pl" to "Mięśnie klatki piersiowej", "en" to "Thoracic muscles"),
        definition = "The muscles of the chest wall.",
        isGroup = true,
        system = "muscular-system",
    )

    /** Four, so the group can make a four-option quiz question like the others. */
    private val muscles: List<FixtureStructure> = listOf(
        Triple("musculus-pectoralis-major", "Musculus pectoralis major", "Mięsień piersiowy większy" to "Pectoralis major"),
        Triple("musculus-pectoralis-minor", "Musculus pectoralis minor", "Mięsień piersiowy mniejszy" to "Pectoralis minor"),
        Triple("musculus-serratus-anterior", "Musculus serratus anterior", "Mięsień zębaty przedni" to "Serratus anterior"),
        Triple("musculus-subclavius", "Musculus subclavius", "Mięsień podobojczykowy" to "Subclavius"),
    ).map { (id, latin, local) ->
        FixtureStructure(
            id = StructureId(id),
            parent = thoracicMuscles.id,
            names = mapOf("la" to latin, "pl" to local.first, "en" to local.second),
            definition = "$latin.",
            isGroup = false,
            system = "muscular-system",
        )
    }

    val all: List<FixtureStructure> =
        listOf(skeletal, costae, cervicales) + ribs + cervicalVertebrae +
            listOf(muscular, thoracicMuscles) + muscles

    private val index: Map<StructureId, FixtureStructure> = all.associateBy { it.id }

    fun byId(id: StructureId): FixtureStructure? = index[id]

    fun childrenOf(parent: StructureId): List<FixtureStructure> = all.filter { it.parent == parent }

    fun roots(): List<FixtureStructure> = all.filter { it.parent == null }

    /** Latin is the canonical key, so it is the fallback when a display name is missing. */
    fun nameOf(structure: FixtureStructure, locale: String): String =
        structure.names[locale] ?: structure.names.getValue("la")

    fun summary(id: StructureId, locale: String): StructureSummary? =
        byId(id)?.let { toSummary(it, locale) }

    fun toSummary(structure: FixtureStructure, locale: String) = StructureSummary(
        id = structure.id,
        name = nameOf(structure, locale),
        latinName = structure.names["la"],
        laterality = structure.laterality,
        isGroup = structure.isGroup,
        hasChildren = childrenOf(structure.id).isNotEmpty(),
    )

    /** [StructureDetail.ancestors] is root-first, which is the order the hierarchy displays in. */
    fun ancestorsOf(id: StructureId, locale: String): List<StructureSummary> {
        val chain = mutableListOf<FixtureStructure>()
        var parent = byId(id)?.parent
        while (parent != null) {
            val node = byId(parent) ?: break
            chain += node
            parent = node.parent
        }
        return chain.reversed().map { toSummary(it, locale) }
    }

    fun detail(id: StructureId, locale: String): StructureDetail? {
        val structure = byId(id) ?: return null
        return StructureDetail(
            id = structure.id,
            names = structure.names,
            definition = structure.definition,
            definitionLocale = structure.definition?.let { "en" },
            systemId = structure.system,
            regionId = null,
            laterality = structure.laterality,
            isGroup = structure.isGroup,
            ancestors = ancestorsOf(id, locale),
        )
    }
}
