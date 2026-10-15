package dev.cazimir.voicevault.vault

import dev.cazimir.voicevault.settings.NoteSettings
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * This fork's note-body options, exercised both directly and through [VaultWriter.writeNote].
 * Kept apart from upstream's `VaultWriterTest` so upstream changes to that file merge cleanly.
 */
class NoteExtrasTest {

    /** Records note writes; everything else is irrelevant to the body format. */
    private class RecordingStorage : VaultStorage {
        val notes = linkedMapOf<String, String>()

        override fun access(): VaultAccess = VaultAccess.Available
        override fun folderInfo(): VaultFolderInfo? = null
        override fun writeNote(fileName: String, content: String) {
            notes[fileName] = content
        }
        override fun noteExists(fileName: String): Boolean = fileName in notes
        override fun noteBaseNames(): List<String> = notes.keys.map { it.removeSuffix(".md") }
        override fun listNotes(): List<RawNote> = emptyList()
    }

    private val tasks = NoteSettings(saveAsTask = true)

    private fun write(text: String, settings: NoteSettings, captureId: String = "2026-10-15 0930"): String {
        val storage = RecordingStorage()
        VaultWriter.writeNote(storage, text, captureId, settings)
        return storage.notes.getValue("$captureId.md")
    }

    @Test
    fun `with every setting off a voice note is byte-identical to upstream`() {
        assertEquals(
            "---\ncreated: 2026-10-15\n---\nhello there\n",
            write("hello there\n", NoteSettings())
        )
    }

    @Test
    fun `with every setting off a multi-line quick note keeps its line breaks and no trailing newline`() {
        assertEquals(
            "---\ncreated: 2026-10-15\n---\nfirst line\nsecond line",
            write("first line\nsecond line", NoteSettings())
        )
    }

    @Test
    fun `task mode writes the text as one checklist line under the frontmatter`() {
        assertEquals(
            "---\ncreated: 2026-10-15\n---\n- [ ] call the plumber about the kitchen tap\n",
            write("call the plumber about the kitchen tap\n", tasks)
        )
    }

    @Test
    fun `task mode joins line breaks and runs of whitespace into single spaces`() {
        assertEquals(
            "- [ ] buy milk and eggs\n",
            NoteExtras.body("  buy milk\n\nand \t eggs \r\n", "2026-10-15", tasks)
        )
    }

    @Test
    fun `task mode ends with a newline even for text typed without one`() {
        assertEquals("- [ ] typed note\n", NoteExtras.body("typed note", "2026-10-15", tasks))
    }

    @Test
    fun `the task template can carry the created date for the Tasks plugin`() {
        assertEquals(
            "- [ ] water the plants ➕ 2026-10-15\n",
            NoteExtras.body("water the plants", "2026-10-15", tasks, taskLineTemplate = "- [ ] {{text}} ➕ {{created}}")
        )
    }

    @Test
    fun `a placeholder spoken or typed in the text is written literally, never expanded`() {
        assertEquals(
            "- [ ] remember {{created}} literally\n",
            NoteExtras.body("remember {{created}} literally", "2026-10-15", tasks)
        )
    }
}
