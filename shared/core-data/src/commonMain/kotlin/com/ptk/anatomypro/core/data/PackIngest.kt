package com.ptk.anatomypro.core.data

import com.ptk.anatomypro.core.data.entity.MeshRefEntity
import com.ptk.anatomypro.core.data.entity.PackEntity
import com.ptk.anatomypro.core.data.entity.StructureDefinitionEntity
import com.ptk.anatomypro.core.data.entity.StructureEntity
import com.ptk.anatomypro.core.data.entity.StructureSearchEntity
import com.ptk.anatomypro.core.data.entity.StructureSynonymEntity
import com.ptk.anatomypro.core.data.entity.StructureTextEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Turns a pipeline `manifest.json` into rows.
 *
 * The pipeline owns the JSON and Room owns the schema, so this is the only place the two
 * meet. Keeping it a pure function of the manifest text - no database, no clock, no file
 * system - is what lets the mapping be tested without either side present.
 */
object PackIngest {

    /** Latin is the canonical key; English is a display locale (spec section 13). */
    const val LOCALE_LATIN = "la"
    const val LOCALE_ENGLISH = "en"

    private val json = Json { ignoreUnknownKeys = true }

    data class Rows(
        val pack: PackEntity,
        val structures: List<StructureEntity>,
        val text: List<StructureTextEntity>,
        val definitions: List<StructureDefinitionEntity>,
        val synonyms: List<StructureSynonymEntity>,
        val meshRefs: List<MeshRefEntity>,
        val search: List<StructureSearchEntity>,
    )

    fun parse(manifestJson: String, version: Long, meshUri: String?): Rows {
        val manifest = json.decodeFromString<Manifest>(manifestJson)
        val packId = manifest.packId

        val structures = mutableListOf<StructureEntity>()
        val text = mutableListOf<StructureTextEntity>()
        val definitions = mutableListOf<StructureDefinitionEntity>()
        val meshRefs = mutableListOf<MeshRefEntity>()
        val search = mutableListOf<StructureSearchEntity>()

        for (entry in manifest.structures) {
            structures += StructureEntity(
                id = entry.structureId,
                taCode = entry.ta2Id,
                parentId = entry.parentId,
                systemId = entry.system,
                regionId = entry.region,
                laterality = entry.laterality,
                packId = packId,
                isGroup = entry.isGroup,
            )

            entry.latin?.let { text += textRow(entry, LOCALE_LATIN, it) }
            text += textRow(entry, LOCALE_ENGLISH, entry.english)

            // The source writes its definitions in English, so that is the one locale a
            // definition is stored under.
            entry.definition?.let {
                definitions += StructureDefinitionEntity(
                    structureId = entry.structureId,
                    locale = LOCALE_ENGLISH,
                    text = it,
                    sourceUrl = entry.definitionSource,
                    licence = entry.definitionLicence,
                )
            }

            entry.nodes.forEach { node ->
                meshRefs += MeshRefEntity(entry.structureId, packId, node)
            }

            // One search row per (locale, term). Synonyms will add more once a source for
            // them exists; nothing emits them today.
            entry.latin?.let { search += searchRow(entry.structureId, LOCALE_LATIN, it) }
            search += searchRow(entry.structureId, LOCALE_ENGLISH, entry.english)
        }

        return Rows(
            pack = PackEntity(id = packId, version = version, installedAt = null, meshUri = meshUri),
            structures = structures,
            text = text,
            definitions = definitions,
            synonyms = emptyList(),
            meshRefs = meshRefs,
            search = search,
        )
    }

    private fun textRow(entry: ManifestStructure, locale: String, name: String) =
        StructureTextEntity(
            structureId = entry.structureId,
            locale = locale,
            name = name,
            contentHash = contentHash(name),
        )

    private fun searchRow(structureId: String, locale: String, term: String) =
        StructureSearchEntity(structureId, locale, normalise(term))

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

    /** Lowercased and accent-folded, so search rows and queries meet in one form. */
    fun normalise(term: String): String = buildString {
        for (character in term.lowercase()) {
            when (character) {
                in 'a'..'z', in '0'..'9', ' ', '-' -> append(character)
                '\u00E0', '\u00E1', '\u00E2', '\u00E4', '\u00E3', '\u00E5' -> append('a')
                '\u00E8', '\u00E9', '\u00EA', '\u00EB' -> append('e')
                '\u00EC', '\u00ED', '\u00EE', '\u00EF' -> append('i')
                '\u00F2', '\u00F3', '\u00F4', '\u00F6', '\u00F5' -> append('o')
                '\u00F9', '\u00FA', '\u00FB', '\u00FC' -> append('u')
                '\u00E7', '\u0107' -> append('c')
                '\u00F1' -> append('n')
                '\u0142' -> append('l')
                '\u0105' -> append('a')
                '\u0119' -> append('e')
                '\u015B' -> append('s')
                '\u017C', '\u017A' -> append('z')
                else -> Unit
            }
        }
    }.trim()

    @Serializable
    private data class Manifest(
        @SerialName("pack_id") val packId: String,
        val structures: List<ManifestStructure>,
    )

    @Serializable
    private data class ManifestStructure(
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
    )
}
