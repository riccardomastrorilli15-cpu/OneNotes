package dev.lumenotes.database

import androidx.room3.Entity
import androidx.room3.Fts5
import androidx.room3.Index
import androidx.room3.PrimaryKey
import dev.lumenotes.model.Note

@Entity(tableName = "notes", indices = [Index(value = ["uuid"], unique = true)])
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val title: String,
    val body: String,
    val folder: String,
    val tags: String,
    val drawing: String,
    val pinned: Boolean,
    val favorite: Boolean,
    val archived: Boolean,
    val trashedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long
)

/** Room keeps this external-content FTS5 index in sync with the notes table. */
@Fts5(contentEntity = NoteEntity::class, contentRowId = "id")
@Entity(tableName = "notes_fts")
data class NoteFts(val title: String, val body: String, val folder: String, val tags: String)

fun NoteEntity.toModel() = Note(
    id = id,
    uuid = uuid,
    title = title,
    body = body,
    folder = folder,
    tags = tags,
    drawing = drawing,
    pinned = pinned,
    favorite = favorite,
    archived = archived,
    trashedAt = trashedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    version = version
)

fun Note.toEntity() = NoteEntity(
    id = id,
    uuid = uuid,
    title = title,
    body = body,
    folder = folder,
    tags = tags,
    drawing = drawing,
    pinned = pinned,
    favorite = favorite,
    archived = archived,
    trashedAt = trashedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    version = version
)
