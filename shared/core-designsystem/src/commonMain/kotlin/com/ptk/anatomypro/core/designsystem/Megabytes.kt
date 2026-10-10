package com.ptk.anatomypro.core.designsystem

/**
 * A byte count in megabytes to one decimal place, truncated.
 *
 * Decimal megabytes, as download sizes are quoted. Truncated rather than rounded so the
 * figure never claims bytes that have not arrived. [decimalSeparator] comes from a string
 * resource: common Kotlin has no locale-aware number formatter.
 */
fun megabytes(bytes: Long, decimalSeparator: String): String {
    val tenths = bytes.coerceAtLeast(0) / 100_000
    return "${tenths / 10}$decimalSeparator${tenths % 10}"
}
