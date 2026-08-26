package com.example.audioambientglow

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.audioambientglow.audio.AudioFeatures
import com.example.audioambientglow.audio.AudioVisualizerManager
import com.example.audioambientglow.data.GlowPreferencesRepository
import com.example.audioambientglow.service.AodGlowActivity
import com.example.audioambientglow.service.GlowOverlayService
import com.example.audioambientglow.service.MediaPlaybackDetector
import com.example.audioambientglow.theme.AudioAmbientGlowTheme
import com.example.audioambientglow.ui.GlowDashboardScreen
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var prefsRepo: GlowPreferencesRepository
    private lateinit var audioManager: AudioVisualizerManager

    private var hasOverlayPermission by mutableStateOf(false)
    private var hasNotificationPermission by mutableStateOf(false)
    private var hasMediaListenerPermission by mutableStateOf(false)
    private var hasAudioPermission by mutableStateOf(false)

    private val liveAudioFeatures = MutableStateFlow(AudioFeatures())
    private var testBeatJob: Job? = null

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
    }

    private val requestAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasAudioPermission = isGranted
        if (isGranted) {
            audioManager.start()
            Toast.makeText(this, "✅ 系統硬體音效混音感知已啟用！", Toast.LENGTH_SHORT).show()
        }
    }

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val config = prefsRepo.getConfig()
            if (config.isEnabled && hasOverlayPermission) {
                GlowOverlayService.start(this)
            }
            audioManager.setMediaProjectionData(result.resultCode, result.data!!)
            Toast.makeText(this, "🎧 內部媒體音訊截取已啟用！(如同 PC Rainmeter)", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        prefsRepo = GlowPreferencesRepository.getInstance(this)
        audioManager = AudioVisualizerManager.getInstance(this)

        lifecycleScope.launch {
            audioManager.audioFeatures.collect { features ->
                liveAudioFeatures.value = features
            }
        }

        setContent {
            AudioAmbientGlowTheme {
                val config by prefsRepo.configFlow.collectAsState()
                val currentAudio by liveAudioFeatures.collectAsState()
                val currentMedia by MediaPlaybackDetector.playbackStateFlow.collectAsState()
                val isAudioActive by audioManager.isAudioActive.collectAsState()

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF07090E)
                ) {
                    GlowDashboardScreen(
                        config = config,
                        audioFeatures = currentAudio,
                        mediaTrackInfo = currentMedia,
                        hasOverlayPermission = hasOverlayPermission,
                        hasNotificationPermission = hasNotificationPermission,
                        hasMediaListenerPermission = hasMediaListenerPermission,
                        hasAudioPermission = hasAudioPermission,
                        isAudioActive = isAudioActive,
                        onRequestOverlayPermission = { requestOverlayPermission() },
                        onRequestNotificationPermission = { requestNotificationPermission() },
                        onRequestMediaListenerPermission = { requestMediaListenerPermission() },
                        onRequestAudioPermission = { requestAudioPermission() },
                        onRequestInternalAudioCapture = { requestInternalAudioCapture() },
                        onOpenAppSettings = { openAppSettings() },
                        onPinAodShortcut = { pinAodShortcut() },
                        onConfigChange = { update ->
                            prefsRepo.updateConfig(update)
                            checkAndRestartService()
                        },
                        onSimulateBeat = { simulateTestBassDrop() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStates()
        checkAndRestartService()
        val config = prefsRepo.getConfig()
        if (config.isEnabled) {
            audioManager.start(config.audioSourceType)
        }
    }

    override fun onPause() {
        super.onPause()
        testBeatJob?.cancel()
    }

    private fun updatePermissionStates() {
        hasOverlayPermission = Settings.canDrawOverlays(this)

        hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        hasAudioPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        hasMediaListenerPermission = isNotificationListenerEnabled()
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat?.contains(packageName) == true
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun requestAudioPermission() {
        requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun requestMediaListenerPermission() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        startActivity(intent)
    }

    private fun requestInternalAudioCapture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
            if (mpManager != null) {
                GlowOverlayService.start(this)
                mediaProjectionLauncher.launch(mpManager.createScreenCaptureIntent())
                return
            }
        }
    }

    private fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun pinAodShortcut() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val shortcutManager = getSystemService(ShortcutManager::class.java)
            if (shortcutManager != null && shortcutManager.isRequestPinShortcutSupported) {
                val pinShortcutInfo = ShortcutInfo.Builder(this, "shortcut_aod_mode")
                    .setIcon(Icon.createWithResource(this, R.drawable.ic_launcher_foreground))
                    .setShortLabel(getString(R.string.shortcut_aod_short))
                    .setLongLabel(getString(R.string.shortcut_aod_long))
                    .setIntent(
                        Intent(this, AodGlowActivity::class.java).apply {
                            action = Intent.ACTION_VIEW
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                    )
                    .build()
                shortcutManager.requestPinShortcut(pinShortcutInfo, null)
                Toast.makeText(this, "已發送新增「AMOLED 息屏」桌面捷徑請求，請在桌面確認！", Toast.LENGTH_LONG).show()
                return
            }
        }
        Toast.makeText(this, "提示：長按手機桌面空白處 > 新增小工具 > 選擇「手機音效氣氛燈」即可將 AOD 捷徑放上桌面！", Toast.LENGTH_LONG).show()
    }

    private fun checkAndRestartService() {
        val config = prefsRepo.getConfig()
        if (config.isEnabled && hasOverlayPermission) {
            GlowOverlayService.start(this)
        } else if (!config.isEnabled) {
            GlowOverlayService.stop(this)
        }
    }

    private fun simulateTestBassDrop() {
        testBeatJob?.cancel()
        testBeatJob = lifecycleScope.launch {
            MediaPlaybackDetector.setPlaying(true, "춤 (CHOOM)", "BABYMONSTER", "com.google.android.apps.youtube.music")
            for (kick in 1..6) {
                val bands = FloatArray(32) { idx ->
                    if (idx < 6) 0.98f else (0.5f / (idx - 4))
                }
                liveAudioFeatures.value = AudioFeatures(
                    rawRms = 0.95f,
                    bassEnergy = 0.98f,
                    midEnergy = 0.6f,
                    trebleEnergy = 0.4f,
                    spectrumBands = bands
                )
                delay(220)
                val lowBands = FloatArray(32) { 0.15f }
                liveAudioFeatures.value = AudioFeatures(
                    rawRms = 0.25f,
                    bassEnergy = 0.15f,
                    midEnergy = 0.2f,
                    trebleEnergy = 0.1f,
                    spectrumBands = lowBands
                )
                delay(180)
            }
            delay(500)
            liveAudioFeatures.value = AudioFeatures()
        }
    }
}
