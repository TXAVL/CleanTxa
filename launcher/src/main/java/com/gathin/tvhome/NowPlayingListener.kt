package com.gathin.tvhome

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService

class NowPlayingListener : NotificationListenerService() {
    companion object {
        fun activeControllers(ctx: Context): List<MediaController> = runCatching {
            val mgr = ctx.getSystemService(MediaSessionManager::class.java)
            mgr?.getActiveSessions(ComponentName(ctx, NowPlayingListener::class.java)) ?: emptyList()
        }.getOrDefault(emptyList())

        fun pauseAll(ctx: Context): Int {
            var count = 0
            activeControllers(ctx).forEach { mc ->
                if (mc.playbackState?.state == PlaybackState.STATE_PLAYING) {
                    mc.transportControls.pause()
                    count++
                }
            }
            return count
        }
    }
}

