package com.onenotes.app.data.export

import android.content.Context
import android.net.Uri
import android.util.Log
import com.onenotes.app.data.model.AudioBlock
import com.onenotes.app.data.model.Block
import com.onenotes.app.data.model.FileBlock
import com.onenotes.app.data.model.ImageBlock
import com.onenotes.app.data.model.TextBlock
import com.onenotes.app.data.repository.FileStorage
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Import of the notes exported by Samsung Notes.
 *
 * The ".sdocx" container is not documented: it is a zip archive whose entries change between
 * the versions of the app, so the importer is deliberately tolerant. It walks the archive,
 * keeps every textual entry (JSON, XML, HTML or plain text) and every image, and rebuilds a
 * note from what it finds. A ".memo", ".txt" or ".md" file is imported as plain text.
 */
class SamsungNotesImporter(
    private val context: Context,
    private val storage: FileStorage
) {

    data class ImportedNote(
        val title: String,
        val blocks: MutableList<Block>,
        val createdAt: Long = 0L
    )

    fun import(uri: Uri): List<ImportedNote> {
        val bytes = readAll(uri) ?: return emptyList()
        val zipped = extractZip(bytes)
        if (zipped.isNotEmpty()) return zipped

        val text = stripMarkup(String(bytes, Charsets.UTF_8))
        if (text.isBlank()) return emptyList()
        val lines = text.lineSequence().map { it.trimEnd() }.toList()
        val title = lines.firstOrNull { it.isNotBlank() }?.take(80).orEmpty()
        return listOf(
            ImportedNote(
                title = title,
                blocks = mutableListOf<Block>(TextBlock(text = text))
            )
        )
    }

    // --------------------------------------------------------------------- zip

    private fun extractZip(bytes: ByteArray): List<ImportedNote> {
        val texts = LinkedHashMap<String, String>()
        val images = ArrayList<String>()
        val otherFiles = ArrayList<Pair<String, String>>()
        var createdAt = 0L

        try {
            ZipInputStream(bytes.inputStream()).use { zip ->
                var entry: ZipEntry? = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val name = entry.name.substringAfterLast('/').lowercase()
                        val data = zip.readBytes()
                        when {
                            name.endsWith(".json") -> {
                                val json = String(data, Charsets.UTF_8)
                                collectJsonText(json, texts)
                                val time = extractTime(json)
                                if (time > 0) createdAt = time
                            }
                            name.endsWith(".xml") || name.endsWith(".html") || name.endsWith(".htm") -> {
                                texts[name] = stripMarkup(String(data, Charsets.UTF_8))
                            }
                            name.endsWith(".txt") || name.endsWith(".md") || name.endsWith(".memo") -> {
                                texts[name] = String(data, Charsets.UTF_8)
                            }
                            name.endsWith(".jpg") || name.endsWith(".jpeg") ||
                                name.endsWith(".png") || name.endsWith(".webp") -> {
                                val extension = name.substringAfterLast('.')
                                val fileName = storage.newAttachmentName(extension)
                                storage.write(fileName, data)
                                images.add(fileName)
                            }
                            name.endsWith(".pdf") || name.endsWith(".mp3") || name.endsWith(".m4a") ||
                                name.endsWith(".aac") || name.endsWith(".3gp") -> {
                                val extension = name.substringAfterLast('.')
                                val fileName = storage.newAttachmentName(extension)
                                storage.write(fileName, data)
                                otherFiles += fileName to mimeOf(extension)
                            }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "The file is not a valid archive", t)
            return emptyList()
        }

        if (texts.isEmpty() && images.isEmpty()) return emptyList()

        val body = texts.entries
            .sortedByDescending { it.value.length }
            .joinToString("\n\n") { it.value.trim() }
            .trim()

        val blocks = mutableListOf<Block>()
        if (body.isNotBlank()) {
            body.lineSequence().forEach { line ->
                blocks.add(TextBlock(text = line))
            }
        }
        images.forEach { name ->
            blocks.add(ImageBlock(fileName = name, name = "immagine"))
        }
        otherFiles.forEach { (name, mime) ->
            if (mime.startsWith("audio/")) {
                blocks.add(AudioBlock(fileName = name, name = "registrazione"))
            } else {
                blocks.add(FileBlock(fileName = name, name = name, mime = mime))
            }
        }
        if (blocks.isEmpty()) blocks.add(TextBlock())

        val title = body.lineSequence().firstOrNull { it.isNotBlank() }?.take(80).orEmpty()
        return listOf(ImportedNote(title = title, blocks = blocks, createdAt = createdAt))
    }

    /** Walks a JSON document and collects every string that looks like note content. */
    private fun collectJsonText(raw: String, out: MutableMap<String, String>) {
        val root = try {
            JSONObject(raw)
        } catch (t: Throwable) {
            try {
                JSONArray(raw)
            } catch (ignored: Throwable) {
                return
            }
        }
        val builder = StringBuilder()
        walk(root, builder)
        val text = builder.toString().trim()
        if (text.isNotBlank()) out["content.json"] = text
    }

    private fun walk(node: Any?, builder: StringBuilder) {
        when (node) {
            null -> Unit
            is JSONObject -> {
                val keys = node.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = node.opt(key)
                    if (value is String && value.length > 1 && !isNoise(key, value)) {
                        builder.append(value.trim())
                        builder.append('\n')
                    } else {
                        walk(value, builder)
                    }
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) walk(node.opt(i), builder)
            }
            is String -> {
                if (node.length > 1) {
                    builder.append(node.trim()).append('\n')
                }
            }
        }
    }

    /** Ignores the metadata of the archive (ids, paths, checksums…). */
    private fun isNoise(key: String, value: String): Boolean {
        val lowerKey = key.lowercase()
        if (lowerKey in IGNORED_KEYS) return true
        if (value.startsWith("http", ignoreCase = true)) return true
        if (value.length < 2) return true
        return value.matches(Regex("[0-9a-fA-F]{16,}"))
    }

    private fun extractTime(json: String): Long {
        val patterns = listOf("\"createdAt\"", "\"createdTime\"", "\"created\"", "\"date\"")
        patterns.forEach { pattern ->
            val index = json.indexOf(pattern)
            if (index >= 0) {
                val tail = json.substring(index + pattern.length, (index + pattern.length + 40).coerceAtMost(json.length))
                val digits = Regex("[0-9]{10,13}").find(tail)?.value ?: return@forEach
                val value = digits.toLongOrNull() ?: return@forEach
                return if (value > 10_000_000_000L) value else value * 1000
            }
        }
        return 0L
    }

    // ------------------------------------------------------------------ helpers

    private fun readAll(uri: Uri): ByteArray? = try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArrayOutputStream()
            input.copyTo(buffer)
            buffer.toByteArray()
        }
    } catch (t: Throwable) {
        Log.w(TAG, "Unable to read the file", t)
        null
    }

    /** Removes the tags of a XML / HTML document, keeping the text. */
    fun stripMarkup(source: String): String {
        var text = source
        text = text.replace(Regex("(?s)<script.*?</script>"), " ")
        text = text.replace(Regex("(?s)<style.*?</style>"), " ")
        text = text.replace(Regex("<br\\s*/?>"), "\n")
        text = text.replace(Regex("</p>|</div>|</li>|</h[1-6]>"), "\n")
        text = text.replace(Regex("<[^>]*>"), " ")
        text = text.replace("&nbsp;", " ")
        text = text.replace("&amp;", "&")
        text = text.replace("&lt;", "<")
        text = text.replace("&gt;", ">")
        text = text.replace("&quot;", "\"")
        text = text.replace("&#39;", "'")
        return text.lineSequence()
            .map { it.replace(Regex("[ \\t]+"), " ").trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")
            .trim()
    }

    private fun mimeOf(extension: String): String = when (extension) {
        "pdf" -> "application/pdf"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "aac" -> "audio/aac"
        "3gp" -> "audio/3gpp"
        else -> "application/octet-stream"
    }

    companion object {
        private const val TAG = "SamsungNotesImporter"

        private val IGNORED_KEYS = setOf(
            "id", "uuid", "guid", "path", "uri", "type", "mimetype", "mime",
            "version", "checksum", "hash", "thumbnail", "preview", "filename",
            "filenamewithoutext", "favorite", "locked", "category"
        )
    }
}