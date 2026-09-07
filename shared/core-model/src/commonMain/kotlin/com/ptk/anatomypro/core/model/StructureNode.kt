package com.ptk.anatomypro.core.model

/**
 * A mesh node whose name follows the sourcing convention, optionally with a trailing
 * discriminator: `<ta_code>__<latin_slug>__<L|R|M>[__<discriminator>]` (model sourcing
 * spec §2.2).
 *
 * The discriminator exists because a structure may be modelled as several objects while
 * glTF node names must stay unique. It does not enter the [StructureId], so those objects
 * resolve to one structure with several [MeshRef]s — which is what MeshRef being a list
 * per structure already meant (spec §5, §2.1).
 *
 * The convention is deliberately self-describing: the renderer can turn a picked node into
 * a [StructureId] with no content database behind it, which is what lets Phase 0 prove
 * picking before `core-data` exists (design spec §20.2). It also means a botched export
 * fails visibly — an unparseable name resolves to nothing rather than to the wrong
 * structure.
 */
data class StructureNode(
    val structure: StructureId,
    val laterality: Laterality,
    val taCode: String,
    val latinSlug: String,
    val discriminator: String? = null,
) {
    companion object {
        private val PATTERN = Regex("^([A-Za-z0-9_]+)__([a-z0-9_]+)__([LRM])(?:__([A-Za-z0-9]+))?$")

        /** Returns null when [nodeName] does not follow the convention. */
        fun parse(nodeName: String): StructureNode? {
            val match = PATTERN.matchEntire(nodeName) ?: return null
            val (taCode, latinSlug, side, discriminator) = match.destructured

            val laterality = when (side) {
                "L" -> Laterality.LEFT
                "R" -> Laterality.RIGHT
                else -> Laterality.MEDIAN
            }

            // The TA code is part of the identifier because Latin names are not unique on
            // their own, and §5 requires an identifier that is never reused.
            val slug = buildString {
                append(taCode.lowercase().replace('_', '-'))
                append('-')
                append(latinSlug.replace('_', '-'))
                append('-')
                append(laterality.name.lowercase())
            }

            return StructureNode(
                structure = StructureId(slug),
                laterality = laterality,
                taCode = taCode,
                latinSlug = latinSlug,
                discriminator = discriminator.ifEmpty { null },
            )
        }
    }
}
