package com.example.audioambientglow.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.audioambientglow.lyrics.LyricsEngine

class GlowNotificationListenerService : NotificationListenerService() {

    private val tag = "GlowNotifListener"
    private var mediaSessionManager: MediaSessionManager? = null
    private val activeControllers = mutableListOf<MediaController>()
    private val handler = Handler(Looper.getMainLooper())

    private val pollRunnable = object : Runnable {
        override fun run() {
            refreshActiveSessions()
            handler.postDelayed(this, 1200L)
        }
    }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            updateMediaSessions()
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            updateMediaSessions()
        }
    }

    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        registerControllers(controllers)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(tag, "Notification Listener connected!")
        setupMediaSessionManager()
        handler.removeCallbacks(pollRunnable)
        handler.post(pollRunnable)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        handler.removeCallbacks(pollRunnable)
        unregisterControllers()
    }

    private fun setupMediaSessionManager() {
        try {
            mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            val componentName = ComponentName(this, GlowNotificationListenerService::class.java)
            mediaSessionManager?.addOnActiveSessionsChangedListener(sessionListener, componentName)
            refreshActiveSessions()
        } catch (e: Exception) {
            Log.e(tag, "Error setting up MediaSessionManager: ", e)
        }
    }

    private fun refreshActiveSessions() {
        try {
            val componentName = ComponentName(this, GlowNotificationListenerService::class.java)
            val controllers = mediaSessionManager?.getActiveSessions(componentName)
            if (controllers != null) {
                registerControllers(controllers)
            } else {
                scanActiveNotifications()
            }
        } catch (e: Exception) {
            scanActiveNotifications()
        }
    }

    private fun registerControllers(controllers: List<MediaController>?) {
        if (controllers == null) return
        
        var listChanged = false
        if (controllers.size != activeControllers.size) {
            listChanged = true
        } else {
            for (i in controllers.indices) {
                if (controllers[i].sessionToken != activeControllers[i].sessionToken) {
                    listChanged = true
                    break
                }
            }
        }

        if (listChanged) {
            unregisterControllers()
            for (controller in controllers) {
                try {
                    controller.registerCallback(controllerCallback, handler)
                    activeControllers.add(controller)
                } catch (e: Exception) {
                    Log.w(tag, "Failed to register controller callback: ")
                }
            }
        }
        updateMediaSessions()
    }

    private fun unregisterControllers() {
        for (controller in activeControllers) {
            try {
                controller.unregisterCallback(controllerCallback)
            } catch (e: Exception) {
                // Ignored
            }
        }
        activeControllers.clear()
    }

    private fun scanActiveNotifications() {
        try {
            val activeNotifs = activeNotifications ?: return
            for (sbn in activeNotifs) {
                inspectNotification(sbn)
            }
        } catch (e: Exception) {
            Log.w(tag, "Error scanning active notifications: ")
        }
    }

    private fun inspectNotification(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val notif = sbn.notification ?: return
        val extras = notif.extras ?: return

        val title = (extras.getCharSequence(Notification.EXTRA_TITLE)
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT))?.toString()?.trim() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: ""
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim() ?: ""

        val isMediaCategory = notif.category == Notification.CATEGORY_TRANSPORT ||
                             extras.containsKey(Notification.EXTRA_MEDIA_SESSION)
        val isMusicApp = sbn.packageName.contains("music", ignoreCase = true) ||
                         sbn.packageName.contains("chrome", ignoreCase = true) ||
                         sbn.packageName.contains("youtube", ignoreCase = true) ||
                         sbn.packageName.contains("spotify", ignoreCase = true) ||
                         sbn.packageName.contains("browser", ignoreCase = true) ||
                         sbn.packageName.contains("morphe", ignoreCase = true)

        if ((isMediaCategory || isMusicApp) && title.isNotEmpty()) {
            val artist = if (text.isNotEmpty()) text else subText
            MediaPlaybackDetector.setPlaying(
                isPlaying = true,
                title = title,
                artist = artist,
                pkg = sbn.packageName
            )
            // Preload lyrics in background immediately
            LyricsEngine.fetchLyrics(title, artist)
        }
    }

    private fun updateMediaSessions() {
        var foundPlaying = false
        for (controller in activeControllers) {
            val state = controller.playbackState
            if (state?.state == PlaybackState.STATE_PLAYING) {
                foundPlaying = true
                MediaPlaybackDetector.updateFromMediaSession(
                    packageName = controller.packageName,
                    state = state,
                    metadata = controller.metadata,
                    controller = controller
                )
                break
            }
        }

        if (!foundPlaying) {
            val firstController = activeControllers.firstOrNull()
            if (firstController != null) {
                MediaPlaybackDetector.updateFromMediaSession(
                    packageName = firstController.packageName,
                    state = firstController.playbackState,
                    metadata = firstController.metadata,
                    controller = firstController
                )
            } else {
                scanActiveNotifications()
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        inspectNotification(sbn)
        updateMediaSessions()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        updateMediaSessions()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(pollRunnable)
        unregisterControllers()
        try {
            mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionListener)
        } catch (e: Exception) {
            // Ignored
        }
    }
}