package com.ptk.anatomypro.renderer.filament

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.create
import platform.Foundation.writeToFile

@OptIn(ExperimentalForeignApi::class)
internal actual fun writeTemporaryFile(name: String, bytes: ByteArray): String {
    val file = NSTemporaryDirectory() + name
    bytes.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
            .writeToFile(file, atomically = true)
    }
    return file
}
