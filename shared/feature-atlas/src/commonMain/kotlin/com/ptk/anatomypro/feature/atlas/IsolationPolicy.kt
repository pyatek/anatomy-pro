package com.ptk.anatomypro.feature.atlas

import com.ptk.anatomypro.core.model.StructureId

/**
 * What the renderer should show while one structure is being studied.
 *
 * [focus] stays fully opaque, [ghosted] becomes a pale shell, [hidden] is not drawn and
 * not picked. Every structure in the pack falls into exactly one of the three.
 */
data class Isolation(
    val focus: StructureId?,
    val ghosted: Set<StructureId>,
    val hidden: Set<StructureId>,
)

/**
 * Turns a selection into the three sets the renderer understands.
 *
 * This lives here rather than behind an `isolate` verb on `AnatomyRenderer` because it
 * needs §25.1's taxonomy, which lives in `core-data` — and §4 gives the renderer geometry
 * and picking and nothing else. See §26.2.
 *
 * Pure and synchronous: the caller does the querying, so this is testable without a
 * database and without a GPU.
 */
object IsolationPolicy {

    fun resolve(
        focus: StructureId?,
        siblings: Set<StructureId>,
        everything: Set<StructureId>,
    ): Isolation {
        if (focus == null) return Isolation(focus = null, ghosted = emptySet(), hidden = emptySet())

        val ghosted = siblings - focus
        return Isolation(
            focus = focus,
            ghosted = ghosted,
            hidden = everything - ghosted - focus,
        )
    }
}
