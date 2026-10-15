package dev.cazimir.voicevault.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteSettingsTest {

    @Test
    fun `an install with nothing stored resolves to the upstream-behaviour defaults`() {
        assertEquals(NoteSettings(), NoteSettings.from { key -> key.default })
    }

    @Test
    fun `every stored setting reads through to its field`() {
        assertEquals(NoteSettings(saveAsTask = true), NoteSettings.from { true })
    }

    @Test
    fun `the upstream-behaviour defaults have every fork option off`() {
        assertTrue(listOf(SettingsStore.SAVE_AS_TASK).none { it.default })
        assertEquals(NoteSettings(saveAsTask = false), NoteSettings())
    }
}
