package dev.lumenotes.app

import android.content.Context
import android.net.Uri
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import androidx.room3.withReadTransaction
import androidx.room3.withWriteTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.lumenotes.database.NoteEntity
import dev.lumenotes.database.NotesDatabase
import java.io.File
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.Reader
import java.nio.charset.CodingErrorAction
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Explicit, unencrypted SAF backup. A restore adds missing UUIDs and never replaces existing notes. */
@Singleton
class NotesBackup @Inject constructor(@ApplicationContext private val context: Context, private val database: NotesDatabase) {
    data class RestoreResult(val imported: Int, val skipped: Int)

    suspend fun export(uri: Uri): Int = withContext(Dispatchers.IO) {
        withSnapshot { file, count ->
            val output = context.contentResolver.openOutputStream(uri, "wt") ?: error("Cannot open backup destination")
            output.use { destination -> file.inputStream().use { copyLimited(it, destination) } }
            count
        }
    }

    suspend fun restore(uri: Uri): RestoreResult = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open backup")
        input.use { restoreFrom(it) }
    }

    /** In-memory streams let instrumented tests exercise exactly the same backup implementation. */
    internal suspend fun exportTo(output: OutputStream): Int = withContext(Dispatchers.IO) {
        withSnapshot { file, count ->
            file.inputStream().use { copyLimited(it, output) }
            count
        }
    }

    internal suspend fun restoreFrom(input: InputStream): RestoreResult = withContext(Dispatchers.IO) {
        withTemporaryFile { file ->
            file.outputStream().buffered().use { copyLimited(input, it) }
            database.withWriteTransaction {
                var imported = 0
                var skipped = 0
                readBackup(file) { note ->
                    if (database.notes().insertIfNew(note) == -1L) skipped++ else imported++
                }
                RestoreResult(imported, skipped)
            }
        }
    }

    private suspend fun <T> withSnapshot(block: suspend (File, Int) -> T): T = withTemporaryFile { file ->
        val count = database.withReadTransaction { writeBackup(file) }
        // Reject an export that our own bounded reader cannot subsequently restore.
        check(readBackup(file) {} == count)
        block(file, count)
    }

    private suspend fun <T> withTemporaryFile(block: suspend (File) -> T): T {
        val file = File.createTempFile("lume-backup-", ".json", context.cacheDir)
        try {
            return block(file)
        } finally {
            file.delete()
        }
    }

    private suspend fun writeBackup(file: File): Int {
        var count = 0
        var after = 0L
        file.outputStream().buffered().use { output ->
            val encoder = Charsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            JsonWriter(OutputStreamWriter(LimitedOutputStream(output, MAX_BYTES), encoder)).use { writer ->
                writer.beginObject()
                writer.name("format").value(FORMAT)
                writer.name("version").value(FORMAT_VERSION)
                writer.name("notes").beginArray()
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val page = database.notes().backupPage(after, PAGE_SIZE)
                    if (page.isEmpty()) break
                    for (note in page) {
                        require(++count <= MAX_NOTES) { "Too many notes for one backup" }
                        writeNote(writer, note)
                        after = note.id
                    }
                }
                writer.endArray()
                writer.name("count").value(count.toLong())
                writer.endObject()
            }
        }
        return count
    }

    private fun writeNote(writer: JsonWriter, note: NoteEntity) {
        writer.beginObject()
        writer.name("uuid").value(note.uuid)
        writer.name("title").value(note.title)
        writer.name("body").value(note.body)
        writer.name("folder").value(note.folder)
        writer.name("tags").value(note.tags)
        writer.name("drawing").value(note.drawing)
        writer.name("pinned").value(note.pinned)
        writer.name("favorite").value(note.favorite)
        writer.name("archived").value(note.archived)
        writer.name("trashedAt")
        val trashedAt = note.trashedAt
        if (trashedAt == null) writer.nullValue() else writer.value(trashedAt)
        writer.name("createdAt").value(note.createdAt)
        writer.name("updatedAt").value(note.updatedAt)
        writer.name("version").value(note.version)
        writer.endObject()
    }

    private suspend fun readBackup(file: File, onNote: suspend (NoteEntity) -> Unit): Int {
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val source = InputStreamReader(file.inputStream(), decoder)
        JsonReader(JsonStringLimitReader(source, MAX_JSON_STRING_CHARS)).use { reader ->
            val fields = mutableSetOf<String>()
            val seenIds = mutableSetOf<String>()
            var format: String? = null
            var version: Long? = null
            var count = 0
            var declared: Long? = null
            var notesSeen = false
            reader.beginObject()
            while (reader.hasNext()) {
                val field = reader.nextName()
                require(fields.add(field)) { "Duplicate backup field" }
                when (field) {
                    "format" -> format = readString(reader, 64)

                    "version" -> version = readLong(reader)

                    "count" -> declared = readLong(reader)

                    "notes" -> {
                        require(format == FORMAT && version == FORMAT_VERSION) { "Unsupported backup format" }
                        notesSeen = true
                        reader.beginArray()
                        while (reader.hasNext()) {
                            currentCoroutineContext().ensureActive()
                            require(++count <= MAX_NOTES) { "Too many notes" }
                            val note = readNote(reader)
                            require(seenIds.add(note.uuid)) { "Duplicate note UUID" }
                            onNote(note)
                        }
                        reader.endArray()
                    }

                    else -> error("Unknown backup field")
                }
            }
            reader.endObject()
            require(format == FORMAT && version == FORMAT_VERSION && notesSeen && declared == count.toLong()) {
                "Incomplete backup"
            }
            require(reader.peek() == JsonToken.END_DOCUMENT) { "Trailing data after backup" }
            return count
        }
    }

    private fun readNote(reader: JsonReader): NoteEntity {
        val fields = mutableSetOf<String>()
        var uuid: String? = null
        var title: String? = null
        var body: String? = null
        var folder: String? = null
        var tags: String? = null
        var drawing: String? = null
        var pinned: Boolean? = null
        var favorite: Boolean? = null
        var archived: Boolean? = null
        var trashedAt: Long? = null
        var createdAt: Long? = null
        var updatedAt: Long? = null
        var revision: Long? = null
        var remainingChars = MAX_NOTE_CHARS
        fun readText(max: Int = MAX_FIELD_CHARS): String {
            val value = readString(reader, minOf(max, remainingChars))
            remainingChars -= value.length
            return value
        }
        reader.beginObject()
        while (reader.hasNext()) {
            val field = reader.nextName()
            require(fields.add(field)) { "Duplicate note field" }
            when (field) {
                "uuid" -> uuid = readText(128)

                "title" -> title = readText()

                "body" -> body = readText()

                "folder" -> folder = readText()

                "tags" -> tags = readText()

                "drawing" -> drawing = readText()

                "pinned" -> pinned = readBoolean(reader)

                "favorite" -> favorite = readBoolean(reader)

                "archived" -> archived = readBoolean(reader)

                "trashedAt" -> trashedAt = if (reader.peek() == JsonToken.NULL) {
                    reader.nextNull()
                    null
                } else {
                    readLong(reader)
                }

                "createdAt" -> createdAt = readLong(reader)

                "updatedAt" -> updatedAt = readLong(reader)

                "version" -> revision = readLong(reader)

                else -> error("Unknown note field")
            }
        }
        reader.endObject()
        require("trashedAt" in fields && !uuid.isNullOrBlank() && revision != null && revision > 0) { "Invalid note" }
        return NoteEntity(
            id = 0,
            uuid = checkNotNull(uuid),
            title = checkNotNull(title),
            body = checkNotNull(body),
            folder = checkNotNull(folder),
            tags = checkNotNull(tags),
            drawing = checkNotNull(drawing),
            pinned = checkNotNull(pinned),
            favorite = checkNotNull(favorite),
            archived = checkNotNull(archived),
            trashedAt = trashedAt,
            createdAt = checkNotNull(createdAt),
            updatedAt = checkNotNull(updatedAt),
            version = revision
        )
    }

    private fun readString(reader: JsonReader, max: Int): String {
        require(reader.peek() == JsonToken.STRING) { "Expected text" }
        return reader.nextString().also { require(it.length <= max) { "Text is too large" } }
    }

    private fun readLong(reader: JsonReader): Long {
        require(reader.peek() == JsonToken.NUMBER) { "Expected number" }
        return reader.nextLong()
    }

    private fun readBoolean(reader: JsonReader): Boolean {
        require(reader.peek() == JsonToken.BOOLEAN) { "Expected boolean" }
        return reader.nextBoolean()
    }

    private suspend fun copyLimited(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(8192)
        var total = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) throw IOException("Input stream made no progress")
            total += count
            require(total <= MAX_BYTES) { "Backup is too large" }
            output.write(buffer, 0, count)
        }
        output.flush()
    }

    private class LimitedOutputStream(output: OutputStream, private val limit: Long) : FilterOutputStream(output) {
        private var total = 0L
        override fun write(value: Int) {
            require(++total <= limit) { "Backup is too large" }
            out.write(value)
        }
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            total += length
            require(total <= limit) { "Backup is too large" }
            out.write(bytes, offset, length)
        }
    }

    /** Bound names, string tokens and nesting before JsonReader allocates them. */
    private class JsonStringLimitReader(private val source: Reader, private val limit: Int) : Reader() {
        private data class Frame(val type: Char, var expectingName: Boolean)

        private val stack = java.util.ArrayDeque<Frame>()
        private var inside = false
        private var escaped = false
        private var size = 0
        private var tokenLimit = limit

        override fun read(buffer: CharArray, offset: Int, length: Int): Int {
            val read = source.read(buffer, offset, length)
            for (index in offset until offset + maxOf(0, read)) {
                val char = buffer[index]
                if (inside) {
                    require(++size <= tokenLimit) { "JSON string is too large" }
                    if (escaped) {
                        escaped = false
                    } else if (char == '\\') {
                        escaped = true
                    } else if (char == '"') {
                        inside = false
                    }
                } else {
                    when (char) {
                        '"' -> {
                            inside = true
                            size = 0
                            val frame = stack.peekLast()
                            tokenLimit = if (frame?.type == '{' && frame.expectingName) MAX_JSON_NAME_CHARS else limit
                        }

                        '{', '[' -> {
                            require(stack.size < 16) { "JSON nesting is too deep" }
                            stack.addLast(Frame(char, char == '{'))
                        }

                        '}', ']' -> {
                            val expected = if (char == '}') '{' else '['
                            require(stack.peekLast()?.type == expected) { "Invalid JSON nesting" }
                            stack.removeLast()
                        }

                        ':' -> stack.peekLast()?.expectingName = false

                        ',' -> if (stack.peekLast()?.type == '{') stack.peekLast().expectingName = true
                    }
                }
            }
            return read
        }

        override fun close() = source.close()
    }

    companion object {
        private const val FORMAT = "lume-notes-backup"
        private const val FORMAT_VERSION = 1L

        // A single row per page avoids holding multiple large drawings in memory.
        private const val PAGE_SIZE = 1
        private const val MAX_NOTES = 10_000
        private const val MAX_BYTES = 128L * 1024 * 1024
        private const val MAX_FIELD_CHARS = 8_000_000
        private const val MAX_NOTE_CHARS = 12_000_000
        private const val MAX_JSON_NAME_CHARS = 256
        private const val MAX_JSON_STRING_CHARS = 16_000_000
    }
}
