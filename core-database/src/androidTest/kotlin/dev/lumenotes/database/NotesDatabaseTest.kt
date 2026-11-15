package dev.lumenotes.database

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.lumenotes.model.Note
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotesDatabaseTest {
    private lateinit var db: NotesDatabase
    private lateinit var dao: NoteDao

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder<NotesDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        dao = db.notes()
    }

    @After fun close() {
        db.close()
    }

    @Test fun ftsTracksEditTrashRestoreAndDelete() = runBlocking {
        val note = Note(uuid = "test-uuid", title = "Caffè", body = "old text", createdAt = 1, updatedAt = 1)
        val id = dao.insert(note.toEntity())
        assertEquals(1, dao.search("notes", null, "\"caff\"*", 50).first().size)
        dao.updateContent(id, "New title", "new body", "Studio", "università", 2)
        assertTrue(dao.search("notes", null, "\"caff\"*", 50).first().isEmpty())
        assertEquals(1, dao.search("notes", "Studio", "\"università\"*", 50).first().size)
        dao.trash(id, 3)
        assertTrue(dao.search("notes", null, "\"new\"*", 50).first().isEmpty())
        assertEquals(1, dao.search("trash", null, "\"new\"*", 50).first().size)
        dao.restore(id, 4)
        assertEquals(1, dao.search("notes", null, "\"new\"*", 50).first().size)
        dao.trash(id, 5)
        dao.deletePermanently(id)
        assertTrue(dao.search("trash", null, "\"new\"*", 50).first().isEmpty())
    }

    @Test fun savingUnchangedContentAndDrawingDoesNotAlterModificationTime() = runBlocking {
        val original = Note(
            uuid = "unchanged", title = "Title", body = "Body", folder = "Folder", tags = "tag",
            drawing = "ink", createdAt = 1, updatedAt = 2, version = 7
        )
        val id = dao.insert(original.toEntity())
        assertEquals(1, dao.updateContent(id, "Title", "Body", "Folder", "tag", 100))
        assertEquals(1, dao.updateDrawing(id, "ink", 200))
        val unchanged = dao.find(id)!!
        assertEquals(2L, unchanged.updatedAt)
        assertEquals(7L, unchanged.version)
        assertEquals(1, dao.updateContent(id, "Title", "Changed", "Folder", "tag", 300))
        val edited = dao.find(id)!!
        assertEquals(300L, edited.updatedAt)
        assertEquals(8L, edited.version)
    }

    @Test fun contentSaveDoesNotOverwriteOtherFields() = runBlocking {
        val id = dao.insert(Note(uuid = "another", createdAt = 1, updatedAt = 1).toEntity())
        dao.setPinned(id, true, 2)
        dao.updateDrawing(id, "ink", 3)
        dao.updateContent(id, "Title", "Body", "", "", 4)
        val saved = dao.find(id)!!
        assertTrue(saved.pinned)
        assertEquals("ink", saved.drawing)
    }
}
