package com.ptk.anatomypro.backend

import com.ptk.anatomypro.backend.content.Content
import com.ptk.anatomypro.core.data.quiz.SAMPLE_QUIZ_NODES
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** `backend ingest <manifest.json>` and `backend verify <file>` (backend spec §10). */
class CommandsTest {

    private val content = Content(TestDatabase.fresh())

    private fun file(text: String) = Files.createTempFile("anatomy", ".json").also { Files.writeString(it, text) }.toString()

    @Test
    fun ingest_reads_a_manifest_file_and_says_what_it_read() {
        val said = contentCommand(listOf("ingest", file(SampleContent.manifest())), content)

        assertTrue(SAMPLE_QUIZ_NODES.size.toString() in said!!)
        assertEquals(SAMPLE_QUIZ_NODES.size, content.nodes(SampleContent.PACK, "la").size)
    }

    @Test
    fun verify_reads_a_review_file() {
        contentCommand(listOf("ingest", file(SampleContent.manifest())), content)

        contentCommand(listOf("verify", file(SampleContent.verifications())), content)

        assertTrue(content.nodes(SampleContent.PACK, "la").any { it.verified })
    }

    @Test
    fun anything_else_is_not_a_content_command_and_the_server_starts() {
        assertNull(contentCommand(emptyList(), content))
    }

    @Test
    fun a_command_with_no_file_or_an_unknown_one_is_refused_rather_than_ignored() {
        assertFailsWith<IllegalArgumentException> { contentCommand(listOf("ingest"), content) }
        assertFailsWith<IllegalArgumentException> { contentCommand(listOf("ingset", "x.json"), content) }
    }
}
