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
    fun `every stored setting reads through to its own field`() {
        assertEquals(NoteSettings(saveAsTask = true), NoteSettings.from { it == SettingsStore.SAVE_AS_TASK })
        assertEquals(NoteSettings(keepAudio = true), NoteSettings.from { it == SettingsStore.KEEP_AUDIO })
    }

    @Test
    fun `the upstream-behaviour defaults have every fork option off`() {
        assertTrue(listOf(SettingsStore.SAVE_AS_TASK, SettingsStore.KEEP_AUDIO).none { it.default })
        assertEquals(NoteSettings(saveAsTask = false, keepAudio = false), NoteSettings())
    }

    @Test
    fun `stored keys are distinct`() {
        val names = listOf(SettingsStore.SAVE_AS_TASK, SettingsStore.KEEP_AUDIO).map { it.name }
        assertEquals(names.size, names.toSet().size)
    }
}
