package dev.cazimir.voicevault.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.io.InputStream

/** The naming rule that keeps [SafAudioWriter] from ever replacing a kept recording. */
class AudioSlotTest {

    private fun choose(stored: Map<String, String>, recording: String = "this capture") =
        chooseAudioSlot("2026-10-15 0930.wav", existing = { stored[it] }) { it == recording }

    @Test
    fun `a free name is used as-is`() {
        assertEquals(AudioSlot.Free("2026-10-15 0930.wav"), choose(emptyMap()))
    }

    @Test
    fun `the same recording already under the name is reused - a retry of the same capture`() {
        assertEquals(
            AudioSlot.AlreadyThere("2026-10-15 0930.wav"),
            choose(mapOf("2026-10-15 0930.wav" to "this capture"))
        )
    }

    @Test
    fun `a different recording under the name is kept and the copy goes to the next free name`() {
        assertEquals(
            AudioSlot.Free("2026-10-15 0930 (2).wav"),
            choose(mapOf("2026-10-15 0930.wav" to "an older capture from the repeated hour"))
        )
    }

    @Test
    fun `a retry after such a collision finds its own earlier copy`() {
        assertEquals(
            AudioSlot.AlreadyThere("2026-10-15 0930 (2).wav"),
            choose(mapOf("2026-10-15 0930.wav" to "older", "2026-10-15 0930 (2).wav" to "this capture"))
        )
    }

    @Test
    fun `every taken name is skipped`() {
        assertEquals(
            AudioSlot.Free("2026-10-15 0930 (3).wav"),
            choose(mapOf("2026-10-15 0930.wav" to "older", "2026-10-15 0930 (2).wav" to "also older"))
        )
    }

    // --- isSameRecording: the only path to reusing a taken name ---

    private fun stream(s: String) = s.byteInputStream()

    private val neverOpened: () -> InputStream? = { throw AssertionError("must not be opened") }

    @Test
    fun `a directory is never the same recording and is never opened`() {
        assertFalse(isSameRecording(true, 9, 9, neverOpened) { stream("RIFF data") })
    }

    @Test
    fun `a known size mismatch is never the same recording and is never opened`() {
        assertFalse(isSameRecording(false, 8, 9, neverOpened) { stream("RIFF data") })
    }

    @Test
    fun `equal sizes are not enough - different bytes are a different recording`() {
        assertFalse(isSameRecording(false, 9, 9, { stream("RIFF dat4") }) { stream("RIFF data") })
    }

    @Test
    fun `identical bytes are the same recording, even when the provider reports no size`() {
        assertTrue(isSameRecording(false, 9, 9, { stream("RIFF data") }) { stream("RIFF data") })
        assertTrue(isSameRecording(false, -1, 9, { stream("RIFF data") }) { stream("RIFF data") })
    }

    @Test
    fun `an unreadable stored file counts as different, so nothing gets reused wrongly`() {
        assertFalse(isSameRecording(false, 9, 9, { null }) { stream("RIFF data") })
        assertFalse(isSameRecording(false, 9, 9, { throw IOException("gone") }) { stream("RIFF data") })
    }

    @Test
    fun `sameContent compares every byte and the length`() {
        fun same(a: String, b: String) = sameContent(a.byteInputStream(), b.byteInputStream())

        assertTrue(same("RIFF data", "RIFF data"))
        assertTrue(same("", ""))
        assertFalse(same("RIFF data", "RIFF dat4"))
        assertFalse(same("RIFF data", "RIFF data and more"))
        assertFalse(same("RIFF data and more", "RIFF data"))
    }
}
