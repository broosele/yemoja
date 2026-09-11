package yemoja.ui.gui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/*
 * The chapters as they are on disk against the chapters as the application lists them.
 *
 * Runs on the JVM only, because it reads the folder. See ../../../../../../gui/doc.md — `GUI-15`.
 */
class ChaptersTest {

    private val folder = File("../manual")

    private val onDisk = folder.listFiles { f -> f.extension == "md" && f.name != "doc.md" }!!
        .map { it.name }.sorted()

    @Test
    fun `every chapter in the folder is listed, and nothing listed is missing`() {
        assertEquals(onDisk, CHAPTERS.sorted())
    }

    @Test
    fun `every chapter is bundled with the application`() {
        for (file in CHAPTERS) {
            assertNotNull(
                ChaptersTest::class.java.getResource("/manual/$file"),
                "manual/$file should be on the classpath",
            )
        }
    }

    @Test
    fun `every chapter opens with its title and has sections to offer`() {
        for (chapter in read()) {
            val opening = chapter.blocks.first()
            assertTrue(opening is Block.Heading, "${chapter.file} should open with a heading")
            assertTrue(chapter.sections.isNotEmpty(), "${chapter.file} should have a section")
        }
    }

    @Test
    fun `the manual is closed under its own links`() {
        // The convention in manual/doc.md: no link leads out of the folder. A link to a chapter
        // names a listed file, and a link to a section names a heading in it.
        val chapters = read()
        for (chapter in chapters) {
            for (link in linksIn(chapter)) {
                val file = link.substringBefore('#')
                val to = chapters.firstOrNull { it.file == file }
                assertNotNull(to, "${chapter.file} links to $file, which is no chapter")
                val anchor = link.substringAfter('#', "")
                if (anchor.isNotEmpty()) {
                    assertTrue(
                        to.sections.any { it.anchor == anchor },
                        "${chapter.file} links to $link, and ${to.file} has no such section",
                    )
                }
            }
        }
    }

    private fun read(): List<Chapter> =
        CHAPTERS.map { chapterOf(it, File(folder, it).readText()) }

    private fun linksIn(chapter: Chapter): List<String> = chapter.blocks.flatMap { block ->
        when (block) {
            is Block.Heading -> block.text
            is Block.Paragraph -> block.text
            is Block.Bullets -> block.items.flatten()
            is Block.Numbered -> block.items.flatten()
            is Block.Table -> (block.header + block.rows.flatten()).flatten()
            is Block.Code, Block.Rule -> emptyList()
        }
    }.filterIsInstance<Span.Link>().map { it.target }
}
