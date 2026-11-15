package dev.lumenotes.app

import android.content.Context
import android.net.Uri
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.lumenotes.database.NoteEntity
import dev.lumenotes.database.NotesDatabase
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotesBackupTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var source: NotesDatabase
    private lateinit var target: NotesDatabase
    private lateinit var exporter: NotesBackup
    private lateinit var importer: NotesBackup

    @Before fun open() {
        source = newDatabase()
        target = newDatabase()
        exporter = NotesBackup(context, source)
        importer = NotesBackup(context, target)
    }

    @After fun close() {
        source.close()
        target.close()
    }

    @Test fun roundTripPreservesDrawingMetadataTrashAndSearchIndex() = runBlocking {
        val active = note("active").copy(
            title = "Caffè 🦋", body = "First line\n\"quoted\" \\ slash\tend",
            folder = "Studio", tags = "università, test", drawing = "{\"strokes\":[[1,2,3]]}",
            pinned = true, favorite = true, createdAt = 123, updatedAt = 456, version = 7
        )
        val trash = note("trash").copy(title = "In trash", archived = true, trashedAt = 999, version = 3)
        val archived = note("archive").copy(title = "Archived", archived = true)
        listOf(active, trash, archived).forEach { source.notes().insert(it) }
        val output = ByteArrayOutputStream()
        assertEquals(3, exporter.exportTo(output))
        assertEquals(NotesBackup.RestoreResult(3, 0), importer.restoreFrom(output.toByteArray().inputStream()))
        assertEquals(listOf(active, trash, archived), target.notes().backupPage(0, 10).map { it.copy(id = 0) })
        assertEquals(1, target.notes().search("notes", null, "\"università\"*", 20).first().size)
        assertEquals(1, target.notes().observeCollection("trash", null, 20).first().size)
        assertEquals(1, target.notes().observeCollection("archive", null, 20).first().size)
    }

    @Test fun existingUuidIsNotOverwrittenAndNewNoteIsAdded() = runBlocking {
        source.notes().insert(note("same").copy(body = "From backup"))
        source.notes().insert(note("new").copy(body = "Brand new"))
        val existing = note("same").copy(body = "Local changes", updatedAt = 500, version = 9)
        target.notes().insert(existing)
        val bytes = snapshot()
        assertEquals(NotesBackup.RestoreResult(1, 1), importer.restoreFrom(bytes.inputStream()))
        assertEquals(listOf(existing, note("new").copy(body = "Brand new")), target.notes().backupPage(0, 10).map { it.copy(id = 0) })
        assertEquals(NotesBackup.RestoreResult(0, 2), importer.restoreFrom(bytes.inputStream()))
    }

    @Test fun truncatedBackupRollsBackAllInsertedRows() = runBlocking {
        source.notes().insert(note("one"))
        source.notes().insert(note("two"))
        target.notes().insert(note("local"))
        val bytes = snapshot()
        assertNotNull(runCatching { importer.restoreFrom(bytes.copyOf(bytes.size - 1).inputStream()) }.exceptionOrNull())
        assertEquals(listOf(note("local")), target.notes().backupPage(0, 10).map { it.copy(id = 0) })
        assertTrue(target.notes().search("notes", null, "\"one\"*", 20).first().isEmpty())
    }

    @Test fun duplicateUuidInvalidMetadataAndTrailingDataAreRejectedAtomically() = runBlocking {
        source.notes().insert(note("one"))
        source.notes().insert(note("two"))
        target.notes().insert(note("local"))
        val json = snapshot().toString(Charsets.UTF_8)
        val invalid = listOf(
            json.replace("\"uuid\":\"two\"", "\"uuid\":\"one\""),
            json.replace("\"count\":2", "\"count\":3"),
            json.replace("\"version\":1,\"notes\"", "\"version\":2,\"notes\""),
            json.replace("\"trashedAt\":null", "\"trashedAt\":null,\"trashedAt\":null"),
            json.replace("\"body\":\"\"", "\"body\":\"\\ud800\""),
            json.replace("\"folder\":\"\"", "\"folder\":\"${"f".repeat(121)}\""),
            json.replace("\"tags\":\"\"", "\"tags\":\"${"t".repeat(501)}\""),
            json + "{}"
        )
        for (data in invalid) {
            assertNotNull("Invalid backup was accepted", runCatching { importer.restoreFrom(data.byteInputStream()) }.exceptionOrNull())
            assertEquals(listOf(note("local")), target.notes().backupPage(0, 10).map { it.copy(id = 0) })
        }
    }

    @Test fun rejectsInvalidUtf8OverlongNamesAndStalledInput() = runBlocking {
        source.notes().insert(note("one"))
        val json = snapshot().toString(Charsets.UTF_8)
        val longName = json.replace("\"format\"", "\"${"x".repeat(300)}\"")
        assertNotNull(runCatching { importer.restoreFrom(longName.byteInputStream()) }.exceptionOrNull())
        val invalidUtf8 = json.toByteArray() + byteArrayOf(0xc3.toByte(), 0x28)
        assertNotNull(runCatching { importer.restoreFrom(ByteArrayInputStream(invalidUtf8)) }.exceptionOrNull())
        val stalled = object : InputStream() {
            override fun read(): Int = 0
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int = 0
        }
        assertNotNull(runCatching { importer.restoreFrom(stalled) }.exceptionOrNull())
        assertTrue(target.notes().backupPage(0, 10).isEmpty())
    }

    @Test fun streamWriteFailureLeavesSourceUntouchedAndCleansTemporaryFile() = runBlocking {
        source.notes().insert(note("one"))
        val before = temporaryFiles()
        val failing = object : OutputStream() {
            override fun write(value: Int): Unit = throw IOException("synthetic write failure")
            override fun write(bytes: ByteArray, offset: Int, length: Int): Unit = throw IOException("synthetic write failure")
        }
        assertNotNull(runCatching { exporter.exportTo(failing) }.exceptionOrNull())
        assertEquals(listOf(note("one")), source.notes().backupPage(0, 10).map { it.copy(id = 0) })
        assertEquals(before, temporaryFiles())
    }

    @Test fun resolverStreamsTruncateDestinationAndCanBeRestored() = runBlocking {
        source.notes().insert(note("resolver").copy(body = "Safely restored"))
        val file = java.io.File.createTempFile("backup-resolver-test-", ".json", context.cacheDir)
        try {
            file.writeText("unrelated data".repeat(1_000))
            val uri = Uri.fromFile(file) // Exercises ContentResolver streams without an external document provider.
            assertEquals(1, exporter.export(uri))
            assertTrue(!file.readText().contains("unrelated data"))
            assertEquals(NotesBackup.RestoreResult(1, 0), importer.restore(uri))
            assertEquals("Safely restored", target.notes().backupPage(0, 10).single().body)
        } finally {
            file.delete()
        }
    }

    private fun newDatabase(): NotesDatabase = Room.inMemoryDatabaseBuilder<NotesDatabase>()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

    private suspend fun snapshot(): ByteArray = ByteArrayOutputStream().also { exporter.exportTo(it) }.toByteArray()

    private fun temporaryFiles(): Set<String> = context.cacheDir.listFiles().orEmpty()
        .filter { it.name.startsWith("lume-backup-") }.map { it.name }.toSet()

    private fun note(uuid: String) = NoteEntity(
        uuid = uuid, title = uuid, body = "", folder = "", tags = "", drawing = "",
        pinned = false, favorite = false, archived = false, trashedAt = null,
        createdAt = 100, updatedAt = 200, version = 1
    )
}
