package dev.cazimir.voicevault.vault

import dev.cazimir.voicevault.settings.NoteSettings
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.io.IOException

/**
 * This fork's note options, exercised both directly and through [VaultWriter.writeNote].
 * Kept apart from upstream's `VaultWriterTest` so upstream changes to that file merge cleanly.
 */
class NoteExtrasTest {

    /** Records note and audio writes; either can be told to fail. */
    private class RecordingStorage(
        private val failAudio: Boolean = false,
        private val failNotes: Boolean = false,
        /** The name the storage reports the audio was kept under, if not the requested one. */
        private val storeAudioAs: String? = null,
    ) : VaultStorage {
        val notes = linkedMapOf<String, String>()
        val audio = linkedMapOf<String, ByteArray>()

        override fun access(): VaultAccess = VaultAccess.Available
        override fun folderInfo(): VaultFolderInfo? = null
        override fun writeNote(fileName: String, content: String) {
            if (failNotes) throw IOException("vault not accessible")
            notes[fileName] = content
        }
        override fun noteExists(fileName: String): Boolean = fileName in notes
        override fun noteBaseNames(): List<String> = notes.keys.map { it.removeSuffix(".md") }
        override fun listNotes(): List<RawNote> = emptyList()
        override fun writeAudio(fileName: String, source: File): String {
            if (failAudio) throw IOException("no space left for audio")
            val name = storeAudioAs ?: fileName
            audio[name] = source.readBytes()
            return name
        }
    }

    private val captureId = "2026-10-15 0930"
    private val tasks = NoteSettings(saveAsTask = true)
    private val keepAudio = NoteSettings(keepAudio = true)
    private val both = NoteSettings(saveAsTask = true, keepAudio = true)
    private val recordingBytes = byteArrayOf(82, 73, 70, 70, 1, 2, 3)
    private val recording: File = File.createTempFile("capture", ".wav").apply { writeBytes(recordingBytes) }

    @After
    fun deleteRecording() {
        recording.delete()
    }

    private fun write(
        text: String,
        settings: NoteSettings,
        storage: RecordingStorage = RecordingStorage(),
        audio: File? = null,
    ): String {
        VaultWriter.writeNote(storage, text, captureId, settings, audio)
        return storage.notes.getValue("$captureId.md")
    }

    // --- defaults: upstream behaviour ---

    @Test
    fun `with every setting off a voice note is byte-identical to upstream`() {
        assertEquals(
            "---\ncreated: 2026-10-15\n---\nhello there\n",
            write("hello there\n", NoteSettings(), audio = recording)
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
    fun `with keep-audio off the recording is never copied`() {
        val storage = RecordingStorage()
        write("hello there\n", tasks, storage, audio = recording)
        assertTrue(storage.audio.isEmpty())
    }

    // --- save notes as tasks ---

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
            NoteExtras.body(
                "water the plants", "2026-10-15", tasks,
                taskLineTemplate = "- [ ] {{text}}{{audio}} ➕ {{created}}"
            )
        )
    }

    @Test
    fun `a placeholder spoken or typed in the text is written literally, never expanded`() {
        assertEquals(
            "- [ ] remember {{created}} and {{audio}} literally\n",
            NoteExtras.body("remember {{created}} and {{audio}} literally", "2026-10-15", tasks)
        )
    }

    // --- keep audio in the vault ---

    @Test
    fun `keep-audio copies the recording and links it on its own line under the transcript`() {
        val storage = RecordingStorage()
        assertEquals(
            "---\ncreated: 2026-10-15\n---\nhello there\n\n[[2026-10-15 0930.wav|🎙]]\n",
            write("hello there\n", keepAudio, storage, audio = recording)
        )
        assertArrayEquals(recordingBytes, storage.audio.getValue("2026-10-15 0930.wav"))
    }

    @Test
    fun `with both settings on the link ends the task line`() {
        assertEquals(
            "---\ncreated: 2026-10-15\n---\n- [ ] hello there [[2026-10-15 0930.wav|🎙]]\n",
            write("hello there\n", both, audio = recording)
        )
    }

    @Test
    fun `the link sits before Tasks emoji fields, which must end the line`() {
        assertEquals(
            "- [ ] water the plants [[x.wav|🎙]] ➕ 2026-10-15\n",
            NoteExtras.body(
                "water the plants", "2026-10-15", both, KeptAudio.Saved("x.wav"),
                taskLineTemplate = "- [ ] {{text}}{{audio}} ➕ {{created}}"
            )
        )
    }

    @Test
    fun `the note links the name the recording was actually kept under`() {
        val storage = RecordingStorage(storeAudioAs = "2026-10-15 0930 (2).wav")
        assertEquals(
            "---\ncreated: 2026-10-15\n---\nhello there\n\n[[2026-10-15 0930 (2).wav|🎙]]\n",
            write("hello there\n", keepAudio, storage, audio = recording)
        )
    }

    @Test
    fun `a failed audio copy still writes the note, marked so the loss is visible`() {
        val storage = RecordingStorage(failAudio = true)
        assertEquals(
            "---\ncreated: 2026-10-15\n---\nhello there\n\n(audio not saved)\n",
            write("hello there\n", keepAudio, storage, audio = recording)
        )
        assertEquals(
            "- [ ] hello there (audio not saved)\n",
            NoteExtras.body("hello there\n", "2026-10-15", both, KeptAudio.Failed)
        )
    }

    @Test
    fun `a task template without the audio placeholder carries neither the link nor the marker`() {
        val template = "- [ ] {{text}}"
        assertEquals("- [ ] x\n", NoteExtras.body("x", "2026-10-15", both, KeptAudio.Saved("x.wav"), template))
        assertEquals("- [ ] x\n", NoteExtras.body("x", "2026-10-15", both, KeptAudio.Failed, template))
    }

    @Test
    fun `a storage that cannot keep audio counts as a failed copy, never a lost note`() {
        val noAudioSupport = object : VaultStorage {
            val notes = linkedMapOf<String, String>()
            override fun access(): VaultAccess = VaultAccess.Available
            override fun folderInfo(): VaultFolderInfo? = null
            override fun writeNote(fileName: String, content: String) {
                notes[fileName] = content
            }
            override fun noteExists(fileName: String): Boolean = fileName in notes
            override fun noteBaseNames(): List<String> = emptyList()
            override fun listNotes(): List<RawNote> = emptyList()
        }
        VaultWriter.writeNote(noAudioSupport, "hello there\n", captureId, keepAudio, recording)
        assertEquals(
            "---\ncreated: 2026-10-15\n---\nhello there\n\n(audio not saved)\n",
            noAudioSupport.notes.getValue("$captureId.md")
        )
    }

    @Test
    fun `a note without a recording - a quick note - gets no link and no marker`() {
        val storage = RecordingStorage()
        assertEquals(
            "---\ncreated: 2026-10-15\n---\n- [ ] typed note\n",
            write("typed note", both, storage, audio = null)
        )
        assertTrue(storage.audio.isEmpty())
    }

    @Test
    fun `a note write failing after the audio copied still throws`() {
        val storage = RecordingStorage(failNotes = true)
        try {
            VaultWriter.writeNote(storage, "hello there\n", captureId, keepAudio, recording)
            fail("expected the note write failure to propagate")
        } catch (expected: IOException) {
            // TranscriptionQueue turns this into NoteWriteFailed and leaves the WAV in pending/;
            // the retry finds the identical copy already in the vault and reuses it.
        }
        assertTrue(storage.audio.containsKey("2026-10-15 0930.wav"))
    }
}
