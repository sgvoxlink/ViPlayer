package com.viplayer.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Корневая папка, которую пользователь выдал приложению через системный выбор папки. */
@Entity(tableName = "folders", indices = [Index(value = ["treeUri"], unique = true)])
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val treeUri: String,
    val name: String,
)

/**
 * Один аудио/видеофайл. [groupName] — путь папки внутри корня («Космос» или «Космос / Часть 2»),
 * по нему файлы раскладываются в библиотеке.
 */
@Entity(
    tableName = "media",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["uri"], unique = true), Index("folderId"), Index("groupName")],
)
data class MediaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val folderId: Long,
    val uri: String,
    val groupName: String,
    val name: String,
    val isVideo: Boolean,
    val sizeBytes: Long,
    val durationMs: Long = 0,
    val positionMs: Long = 0,
    val played: Boolean = false,
    val playedAt: Long = 0,
)

/** Элемент пользовательского плейлиста. */
@Entity(
    tableName = "queue",
    foreignKeys = [
        ForeignKey(
            entity = MediaEntity::class,
            parentColumns = ["id"],
            childColumns = ["mediaId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("mediaId")],
)
data class QueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaId: Long,
    val sortOrder: Int,
)

data class QueueRow(
    @Embedded val media: MediaEntity,
    val queueId: Long,
    val sortOrder: Int,
)

data class GroupSummary(
    val groupName: String,
    val total: Int,
    val playedCount: Int,
    val totalMs: Long,
    val remainingMs: Long,
)

/** Имя файла без расширения. */
val MediaEntity.title: String get() = name.substringBeforeLast('.', name)

/** Сколько осталось дослушать. */
val MediaEntity.remainingMs: Long
    get() = if (played) 0 else (durationMs - positionMs).coerceAtLeast(0)
