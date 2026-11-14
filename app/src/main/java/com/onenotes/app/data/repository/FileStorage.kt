package com.onenotes.app.data.repository

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Private storage of the attachments (images, recordings, drawings, PDF files).
 *
 * Everything a note references is copied inside [Context.getFilesDir], so an attachment can
 * never disappear because the user deleted the original picture from the gallery, and no
 * storage permission is needed to read it back.
 */
class FileStorage(private val context: Context) {

    fun attachmentsDir(): File = File(context.filesDir, "attachments").apply { mkdirs() }

    fun drawingsDir(): File = File(context.filesDir, "drawings").apply { mkdirs() }

    fun exportsDir(): File = File(context.cacheDir, "exports").apply { mkdirs() }

    fun attachment(name: String): File = File(attachmentsDir(), name)

    fun drawing(name: String): File = File(drawingsDir(), name)

    fun newAttachmentName(extension: String): String {
        val ext = extension.trim().trim('.').ifBlank { "bin" }
        return "${UUID.randomUUID()}.$ext"
    }

    /** Copies [source] into the private storage and returns the generated file name. */
    fun import(source: Uri, extension: String): String {
        val name = newAttachmentName(extension)
        val target = attachment(name)
        context.contentResolver.openInputStream(source)?.use { input ->
            FileOutputStream(target).use { output -> input.copyTo(output) }
        } ?: throw java.io.IOException("Unable to open $source")
        return name
    }

    /** Writes raw bytes (used by the camera and by the document scanner). */
    fun write(name: String, bytes: ByteArray): File {
        val target = attachment(name)
        FileOutputStream(target).use { it.write(bytes) }
        return target
    }

    fun writeDrawing(name: String, json: String): File {
        val target = drawing(name)
        target.writeText(json, Charsets.UTF_8)
        return target
    }

    fun readDrawing(name: String): String = drawing(name).readText(Charsets.UTF_8)

    /** PNG preview rendered when a drawing is saved, used by the editor and the exports. */
    fun drawingPreview(name: String): File =
        File(drawingsDir(), name.substringBeforeLast('.') + ".png")

    fun delete(names: Collection<String>) {
        names.filter { it.isNotBlank() }.forEach { name ->
            runCatching { attachment(name).delete() }
            runCatching { drawing(name).delete() }
        }
    }

    /** Deletes the files that are not referenced by any note any more. */
    fun deleteUnreferenced(usedNames: Set<String>) {
        val known = usedNames.filter { it.isNotBlank() }.toSet()
        attachmentsDir().listFiles()?.forEach { file ->
            if (file.isFile && file.name !in known) runCatching { file.delete() }
        }
        drawingsDir().listFiles()?.forEach { file ->
            if (file.isFile && file.name !in known) runCatching { file.delete() }
        }
    }

    fun clearExports() {
        exportsDir().listFiles()?.forEach { runCatching { it.delete() } }
    }
}
