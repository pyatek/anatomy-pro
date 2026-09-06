package com.ptk.anatomypro.core.model

import kotlin.jvm.JvmInline

private val SLUG = Regex("^[a-z0-9]+(-[a-z0-9]+)*$")

private fun requireSlug(value: String, kind: String): String {
    require(value.matches(SLUG)) {
        "$kind must be a lowercase kebab-case slug, was: '$value'"
    }
    return value
}

/** Stable identifier for an anatomical structure. Never reused once assigned (spec §5). */
@JvmInline
value class StructureId(val value: String) {
    init { requireSlug(value, "StructureId") }
}

@JvmInline
value class SystemId(val value: String) {
    init { requireSlug(value, "SystemId") }
}

@JvmInline
value class RegionId(val value: String) {
    init { requireSlug(value, "RegionId") }
}

/** Unit of download, entitlement, and verification release (spec §6). */
@JvmInline
value class PackId(val value: String) {
    init { requireSlug(value, "PackId") }
}
