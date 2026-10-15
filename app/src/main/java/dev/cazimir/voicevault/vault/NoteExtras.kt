package dev.cazimir.voicevault.vault

import android.util.Log
import dev.cazimir.voicevault.Config
import dev.cazimir.voicevault.settings.NoteSettings
import java.io.File

/** What became of a capture's recording on its way into the vault. */
internal sealed interface KeptAudio {
    /** Not asked for: the setting is off, or the note has no recording (a Quick Note). */
    data object None : KeptAudio

    data class Saved(val fileName: String) : KeptAudio

    /** Asked for, but the copy failed. The note is still written - its text matters more than the audio. */
    data object Failed : KeptAudio
}

/**
 * This fork's additions to a note, kept out of [VaultWriter] so that upstream's planned rewrite
 * of `writeNote` (openspec/changes/add-pro-features) merges without dragging fork logic through
 * the conflict.
 *
 * [body] must stay the last transform before the storage write: anything that inspects the raw
 * transcript - such as upstream's planned first-word routing - has to run before a leading
 * "- [ ]" is added.
 */
internal object NoteExtras {

    private val WHITESPACE = Regex("\\s+")

    /**
     * Copies [audio] into the vault when [NoteSettings.keepAudio] is on. Runs before the note is
     * written, so a note never links a recording that is not there yet.
     *
     * Never throws: text comes first, so a failed copy must not cost the note. It is logged and
     * reported as [KeptAudio.Failed], which [body] marks in the note. The pending WAV is still
     * deleted once the note is written, so that recording is gone - the accepted price of
     * text-first.
     */
    fun keepAudio(storage: VaultStorage, captureId: String, settings: NoteSettings, audio: File?): KeptAudio {
        if (!settings.keepAudio || audio == null) return KeptAudio.None
        return try {
            KeptAudio.Saved(storage.writeAudio("$captureId.wav", audio))
        } catch (t: Throwable) {
            Log.w(Config.LOG_TAG, "could not keep the audio for $captureId; writing the note without it", t)
            KeptAudio.Failed
        }
    }

    /**
     * [text] as it should appear under the frontmatter. With every setting off it is returned
     * unchanged, so the note is byte-identical to upstream's.
     *
     * In task mode the text is collapsed onto one line and expanded into [taskLineTemplate], with
     * the recording's link (or the not-saved marker) at `{{audio}}`. Otherwise the link goes on
     * its own line under the transcript. `{{text}}` is replaced last, so a transcript that happens
     * to contain a placeholder is never expanded.
     */
    fun body(
        text: String,
        createdDate: String,
        settings: NoteSettings,
        audio: KeptAudio = KeptAudio.None,
        taskLineTemplate: String = Config.TASK_LINE_TEMPLATE,
    ): String {
        val audioText = when (audio) {
            is KeptAudio.Saved -> Config.AUDIO_LINK_TEMPLATE.replace("{{file}}", audio.fileName)
            KeptAudio.Failed -> Config.AUDIO_NOT_SAVED_MARKER
            KeptAudio.None -> ""
        }
        if (settings.saveAsTask) {
            return taskLineTemplate
                .replace("{{created}}", createdDate)
                .replace("{{audio}}", if (audioText.isEmpty()) "" else " $audioText")
                .replace("{{text}}", text.trim().replace(WHITESPACE, " ")) + "\n"
        }
        if (audioText.isEmpty()) return text
        return text.trimEnd() + "\n\n" + audioText + "\n"
    }
}
