package com.onenotes.app.data.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.onenotes.app.data.model.Block
import com.onenotes.app.data.model.DrawingBlock
import com.onenotes.app.data.model.ImageBlock
import com.onenotes.app.data.model.NoteBody
import com.onenotes.app.data.model.NoteEntity
import com.onenotes.app.data.model.TextBlock
import com.onenotes.app.data.repository.FileStorage
import com.onenotes.app.data.repository.FoldersRepository
import com.onenotes.app.data.repository.NotesRepository
import com.onenotes.app.data.repository.TagsRepository
import com.onenotes.app.ui.draw.DrawingRenderer
import com.onenotes.app.ui.draw.StrokeSerializer
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Local export / import of the notes. Everything is done with the platform APIs only
 * (org.json, PdfDocument, BitmapFactory) so that the feature works offline and does not add a
 * single dependency to the app.
 */
class ExportManager(
    private val context: Context,
    private val notes: NotesRepository,
    private val folders: FoldersRepository,
    private val tags: TagsRepository,
    private val storage: FileStorage
) {

    // ------------------------------------------------------------------- backup

    /** Writes every note, folder and tag into a single JSON file. */
    suspend fun exportJson(): File {
        val snapshot = notes.exportSnapshot()
        val folderList = folders.getAll()
        val tagList = tags.getAll()

        val root = JSONObject().apply {
            put("app", APP_NAME)
            put("schema", SCHEMA_VERSION)
            put("exportedAt", System.currentTimeMillis())
            put("folders", JSONArray().apply {
                folderList.forEach { folder ->
                    put(JSONObject().apply {
                        put("id", folder.id)
                        put("name", folder.name)
                        put("color", folder.color)
                        put("position", folder.position)
                    })
                }
            })
            put("tags", JSONArray().apply {
                tagList.forEach { tag ->
                    put(JSONObject().apply {
                        put("id", tag.id)
                        put("name", tag.name)
                    })
                }
            })
            put("notes", JSONArray().apply {
                snapshot.forEach { note ->
                    put(JSONObject().apply {
                        put("id", note.id)
                        put("title", note.title)
                        put("content", note.content)
                        put("folderId", note.folderId ?: 0L)
                        put("color", note.color)
                        put("pinned", note.pinned)
                        put("archived", note.archived)
                        put("createdAt", note.createdAt)
                        put("updatedAt", note.updatedAt)
                        put("reminderAt", note.reminderAt ?: 0L)
                        put("tags", JSONArray().apply {
                            tags.tagsForNote(note.id).forEach { tag -> put(tag.name) }
                        })
                    })
                }
            })
        }

        val file = File(storage.exportsDir(), "onenotes-backup-${stamp()}.json")
        file.writeText(root.toString(2), Charsets.UTF_8)
        return file
    }

    /**
     * Reads a backup written by [exportJson]. The identifiers of the folders are remapped on
     * import, so restoring a backup twice does not merge the notes of the two copies.
     */
    suspend fun importJson(uri: Uri): Int {
        val raw = readText(uri) ?: return 0
        val root = try {
            JSONObject(raw)
        } catch (t: Throwable) {
            try {
                JSONObject(SamsungNotesImporter(context, storage).stripMarkup(raw))
            } catch (ignored: Throwable) {
                return 0
            }
        }

        val folderMap = HashMap<Long, Long>()
        val folderArray = root.optJSONArray("folders")
        if (folderArray != null) {
            for (i in 0 until folderArray.length()) {
                val obj = folderArray.optJSONObject(i) ?: continue
                val oldId = obj.optLong("id", 0L)
                val name = obj.optString("name").ifBlank { continue }
                folderMap[oldId] = folders.create(name, obj.optInt("color", 0))
            }
        }

        val noteArray = root.optJSONArray("notes") ?: return 0
        var imported = 0
        for (i in 0 until noteArray.length()) {
            val obj = noteArray.optJSONObject(i) ?: continue
            val content = obj.optString("content", "")
            val blocks = NoteBody.fromJson(content)
            val oldFolder = obj.optLong("folderId", 0L)
            val now = System.currentTimeMillis()
            val note = NoteEntity(
                title = obj.optString("title", ""),
                content = content.ifBlank { NoteBody.toJson(blocks) },
                plainText = NoteBody.toPlainText(blocks),
                folderId = folderMap[oldFolder],
                color = obj.optInt("color", 0),
                pinned = obj.optBoolean("pinned", false),
                archived = obj.optBoolean("archived", false),
                createdAt = obj.optLong("createdAt", now).takeIf { it > 0 } ?: now,
                updatedAt = obj.optLong("updatedAt", now).takeIf { it > 0 } ?: now,
                reminderAt = obj.optLong("reminderAt", 0L).takeIf { it > 0 }
            )
            val tagNames = ArrayList<String>()
            val tagArray = obj.optJSONArray("tags")
            if (tagArray != null) {
                for (t in 0 until tagArray.length()) {
                    tagArray.optString(t).takeIf { it.isNotBlank() }?.let { tagNames.add(it) }
                }
            }
            notes.insertImported(note, tagNames)
            imported++
        }
        return imported
    }

    /** Imports a file exported by Samsung Notes (.sdocx, .memo, .txt, .md, .html). */
    suspend fun importSamsungNotes(uri: Uri): Int {
        val importer = SamsungNotesImporter(context, storage)
        val imported = importer.import(uri)
        imported.forEach { item ->
            val note = NoteEntity(
                title = item.title,
                content = NoteBody.toJson(item.blocks),
                plainText = NoteBody.toPlainText(item.blocks),
                createdAt = item.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            notes.insertImported(note, emptyList())
        }
        return imported.size
    }

    // ------------------------------------------------------------ single notes

    suspend fun exportMarkdown(note: NoteEntity): File {
        val blocks = com.onenotes.app.data.model.NoteBody.fromJson(note.content)
        val file = File(storage.exportsDir(), "${fileNameOf(note.title)}.md")
        file.writeText(NoteBody.toMarkdown(note.title, blocks), Charsets.UTF_8)
        return file
    }

    suspend fun exportText(note: NoteEntity): File {
        val blocks = NoteBody.fromJson(note.content)
        val file = File(storage.exportsDir(), "${fileNameOf(note.title)}.txt")
        val text = if (note.title.isBlank()) {
            NoteBody.toPlainText(blocks)
        } else {
            note.title + "\n\n" + NoteBody.toPlainText(blocks)
        }
        file.writeText(text, Charsets.UTF_8)
        return file
    }

    /** Renders a note to a one (or more) page A4 PDF, images and drawings included. */
    suspend fun exportPdf(note: NoteEntity): File {
        val blocks = NoteBody.fromJson(note.content)
        val file = File(storage.exportsDir(), "${fileNameOf(note.title)}.pdf")
        val document = PdfDocument()

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 20f
            color = Color.BLACK
            isFakeBoldText = true
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 12f
            color = Color.DKGRAY
        }
        val captionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 10f
            color = Color.GRAY
        }

        var pageNumber = 1
        var page = document.startPage(
            PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        )
        var canvas: Canvas = page.canvas
        var y = MARGIN

        if (note.title.isNotBlank()) {
            val layout = buildLayout(note.title, titlePaint, CONTENT_WIDTH)
            canvas.save()
            canvas.translate(MARGIN, y)
            layout.draw(canvas)
            canvas.restore()
            y += layout.height + 18f
        }

        blocks.forEach { block ->
            val needed = estimatedHeight(block, bodyPaint)
            if (y + needed > PAGE_HEIGHT - MARGIN) {
                document.finishPage(page)
                pageNumber++
                page = document.startPage(
                    PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                )
                canvas = page.canvas
                y = MARGIN
            }
            y = when (block) {
                is TextBlock -> {
                    val paint = paintFor(block, bodyPaint)
                    val prefix = prefixFor(block)
                    val text = prefix + block.text
                    val layout = buildLayout(text, paint, CONTENT_WIDTH - 24f)
                    canvas.save()
                    canvas.translate(MARGIN + 24f, y)
                    layout.draw(canvas)
                    canvas.restore()
                    y + layout.height + 10f
                }
                is ImageBlock -> {
                    val bitmap = decodeFile(storage.attachment(block.fileName), CONTENT_WIDTH)
                    if (bitmap != null) {
                        canvas.drawBitmap(bitmap, MARGIN + 24f, y, null)
                        bitmap.height + 10f + y
                    } else {
                        y
                    }
                }
                is DrawingBlock -> {
                    val strokes = runCatching {
                        StrokeSerializer.fromJson(storage.readDrawing(block.fileName))
                    }.getOrDefault(emptyList())
                    if (strokes.isNotEmpty()) {
                        val height = block.height.coerceIn(200, 1200)
                        val bitmap = DrawingRenderer.render(
                            strokes = strokes,
                            width = CONTENT_WIDTH,
                            height = height,
                            background = Color.WHITE
                        )
                        canvas.drawBitmap(bitmap, MARGIN + 24f, y, null)
                        y + bitmap.height + 10f
                    } else {
                        y
                    }
                }
                else -> {
                    val label = when (block) {
                        is com.onenotes.app.data.model.AudioBlock -> "🔊 ${block.name.ifBlank { "registrazione" }}"
                        is com.onenotes.app.data.model.FileBlock -> "📎 ${block.name.ifBlank { "allegato" }}"
                        else -> "allegato"
                    }
                    val layout = buildLayout(label, captionPaint, CONTENT_WIDTH)
                    canvas.save()
                    canvas.translate(MARGIN + 24f, y)
                    layout.draw(canvas)
                    canvas.restore()
                    y + layout.height + 10f
                }
            }
        }

        document.finishPage(page)
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        return file
    }

    // ------------------------------------------------------------- whole archive

    /** Every note of the archive in a single Markdown file. */
    suspend fun exportAllMarkdown(): File {
        val snapshot = notes.exportSnapshot()
        val builder = StringBuilder()
        builder.append("# OneNotes\n\n")
        snapshot.forEach { note ->
            val blocks = NoteBody.fromJson(note.content)
            builder.append(NoteBody.toMarkdown(note.title, blocks)).append("\n\n---\n\n")
        }
        val file = File(storage.exportsDir(), "onenotes-${stamp()}.md")
        file.writeText(builder.toString(), Charsets.UTF_8)
        return file
    }

    /** Every note of the archive in a single plain text file. */
    suspend fun exportAllText(): File {
        val snapshot = notes.exportSnapshot()
        val builder = StringBuilder()
        snapshot.forEach { note ->
            val blocks = NoteBody.fromJson(note.content)
            if (note.title.isNotBlank()) builder.append(note.title).append("\n\n")
            builder.append(NoteBody.toPlainText(blocks)).append("\n\n---\n\n")
        }
        val file = File(storage.exportsDir(), "onenotes-${stamp()}.txt")
        file.writeText(builder.toString(), Charsets.UTF_8)
        return file
    }

    /** Every note of the archive in a single PDF document. */
    suspend fun exportAllPdf(): File {
        val snapshot = notes.exportSnapshot()
        val file = File(storage.exportsDir(), "onenotes-${stamp()}.pdf")
        val document = PdfDocument()

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 20f
            color = Color.BLACK
            isFakeBoldText = true
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 12f
            color = Color.DKGRAY
        }
        val captionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 10f
            color = Color.GRAY
        }

        var pageNumber = 0
        var page = newPage(document, ++pageNumber)
        var canvas: Canvas = page.canvas
        var y = MARGIN

        val heading = "# OneNotes"
        val headingLayout = buildLayout(heading, titlePaint, CONTENT_WIDTH)
        canvas.save()
        canvas.translate(MARGIN, y)
        headingLayout.draw(canvas)
        canvas.restore()
        y += headingLayout.height + 18f

        snapshot.forEach { note ->
            val blocks = NoteBody.fromJson(note.content)
            var needed = 30f
            blocks.forEach { block -> needed += estimatedHeight(block, bodyPaint) }
            if (y + needed > PAGE_HEIGHT - MARGIN) {
                document.finishPage(page)
                page = newPage(document, ++pageNumber)
                canvas = page.canvas
                y = MARGIN
            }
            if (note.title.isNotBlank()) {
                val layout = buildLayout(note.title, titlePaint, CONTENT_WIDTH)
                canvas.save()
                canvas.translate(MARGIN, y)
                layout.draw(canvas)
                canvas.restore()
                y += layout.height + 12f
            }
            blocks.forEach { block ->
                if (y + estimatedHeight(block, bodyPaint) > PAGE_HEIGHT - MARGIN) {
                    document.finishPage(page)
                    page = newPage(document, ++pageNumber)
                    canvas = page.canvas
                    y = MARGIN
                }
                y = drawBlock(canvas, block, y, bodyPaint, captionPaint)
            }
            y += 24f
        }

        document.finishPage(page)
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        return file
    }

    private fun newPage(document: PdfDocument, number: Int): PdfDocument.Page =
        document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, number).create())

    /** Draws one block of a note on a PDF page and returns the new vertical offset. */
    private fun drawBlock(
        canvas: Canvas,
        block: Block,
        top: Float,
        bodyPaint: TextPaint,
        captionPaint: TextPaint
    ): Float = when (block) {
        is TextBlock -> {
            val paint = paintFor(block, bodyPaint)
            val layout = buildLayout(prefixFor(block) + block.text, paint, CONTENT_WIDTH - 24f)
            canvas.save()
            canvas.translate(MARGIN + 24f, top)
            layout.draw(canvas)
            canvas.restore()
            top + layout.height + 10f
        }
        is ImageBlock -> {
            val bitmap = decodeFile(storage.attachment(block.fileName), CONTENT_WIDTH)
            if (bitmap != null) {
                canvas.drawBitmap(bitmap, MARGIN + 24f, top, null)
                top + bitmap.height + 10f
            } else {
                top
            }
        }
        is DrawingBlock -> {
            val strokes = runCatching {
                StrokeSerializer.fromJson(storage.readDrawing(block.fileName))
            }.getOrDefault(emptyList())
            if (strokes.isNotEmpty()) {
                val height = block.height.coerceIn(200, 1200)
                val bitmap = DrawingRenderer.render(
                    strokes = strokes,
                    width = CONTENT_WIDTH,
                    height = height,
                    background = Color.WHITE
                )
                canvas.drawBitmap(bitmap, MARGIN + 24f, top, null)
                top + bitmap.height + 10f
            } else {
                top
            }
        }
        else -> {
            val label = when (block) {
                is com.onenotes.app.data.model.AudioBlock ->
                    "\uD83D\uDD0A ${block.name.ifBlank { "registrazione" }}"
                is com.onenotes.app.data.model.FileBlock ->
                    "\uD83D\uDCCE ${block.name.ifBlank { "allegato" }}"
                else -> "allegato"
            }
            val layout = buildLayout(label, captionPaint, CONTENT_WIDTH)
            canvas.save()
            canvas.translate(MARGIN + 24f, top)
            layout.draw(canvas)
            canvas.restore()
            top + layout.height + 10f
        }
    }

    // ------------------------------------------------------------------ helpers

    private fun buildLayout(text: String, paint: TextPaint, width: Int): StaticLayout =
        StaticLayout.Builder
            .obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(2f, 1.05f)
            .setIncludePad(false)
            .build()

    private fun paintFor(block: TextBlock, base: TextPaint): TextPaint {
        val size = when (block.blockType) {
            com.onenotes.app.data.model.BlockType.HEADING1 -> 20f
            com.onenotes.app.data.model.BlockType.HEADING2 -> 17f
            com.onenotes.app.data.model.BlockType.HEADING3 -> 15f
            else -> 12f
        }
        if (size == 12f) return base
        return TextPaint(base).apply { textSize = size }
    }

    private fun prefixFor(block: TextBlock): String = when (block.blockType) {
        com.onenotes.app.data.model.BlockType.CHECK -> if (block.checked) "☑ " else "☐ "
        com.onenotes.app.data.model.BlockType.BULLET -> "• "
        com.onenotes.app.data.model.BlockType.QUOTE -> "“"
        else -> ""
    }

    private fun estimatedHeight(block: Block, paint: TextPaint): Float = when (block) {
        is TextBlock -> buildLayout(prefixFor(block) + block.text, paintFor(block, paint), CONTENT_WIDTH - 24f)
            .height.toFloat() + 10f
        is ImageBlock -> 240f
        is DrawingBlock -> block.height.coerceIn(200, 1200).toFloat() + 10f
        else -> 30f
    }

    private fun decodeFile(file: File, maxWidth: Int): Bitmap? {
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > maxWidth * 2) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
        val scale = maxWidth.toFloat() / bitmap.width
        return Bitmap.createScaledBitmap(bitmap, maxWidth, (bitmap.height * scale).toInt(), true)
    }

    private fun readText(uri: Uri): String? = try {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?.toString(Charsets.UTF_8)
    } catch (t: Throwable) {
        null
    }

    private fun fileNameOf(title: String): String {
        val base = title.trim()
            .replace(Regex("[^A-Za-z0-9À-ÿ _-]"), "")
            .trim()
            .ifBlank { "nota" }
        return base.take(48)
    }

    private fun stamp(): String =
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

    companion object {
        const val APP_NAME = "OneNotes"
        const val SCHEMA_VERSION = 1

        private const val PAGE_WIDTH = 595
        private const val PAGE_HEIGHT = 842
        private const val MARGIN = 48f
        private const val CONTENT_WIDTH = PAGE_WIDTH - (MARGIN * 2).toInt()
    }
}
