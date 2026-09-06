package com.ptk.anatomypro.renderer.filament

/**
 * The narrow seam the Swift/Obj-C++ Filament host implements.
 *
 * Kotlin declares the protocol and Swift injects an implementation at startup, so the
 * Kotlin framework has no compile-time dependency on Filament (spec §4.1). Node names, not
 * StructureIds, cross this boundary: mapping node names back to structures is the app's
 * job, and keeping that knowledge out of the shim is what keeps the shim narrow.
 */
interface FilamentBridge {
    fun initialize()
    fun loadModel(uri: String)
    fun unloadModel(uri: String)
    fun setNodeVisibility(nodeNames: List<String>, visible: Boolean)
    fun setHighlight(nodeNames: List<String>, outlineArgb: Int, luminanceShift: Float)
    fun pickAt(xPx: Float, yPx: Float)
    fun dispose()
}

/** Where the Swift host registers itself before the first renderer is created. */
object FilamentBridgeRegistry {
    var bridge: FilamentBridge? = null
}
