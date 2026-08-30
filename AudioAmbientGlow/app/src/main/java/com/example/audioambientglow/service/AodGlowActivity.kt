package com.example.audioambientglow.service

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
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
import com.example.audioambientglow.lyrics.LyricsEngine
import com.example.audioambientglow.util.CrashHandler
import com.example.audioambientglow.util.LunarCalendarUtil
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

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

    // vivo OriginOS Style Pure Music Card
    private var musicCard: LinearLayout? = null
    private var tvMusicTitle: TextView? = null
    private var tvMusicArtist: TextView? = null
    private var tvTimeElapsed: TextView? = null
    private var tvTimeDuration: TextView? = null
    private var musicSeekBar: SeekBar? = null

    // 🎤 Dynamic Synced Lyrics Big Card (Pure White Active Line, Lower-Middle Spaced)
    private var lyricsCard: LinearLayout? = null
    private var tvLyricPrev: TextView? = null
    private var tvLyricActive: TextView? = null
    private var tvLyricNext: TextView? = null
    private var lastActiveLyric = ""
    private var lastObservedTitle = ""

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

    // Music progress & lyrics realtime updater (160ms for PC-like ultra smooth tracking)
    private val progressRunnable = object : Runnable {
        override fun run() {
            updateProgressTime()
            if (lastTrackInfo.isPlaying) {
                handler.postDelayed(this, 160L)
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

        // 1. Fixed Full-Screen Glow Track
        glowTrackView = GlowTrackView(this).apply {
            setAodFullscreen(true)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        rootContainer.addView(glowTrackView)

        // 2. Center Content Layer
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
                topMargin = (44 * density).toInt()
                bottomMargin = (20 * density).toInt()
                leftMargin = (18 * density).toInt()
                rightMargin = (18 * density).toInt()
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
                bottomMargin = (6 * density).toInt()
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

        // Date + Lunar Row
        tvDateLunar = TextView(this).apply {
            text = "8月31日 星期一 · 農曆七月十九"
            textSize = 13.5f
            alpha = 0.75f
            setTextColor(Color.parseColor("#8A99AD"))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (4 * density).toInt()
            }
        }
        centerLayout.addView(tvDateLunar)

        // Digital Clock (Rounded pure HH:mm)
        tvClockHourMin = TextView(this).apply {
            text = "18:00"
            textSize = 66f
            alpha = 0.88f
            setTextColor(Color.parseColor("#E2E8F0"))
            typeface = getRoundedClockTypeface()
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (10 * density).toInt()
            }
        }
        centerLayout.addView(tvClockHourMin)

        // 1. vivo Style Pure Music Info & Progress Card (Horizontal Title & Artist)
        val mCard = createVivoStyleMusicCard(density, isLandscape = false)
        centerLayout.addView(mCard)

        // 2. 🎤 Separate Big Dynamic Synced Lyrics Card (Spaced downward to lower-center zone)
        val lCard = createBigLyricsCard(density, isLandscape = false)
        centerLayout.addView(lCard)

        // Spacer
        val bottomSpacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.0f
            )
        }
        centerLayout.addView(bottomSpacer)

        // Bottom Clean Unlock Indicator
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
                bottomMargin = (26 * density).toInt()
            }
        }
        centerLayout.addView(tvUnlock)

        parent.addView(centerLayout)
    }

    // ==========================================
    // LANDSCAPE LAYOUT (橫向模式)
    // ==========================================
    private fun buildLandscapeContent(parent: FrameLayout, density: Float) {
        val rootLinear = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ).apply {
                topMargin = (12 * density).toInt()
                bottomMargin = (8 * density).toInt()
                leftMargin = (24 * density).toInt()
                rightMargin = (24 * density).toInt()
            }
        }

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
                0.85f
            ).apply {
                leftMargin = (8 * density).toInt()
                rightMargin = (8 * density).toInt()
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
            textSize = 54f
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
            text = "8月31日 星期一 · 農曆七月十九"
            textSize = 12f
            alpha = 0.72f
            setTextColor(Color.parseColor("#8A99AD"))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        leftCol.addView(tvDateLunar)
        rowContent.addView(leftCol)

        // Right Column (Music Card + Spaced Big Lyrics Card)
        val rightCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.15f
            )
        }
        val mCard = createVivoStyleMusicCard(density, isLandscape = true)
        val lCard = createBigLyricsCard(density, isLandscape = true)
        rightCol.addView(mCard)
        rightCol.addView(lCard)
        rowContent.addView(rightCol)

        rootLinear.addView(rowContent)

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
     * Card 1: Pure Music Info & Realtime Progress Bar
     * Horizontal Row: Left = Song Title (Bold), Right = Artist (Gray).
     */
    private fun createVivoStyleMusicCard(density: Float, isLandscape: Boolean): LinearLayout {
        musicCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 18 * density
                setColor(Color.parseColor("#0B0C10"))
                setStroke((1 * density).toInt(), Color.parseColor("#181A22"))
            }
            background = bg
            setPadding((16 * density).toInt(), (12 * density).toInt(), (16 * density).toInt(), (12 * density).toInt())
            layoutParams = LinearLayout.LayoutParams(
                (if (isLandscape) 360 * density else 330 * density).toInt(),
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Row 1: Horizontal Song Title (Left) + Artist (Right)
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (6 * density).toInt()
            }
        }

        tvMusicTitle = TextView(this).apply {
            text = "手機音效氣氛燈"
            textSize = 15.5f
            alpha = 0.95f
            setTextColor(Color.parseColor("#E2E8F0"))
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.0f
            )
        }

        tvMusicArtist = TextView(this).apply {
            text = "音樂律動氣氛燈"
            textSize = 12.5f
            alpha = 0.70f
            setTextColor(Color.parseColor("#8A99AD"))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = (10 * density).toInt()
            }
        }
        headerRow.addView(tvMusicTitle)
        headerRow.addView(tvMusicArtist)
        musicCard?.addView(headerRow)

        // Row 2: Realtime Progress Bar (00:24 ----------- 03:40)
        val progressRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        tvTimeElapsed = TextView(this).apply {
            text = "00:00"
            textSize = 10.5f
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
            textSize = 10.5f
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

        return musicCard!!
    }

    /**
     * Card 2: 🎤 Separate Big Dynamic Synced Lyrics Card
     * (Spaced downward to lower-center zone, pure white active text color)
     */
    private fun createBigLyricsCard(density: Float, isLandscape: Boolean): LinearLayout {
        lyricsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val lyricsBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 18 * density
                setColor(Color.parseColor("#060709"))
                setStroke((1 * density).toInt(), Color.parseColor("#151720"))
            }
            background = lyricsBg
            setPadding((18 * density).toInt(), (14 * density).toInt(), (18 * density).toInt(), (14 * density).toInt())
            layoutParams = LinearLayout.LayoutParams(
                (if (isLandscape) 360 * density else 330 * density).toInt(),
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (68 * density).toInt() // Spaced down to lower-middle zone!
            }
        }

        tvLyricPrev = TextView(this).apply {
            text = ""
            textSize = 14f
            alpha = 0.45f
            setTextColor(Color.parseColor("#717E8E"))
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (4 * density).toInt()
            }
        }

        tvLyricActive = TextView(this).apply {
            text = "🎵 正在載入歌詞..."
            textSize = 19.5f
            alpha = 0.98f
            setTextColor(Color.parseColor("#FFFFFF")) // Pure White!
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (4 * density).toInt()
                bottomMargin = (4 * density).toInt()
            }
        }

        tvLyricNext = TextView(this).apply {
            text = ""
            textSize = 14f
            alpha = 0.45f
            setTextColor(Color.parseColor("#717E8E"))
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (4 * density).toInt()
            }
        }

        lyricsCard?.addView(tvLyricPrev)
        lyricsCard?.addView(tvLyricActive)
        lyricsCard?.addView(tvLyricNext)

        return lyricsCard!!
    }

    private fun updateTimeAndDate() {
        val cal = Calendar.getInstance()

        // Time (No seconds, elegant rounded HH:mm)
        val hourMinFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        tvClockHourMin?.text = hourMinFormat.format(cal.time)

        // Gregorian Date + Weekday
        val monthDayFormat = SimpleDateFormat("M'月'd'日' E", Locale.TRADITIONAL_CHINESE)
        val gregorianStr = monthDayFormat.format(cal.time)

        // Chinese Lunar Calendar (格式：8月31日 星期一 · 農曆七月十九)
        val lunar = LunarCalendarUtil.getLunarDate(cal)
        tvDateLunar?.text = "$gregorianStr · ${lunar.formatted}"
    }

    private fun updateMusicState(track: MediaTrackInfo) {
        lastTrackInfo = track
        if (track.title.isNotEmpty()) {
            tvMusicTitle?.text = track.title
            val subtitle = track.artist.ifEmpty { "音樂播放中" }
            tvMusicArtist?.text = subtitle
            musicCard?.visibility = View.VISIBLE
            lyricsCard?.visibility = View.VISIBLE

            // Track Switch: Immediately flush & prefetch if track title changed
            if (track.title != lastObservedTitle) {
                lastObservedTitle = track.title
                lastActiveLyric = ""
                tvLyricPrev?.text = ""
                tvLyricActive?.text = "🎵 正在載入歌詞..."
                tvLyricNext?.text = ""
                tvLyricActive?.setTextColor(Color.parseColor("#FFFFFF"))
                LyricsEngine.fetchLyrics(track.title, track.artist)
            }

            if (track.isPlaying) {
                handler.removeCallbacks(progressRunnable)
                handler.post(progressRunnable)
            }
        } else {
            tvMusicTitle?.text = "手機音效氣氛燈"
            tvMusicArtist?.text = "音樂律動氣氛燈"
            lastObservedTitle = ""
            lastActiveLyric = ""
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

        // Update Dynamic Synced Lyrics
        updateLyrics(cur)
    }

    private fun updateLyrics(curMs: Long) {
        val lyricsState = LyricsEngine.lyricsState.value
        if (lyricsState.isLoading) {
            tvLyricPrev?.text = ""
            tvLyricActive?.text = "🎵 正在搜尋歌詞..."
            tvLyricNext?.text = ""
            tvLyricActive?.setTextColor(Color.parseColor("#A0AEC0"))
            lastActiveLyric = ""
        } else if (lyricsState.isFound && lyricsState.lines.isNotEmpty()) {
            val (prev, active, next) = LyricsEngine.getActiveLines(curMs)
            val currentText = if (active.isNotEmpty()) active else (if (lastTrackInfo.title.isNotEmpty()) lastTrackInfo.title else "🎵 音樂律動中")

            if (currentText != lastActiveLyric) {
                lastActiveLyric = currentText
                // Silky Smooth Text Transition Animation
                tvLyricActive?.animate()
                    ?.alpha(0f)
                    ?.translationY(-6f)
                    ?.setDuration(90)
                    ?.withEndAction {
                        tvLyricPrev?.text = prev
                        tvLyricActive?.text = currentText
                        tvLyricNext?.text = next
                        tvLyricActive?.translationY = 6f
                        tvLyricActive?.animate()
                            ?.alpha(1.0f)
                            ?.translationY(0f)
                            ?.setDuration(120)
                            ?.start()
                    }?.start()
            } else {
                tvLyricPrev?.text = prev
                tvLyricNext?.text = next
            }
            tvLyricActive?.setTextColor(Color.parseColor("#FFFFFF")) // Pure White
        } else {
            tvLyricPrev?.text = ""
            tvLyricActive?.text = if (lastTrackInfo.title.isNotEmpty()) "🎵 ${lastTrackInfo.title}" else "🎵 純音樂律動中"
            tvLyricNext?.text = ""
            tvLyricActive?.setTextColor(Color.parseColor("#FFFFFF"))
            lastActiveLyric = ""
        }
    }

    private fun formatTime(ms: Long): String {
        if (ms <= 0) return "00:00"
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return String.format(Locale.getDefault(), "%02d:%02d", min, sec)
    }

    /**
     * Touch Handling: Pure Swipe UP Only.
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
                if (dy < -touchSlop && !isDragging) {
                    isDragging = true
                }
                if (isDragging && dy < 0) {
                    contentLayer.translationY = dy * 0.75f
                    val progress = (-dy / (resources.displayMetrics.heightPixels * 0.35f)).coerceIn(0f, 1f)
                    contentLayer.alpha = 1.0f - progress * 0.5f
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    val dy = ev.rawY - touchDownY
                    velocityTracker?.computeCurrentVelocity(1000)
                    val yVelocity = velocityTracker?.yVelocity ?: 0f

                    val screenHeight = resources.displayMetrics.heightPixels
                    val shouldDismiss = dy < -(screenHeight * 0.18f) || yVelocity < -1200f

                    if (shouldDismiss) {
                        dismissWithAnimation()
                    } else {
                        snapBack()
                    }
                    isDragging = false
                    return true
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun snapBack() {
        contentLayer.animate()
            .translationY(0f)
            .alpha(1.0f)
            .setDuration(240)
            .setInterpolator(OvershootInterpolator(1.2f))
            .start()
    }

    private fun dismissWithAnimation() {
        val screenHeight = resources.displayMetrics.heightPixels.toFloat()
        contentLayer.animate()
            .translationY(-screenHeight)
            .alpha(0f)
            .setDuration(220)
            .setInterpolator(DecelerateInterpolator())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    finish()
                    overridePendingTransition(0, 0)
                }
            })
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
                    }
                }
                launch {
                    MediaPlaybackDetector.playbackStateFlow.collectLatest { track ->
                        updateMusicState(track)
                    }
                }
                launch {
                    LyricsEngine.lyricsState.collectLatest {
                        updateProgressTime()
                    }
                }
            }
        }
    }
}