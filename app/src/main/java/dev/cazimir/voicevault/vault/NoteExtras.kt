package dev.cazimir.voicevault.vault

import dev.cazimir.voicevault.Config
import dev.cazimir.voicevault.settings.NoteSettings

/**
 * This fork's additions to a note's body, kept out of [VaultWriter] so that upstream's planned
 * rewrite of `writeNote` (openspec/changes/add-pro-features) merges without dragging fork logic
 * through the conflict.
 *
 * [body] must stay the last transform before the storage write: anything that inspects the raw
 * transcript - such as upstream's planned first-word routing - has to run before a leading
 * "- [ ]" is added.
 */
internal object NoteExtras {

    private val WHITESPACE = Regex("\\s+")

    /**
     * [text] as it should appear under the frontmatter. With every setting off it is returned
     * unchanged, so the note is byte-identical to upstream's.
     *
     * In task mode the text is collapsed onto one line and expanded into [taskLineTemplate].
     * `{{text}}` is replaced last, so a transcript that happens to contain a placeholder is
     * never expanded.
     */
    fun body(
        text: String,
        createdDate: String,
        settings: NoteSettings,
        taskLineTemplate: String = Config.TASK_LINE_TEMPLATE,
    ): String {
        if (!settings.saveAsTask) return text
        return taskLineTemplate
            .replace("{{created}}", createdDate)
            .replace("{{text}}", text.trim().replace(WHITESPACE, " ")) + "\n"
    }
}
