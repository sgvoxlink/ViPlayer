package com.viplayer.playback

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.viplayer.data.MediaEntity
import com.viplayer.data.title

object MediaItems {
    const val EXTRA_VIDEO = "viplayer.video"

    fun from(m: MediaEntity): MediaItem {
        val extras = Bundle().apply { putBoolean(EXTRA_VIDEO, m.isVideo) }
        return MediaItem.Builder()
            .setMediaId(m.id.toString())
            .setUri(m.uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(m.title)
                    .setArtist(m.groupName.substringAfterLast(" / "))
                    .setAlbumTitle(m.groupName)
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .setExtras(extras)
                    .build()
            )
            .build()
    }
}
