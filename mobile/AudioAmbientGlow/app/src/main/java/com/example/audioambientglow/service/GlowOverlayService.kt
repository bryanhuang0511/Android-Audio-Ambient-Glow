package com.example.audioambientglow.service

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.audioambientglow.MainActivity
import com.example.audioambientglow.R
import com.example.audioambientglow.audio.AudioVisualizerManager
import com.example.audioambientglow.data.GlowConfig
import com.example.audioambientglow.data.GlowDisplayMode
import com.example.audioambientglow.data.GlowPreferencesRepository
import com.example.audioambientglow.util.CrashHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class GlowOverlayService : Service() {

    private val tag = "GlowOverlayService"
    private val scope = CoroutineScope(Dispatchers.Main)

    private lateinit var windowManager: WindowManager
    private lateinit var keyguardManager: KeyguardManager
    private lateinit var prefsRepo: GlowPreferencesRepository
    private lateinit var audioManager: AudioVisualizerManager

    private var overlayView: GlowTrackView? = null
    private var windowLayoutParams: WindowManager.LayoutParams? = null
    private var isOverlayAttached = false

    private var isScreenOff = false
    private var isMediaPlaying = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOff = true
                    Log.d(tag, "Screen turned OFF -> pausing overlay render loop")
                    updateOverlayVisibilityAndMode()
                }
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOff = false
                    Log.d(tag, "Screen turned ON")
                    updateOverlayVisibilityAndMode()
                }
                Intent.ACTION_USER_PRESENT -> {
                    isScreenOff = false
                    Log.d(tag, "User unlocked device")
                    updateOverlayVisibilityAndMode()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        prefsRepo = GlowPreferencesRepository.getInstance(this)
        audioManager = AudioVisualizerManager.getInstance(this)

        startForegroundNotification()
        registerScreenStateReceivers()
        observeAppConfigurations()

        val config = prefsRepo.getConfig()
        if (config.isEnabled) {
            audioManager.start(config.audioSourceType)
            attachOverlayView(config)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val config = prefsRepo.getConfig()
                if (config.isEnabled) {
                    audioManager.start(config.audioSourceType)
                    attachOverlayView(config)
                    updateOverlayVisibilityAndMode()
                }
            }
            ACTION_STOP -> {
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {
            Log.w(tag, "Receiver already unregistered or failed: ${e.message}")
        }
        detachOverlayView()
        audioManager.stop()
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundNotification() {
        val channelId = "audio_ambient_glow_service"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "手機音效氣氛燈 守護進程",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "維持 144Hz 滿版音樂賽道流光在全螢幕與息屏環境下的即時渲染"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("手機音效氣氛燈 運作中")
            .setContentText("144Hz 賽道流光就緒，感知音樂重低音與節奏")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    1001,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(1001, notification)
            }
        } catch (e: Exception) {
            CrashHandler.recordException(tag, "Failed startForeground", e)
        }
    }

    private fun registerScreenStateReceivers() {
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(screenReceiver, filter)
            }
        } catch (e: Exception) {
            CrashHandler.recordException(tag, "Failed to register screen state receiver", e)
        }
    }

    private fun observeAppConfigurations() {
        scope.launch {
            prefsRepo.configFlow.collectLatest { config ->
                if (config.isEnabled) {
                    audioManager.start(config.audioSourceType)
                    attachOverlayView(config)
                } else {
                    detachOverlayView()
                    audioManager.stop()
                }
                updateOverlayVisibilityAndMode()
            }
        }

        // Observe Media Playback State (YouTube Music / Spotify / vivo Player)
        scope.launch {
            MediaPlaybackDetector.playbackStateFlow.collectLatest { trackInfo ->
                isMediaPlaying = trackInfo.isPlaying
                Log.d(tag, "Media playing state changed: isPlaying=$isMediaPlaying (Track: ${trackInfo.title})")
                updateOverlayVisibilityAndMode()
            }
        }

        // Observe Audio FFT / Beat features
        scope.launch {
            audioManager.audioFeatures.collectLatest { features ->
                overlayView?.updateAudio(features)
            }
        }

        // Observe AOD Activity status to prevent double-drawing and conflict
        scope.launch {
            AodStateManager.isAodActivityActive.collectLatest { isAodActive ->
                Log.d(tag, "AOD Activity active changed: $isAodActive")
                updateOverlayVisibilityAndMode()
            }
        }
    }

    private fun attachOverlayView(config: GlowConfig) {
        if (isOverlayAttached) {
            overlayView?.updateConfig(config)
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            Log.w(tag, "Cannot attach overlay view: SYSTEM_ALERT_WINDOW permission missing.")
            return
        }

        // Use MATCH_PARENT so that system automatically scales the view to full screen in Portrait & Landscape
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.FILL
            x = 0
            y = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        val view = GlowTrackView(this).apply {
            updateConfig(config)
            setAodFullscreen(false)
        }

        try {
            windowManager.addView(view, layoutParams)
            overlayView = view
            windowLayoutParams = layoutParams
            isOverlayAttached = true
            Log.d(tag, "Fullscreen Glow Overlay attached successfully (MATCH_PARENT auto-rotation).")
        } catch (e: Exception) {
            Log.e(tag, "Failed to add floating overlay: ${e.message}", e)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val view = overlayView ?: return
        val lp = windowLayoutParams ?: return
        try {
            lp.width = WindowManager.LayoutParams.MATCH_PARENT
            lp.height = WindowManager.LayoutParams.MATCH_PARENT
            windowManager.updateViewLayout(view, lp)
            view.post {
                view.requestLayout()
                view.invalidate()
            }
            Log.d(tag, "Overlay updated onConfigurationChanged (orientation=${newConfig.orientation})")
        } catch (e: Exception) {
            Log.e(tag, "Error updating layout on configuration change: ${e.message}", e)
        }
    }

    private fun updateOverlayVisibilityAndMode() {
        val view = overlayView ?: return
        val config = prefsRepo.getConfig()

        // 1. If global disabled or dedicated AodGlowActivity is running, hide overlay completely
        if (!config.isEnabled || AodStateManager.isAodActivityActive.value) {
            view.visibility = View.GONE
            return
        }

        // 2. If screen is OFF, hide overlay (saves battery, never fight with PowerManager)
        if (isScreenOff) {
            view.visibility = View.GONE
            return
        }

        val isLocked = keyguardManager.isKeyguardLocked
        val musicActive = isMediaPlaying || MediaPlaybackDetector.isMusicActive(this)

        when (config.displayMode) {
            GlowDisplayMode.AOD_ONLY -> {
                // In AOD_ONLY mode, background overlay stays hidden while unlocked screen is on.
                // Full AOD is handled by AodGlowActivity
                view.visibility = View.GONE
            }
            GlowDisplayMode.ALWAYS_OVERLAY -> {
                // Floating border glow on top of all apps
                view.visibility = View.VISIBLE
                view.setAodFullscreen(false)
            }
            GlowDisplayMode.SCREEN_ON_ONLY -> {
                if (!isLocked) {
                    view.visibility = View.VISIBLE
                    view.setAodFullscreen(false)
                } else {
                    view.visibility = View.GONE
                }
            }
        }
    }

    private fun detachOverlayView() {
        if (!isOverlayAttached) return
        try {
            overlayView?.let { windowManager.removeViewImmediate(it) }
        } catch (e: Exception) {
            Log.e(tag, "Error removing overlay view: ${e.message}", e)
        } finally {
            overlayView = null
            windowLayoutParams = null
            isOverlayAttached = false
        }
    }

    companion object {
        const val ACTION_START = "com.example.audioambientglow.action.START"
        const val ACTION_STOP = "com.example.audioambientglow.action.STOP"

        fun start(context: Context) {
            val intent = Intent(context, GlowOverlayService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, GlowOverlayService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
