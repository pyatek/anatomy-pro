package com.ptk.anatomypro.core.data.quiz

import com.ptk.anatomypro.core.model.Laterality
import com.ptk.anatomypro.core.model.StructureId

private fun group(id: String, parent: String?, name: String, system: String? = null) = QuizNode(
    id = StructureId(id),
    parentId = parent?.let(::StructureId),
    systemId = system,
    regionId = null,
    laterality = Laterality.MEDIAN,
    isGroup = true,
    name = name,
    nameLocale = "la",
    latinName = name,
    verified = true,
)

private fun leaf(
    id: String,
    parent: String,
    name: String,
    system: String = "skeletal-system",
    region: String = "trunk",
    side: Laterality = Laterality.MEDIAN,
    verified: Boolean = true,
) = QuizNode(
    id = StructureId(id),
    parentId = StructureId(parent),
    systemId = system,
    regionId = region,
    laterality = side,
    isGroup = false,
    name = name,
    nameLocale = "la",
    latinName = name,
    verified = verified,
)

/**
 * A small atlas with every case the generator has to handle.
 *
 * Ribs drawn on both sides under one name, one of them unverified; thoracic vertebrae in the
 * same region under another parent (medium distractors for a rib); muscles in another
 * system (easy ones); a carpal group with too few verified bones to be a topic, one of which
 * shares a name with a rib.
 */
val SAMPLE_QUIZ_NODES: List<QuizNode> = buildList {
    add(group("skeleton", null, "Systema skeletale"))
    add(group("skeleton-thoracis", "skeleton", "Skeleton thoracis"))
    add(group("costae", "skeleton-thoracis", "Costae"))
    add(group("vertebrae-thoracicae", "skeleton-thoracis", "Vertebrae thoracicae"))
    add(group("ossa-carpi", "skeleton", "Ossa carpi"))
    add(group("costae-homonym-group", "skeleton", "Costae"))
    add(group("musculi", null, "Systema musculare"))
    add(group("musculi-thoracis", "musculi", "Musculi thoracis"))

    for (n in 1..5) {
        val name = if (n == 1) "Caput" else "Costa $n"
        add(leaf("costa-$n-left", "costae", name, side = Laterality.LEFT))
        add(leaf("costa-$n-right", "costae", name, side = Laterality.RIGHT))
    }
    add(leaf("costa-6-left", "costae", "Costa 6", side = Laterality.LEFT, verified = false))
    add(leaf("costa-6-right", "costae", "Costa 6", side = Laterality.RIGHT, verified = false))

    for (n in 1..4) add(leaf("vertebra-t$n", "vertebrae-thoracicae", "Vertebra T$n"))

    add(leaf("os-carpi-homonym", "ossa-carpi", "Caput", region = "upper-limb"))
    add(leaf("os-carpi-2", "ossa-carpi", "Os lunatum", region = "upper-limb"))
    add(leaf("os-carpi-3", "ossa-carpi", "Os triquetrum", region = "upper-limb"))
    add(leaf("os-carpi-4", "ossa-carpi", "Os pisiforme", region = "upper-limb", verified = false))

    for (n in 1..4) add(leaf("musculus-$n", "musculi-thoracis", "Musculus $n", system = "muscular-system"))
}
