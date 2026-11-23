package com.viplayer.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class ScanState(val running: Boolean = false, val message: String = "")

class Repository(
    private val context: Context,
    db: AppDatabase,
    private val scope: CoroutineScope,
    private val notify: (String) -> Unit,
) {
    private val dao = db.dao()
    private val scanner = Scanner(context)
    private val scanMutex = Mutex()
    private val _scan = MutableStateFlow(ScanState())
    val scanState: StateFlow<ScanState> = _scan.asStateFlow()

    // ---------- Чтение ----------

    fun folders(): Flow<List<FolderEntity>> = dao.foldersFlow()

    fun groups(): Flow<List<GroupSummary>> =
        dao.groups().map { list -> list.sortedWith(compareBy(NaturalOrder) { it.groupName }) }

    fun filesInGroup(group: String): Flow<List<MediaEntity>> =
        dao.inGroup(group).map { it.sortedWith(NaturalOrder.media) }

    suspend fun filesInGroupNow(group: String): List<MediaEntity> =
        dao.inGroupNow(group).sortedWith(NaturalOrder.media)

    fun queue(): Flow<List<QueueRow>> = dao.queue()

    suspend fun queueNow(): List<QueueRow> = dao.queueNow()

    fun playedCount(): Flow<Int> = dao.playedCount()

    suspend fun media(id: Long): MediaEntity? = dao.media(id)

    /** Файлы по списку id в том же порядке. */
    suspend fun mediaByIds(ids: List<Long>): List<MediaEntity> {
        if (ids.isEmpty()) return emptyList()
        val byId = dao.mediaByIds(ids).associateBy { it.id }
        return ids.mapNotNull { byId[it] }
    }

    // ---------- Папки и сканирование ----------

    fun addFolder(treeUri: Uri) = scope.launch {
        try {
            context.contentResolver.takePersistableUriPermission(
                treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: Exception) {
        }
        val name = DocumentFile.fromTreeUri(context, treeUri)?.name
            ?: treeUri.lastPathSegment?.substringAfterLast(':')
            ?: "Папка"
        dao.insertFolder(FolderEntity(treeUri = treeUri.toString(), name = name))
        rescan()
    }

    fun removeFolder(folder: FolderEntity) = scope.launch {
        dao.deleteQueueOfFolder(folder.id)
        dao.deleteMediaOfFolder(folder.id)
        dao.deleteFolder(folder.id)
        try {
            context.contentResolver.releasePersistableUriPermission(
                Uri.parse(folder.treeUri), Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: Exception) {
        }
    }

    fun rescan() = scope.launch {
        if (scanMutex.isLocked) return@launch
        scanMutex.withLock {
            try {
                withContext(Dispatchers.IO) { doScan() }
            } finally {
                _scan.value = ScanState()
            }
        }
    }

    private suspend fun doScan() {
        _scan.value = ScanState(true, "Сканирование папок…")
        var failed = 0
        for (folder in dao.folders()) {
            _scan.value = ScanState(true, "Сканирование: ${folder.name}")
            val found = try {
                scanner.scan(Uri.parse(folder.treeUri), folder.name)
            } catch (e: Exception) {
                // Нет доступа (папку удалили или отозвали разрешение) — ничего не трогаем.
                failed++
                continue
            }
            val existing = dao.mediaOfFolder(folder.id).associateBy { it.uri }
            val foundUris = found.mapTo(HashSet()) { it.uri }

            val toInsert = found.filter { it.uri !in existing }.map {
                MediaEntity(
                    folderId = folder.id,
                    uri = it.uri,
                    groupName = it.group,
                    name = it.name,
                    isVideo = it.isVideo,
                    sizeBytes = it.size,
                )
            }
            if (toInsert.isNotEmpty()) dao.insertMedia(toInsert)

            for (f in found) {
                val e = existing[f.uri] ?: continue
                if (e.groupName != f.group || e.name != f.name || e.sizeBytes != f.size) {
                    dao.updateMeta(e.id, f.group, f.name, f.size)
                }
            }

            val gone = existing.values.filter { it.uri !in foundUris }.map { it.id }
            if (gone.isNotEmpty()) {
                dao.deleteQueueByMedia(gone)
                dao.deleteMedia(gone)
            }
        }

        val noDuration = dao.withoutDuration()
        noDuration.forEachIndexed { i, m ->
            _scan.value = ScanState(true, "Определяю длительность: ${i + 1} из ${noDuration.size}")
            val d = scanner.readDuration(Uri.parse(m.uri))
            if (d > 0) dao.setDuration(m.id, d)
        }
        if (failed > 0) notify("Нет доступа к папкам: $failed. Добавьте их заново в настройках.")
    }

    // ---------- Плейлист ----------

    fun addToQueue(mediaIds: List<Long>) = scope.launch {
        val present = dao.queueMediaIds().toHashSet()
        var order = dao.maxQueueOrder()
        val items = mediaIds.distinct().filter { it !in present }.map {
            QueueEntity(mediaId = it, sortOrder = ++order)
        }
        if (items.isNotEmpty()) dao.insertQueue(items)
        notify(
            when {
                items.isEmpty() -> "Уже в плейлисте"
                items.size == 1 -> "Добавлено в плейлист"
                else -> "Добавлено в плейлист: ${items.size}"
            }
        )
    }

    fun addGroupToQueue(group: String) = scope.launch {
        val ids = filesInGroupNow(group).filter { !it.played }.map { it.id }
        if (ids.isEmpty()) notify("В этой папке всё прослушано") else addToQueue(ids)
    }

    fun removeFromQueue(queueId: Long) = scope.launch { dao.deleteQueueItem(queueId) }

    fun clearQueue() = scope.launch { dao.clearQueue() }

    fun clearPlayedFromQueue() = scope.launch { dao.clearPlayedFromQueue() }

    fun reorderQueue(queueIds: List<Long>) = scope.launch { dao.reorderQueue(queueIds) }

    // ---------- Прогресс ----------

    fun setPlayed(ids: List<Long>, played: Boolean) = scope.launch {
        if (ids.isNotEmpty()) dao.setPlayed(ids, played, if (played) System.currentTimeMillis() else 0)
    }

    suspend fun markPlayed(id: Long) = dao.setPlayed(listOf(id), true, System.currentTimeMillis())

    suspend fun savePosition(id: Long, positionMs: Long) = dao.setPosition(id, positionMs)

    suspend fun setDurationIfMissing(id: Long, durationMs: Long) = dao.setDurationIfMissing(id, durationMs)
}
