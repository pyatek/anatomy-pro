package com.ptk.anatomypro.backend

import com.ptk.anatomypro.backend.content.Content
import java.io.File

/**
 * `backend ingest <manifest.json>` and `backend verify <file>` (backend spec §10): how
 * content reaches the server. Run on the box, against the same database the server uses.
 *
 * @return what was done, or null when [args] ask for nothing and the server should start.
 * @throws IllegalArgumentException for a command that is not one of these: a mistyped
 *   command must not quietly start a second server instead.
 */
fun contentCommand(args: List<String>, content: Content): String? {
    if (args.isEmpty()) return null
    val file = args.getOrNull(1)
    return when (args[0]) {
        "ingest" -> {
            requireNotNull(file) { "usage: backend ingest <manifest.json>" }
            "ingested ${content.ingest(File(file).readText())} structures from $file"
        }
        "verify" -> {
            requireNotNull(file) { "usage: backend verify <verifications.json>" }
            "recorded ${content.verify(File(file).readText())} reviews from $file"
        }
        else -> throw IllegalArgumentException("unknown command '${args[0]}'; expected ingest or verify")
    }
}
