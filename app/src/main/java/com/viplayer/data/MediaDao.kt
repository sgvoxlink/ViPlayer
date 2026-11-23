package com.viplayer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class MediaDao {

    // ---------- Папки ----------

    @Query("SELECT * FROM folders ORDER BY name COLLATE NOCASE")
    abstract fun foldersFlow(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders")
    abstract suspend fun folders(): List<FolderEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertFolder(folder: FolderEntity): Long

    @Query("DELETE FROM folders WHERE id = :id")
    abstract suspend fun deleteFolder(id: Long)

    // ---------- Файлы ----------

    @Query(
        """
        SELECT groupName,
               COUNT(*) AS total,
               SUM(CASE WHEN played = 1 THEN 1 ELSE 0 END) AS playedCount,
               SUM(MAX(durationMs, 0)) AS totalMs,
               SUM(CASE WHEN played = 1 THEN 0 ELSE MAX(durationMs - positionMs, 0) END) AS remainingMs
        FROM media
        GROUP BY groupName
        """
    )
    abstract fun groups(): Flow<List<GroupSummary>>

    @Query("SELECT * FROM media WHERE groupName = :group")
    abstract fun inGroup(group: String): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE groupName = :group")
    abstract suspend fun inGroupNow(group: String): List<MediaEntity>

    @Query("SELECT * FROM media WHERE folderId = :folderId")
    abstract suspend fun mediaOfFolder(folderId: Long): List<MediaEntity>

    @Query("SELECT * FROM media WHERE id = :id")
    abstract suspend fun media(id: Long): MediaEntity?

    @Query("SELECT * FROM media WHERE id IN (:ids)")
    abstract suspend fun mediaByIds(ids: List<Long>): List<MediaEntity>

    @Query("SELECT * FROM media WHERE durationMs <= 0")
    abstract suspend fun withoutDuration(): List<MediaEntity>

    @Query("SELECT COUNT(*) FROM media WHERE played = 1")
    abstract fun playedCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertMedia(items: List<MediaEntity>)

    @Query("UPDATE media SET groupName = :group, name = :name, sizeBytes = :size WHERE id = :id")
    abstract suspend fun updateMeta(id: Long, group: String, name: String, size: Long)

    @Query("DELETE FROM media WHERE id IN (:ids)")
    abstract suspend fun deleteMedia(ids: List<Long>)

    @Query("DELETE FROM media WHERE folderId = :folderId")
    abstract suspend fun deleteMediaOfFolder(folderId: Long)

    @Query("UPDATE media SET durationMs = :duration WHERE id = :id")
    abstract suspend fun setDuration(id: Long, duration: Long)

    @Query("UPDATE media SET durationMs = :duration WHERE id = :id AND durationMs <= 0")
    abstract suspend fun setDurationIfMissing(id: Long, duration: Long)

    @Query("UPDATE media SET positionMs = :position WHERE id = :id")
    abstract suspend fun setPosition(id: Long, position: Long)

    @Query("UPDATE media SET played = :played, playedAt = :at, positionMs = 0 WHERE id IN (:ids)")
    abstract suspend fun setPlayed(ids: List<Long>, played: Boolean, at: Long)

    // ---------- Плейлист ----------

    @Query(
        """
        SELECT m.*, q.id AS queueId, q.sortOrder AS sortOrder
        FROM queue q JOIN media m ON m.id = q.mediaId
        ORDER BY q.sortOrder
        """
    )
    abstract fun queue(): Flow<List<QueueRow>>

    @Query(
        """
        SELECT m.*, q.id AS queueId, q.sortOrder AS sortOrder
        FROM queue q JOIN media m ON m.id = q.mediaId
        ORDER BY q.sortOrder
        """
    )
    abstract suspend fun queueNow(): List<QueueRow>

    @Query("SELECT mediaId FROM queue")
    abstract suspend fun queueMediaIds(): List<Long>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM queue")
    abstract suspend fun maxQueueOrder(): Int

    @Insert
    abstract suspend fun insertQueue(items: List<QueueEntity>)

    @Query("DELETE FROM queue WHERE id = :queueId")
    abstract suspend fun deleteQueueItem(queueId: Long)

    @Query("DELETE FROM queue")
    abstract suspend fun clearQueue()

    @Query("DELETE FROM queue WHERE mediaId IN (SELECT id FROM media WHERE played = 1)")
    abstract suspend fun clearPlayedFromQueue()

    @Query("DELETE FROM queue WHERE mediaId IN (:mediaIds)")
    abstract suspend fun deleteQueueByMedia(mediaIds: List<Long>)

    @Query("DELETE FROM queue WHERE mediaId IN (SELECT id FROM media WHERE folderId = :folderId)")
    abstract suspend fun deleteQueueOfFolder(folderId: Long)

    @Query("UPDATE queue SET sortOrder = :order WHERE id = :queueId")
    abstract suspend fun setQueueOrder(queueId: Long, order: Int)

    @Transaction
    open suspend fun reorderQueue(queueIds: List<Long>) {
        queueIds.forEachIndexed { index, id -> setQueueOrder(id, index) }
    }
}
