package com.example.audioambientglow.service

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class GlowNotificationListenerService : NotificationListenerService() {

    private val tag = "GlowNotifListener"
    private var mediaSessionManager: MediaSessionManager? = null
    private val activeControllers = mutableListOf<MediaController>()

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            updateMediaSessions()
        }

        override fun onMetadataChanged(metadata: android.media.MediaMetadata?) {
            updateMediaSessions()
        }
    }

    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        registerControllers(controllers)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(tag, "Notification Listener connected!")
        setupMediaSessionManager()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        unregisterControllers()
    }

    private fun setupMediaSessionManager() {
        try {
            mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            val componentName = ComponentName(this, GlowNotificationListenerService::class.java)
            mediaSessionManager?.addOnActiveSessionsChangedListener(sessionListener, componentName)

            val controllers = mediaSessionManager?.getActiveSessions(componentName)
            registerControllers(controllers)
        } catch (e: Exception) {
            Log.e(tag, "Error setting up MediaSessionManager: ${e.message}", e)
        }
    }

    private fun registerControllers(controllers: List<MediaController>?) {
        unregisterControllers()
        if (controllers != null) {
            for (controller in controllers) {
                try {
                    controller.registerCallback(controllerCallback)
                    activeControllers.add(controller)
                } catch (e: Exception) {
                    Log.w(tag, "Failed to register controller callback: ${e.message}")
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
            // Check if any controller is active
            val firstController = activeControllers.firstOrNull()
            if (firstController != null) {
                MediaPlaybackDetector.updateFromMediaSession(
                    packageName = firstController.packageName,
                    state = firstController.playbackState,
                    metadata = firstController.metadata,
                    controller = firstController
                )
            } else {
                MediaPlaybackDetector.setPlaying(false)
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        // Whenever a notification is posted (e.g. YouTube Music / vivo music card appears), refresh sessions
        updateMediaSessions()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        updateMediaSessions()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterControllers()
        try {
            mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionListener)
        } catch (e: Exception) {
            // Ignored
        }
    }
}
