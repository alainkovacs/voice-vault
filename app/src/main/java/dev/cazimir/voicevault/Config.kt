package dev.cazimir.voicevault

object Config {
    /**
     * Folder under `context.filesDir` where a capture's `.wav` waits between "recording
     * finished" and "note written". App-private on purpose: whisper needs a real filesystem
     * path, and orphan/partial audio must never appear in the user's synced vault. The
     * finished `.md` note is the only thing that goes into the SAF-selected vault folder -
     * plus, in this fork, a finished copy of the recording when [VAULT_AUDIO_FOLDER]'s
     * setting is on.
     */
    const val PENDING_AUDIO_DIR = "pending"

    const val SAMPLE_RATE_HZ = 16_000
    const val SILENCE_RMS_THRESHOLD = 700.0
    const val SILENCE_DURATION_MS = 5_000L
    const val MAX_RECORDING_DURATION_MS = 180_000L

    /** Tag for every `Log` call in the app, so a tethered logcat session can filter on one string. */
    const val LOG_TAG = "VoiceVault"

    /** Extension for the per-capture diagnostic record written beside orphan audio in the pending folder. */
    const val DIAGNOSTIC_RECORD_EXTENSION = "log"

    /** Byte size of the fixed PCM16 mono header `WavEncoder` writes, used to derive duration from file size. */
    const val WAV_HEADER_SIZE_BYTES = 44

    /**
     * YAML frontmatter prepended to every note, with `{{created}}` replaced by the capture
     * date (yyyy-MM-dd). Deliberately minimal so notes drop cleanly into any vault. This is not
     * on the settings screen: to tailor this to your own vault (add Priority, Area, tags, whatever),
     * edit this template and rebuild. Set it to an empty string to write notes with no
     * frontmatter at all.
     */
    const val NOTE_FRONTMATTER_TEMPLATE = "---\ncreated: {{created}}\n---\n"

    /**
     * The line a note's text becomes when this fork's "Save notes as tasks" setting is on.
     * `{{text}}` is the transcript collapsed onto one line; `{{audio}}` is a space plus the kept
     * recording's link ([AUDIO_LINK_TEMPLATE]) or [AUDIO_NOT_SAVED_MARKER], and empty when there
     * is neither; `{{created}}` is the capture date (yyyy-MM-dd). The Obsidian Tasks plugin reads
     * its emoji fields backwards from the end of the line and stops at anything else, such as a
     * link, so put them after `{{audio}}`: "- [ ] {{text}}{{audio}} ➕ {{created}}" gives each
     * task a created date. Without `{{audio}}`, task notes carry neither the link nor
     * [AUDIO_NOT_SAVED_MARKER], so a failed copy leaves no trace in the note - while recordings
     * are still copied into [VAULT_AUDIO_FOLDER] whenever "Keep audio" is on.
     */
    const val TASK_LINE_TEMPLATE = "- [ ] {{text}}{{audio}}"

    /**
     * Subfolder of the selected vault folder that this fork's "Keep audio in the vault" setting
     * copies recordings into. It has to be inside the selected folder, because the SAF grant
     * covers only that subtree. It gets an empty `.nomedia` so recordings stay out of music apps.
     */
    const val VAULT_AUDIO_FOLDER = "Voice Audio"

    /** How a note links its kept recording; `{{file}}` is the WAV's name. "![[{{file}}]]" embeds a player instead. */
    const val AUDIO_LINK_TEMPLATE = "[[{{file}}|🎙]]"

    /**
     * Written where the link would go when keeping the recording was switched on but copying it
     * failed: the note is saved anyway and that recording is lost. Empty to write nothing.
     */
    const val AUDIO_NOT_SAVED_MARKER = "(audio not saved)"

    const val MODEL_FILE_NAME = "ggml-base.en-q5_1.bin"

    /**
     * The model is fetched once on first launch instead of shipping in assets: at ~57 MB it
     * dominates both the APK and the git repository. This is the only network access the app
     * ever makes - no audio, text, or telemetry is ever sent anywhere.
     *
     * Size and digest are pinned so a truncated or substituted download is rejected rather
     * than handed to whisper, which would otherwise fail much later and far less clearly.
     * Both must be updated together with [MODEL_FILE_NAME] when swapping models.
     */
    const val MODEL_DOWNLOAD_URL =
        "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.en-q5_1.bin"
    const val MODEL_SIZE_BYTES = 59_721_011L
    const val MODEL_SHA256 = "4baf70dd0d7c4247ba2b81fafd9c01005ac77c2f9ef064e00dcf195d0e2fdd2f"

    /**
     * ISO 639-1 code forced on every transcription. Must stay in sync with the model:
     * the `.en` models above cannot decode anything but English, so any non-"en" value
     * here also requires swapping in a multilingual model. "auto" is deliberately not
     * used - detection on a few seconds of speech is unreliable, and this is a
     * single-language device.
     */
    const val TRANSCRIPTION_LANGUAGE = "en"
}
