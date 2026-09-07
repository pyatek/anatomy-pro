package com.ptk.anatomypro.core.model

/**
 * A mesh node whose name follows the sourcing convention `<ta_code>__<latin_slug>__<L|R|M>`
 * (model sourcing spec §2.2).
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
) {
    companion object {
        private val PATTERN = Regex("^([A-Za-z0-9_]+)__([a-z0-9_]+)__([LRM])$")

        /** Returns null when [nodeName] does not follow the convention. */
        fun parse(nodeName: String): StructureNode? {
            val match = PATTERN.matchEntire(nodeName) ?: return null
            val (taCode, latinSlug, side) = match.destructured

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

            return StructureNode(StructureId(slug), laterality, taCode, latinSlug)
        }
    }
}
