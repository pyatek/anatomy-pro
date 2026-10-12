package com.ptk.anatomypro.core.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A pipeline `manifest.json`, as both ends read it.
 *
 * The app reads it into its database and the server into its own (backend spec §10). What
 * a structure is called in each language, and the hash that says which name a reviewer
 * approved, have to come out the same on both, so they are worked out here and nowhere else.
 */
@Serializable
data class PackManifest(
    @SerialName("pack_id") val packId: String,
    val structures: List<ManifestStructure>,
) {
    companion object {
        /** Latin is the canonical key; English is a display locale (spec section 13). */
        const val LOCALE_LATIN = "la"
        const val LOCALE_ENGLISH = "en"

        private val json = Json { ignoreUnknownKeys = true }

        fun parse(manifestJson: String): PackManifest = json.decodeFromString(serializer(), manifestJson)

        /**
         * Identifies the exact name a reviewer approved.
         *
         * The name and nothing else. A verification says this mesh is this term; the definition
         * is an encyclopedia extract with its own source, and hashing it in made approving a
         * name mean approving an article (spec section 30).
         *
         * FNV-1a rather than `hashCode`, so the value is defined here and cannot drift with a
         * compiler or platform change. A hash that silently stopped matching would either
         * resurrect stale approvals or discard sound ones (spec section 7).
         */
        fun contentHash(name: String): String {
            var hash = 0x811C9DC5u
            for (byte in name.encodeToByteArray()) {
                hash = hash xor byte.toUByte().toUInt()
                hash *= 0x01000193u
            }
            return hash.toString(16).padStart(8, '0')
        }
    }
}

@Serializable
data class ManifestStructure(
    @SerialName("structure_id") val structureId: String,
    @SerialName("ta2_id") val ta2Id: String? = null,
    val english: String,
    val latin: String? = null,
    val definition: String? = null,
    @SerialName("definition_source") val definitionSource: String? = null,
    @SerialName("definition_licence") val definitionLicence: String? = null,
    val system: String? = null,
    val region: String? = null,
    @SerialName("parent_id") val parentId: String? = null,
    val laterality: String,
    @SerialName("is_group") val isGroup: Boolean = false,
    val nodes: List<String> = emptyList(),
) {
    /** The structure's name in each language the manifest carries, Latin first. */
    val names: List<Pair<String, String>>
        get() = listOfNotNull(latin?.let { PackManifest.LOCALE_LATIN to it }, PackManifest.LOCALE_ENGLISH to english)
}
