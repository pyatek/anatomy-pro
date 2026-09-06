package com.ptk.anatomypro.core.model

/**
 * Left and right scapula are two meshes but one concept (spec §5). A quiz asking for
 * "scapula" must accept either, while a harder question may ask for a specific side.
 */
enum class Laterality {
    LEFT,
    RIGHT,
    MEDIAN;

    /** The mirrored side, or null for median structures, which have none. */
    fun opposite(): Laterality? = when (this) {
        LEFT -> RIGHT
        RIGHT -> LEFT
        MEDIAN -> null
    }
}
