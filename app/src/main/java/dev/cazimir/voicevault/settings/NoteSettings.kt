package dev.cazimir.voicevault.settings

import android.content.Context

/**
 * The settings that shape a vault note, resolved when the note is written and handed to
 * `vault/` as a plain value, so `VaultWriter` stays testable without Android. Reading at write
 * time rather than capture time means a capture retried from `pending/` later uses the settings
 * in force then. The no-argument instance is upstream's behaviour.
 */
data class NoteSettings(
    val saveAsTask: Boolean = false,
    val keepAudio: Boolean = false,
) {
    companion object {
        fun load(context: Context): NoteSettings = from { SettingsStore.getBoolean(context, it) }

        internal fun from(read: (BooleanKey) -> Boolean) = NoteSettings(
            saveAsTask = read(SettingsStore.SAVE_AS_TASK),
            keepAudio = read(SettingsStore.KEEP_AUDIO),
        )
    }
}
