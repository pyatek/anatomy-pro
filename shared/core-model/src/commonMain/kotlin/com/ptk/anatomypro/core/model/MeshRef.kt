package com.ptk.anatomypro.core.model

/**
 * One node of geometry inside a pack. A structure holds a *list* of these: the vertebral
 * column and muscle groups span many nodes, and assuming one mesh per structure breaks on
 * real data (spec §5).
 */
data class MeshRef(val packId: PackId, val nodeName: String) {
    init { require(nodeName.isNotBlank()) { "nodeName must not be blank" } }
}
