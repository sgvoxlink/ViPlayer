package com.viplayer.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document

/**
 * Обходит папку, выданную через Storage Access Framework, и находит аудио/видеофайлы.
 * Работает напрямую через DocumentsContract — это в разы быстрее, чем DocumentFile.
 */
class Scanner(private val context: Context) {

    data class Found(
        val uri: String,
        val group: String,
        val name: String,
        val isVideo: Boolean,
        val size: Long,
    )

    fun scan(treeUri: Uri, rootName: String): List<Found> {
        val out = mutableListOf<Found>()
        walk(treeUri, DocumentsContract.getTreeDocumentId(treeUri), rootName, out)
        return out
    }

    private fun walk(tree: Uri, docId: String, group: String, out: MutableList<Found>) {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
        val projection = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
        )
        val subdirs = mutableListOf<Pair<String, String>>()
        context.contentResolver.query(children, projection, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                val mime = c.getString(2).orEmpty()
                val size = if (c.isNull(3)) 0L else c.getLong(3)
                if (name.startsWith(".")) continue
                if (mime == Document.MIME_TYPE_DIR) {
                    subdirs += id to name
                } else {
                    val video = isVideo(name, mime) ?: continue
                    out += Found(
                        uri = DocumentsContract.buildDocumentUriUsingTree(tree, id).toString(),
                        group = group,
                        name = name,
                        isVideo = video,
                        size = size,
                    )
                }
            }
        }
        for ((id, name) in subdirs) walk(tree, id, "$group / $name", out)
    }

    fun readDuration(uri: Uri): Long {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, uri)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            try {
                r.release()
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        private val AUDIO = setOf(
            "mp3", "m4a", "m4b", "aac", "ogg", "oga", "opus", "flac", "wav", "amr", "mka", "3ga",
        )
        private val VIDEO = setOf("mp4", "m4v", "mkv", "webm", "mov", "3gp", "avi", "ts", "flv")

        /** true — видео, false — аудио, null — не медиафайл. */
        fun isVideo(name: String, mime: String): Boolean? {
            val ext = name.substringAfterLast('.', "").lowercase()
            return when {
                ext in VIDEO -> true
                ext in AUDIO -> false
                mime.startsWith("video/") -> true
                mime.startsWith("audio/") -> false
                else -> null
            }
        }
    }
}
