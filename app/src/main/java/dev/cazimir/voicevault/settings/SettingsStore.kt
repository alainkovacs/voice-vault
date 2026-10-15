package dev.cazimir.voicevault.settings

import android.content.Context
import androidx.core.content.edit

/** A stored on/off setting: its preference key and the value it has until the user changes it. */
data class BooleanKey(val name: String, val default: Boolean)

/**
 * This fork's user-facing settings, persisted in their own small app-private
 * `SharedPreferences` file - separate from [dev.cazimir.voicevault.vault.VaultGrant]'s, which
 * stays exactly as upstream wrote it.
 *
 * Every default reproduces upstream behaviour, so an install where nothing has been switched on
 * writes exactly the notes upstream would. Persistence only: each setting's title and
 * description live with its row in `SettingsScreen.kt`.
 */
object SettingsStore {

    private const val PREFS = "settings"

    val SAVE_AS_TASK = BooleanKey("save_as_task", default = false)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Never throws: this is read on the way to writing a note, and a stored value of another
     * type (a key later reused for a different setting) must not cost a capture - it reads as
     * the default instead.
     */
    fun getBoolean(context: Context, key: BooleanKey): Boolean =
        runCatching { prefs(context).getBoolean(key.name, key.default) }.getOrDefault(key.default)

    fun setBoolean(context: Context, key: BooleanKey, value: Boolean) {
        prefs(context).edit { putBoolean(key.name, value) }
    }
}
