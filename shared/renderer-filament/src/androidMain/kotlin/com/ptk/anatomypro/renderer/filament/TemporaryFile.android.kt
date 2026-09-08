package com.ptk.anatomypro.renderer.filament

import java.io.File

internal actual fun writeTemporaryFile(name: String, bytes: ByteArray): String {
    val file = File(System.getProperty("java.io.tmpdir") ?: ".", name)
    file.writeBytes(bytes)
    return file.absolutePath
}
