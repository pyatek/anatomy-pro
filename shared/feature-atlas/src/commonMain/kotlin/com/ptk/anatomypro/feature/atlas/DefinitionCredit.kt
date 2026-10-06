package com.ptk.anatomypro.feature.atlas

/**
 * Who to credit for a definition, or null when the pack recorded nothing about it.
 *
 * Definitions are share-alike text and have to be attributed where they are shown (spec
 * §29.3). Text with a licence but no address is the atlas's own writing.
 */
internal fun definitionSourceName(sourceUrl: String?, licence: String?): String? = when {
    sourceUrl == null -> if (licence != null) "Z-Anatomy" else null
    "wikipedia.org" in sourceUrl -> "Wikipedia"
    else -> sourceUrl.substringAfter("://").substringBefore('/')
}
