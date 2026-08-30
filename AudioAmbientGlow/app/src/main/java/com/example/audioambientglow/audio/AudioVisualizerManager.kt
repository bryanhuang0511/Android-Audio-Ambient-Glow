package com.example.audioambientglow.audio

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.audiofx.Visualizer
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.util.Log
import com.example.audioambientglow.data.AudioSourceType
import com.example.audioambientglow.service.MediaPlaybackDetector
import com.example.audioambientglow.util.CrashHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

class AudioVisualizerManager private constructor(private val context: Context) {

    private val tag = "AudioVisualizerManager"

    private val _audioFeatures = MutableStateFlow(AudioFeatures())
    val audioFeatures: StateFlow<AudioFeatures> = _audioFeatures.asStateFlow()

    private val _isAudioActive = MutableStateFlow(false)
    val isAudioActive: StateFlow<Boolean> = _isAudioActive.asStateFlow()

    private var hardwareVisualizer: Visualizer? = null
    private var audioRecord: AudioRecord? = null
    private var mediaProjection: MediaProjection? = null
    private var captureJob: Job? = null
    private var hardwarePollJob: Job? = null

    private val scope = CoroutineScope(Dispatchers.Default)
    private val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val fftEngine = FastFourierTransform(512)

    private var isRunning = false
    private val smoothBands = FloatArray(32)
    private var smoothRms = 0f
    private var smoothBass = 0f
    private var smoothMid = 0f
    private var smoothTreble = 0f
    private var runningAgcPeak = 0.05f

    init {
        MediaPlaybackDetector.init(context)
    }

    fun start(sourceType: AudioSourceType = AudioSourceType.SYSTEM_MEDIA_PLAYBACK) {
        if (isRunning) return
        isRunning = true
        Log.i(tag, "Starting AudioVisualizerManager...")

        // Primary: Hardware Visualizer(0)
        val fxStarted = startHardwareAudioFxVisualizer()
        if (!fxStarted) {
            if (mediaProjection != null) {
                startInternalAudioPlaybackCapture()
            }
        }
    }

    fun stop() {
        isRunning = false
        stopHardwareVisualizer()
        stopInternalCapture()
        smoothRms = 0f
        smoothBass = 0f
        smoothMid = 0f
        smoothTreble = 0f
        for (i in 0 until 32) smoothBands[i] = 0f
        _audioFeatures.value = AudioFeatures()
        _isAudioActive.value = false
    }

    /**
     * Primary Hardware AudioFX Visualizer (System Session 0 - Mix Output)
     */
    private fun startHardwareAudioFxVisualizer(): Boolean {
        stopHardwareVisualizer()
        try {
            val visualizer = Visualizer(0)
            val captureSizeRange = Visualizer.getCaptureSizeRange()
            val captureSize = 512.coerceIn(captureSizeRange[0], captureSizeRange[1])
            visualizer.captureSize = captureSize
            visualizer.scalingMode = Visualizer.SCALING_MODE_NORMALIZED
            visualizer.enabled = true
            hardwareVisualizer = visualizer

            val waveBuffer = ByteArray(captureSize)
            val fftBuffer = ByteArray(captureSize)

            hardwarePollJob?.cancel()
            hardwarePollJob = scope.launch {
                while (isActive && isRunning) {
                    if (isInPhoneCall()) {
                        resetToSilence()
                        delay(200)
                        continue
                    }
                    val v = hardwareVisualizer ?: break
                    if (!v.enabled) break

                    var frameRms = 0f
                    val waveRes = v.getWaveForm(waveBuffer)
                    if (waveRes == Visualizer.SUCCESS) {
                        var sum = 0.0
                        for (b in waveBuffer) {
                            val sample = (b.toInt() and 0xFF) - 128
                            sum += (sample * sample)
                        }
                        frameRms = (sqrt(sum / waveBuffer.size) / 128.0).toFloat().coerceIn(0f, 1f)
                    }

                    // Strict silence detection: if music is paused or silent, immediately fade to zero
                    if (frameRms < 0.012f) {
                        resetToSilence()
                        delay(25)
                        continue
                    }

                    val fftRes = v.getFft(fftBuffer)
                    if (fftRes == Visualizer.SUCCESS) {
                        processHardwareFftBuffer(fftBuffer, frameRms)
                    }
                    delay(16) // 60Hz polling
                }
            }

            _isAudioActive.value = true
            Log.i(tag, "Hardware AudioFX Visualizer(0) connected successfully!")
            return true
        } catch (e: Throwable) {
            Log.w(tag, "Hardware Visualizer(0) not accessible: ${e.message}")
            return false
        }
    }

    private fun stopHardwareVisualizer() {
        hardwarePollJob?.cancel()
        hardwarePollJob = null
        try {
            hardwareVisualizer?.enabled = false
            hardwareVisualizer?.release()
        } catch (e: Exception) {
            Log.e(tag, "Error releasing hardware visualizer", e)
        } finally {
            hardwareVisualizer = null
        }
    }

    /**
     * Process Hardware FFT Buffer with proper AGC and silence gating (No clipping, No artificial loops)
     */
    private var hwBassPeak = 0.08f
    private var hwMidPeak = 0.02f
    private var hwTreblePeak = 0.01f
    private var hwBassBaseline = 0f

    private fun processHardwareFftBuffer(fft: ByteArray, frameRms: Float) {
        val n = fft.size
        val numBins = n / 2
        val maxFftBin = (numBins * (2000f / 22050f)).toInt().coerceIn(16, numBins - 1)

        val binMags = FloatArray(maxFftBin + 1)
        var bassSum = 0f
        var bassCount = 0
        var midSum = 0f
        var midCount = 0
        var trebleSum = 0f
        var trebleCount = 0

        val bassEndBin = (maxFftBin * (250f / 2000f)).toInt().coerceAtLeast(2)
        val midEndBin = (maxFftBin * (1000f / 2000f)).toInt().coerceAtLeast(bassEndBin + 2)

        for (k in 1..maxFftBin) {
            val r = fft[2 * k].toFloat()
            val i = fft[2 * k + 1].toFloat()
            val mag = hypot(r, i) / 128f

            // Dynamic range dB mapping: [-40dB, -6dB] -> [0.0, 1.0]
            val magDb = (20.0 * kotlin.math.log10(max(mag.toDouble(), 0.0001))).toFloat()
            val normMag = ((magDb + 40f) / 34f).coerceIn(0f, 1f)
            binMags[k] = normMag

            if (k <= bassEndBin) { // 50Hz - 250Hz (Bass)
                bassSum += normMag
                bassCount++
            } else if (k <= midEndBin) { // 250Hz - 1000Hz (Vocal / Mid)
                midSum += normMag
                midCount++
            } else { // 1000Hz - 2000Hz (High-Mid / Harmonics)
                trebleSum += normMag
                trebleCount++
            }
        }

        val rawBass = if (bassCount > 0) bassSum / bassCount else 0f
        val rawMid = if (midCount > 0) midSum / midCount else 0f
        val rawTreble = if (trebleCount > 0) trebleSum / trebleCount else 0f

        val bassTransient = (rawBass - hwBassBaseline).coerceAtLeast(0f)
        hwBassBaseline += (rawBass - hwBassBaseline) * 0.18f
        val rhythmicBass = (hwBassBaseline * 0.30f + bassTransient * 1.70f).coerceIn(0f, 1f)

        // Interpolate 50Hz-2000Hz into 32 bands
        val rawBands = FloatArray(32)
        for (b in 0 until 32) {
            val binPos = 1.0f + (b.toFloat() / 31.0f) * (maxFftBin - 1)
            val lowerBin = binPos.toInt().coerceIn(1, maxFftBin)
            val upperBin = (lowerBin + 1).coerceIn(1, maxFftBin)
            val fraction = binPos - lowerBin
            rawBands[b] = binMags[lowerBin] * (1f - fraction) + binMags[upperBin] * fraction
        }

        // Ballistics smoothing
        smoothRms += (frameRms - smoothRms) * (if (frameRms > smoothRms) 0.65f else 0.25f)
        smoothBass += (rhythmicBass - smoothBass) * (if (rhythmicBass > smoothBass) 0.70f else 0.28f)
        smoothMid += (rawMid - smoothMid) * (if (rawMid > smoothMid) 0.60f else 0.22f)
        smoothTreble += (rawTreble - smoothTreble) * (if (rawTreble > smoothTreble) 0.55f else 0.20f)

        for (b in 0 until 32) {
            val target = rawBands[b]
            smoothBands[b] += (target - smoothBands[b]) * (if (target > smoothBands[b]) 0.65f else 0.22f)
        }

        _audioFeatures.value = AudioFeatures(
            rawRms = smoothRms.coerceIn(0f, 1f),
            bassEnergy = smoothBass.coerceIn(0f, 1f),
            midEnergy = smoothMid.coerceIn(0f, 1f),
            trebleEnergy = smoothTreble.coerceIn(0f, 1f),
            spectrumBands = smoothBands.clone()
        )
    }

    private fun resetToSilence() {
        smoothRms = (smoothRms * 0.7f).coerceAtLeast(0f)
        smoothBass = (smoothBass * 0.7f).coerceAtLeast(0f)
        smoothMid = (smoothMid * 0.7f).coerceAtLeast(0f)
        smoothTreble = (smoothTreble * 0.7f).coerceAtLeast(0f)
        for (i in 0 until 32) {
            smoothBands[i] = (smoothBands[i] * 0.7f).coerceAtLeast(0f)
        }
        _audioFeatures.value = AudioFeatures(
            rawRms = if (smoothRms < 0.005f) 0f else smoothRms,
            bassEnergy = if (smoothBass < 0.005f) 0f else smoothBass,
            midEnergy = if (smoothMid < 0.005f) 0f else smoothMid,
            trebleEnergy = if (smoothTreble < 0.005f) 0f else smoothTreble,
            spectrumBands = smoothBands.clone()
        )
    }

    fun setMediaProjectionData(resultCode: Int, data: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val mpManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
            try {
                mediaProjection?.stop()
                mediaProjection = mpManager?.getMediaProjection(resultCode, data)
                mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() {
                        super.onStop()
                        _isAudioActive.value = false
                        stopInternalCapture()
                    }
                }, null)

                Log.i(tag, "MediaProjection initialized. Starting internal AudioPlaybackCapture...")
                if (isRunning) {
                    startInternalAudioPlaybackCapture()
                }
            } catch (e: Exception) {
                CrashHandler.recordException(tag, "Failed to get MediaProjection", e)
            }
        }
    }

    private fun isInPhoneCall(): Boolean {
        val mode = audioManager?.mode ?: return false
        return mode == AudioManager.MODE_IN_CALL ||
               mode == AudioManager.MODE_IN_COMMUNICATION ||
               mode == AudioManager.MODE_RINGTONE
    }

    @SuppressLint("MissingPermission")
    private fun startInternalAudioPlaybackCapture() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val proj = mediaProjection ?: return

        stopInternalCapture()

        captureJob = scope.launch(Dispatchers.IO) {
            val sampleRate = 44100
            val minBufSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = max(minBufSize, 2048)

            try {
                val captureConfig = AudioPlaybackCaptureConfiguration.Builder(proj)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    .excludeUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .excludeUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .excludeUsage(AudioAttributes.USAGE_ALARM)
                    .build()

                val audioFormat = AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build()

                audioRecord = AudioRecord.Builder()
                    .setAudioPlaybackCaptureConfig(captureConfig)
                    .setAudioFormat(audioFormat)
                    .setBufferSizeInBytes(bufferSize)
                    .build()

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    Log.w(tag, "AudioRecord for internal capture failed init: ${audioRecord?.state}")
                    return@launch
                }

                audioRecord?.startRecording()
                _isAudioActive.value = true
                Log.i(tag, "AudioPlaybackCapture Loopback is active!")

                val pcmBuffer = ShortArray(512)
                while (isActive && isRunning) {
                    if (isInPhoneCall()) {
                        resetToSilence()
                        delay(200)
                        continue
                    }

                    val readSamples = audioRecord?.read(pcmBuffer, 0, 512) ?: -1
                    if (readSamples < 512) {
                        delay(5)
                        continue
                    }

                    // Process 100% Real 512-Point Cooley-Tukey FFT on PCM
                    val features = fftEngine.processPcmSamples(
                        pcm = pcmBuffer,
                        smoothBands = smoothBands,
                        smoothBass = smoothBass,
                        smoothMid = smoothMid,
                        smoothTreble = smoothTreble,
                        smoothRms = smoothRms
                    )
                    smoothRms = features.rawRms
                    smoothBass = features.bassEnergy
                    smoothMid = features.midEnergy
                    smoothTreble = features.trebleEnergy

                    _audioFeatures.value = features
                }
            } catch (e: Throwable) {
                CrashHandler.recordException(tag, "Internal capture loop error", e)
            } finally {
                stopInternalCapture()
            }
        }
    }

    private fun stopInternalCapture() {
        captureJob?.cancel()
        captureJob = null
        try {
            if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord?.stop()
            }
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(tag, "Error releasing audioRecord", e)
        } finally {
            audioRecord = null
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: AudioVisualizerManager? = null

        fun getInstance(context: Context): AudioVisualizerManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AudioVisualizerManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}

