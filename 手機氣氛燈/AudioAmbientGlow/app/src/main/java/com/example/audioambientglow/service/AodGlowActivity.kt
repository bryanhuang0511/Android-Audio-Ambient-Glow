package com.example.audioambientglow.service

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.media.session.PlaybackState
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.audioambientglow.audio.AudioFeatures
import com.example.audioambientglow.audio.AudioVisualizerManager
import com.example.audioambientglow.data.GlowPreferencesRepository
import com.example.audioambientglow.util.CrashHandler
import com.example.audioambientglow.util.LunarCalendarUtil
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

class AodGlowActivity : ComponentActivity() {

    private val tag = "AodGlowActivity"
    private lateinit var rootContainer: FrameLayout
    private lateinit var contentLayer: FrameLayout
    private lateinit var glowTrackView: GlowTrackView
    private lateinit var prefsRepo: GlowPreferencesRepository
    private lateinit var audioManager: AudioVisualizerManager

    // UI Elements
    private var tvDateLunar: TextView? = null
    private var tvClockHourMin: TextView? = null
    private var tvBattery: TextView? = null

    // vivo OriginOS Style Music Card Elements
    private var musicCard: LinearLayout? = null
    private var tvMusicTitle: TextView? = null
    private var tvMusicArtist: TextView? = null
    private var tvTimeElapsed: TextView? = null
    private var tvTimeDuration: TextView? = null
    private var musicSeekBar: SeekBar? = null
    private var btnShuffle: TextView? = null
    private var btnPrev: TextView? = null
    private var btnPlayPause: TextView? = null
    private var btnNext: TextView? = null
    private var btnRepeat: TextView? = null
    private var musicEqBars: Array<View> = emptyArray()

    private var currentBatteryText = "🔋 80%"
    private var lastTrackInfo: MediaTrackInfo = MediaTrackInfo()
    private var isUserSeeking = false

    private val handler = Handler(Looper.getMainLooper())
    private var velocityTracker: VelocityTracker? = null
    private var touchDownY = 0f
    private var isDragging = false
    private var touchSlop = 0

    // Clock minute boundary updater
    private val clockRunnable = object : Runnable {
        override fun run() {
            updateTimeAndDate()
            val now = System.currentTimeMillis()
            val delayToNextMinute = 60000L - (now % 60000L)
            handler.postDelayed(this, delayToNextMinute.coerceAtLeast(1000L))
        }
    }

    // Music progress bar updater (500ms when playing)
    private val progressRunnable = object : Runnable {
        override fun run() {
            updateProgressTime()
            if (lastTrackInfo.isPlaying) {
                handler.postDelayed(this, 500L)
            }
        }
    }

    // Battery status receiver
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                                 status == BatteryManager.BATTERY_STATUS_FULL

                if (level >= 0 && scale > 0) {
                    val batteryPct = (level * 100) / scale
                    val chargeIcon = if (isCharging) "⚡ " else "🔋 "
                    currentBatteryText = "$chargeIcon$batteryPct%"
                    tvBattery?.text = currentBatteryText
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            touchSlop = ViewConfiguration.get(this).scaledTouchSlop
            prefsRepo = GlowPreferencesRepository.getInstance(this)
            audioManager = AudioVisualizerManager.getInstance(this)

            setupAodWindow()
            buildAodLayout()
            setContentView(rootContainer)

            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    dismissWithAnimation()
                }
            })

            observeState()
            registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            updateTimeAndDate()
            handler.post(clockRunnable)
        } catch (e: Throwable) {
            CrashHandler.recordException(tag, "Failed to initialize AodGlowActivity", e)
            finish()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        buildAodLayout()
        updateTimeAndDate()
        tvBattery?.text = currentBatteryText
        updateMusicState(lastTrackInfo)
        glowTrackView.updateConfig(prefsRepo.getConfig())
    }

    override fun onStart() {
        super.onStart()
        AodStateManager.setAodActive(true)
        val config = prefsRepo.getConfig()
        audioManager.start(config.audioSourceType)
        handler.removeCallbacks(progressRunnable)
        handler.post(progressRunnable)
    }

    override fun onResume() {
        super.onResume()
        AodStateManager.setAodActive(true)
        val config = prefsRepo.getConfig()
        audioManager.start(config.audioSourceType)
        updateTimeAndDate()
        handler.removeCallbacks(progressRunnable)
        handler.post(progressRunnable)
    }

    override fun onPause() {
        super.onPause()
        AodStateManager.setAodActive(false)
        handler.removeCallbacks(progressRunnable)
    }

    override fun onStop() {
        super.onStop()
        AodStateManager.setAodActive(false)
        handler.removeCallbacks(progressRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(clockRunnable)
        handler.removeCallbacks(progressRunnable)
        try {
            unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            // ignore
        }
        velocityTracker?.recycle()
        velocityTracker = null
    }

    private fun setupAodWindow() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(false)
            } else {
                @Suppress("DEPRECATION")
                window.addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                )
            }

            window.addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            )

            val lp = window.attributes
            // Dimmer AOD screen brightness for eye comfort and zero-power OLED dark mode
            lp.screenBrightness = 0.08f
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            window.attributes = lp

            WindowCompat.setDecorFitsSystemWindows(window, false)
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } catch (e: Throwable) {
            CrashHandler.recordException(tag, "Failed in setupAodWindow", e)
        }
    }

    private fun getRoundedClockTypeface(): Typeface {
        return try {
            Typeface.create("sans-serif-rounded", Typeface.BOLD)
        } catch (e: Exception) {
            Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
    }

    private fun buildAodLayout() {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val density = resources.displayMetrics.density

        if (!::rootContainer.isInitialized) {
            rootContainer = FrameLayout(this).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(Color.BLACK)
            }
        } else {
            rootContainer.removeAllViews()
        }

        // 1. Fixed 144Hz Full-Screen Glow Track (Stays pinned to screen border)
        glowTrackView = GlowTrackView(this).apply {
            setAodFullscreen(true)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        rootContainer.addView(glowTrackView)

        // 2. Draggable Center Content Layer (Translates Upwards on Swipe)
        contentLayer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        if (isLandscape) {
            buildLandscapeContent(contentLayer, density)
        } else {
            buildPortraitContent(contentLayer, density)
        }

        rootContainer.addView(contentLayer)
    }

    // ==========================================
    // PORTRAIT LAYOUT (直向模式)
    // ==========================================
    private fun buildPortraitContent(parent: FrameLayout, density: Float) {
        val centerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ).apply {
                topMargin = (64 * density).toInt()
                bottomMargin = (32 * density).toInt()
                leftMargin = (20 * density).toInt()
                rightMargin = (20 * density).toInt()
            }
        }

        // Top Status (Lock + Battery)
        val topStatusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (10 * density).toInt()
            }
        }

        val ivLock = TextView(this).apply {
            text = "🔒"
            textSize = 13f
            alpha = 0.70f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                rightMargin = (8 * density).toInt()
            }
        }

        tvBattery = TextView(this).apply {
            text = currentBatteryText
            textSize = 12f
            alpha = 0.78f
            setTextColor(Color.parseColor("#00E676"))
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        topStatusRow.addView(ivLock)
        topStatusRow.addView(tvBattery)
        centerLayout.addView(topStatusRow)

        // Date + Lunar Row (格式：8月17日 星期一 · 歲次丙午年 七月初五)
        tvDateLunar = TextView(this).apply {
            text = "8月17日 星期一 · 歲次丙午年 七月初五"
            textSize = 14f
            alpha = 0.72f
            setTextColor(Color.parseColor("#8A99AD"))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (6 * density).toInt()
            }
        }
        centerLayout.addView(tvDateLunar)

        // Digital Clock (Rounded numerals, NO seconds, elegant pure HH:mm)
        tvClockHourMin = TextView(this).apply {
            text = "18:00"
            textSize = 76f
            alpha = 0.88f
            setTextColor(Color.parseColor("#E2E8F0"))
            typeface = getRoundedClockTypeface()
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (18 * density).toInt()
            }
        }
        centerLayout.addView(tvClockHourMin)

        // vivo Style Music Card (Title, Artist, Progress Bar, Full Controls)
        val mCard = createVivoStyleMusicCard(density, isLandscape = false)
        centerLayout.addView(mCard)

        // Spacer pushing unlock text to bottom
        val bottomSpacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.0f
            )
        }
        centerLayout.addView(bottomSpacer)

        // Clean Bottom Unlock Indicator (Only 向上解鎖)
        val tvUnlock = TextView(this).apply {
            text = "向上解鎖"
            textSize = 12f
            alpha = 0.55f
            setTextColor(Color.parseColor("#64748B"))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (40 * density).toInt()
            }
        }
        centerLayout.addView(tvUnlock)

        parent.addView(centerLayout)
    }

    // ==========================================
    // LANDSCAPE LAYOUT (橫向模式 - 音響桌面擺放專用)
    // ==========================================
    private fun buildLandscapeContent(parent: FrameLayout, density: Float) {
        val rootLinear = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ).apply {
                topMargin = (16 * density).toInt()
                bottomMargin = (12 * density).toInt()
                leftMargin = (32 * density).toInt()
                rightMargin = (32 * density).toInt()
            }
        }

        // Horizontal split: Left = Clock/Date, Right = Wide vivo Music Card
        val rowContent = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.0f
            )
        }

        // Left Column (Clock + Date + Battery)
        val leftCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                0.9f
            ).apply {
                leftMargin = (12 * density).toInt()
                rightMargin = (12 * density).toInt()
            }
        }

        val topStatusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (4 * density).toInt()
            }
        }

        val ivLock = TextView(this).apply {
            text = "🔒"
            textSize = 12f
            alpha = 0.70f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                rightMargin = (6 * density).toInt()
            }
        }

        tvBattery = TextView(this).apply {
            text = currentBatteryText
            textSize = 11f
            alpha = 0.78f
            setTextColor(Color.parseColor("#00E676"))
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        topStatusRow.addView(ivLock)
        topStatusRow.addView(tvBattery)
        leftCol.addView(topStatusRow)

        tvClockHourMin = TextView(this).apply {
            text = "18:00"
            textSize = 62f
            alpha = 0.88f
            setTextColor(Color.parseColor("#E2E8F0"))
            typeface = getRoundedClockTypeface()
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (4 * density).toInt()
            }
        }
        leftCol.addView(tvClockHourMin)

        tvDateLunar = TextView(this).apply {
            text = "8月17日 星期一 · 歲次丙午年 七月初五"
            textSize = 12.5f
            alpha = 0.72f
            setTextColor(Color.parseColor("#8A99AD"))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        leftCol.addView(tvDateLunar)
        rowContent.addView(leftCol)

        // Right Column (Expanded vivo Music Player Card)
        val rightCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.1f
            )
        }
        val mCard = createVivoStyleMusicCard(density, isLandscape = true)
        rightCol.addView(mCard)
        rowContent.addView(rightCol)

        rootLinear.addView(rowContent)

        // Bottom Clean Unlock Indicator (Only 向上解鎖)
        val tvUnlock = TextView(this).apply {
            text = "向上解鎖"
            textSize = 11f
            alpha = 0.50f
            setTextColor(Color.parseColor("#64748B"))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (4 * density).toInt()
            }
        }
        rootLinear.addView(tvUnlock)

        parent.addView(rootLinear)
    }

    /**
     * vivo OriginOS Style Music Card (Title, Artist/Album, Progress Bar, Shuffle/Prev/Play/Next/Repeat)
     */
    private fun createVivoStyleMusicCard(density: Float, isLandscape: Boolean): LinearLayout {
        musicCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 20 * density
                setColor(Color.parseColor("#0B0C10"))
                setStroke((1 * density).toInt(), Color.parseColor("#1A1D26"))
            }
            background = bg
            setPadding((16 * density).toInt(), (12 * density).toInt(), (16 * density).toInt(), (12 * density).toInt())
            layoutParams = LinearLayout.LayoutParams(
                (if (isLandscape) 330 * density else 320 * density).toInt(),
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                if (!isLandscape) {
                    bottomMargin = (16 * density).toInt()
                }
            }
        }

        // Row 1: Music note icon + Song title & Artist + Equalizer bars
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (10 * density).toInt()
            }
        }

        val musicIcon = TextView(this).apply {
            text = "🎧"
            textSize = 16f
            alpha = 0.85f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                rightMargin = (10 * density).toInt()
            }
        }

        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.0f
            )
        }

        tvMusicTitle = TextView(this).apply {
            text = "手機音效氣氛燈"
            textSize = 14f
            alpha = 0.88f
            setTextColor(Color.parseColor("#E2E8F0"))
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

        tvMusicArtist = TextView(this).apply {
            text = "音樂律動氣氛燈"
            textSize = 11.5f
            alpha = 0.65f
            setTextColor(Color.parseColor("#717E8E"))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        textCol.addView(tvMusicTitle)
        textCol.addView(tvMusicArtist)

        // Equalizer bouncing bars
        val eqRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                (14 * density).toInt()
            ).apply {
                leftMargin = (8 * density).toInt()
            }
        }

        musicEqBars = Array(4) { idx ->
            View(this).apply {
                val barBg = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 2 * density
                    setColor(if (idx % 2 == 0) Color.parseColor("#00F5FF") else Color.parseColor("#FF007F"))
                }
                background = barBg
                alpha = 0.75f
                layoutParams = LinearLayout.LayoutParams(
                    (2.5f * density).toInt(),
                    (6 * density).toInt()
                ).apply {
                    leftMargin = (2 * density).toInt()
                }
            }.also { eqRow.addView(it) }
        }

        headerRow.addView(musicIcon)
        headerRow.addView(textCol)
        headerRow.addView(eqRow)
        musicCard?.addView(headerRow)

        // Row 2: Realtime Seek/Progress Bar (00:24 ----------- 02:24)
        val progressRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (10 * density).toInt()
            }
        }

        tvTimeElapsed = TextView(this).apply {
            text = "00:00"
            textSize = 10f
            alpha = 0.60f
            setTextColor(Color.parseColor("#8E8E93"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val trackProgressDrawable = LayerDrawable(arrayOf(
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 2 * density
                setColor(Color.parseColor("#1C1E26"))
                setSize(-1, (3 * density).toInt())
            },
            ClipDrawable(
                GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 2 * density
                    setColor(Color.parseColor("#00F5FF"))
                    setSize(-1, (3 * density).toInt())
                },
                Gravity.START,
                ClipDrawable.HORIZONTAL
            )
        )).apply {
            setId(0, android.R.id.background)
            setId(1, android.R.id.progress)
        }

        val thumbDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setSize((8 * density).toInt(), (8 * density).toInt())
            setColor(Color.parseColor("#E2E8F0"))
        }

        musicSeekBar = SeekBar(this).apply {
            max = 1000
            progress = 0
            progressDrawable = trackProgressDrawable
            thumb = thumbDrawable
            splitTrack = false
            setPadding((6 * density).toInt(), 0, (6 * density).toInt(), 0)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.0f
            ).apply {
                leftMargin = (6 * density).toInt()
                rightMargin = (6 * density).toInt()
            }

            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                var seekTargetMs = 0L

                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val dur = lastTrackInfo.durationMs
                        if (dur > 0) {
                            seekTargetMs = (progress.toLong() * dur) / 1000L
                            tvTimeElapsed?.text = formatTime(seekTargetMs)
                        }
                    }
                }

                override fun onStartTrackingTouch(sb: SeekBar?) {
                    isUserSeeking = true
                }

                override fun onStopTrackingTouch(sb: SeekBar?) {
                    isUserSeeking = false
                    if (lastTrackInfo.durationMs > 0) {
                        MediaPlaybackDetector.seekTo(seekTargetMs)
                    }
                }
            })
        }

        tvTimeDuration = TextView(this).apply {
            text = "00:00"
            textSize = 10f
            alpha = 0.60f
            setTextColor(Color.parseColor("#8E8E93"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        progressRow.addView(tvTimeElapsed)
        progressRow.addView(musicSeekBar)
        progressRow.addView(tvTimeDuration)
        musicCard?.addView(progressRow)

        // Row 3: Control Buttons (🔀, ⏮, ▶/⏸, ⏭, 🔁)
        val controlRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        fun createControlBtn(iconText: String, isMain: Boolean = false, isToggle: Boolean = false): TextView {
            return TextView(this).apply {
                text = iconText
                textSize = if (isMain) 16f else (if (isToggle) 14f else 13f)
                gravity = Gravity.CENTER
                setTextColor(if (isMain) Color.parseColor("#00F5FF") else Color.parseColor("#A0AEC0"))
                alpha = if (isMain) 0.90f else 0.65f
                isClickable = true
                isFocusable = true

                val btnBg = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (isMain) Color.parseColor("#151822") else Color.parseColor("#0F1117"))
                    setStroke((1 * density).toInt(), if (isMain) Color.parseColor("#252A38") else Color.parseColor("#161820"))
                }
                val ripple = RippleDrawable(
                    ColorStateList.valueOf(Color.parseColor("#3300F5FF")),
                    btnBg,
                    null
                )
                background = ripple

                val sizeDp = if (isMain) 36 * density else 28 * density
                layoutParams = LinearLayout.LayoutParams(sizeDp.toInt(), sizeDp.toInt()).apply {
                    val marginDp = if (isLandscape) 14 * density else 9 * density
                    leftMargin = marginDp.toInt()
                    rightMargin = marginDp.toInt()
                }
            }
        }

        btnShuffle = createControlBtn("🔀", isToggle = true).apply {
            setOnClickListener {
                MediaPlaybackDetector.toggleShuffle(this@AodGlowActivity)
            }
        }

        btnPrev = createControlBtn("⏮").apply {
            setOnClickListener {
                MediaPlaybackDetector.skipToPrevious(this@AodGlowActivity)
            }
        }

        btnPlayPause = createControlBtn("▶", isMain = true).apply {
            setOnClickListener {
                MediaPlaybackDetector.togglePlayPause(this@AodGlowActivity)
            }
        }

        btnNext = createControlBtn("⏭").apply {
            setOnClickListener {
                MediaPlaybackDetector.skipToNext(this@AodGlowActivity)
            }
        }

        btnRepeat = createControlBtn("🔁", isToggle = true).apply {
            setOnClickListener {
                MediaPlaybackDetector.toggleRepeat(this@AodGlowActivity)
            }
        }

        controlRow.addView(btnShuffle)
        controlRow.addView(btnPrev)
        controlRow.addView(btnPlayPause)
        controlRow.addView(btnNext)
        controlRow.addView(btnRepeat)
        musicCard?.addView(controlRow)

        return musicCard!!
    }

    private fun updateTimeAndDate() {
        val cal = Calendar.getInstance()

        // Time (No seconds, elegant rounded HH:mm)
        val hourMinFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        tvClockHourMin?.text = hourMinFormat.format(cal.time)

        // Gregorian Date + Weekday
        val monthDayFormat = SimpleDateFormat("M'月'd'日' E", Locale.TRADITIONAL_CHINESE)
        val gregorianStr = monthDayFormat.format(cal.time)

        // Chinese Lunar Calendar (格式：歲次丙午年 七月初五)
        val lunar = LunarCalendarUtil.getLunarDate(cal)
        tvDateLunar?.text = "$gregorianStr · ${lunar.formatted}"
    }

    private fun updateMusicState(track: MediaTrackInfo) {
        lastTrackInfo = track
        if (track.isPlaying && track.title.isNotEmpty()) {
            tvMusicTitle?.text = track.title
            val subtitle = if (track.album.isNotEmpty()) "${track.artist} · ${track.album}" else track.artist.ifEmpty { "音樂播放中" }
            tvMusicArtist?.text = subtitle
            btnPlayPause?.text = "⏸"
            musicCard?.visibility = View.VISIBLE

            handler.removeCallbacks(progressRunnable)
            handler.post(progressRunnable)
        } else {
            tvMusicTitle?.text = if (track.title.isNotEmpty()) track.title else "手機音效氣氛燈"
            val subtitle = if (track.album.isNotEmpty()) "${track.artist} · ${track.album}" else if (track.artist.isNotEmpty()) track.artist else "音樂律動氣氛燈"
            tvMusicArtist?.text = subtitle
            btnPlayPause?.text = "▶"
        }

        // Update Shuffle Toggle State
        val isShuffleOn = track.shuffleMode == MediaPlaybackDetector.SHUFFLE_ALL
        btnShuffle?.setTextColor(if (isShuffleOn) Color.parseColor("#00F5FF") else Color.parseColor("#A0AEC0"))
        btnShuffle?.alpha = if (isShuffleOn) 0.95f else 0.50f

        // Update Repeat Toggle State
        when (track.repeatMode) {
            MediaPlaybackDetector.REPEAT_ONE -> {
                btnRepeat?.text = "🔂"
                btnRepeat?.setTextColor(Color.parseColor("#00F5FF"))
                btnRepeat?.alpha = 0.95f
            }
            MediaPlaybackDetector.REPEAT_ALL -> {
                btnRepeat?.text = "🔁"
                btnRepeat?.setTextColor(Color.parseColor("#00F5FF"))
                btnRepeat?.alpha = 0.95f
            }
            else -> {
                btnRepeat?.text = "🔁"
                btnRepeat?.setTextColor(Color.parseColor("#A0AEC0"))
                btnRepeat?.alpha = 0.50f
            }
        }

        updateProgressTime()
    }

    private fun updateProgressTime() {
        val dur = lastTrackInfo.durationMs
        val cur = lastTrackInfo.getCurrentPositionMs()

        if (dur > 0) {
            tvTimeDuration?.text = formatTime(dur)
            if (!isUserSeeking) {
                tvTimeElapsed?.text = formatTime(cur)
                val progress = ((cur * 1000L) / dur).toInt().coerceIn(0, 1000)
                musicSeekBar?.progress = progress
            }
        } else {
            tvTimeDuration?.text = "--:--"
            if (!isUserSeeking) {
                tvTimeElapsed?.text = formatTime(cur)
                musicSeekBar?.progress = 0
            }
        }
    }

    private fun formatTime(ms: Long): String {
        if (ms <= 0) return "00:00"
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return String.format(Locale.getDefault(), "%02d:%02d", min, sec)
    }

    private fun updateEqualizerHeights(features: AudioFeatures) {
        val density = resources.displayMetrics.density
        val bands = features.spectrumBands
        for (i in musicEqBars.indices) {
            val energy = when (i) {
                0 -> features.bassEnergy
                1 -> if (bands.isNotEmpty()) bands[3] else features.midEnergy
                2 -> if (bands.isNotEmpty()) bands[10] else features.midEnergy
                else -> features.trebleEnergy
            }
            val heightDp = (4 + energy * 10).coerceIn(3f, 14f)
            val lp = musicEqBars[i].layoutParams
            lp.height = (heightDp * density).toInt()
            musicEqBars[i].layoutParams = lp
        }
    }

    /**
     * Touch Handling: Pure Swipe UP Only.
     * Buttons and Seekbar can be interacted with directly. When dragged upward, AOD translates and unlocks.
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain()
        }
        velocityTracker?.addMovement(ev)

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownY = ev.rawY
                isDragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = ev.rawY - touchDownY
                if (!isDragging) {
                    if (abs(dy) > touchSlop) {
                        isDragging = true
                    }
                }

                if (isDragging) {
                    val clampedDy = if (dy < 0) dy else dy * 0.15f
                    contentLayer.translationY = clampedDy

                    val screenH = resources.displayMetrics.heightPixels.toFloat()
                    val progress = (abs(clampedDy) / (screenH * 0.32f)).coerceIn(0f, 1f)

                    glowTrackView.alpha = (1.0f - progress * 1.8f).coerceIn(0f, 1f)
                    contentLayer.alpha = (1.0f - progress * 0.75f).coerceIn(0.2f, 1.0f)
                    rootContainer.alpha = (1.0f - progress * 0.40f).coerceIn(0.4f, 1.0f)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    velocityTracker?.computeCurrentVelocity(1000)
                    val vy = velocityTracker?.yVelocity ?: 0f
                    val dy = ev.rawY - touchDownY
                    val screenH = resources.displayMetrics.heightPixels.toFloat()

                    val isSwipeUpDismiss = (dy < -screenH * 0.14f) || (vy < -650f)
                    if (isSwipeUpDismiss) {
                        dismissWithAnimation()
                    } else {
                        resetContentPosition()
                    }
                    isDragging = false
                    return true
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun dismissWithAnimation() {
        val screenH = resources.displayMetrics.heightPixels.toFloat()
        val targetY = -screenH * 0.95f

        glowTrackView.animate().alpha(0f).setDuration(160).start()
        rootContainer.animate().alpha(0f).setDuration(220).start()

        contentLayer.animate()
            .translationY(targetY)
            .alpha(0f)
            .setDuration(220)
            .setInterpolator(DecelerateInterpolator())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    finish()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, android.R.anim.fade_out)
                    } else {
                        @Suppress("DEPRECATION")
                        overridePendingTransition(0, android.R.anim.fade_out)
                    }
                }
            })
            .start()
    }

    private fun resetContentPosition() {
        glowTrackView.animate().alpha(1.0f).setDuration(250).start()
        rootContainer.animate().alpha(1.0f).setDuration(250).start()
        contentLayer.animate()
            .translationY(0f)
            .alpha(1.0f)
            .setDuration(250)
            .setInterpolator(OvershootInterpolator(1.2f))
            .setListener(null)
            .start()
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    prefsRepo.configFlow.collectLatest { config ->
                        glowTrackView.updateConfig(config)
                    }
                }

                launch {
                    audioManager.audioFeatures.collectLatest { features ->
                        glowTrackView.updateAudio(features)
                        updateEqualizerHeights(features)
                    }
                }

                launch {
                    MediaPlaybackDetector.playbackStateFlow.collectLatest { track ->
                        updateMusicState(track)
                    }
                }
            }
        }
    }
}
