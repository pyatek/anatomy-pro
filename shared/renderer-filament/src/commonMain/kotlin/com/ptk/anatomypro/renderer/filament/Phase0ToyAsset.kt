package com.ptk.anatomypro.renderer.filament

/**
 * A three-node glTF built in code.
 *
 * Throwaway, and in commonMain rather than a test source set because both platforms'
 * contract tests and the Phase 0 harness need something to draw before the Z-Anatomy
 * pipeline output is wired into the app. It goes when the first real content pack ships.
 *
 * The names follow the sourcing convention exactly (model sourcing spec §2.2) — proving
 * the naming round-trips is as much a part of Phase 0 as proving the GPU works.
 *
 * Layout, viewed down -Z: three unit cubes in a row, the median one at the origin. The
 * centre of a square viewport therefore hits [MEDIAN_NODE], and a corner hits nothing.
 */
object Phase0ToyAsset {

    const val LEFT_NODE = "A02_4_01_001__scapula__L"
    const val MEDIAN_NODE = "A02_2_00_000__columna_vertebralis__M"
    const val RIGHT_NODE = "A02_4_01_001__scapula__R"

    /** Writes the asset to a temporary file and returns its path, building it once. */
    val path: String by lazy { writeTemporaryFile("anatomypro-phase0-toy.glb", build()) }

    private const val HALF = 0.5f
    private const val SPACING = 1.5f

    private fun build(): ByteArray {
        val positions = ArrayList<Float>(24 * 3)
        val normals = ArrayList<Float>(24 * 3)

        // Six faces, four corners each, wound counter-clockwise seen from outside.
        val faces = listOf(
            floatArrayOf(0f, 0f, 1f, -HALF, -HALF, HALF, HALF, -HALF, HALF, HALF, HALF, HALF, -HALF, HALF, HALF),
            floatArrayOf(0f, 0f, -1f, HALF, -HALF, -HALF, -HALF, -HALF, -HALF, -HALF, HALF, -HALF, HALF, HALF, -HALF),
            floatArrayOf(1f, 0f, 0f, HALF, -HALF, HALF, HALF, -HALF, -HALF, HALF, HALF, -HALF, HALF, HALF, HALF),
            floatArrayOf(-1f, 0f, 0f, -HALF, -HALF, -HALF, -HALF, -HALF, HALF, -HALF, HALF, HALF, -HALF, HALF, -HALF),
            floatArrayOf(0f, 1f, 0f, -HALF, HALF, HALF, HALF, HALF, HALF, HALF, HALF, -HALF, -HALF, HALF, -HALF),
            floatArrayOf(0f, -1f, 0f, -HALF, -HALF, -HALF, HALF, -HALF, -HALF, HALF, -HALF, HALF, -HALF, -HALF, HALF),
        )
        val indices = ArrayList<Short>(36)
        faces.forEachIndexed { face, data ->
            repeat(4) { corner ->
                val offset = 3 + corner * 3
                positions += data[offset]
                positions += data[offset + 1]
                positions += data[offset + 2]
                normals += data[0]
                normals += data[1]
                normals += data[2]
            }
            val base = (face * 4).toShort()
            listOf(0, 1, 2, 0, 2, 3).forEach { indices += (base + it).toShort() }
        }

        val positionBytes = floatsToBytes(positions)
        val normalBytes = floatsToBytes(normals)
        val indexBytes = shortsToBytes(indices)
        val binary = positionBytes + normalBytes + padTo4(indexBytes)

        val json = """
            {"asset":{"version":"2.0","generator":"AnatomyPro Phase 0 fixture"},
            "scene":0,"scenes":[{"nodes":[0,1,2]}],
            "nodes":[
            {"name":"$LEFT_NODE","mesh":0,"translation":[${-SPACING},0,0]},
            {"name":"$MEDIAN_NODE","mesh":0,"translation":[0,0,0]},
            {"name":"$RIGHT_NODE","mesh":0,"translation":[$SPACING,0,0]}],
            "meshes":[{"primitives":[{"attributes":{"POSITION":0,"NORMAL":1},"indices":2,"material":0}]}],
            "materials":[{"pbrMetallicRoughness":{"baseColorFactor":[0.8,0.8,0.8,1.0],"metallicFactor":0.0,"roughnessFactor":0.8}}],
            "accessors":[
            {"bufferView":0,"componentType":5126,"count":24,"type":"VEC3","min":[${-HALF},${-HALF},${-HALF}],"max":[$HALF,$HALF,$HALF]},
            {"bufferView":1,"componentType":5126,"count":24,"type":"VEC3"},
            {"bufferView":2,"componentType":5123,"count":36,"type":"SCALAR"}],
            "bufferViews":[
            {"buffer":0,"byteOffset":0,"byteLength":${positionBytes.size},"target":34962},
            {"buffer":0,"byteOffset":${positionBytes.size},"byteLength":${normalBytes.size},"target":34962},
            {"buffer":0,"byteOffset":${positionBytes.size + normalBytes.size},"byteLength":${indexBytes.size},"target":34963}],
            "buffers":[{"byteLength":${binary.size}}]}
        """.trimIndent().replace("\n", "")

        val jsonBytes = padTo4(json.encodeToByteArray(), pad = ' '.code.toByte())
        return glb(jsonBytes, binary)
    }

    /** Wraps JSON and binary chunks in the GLB container. */
    private fun glb(json: ByteArray, binary: ByteArray): ByteArray {
        val total = 12 + 8 + json.size + 8 + binary.size
        val out = ByteArray(total)
        var cursor = 0
        fun putInt(value: Int) {
            out[cursor++] = (value and 0xFF).toByte()
            out[cursor++] = ((value shr 8) and 0xFF).toByte()
            out[cursor++] = ((value shr 16) and 0xFF).toByte()
            out[cursor++] = ((value shr 24) and 0xFF).toByte()
        }
        fun putBytes(bytes: ByteArray) {
            bytes.copyInto(out, cursor)
            cursor += bytes.size
        }

        putBytes("glTF".encodeToByteArray())
        putInt(2)
        putInt(total)
        putInt(json.size); putInt(0x4E4F534A); putBytes(json)
        putInt(binary.size); putInt(0x004E4942); putBytes(binary)
        return out
    }

    private fun floatsToBytes(values: List<Float>): ByteArray {
        val out = ByteArray(values.size * 4)
        values.forEachIndexed { index, value ->
            val bits = value.toRawBits()
            out[index * 4] = (bits and 0xFF).toByte()
            out[index * 4 + 1] = ((bits shr 8) and 0xFF).toByte()
            out[index * 4 + 2] = ((bits shr 16) and 0xFF).toByte()
            out[index * 4 + 3] = ((bits shr 24) and 0xFF).toByte()
        }
        return out
    }

    private fun shortsToBytes(values: List<Short>): ByteArray {
        val out = ByteArray(values.size * 2)
        values.forEachIndexed { index, value ->
            out[index * 2] = (value.toInt() and 0xFF).toByte()
            out[index * 2 + 1] = ((value.toInt() shr 8) and 0xFF).toByte()
        }
        return out
    }

    private fun padTo4(bytes: ByteArray, pad: Byte = 0): ByteArray {
        val remainder = bytes.size % 4
        if (remainder == 0) return bytes
        return bytes + ByteArray(4 - remainder) { pad }
    }
}

/** Somewhere the platform can write a file the renderer will be able to open by path. */
internal expect fun writeTemporaryFile(name: String, bytes: ByteArray): String
