package dev.cazimir.voicevault.vault

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.util.Log
import dev.cazimir.voicevault.Config
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.Locale

/**
 * The SAF side of this fork's "Keep audio in the vault" setting: copies a finished capture's
 * WAV into [Config.VAULT_AUDIO_FOLDER] inside the vault folder. Kept out of [SafVaultStorage]
 * so upstream's own write path stays untouched.
 *
 * Mirrors [SafVaultStorage.writeNote]'s temp-document-then-rename sequence - keep the two in
 * step - with two deliberate differences:
 * - It never deletes or replaces a file under a final name. Unlike the inbox, the audio folder
 *   is permanent (Obsidian does not move attachments when it moves a note), and capture ids are
 *   only unique to the minute among notes still in the inbox, so a name there can belong to an
 *   older capture - e.g. one recorded in the hour that repeats when daylight saving ends.
 * - It lists a folder with one query instead of `DocumentFile.findFile`, which costs one
 *   provider query per child, in a folder that only ever grows.
 */
internal class SafAudioWriter(private val resolver: ContentResolver) {

    private class Child(val name: String, val uri: Uri, val isDirectory: Boolean, val size: Long)

    /** Stores [source] in the audio folder under [vaultDir]; returns the file name it is stored under. */
    fun write(vaultDir: Uri, fileName: String, source: File): String {
        val audioDir = audioFolder(vaultDir)
        val children = list(audioDir)
        ensureNoMedia(audioDir, children)

        val slot = chooseAudioSlot(fileName, existing = { children[key(it)] }) { stored ->
            isSameRecording(
                isDirectory = stored.isDirectory,
                storedSize = stored.size,
                sourceSize = source.length(),
                openStored = { resolver.openInputStream(stored.uri) },
                openSource = { source.inputStream() },
            )
        }
        if (slot is AudioSlot.Free) copyAtomically(audioDir, children, slot.name, source)
        return slot.name
    }

    private fun audioFolder(vaultDir: Uri): Uri {
        val existing = list(vaultDir)[key(Config.VAULT_AUDIO_FOLDER)]
        if (existing != null) {
            if (!existing.isDirectory) {
                throw IOException("\"${existing.name}\" in the vault folder is a file, not a folder")
            }
            return existing.uri
        }
        return DocumentsContract.createDocument(resolver, vaultDir, Document.MIME_TYPE_DIR, Config.VAULT_AUDIO_FOLDER)
            ?: throw IOException("could not create the \"${Config.VAULT_AUDIO_FOLDER}\" folder")
    }

    /**
     * Without a `.nomedia`, Android's media scanner indexes every recording as music, and voice
     * notes turn up in music players and the Files app's Audio view. Obsidian ignores dotfiles.
     * Best-effort: a missing `.nomedia` must never cost the recording.
     */
    private fun ensureNoMedia(audioDir: Uri, children: Map<String, Child>) {
        if (key(NO_MEDIA) in children) return
        try {
            DocumentsContract.createDocument(resolver, audioDir, "application/octet-stream", NO_MEDIA)
        } catch (t: Throwable) {
            Log.w(Config.LOG_TAG, "could not create $NO_MEDIA in the audio folder", t)
        }
    }

    private fun copyAtomically(audioDir: Uri, children: Map<String, Child>, name: String, source: File) {
        val partialName = "$name.partial"
        // A partial left by a crashed copy would otherwise linger beside this one. Tidying only:
        // failing to delete it must not fail the copy.
        children[key(partialName)]?.let { stale ->
            runCatching { DocumentsContract.deleteDocument(resolver, stale.uri) }
                .onFailure { Log.w(Config.LOG_TAG, "could not delete stale $partialName", it) }
        }

        val partial = DocumentsContract.createDocument(resolver, audioDir, "application/octet-stream", partialName)
            ?: throw IOException("could not create $partialName in the audio folder")
        try {
            resolver.openOutputStream(partial)?.use { out ->
                source.inputStream().use { it.copyTo(out) }
                out.flush()
            } ?: throw IOException("could not open $partialName for writing")

            val renamed = DocumentsContract.renameDocument(resolver, partial, name)
            // Some providers return null on a rename that in fact succeeded - confirm by lookup.
            if (renamed == null && list(audioDir)[key(name)]?.isDirectory != false) {
                throw IOException("could not rename $partialName to $name")
            }
        } catch (t: Throwable) {
            runCatching { DocumentsContract.deleteDocument(resolver, partial) }
            throw t
        }
    }

    /** Every child of [dir] in one provider query, keyed by [key]. */
    private fun list(dir: Uri): Map<String, Child> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(dir, DocumentsContract.getDocumentId(dir))
        val projection = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
        )
        val cursor = resolver.query(childrenUri, projection, null, null, null)
            ?: throw IOException("could not list $dir")
        return cursor.use { c ->
            val idColumn = c.getColumnIndexOrThrow(Document.COLUMN_DOCUMENT_ID)
            val nameColumn = c.getColumnIndexOrThrow(Document.COLUMN_DISPLAY_NAME)
            val mimeColumn = c.getColumnIndexOrThrow(Document.COLUMN_MIME_TYPE)
            val sizeColumn = c.getColumnIndex(Document.COLUMN_SIZE)
            buildMap {
                while (c.moveToNext()) {
                    val name = c.getString(nameColumn) ?: continue
                    put(
                        key(name),
                        Child(
                            name = name,
                            uri = DocumentsContract.buildDocumentUriUsingTree(dir, c.getString(idColumn)),
                            isDirectory = c.getString(mimeColumn) == Document.MIME_TYPE_DIR,
                            size = if (sizeColumn < 0 || c.isNull(sizeColumn)) -1L else c.getLong(sizeColumn),
                        )
                    )
                }
            }
        }
    }

    private companion object {
        const val NO_MEDIA = ".nomedia"

        /** Shared storage matches names case-insensitively, so lookups must too. */
        fun key(name: String) = name.lowercase(Locale.ROOT)
    }
}

/** Where a kept recording is stored. */
internal sealed interface AudioSlot {
    val name: String

    /** Nothing has this name yet: copy the recording here. */
    data class Free(override val name: String) : AudioSlot

    /** This exact recording is already stored under this name - a retry of the same capture. */
    data class AlreadyThere(override val name: String) : AudioSlot
}

/**
 * Picks the name a kept recording is stored under: [fileName] if nothing has it or it already
 * holds this recording, otherwise the first "base (n).ext" that is free or holds it. Never
 * picks a name holding anything else. [existing] looks a name up; [isSameRecording] decides
 * whether what is stored there is this recording.
 */
internal fun <T : Any> chooseAudioSlot(
    fileName: String,
    existing: (String) -> T?,
    isSameRecording: (T) -> Boolean,
): AudioSlot {
    val base = fileName.substringBeforeLast('.')
    val extension = fileName.substringAfterLast('.', missingDelimiterValue = "")
    val suffix = if (extension.isEmpty()) "" else ".$extension"
    var n = 1
    while (true) {
        val candidate = if (n == 1) fileName else "$base ($n)$suffix"
        val stored = existing(candidate) ?: return AudioSlot.Free(candidate)
        if (isSameRecording(stored)) return AudioSlot.AlreadyThere(candidate)
        n++
    }
}

/**
 * Whether the file stored under a name is this exact recording - the one check that lets a
 * taken name be reused instead of skipped, so it must never say yes to anything else. Size is
 * not identity (recordings of equal length, e.g. every one that hits the duration cap, have
 * equal sizes), so the bytes are always compared; a directory or a known size mismatch is never
 * the same and is never opened. Unreadable counts as different: the copy then goes to a free
 * name, and nothing is lost.
 */
internal fun isSameRecording(
    isDirectory: Boolean,
    storedSize: Long,
    sourceSize: Long,
    openStored: () -> InputStream?,
    openSource: () -> InputStream,
): Boolean {
    if (isDirectory) return false
    if (storedSize >= 0 && storedSize != sourceSize) return false
    return try {
        openStored()?.use { stored -> openSource().use { source -> sameContent(stored, source) } } ?: false
    } catch (t: Throwable) {
        Log.w(Config.LOG_TAG, "could not compare a stored recording; treating it as different", t)
        false
    }
}

/** Whether two streams hold exactly the same bytes. */
internal fun sameContent(a: InputStream, b: InputStream): Boolean {
    val x = a.buffered()
    val y = b.buffered()
    while (true) {
        val byte = x.read()
        if (byte != y.read()) return false
        if (byte == -1) return true
    }
}
