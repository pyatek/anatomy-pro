package com.ptk.anatomypro.feature.atlas.scene

import com.ptk.anatomypro.core.model.StructureId
import com.ptk.anatomypro.core.model.SystemId
import com.ptk.anatomypro.feature.atlas.Isolation
import com.ptk.anatomypro.renderer.api.AnatomyRenderer

/** How one system is drawn (screen 07). Each mode has a text label, never only a colour (§12). */
enum class LayerMode { Visible, Ghosted, Hidden }

/** Everything the renderer needs to know about visibility, as sets it understands. */
data class RenderState(
    val hidden: Set<StructureId>,
    val ghosted: Set<StructureId>,
    val ghostAlpha: Float,
) {
    companion object {
        val None = RenderState(hidden = emptySet(), ghosted = emptySet(), ghostAlpha = 1f)
    }
}

/**
 * Asks the camera to frame [structure], or the whole model when it is null. [serial] makes
 * asking for the same thing twice a new request, so a screen can re-frame something the
 * user scrolled away from.
 */
data class FocusRequest(val structure: StructureId?, val durationMs: Int, val serial: Int)

object SceneResolver {

    /**
     * Layers and isolation in one answer. Isolation wins when it has a focus: it is the
     * narrower, more recent request, and the prototype shows it overriding the panel.
     */
    fun resolve(
        layers: Map<SystemId, LayerMode>,
        membership: Map<SystemId, Set<StructureId>>,
        isolation: Isolation?,
        ghostAlpha: Float,
    ): RenderState {
        if (isolation?.focus != null) {
            return RenderState(isolation.hidden, isolation.ghosted, ghostAlpha)
        }
        fun membersIn(mode: LayerMode) = layers.filterValues { it == mode }.keys
            .flatMapTo(mutableSetOf()) { membership[it].orEmpty() }

        val hidden = membersIn(LayerMode.Hidden)
        val ghosted = membersIn(LayerMode.Ghosted) - hidden
        if (hidden.isEmpty() && ghosted.isEmpty()) return RenderState.None
        return RenderState(hidden, ghosted, ghostAlpha)
    }
}

/**
 * Moves [renderer] from [previous] to [next] with the fewest calls. Show and un-ghost come
 * first, so a structure moving between the two sets is never briefly in both.
 */
fun applyRenderState(renderer: AnatomyRenderer, previous: RenderState, next: RenderState) {
    val show = previous.hidden - next.hidden
    if (show.isNotEmpty()) renderer.setVisibility(show, visible = true)

    val unghost = previous.ghosted - next.ghosted
    if (unghost.isNotEmpty()) renderer.setOpacity(unghost, alpha = 1f)

    val hide = next.hidden - previous.hidden
    if (hide.isNotEmpty()) renderer.setVisibility(hide, visible = false)

    val alphaChanged = next.ghostAlpha != previous.ghostAlpha
    val ghost = if (alphaChanged) next.ghosted else next.ghosted - previous.ghosted
    if (ghost.isNotEmpty()) renderer.setOpacity(ghost, next.ghostAlpha)
}
