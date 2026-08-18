package com.example.audioambientglow.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MediaTrackInfo(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val isPlaying: Boolean = false,
    val packageName: String = "",
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val lastUpdateTimeMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val shuffleMode: Int = MediaPlaybackDetector.SHUFFLE_NONE,
    val repeatMode: Int = MediaPlaybackDetector.REPEAT_NONE
) {
    fun getCurrentPositionMs(): Long {
        if (!isPlaying || lastUpdateTimeMs == 0L || playbackSpeed == 0f) return positionMs
        val elapsed = (SystemClock.elapsedRealtime() - lastUpdateTimeMs) * playbackSpeed
        val estimated = positionMs + elapsed.toLong()
        return if (durationMs > 0) estimated.coerceIn(0L, durationMs) else estimated.coerceAtLeast(0L)
    }
}

object MediaPlaybackDetector {

    private const val TAG = "MediaPlaybackDetector"

    const val SHUFFLE_NONE = 0
    const val SHUFFLE_ALL = 1

    const val REPEAT_NONE = 0
    const val REPEAT_ALL = 1
    const val REPEAT_ONE = 2

    private val _playbackStateFlow = MutableStateFlow(MediaTrackInfo())
    val playbackStateFlow: StateFlow<MediaTrackInfo> = _playbackStateFlow.asStateFlow()

    private var playbackCallback: AudioManager.AudioPlaybackCallback? = null
    private var isCallbackRegistered = false

    @Volatile
    var activeController: MediaController? = null

    fun init(context: Context) {
        if (isCallbackRegistered) return
        val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            playbackCallback = object : AudioManager.AudioPlaybackCallback() {
                override fun onPlaybackConfigChanged(configs: List<AudioPlaybackConfiguration>) {
                    var anyMusicPlaying = false
                    for (config in configs) {
                        val usage = config.audioAttributes.usage
                        val isMediaUsage = usage == AudioAttributes.USAGE_MEDIA ||
                                           usage == AudioAttributes.USAGE_GAME ||
                                           usage == AudioAttributes.USAGE_UNKNOWN

                        if (isMediaUsage) {
                            anyMusicPlaying = true
                            break
                        }
                    }

                    if (anyMusicPlaying) {
                        if (!_playbackStateFlow.value.isPlaying) {
                            Log.d(TAG, "AudioPlaybackCallback detected active media playback!")
                            val current = _playbackStateFlow.value
                            _playbackStateFlow.value = current.copy(
                                isPlaying = true,
                                title = if (current.title.isEmpty()) "正在播放音樂" else current.title
                            )
                        }
                    } else {
                        val isDirectActive = audioManager.isMusicActive
                        if (!isDirectActive && _playbackStateFlow.value.isPlaying) {
                            Log.d(TAG, "AudioPlaybackCallback detected media stopped.")
                            val current = _playbackStateFlow.value
                            _playbackStateFlow.value = current.copy(isPlaying = false)
                        }
                    }
                }
            }

            try {
                audioManager.registerAudioPlaybackCallback(playbackCallback!!, Handler(Looper.getMainLooper()))
                isCallbackRegistered = true
                Log.d(TAG, "AudioManager AudioPlaybackCallback registered successfully.")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to register AudioPlaybackCallback: ${e.message}")
            }
        }
    }

    fun updateFromMediaSession(
        packageName: String,
        state: PlaybackState?,
        metadata: MediaMetadata?,
        controller: MediaController? = null
    ) {
        if (controller != null) {
            activeController = controller
        }
        val isPlaying = state?.state == PlaybackState.STATE_PLAYING
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        val album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""
        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val position = state?.position ?: 0L
        val lastUpdate = state?.lastPositionUpdateTime ?: SystemClock.elapsedRealtime()
        val speed = state?.playbackSpeed ?: 1.0f

        var shuffle = _playbackStateFlow.value.shuffleMode
        var repeat = _playbackStateFlow.value.repeatMode

        if (controller != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val getShuffle = controller.javaClass.getMethod("getShuffleMode")
                val sMode = getShuffle.invoke(controller) as? Int
                if (sMode != null && sMode != 0) shuffle = SHUFFLE_ALL else if (sMode == 0) shuffle = SHUFFLE_NONE
            } catch (e: Exception) {
                // ignore
            }
            try {
                val getRepeat = controller.javaClass.getMethod("getRepeatMode")
                val rMode = getRepeat.invoke(controller) as? Int
                if (rMode != null) {
                    repeat = when (rMode) {
                        1 -> REPEAT_ONE
                        2 -> REPEAT_ALL
                        else -> REPEAT_NONE
                    }
                }
            } catch (e: Exception) {
                // ignore
            }
        }

        _playbackStateFlow.value = MediaTrackInfo(
            title = title,
            artist = artist,
            album = album,
            isPlaying = isPlaying,
            packageName = packageName,
            durationMs = duration,
            positionMs = position,
            lastUpdateTimeMs = lastUpdate,
            playbackSpeed = speed,
            shuffleMode = shuffle,
            repeatMode = repeat
        )
    }

    fun setPlaying(isPlaying: Boolean, title: String = "", artist: String = "", pkg: String = "") {
        _playbackStateFlow.value = _playbackStateFlow.value.copy(
            title = if (title.isNotEmpty()) title else _playbackStateFlow.value.title,
            artist = if (artist.isNotEmpty()) artist else _playbackStateFlow.value.artist,
            isPlaying = isPlaying,
            packageName = if (pkg.isNotEmpty()) pkg else _playbackStateFlow.value.packageName
        )
    }

    fun isMusicActive(context: Context): Boolean {
        if (_playbackStateFlow.value.isPlaying) return true
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        return audioManager?.isMusicActive ?: false
    }

    /**
     * Media Control: Play / Pause Toggle
     */
    fun togglePlayPause(context: Context) {
        val ctrl = activeController
        if (ctrl != null) {
            try {
                val state = ctrl.playbackState?.state
                if (state == PlaybackState.STATE_PLAYING) {
                    ctrl.transportControls.pause()
                } else {
                    ctrl.transportControls.play()
                }
                return
            } catch (e: Exception) {
                Log.w(TAG, "Failed to control active MediaController: ${e.message}")
            }
        }
        sendMediaKeyEvent(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    }

    /**
     * Media Control: Skip to Next Track
     */
    fun skipToNext(context: Context) {
        val ctrl = activeController
        if (ctrl != null) {
            try {
                ctrl.transportControls.skipToNext()
                return
            } catch (e: Exception) {
                Log.w(TAG, "Failed skipToNext via controller: ${e.message}")
            }
        }
        sendMediaKeyEvent(context, KeyEvent.KEYCODE_MEDIA_NEXT)
    }

    /**
     * Media Control: Skip to Previous Track
     */
    fun skipToPrevious(context: Context) {
        val ctrl = activeController
        if (ctrl != null) {
            try {
                ctrl.transportControls.skipToPrevious()
                return
            } catch (e: Exception) {
                Log.w(TAG, "Failed skipToPrevious via controller: ${e.message}")
            }
        }
        sendMediaKeyEvent(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    }

    /**
     * Media Control: Toggle Shuffle Mode
     */
    fun toggleShuffle(context: Context) {
        val current = _playbackStateFlow.value.shuffleMode
        val newMode = if (current == SHUFFLE_ALL) SHUFFLE_NONE else SHUFFLE_ALL
        _playbackStateFlow.value = _playbackStateFlow.value.copy(shuffleMode = newMode)

        val ctrl = activeController
        if (ctrl != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val setShuffle = ctrl.transportControls.javaClass.getMethod("setShuffleMode", Int::class.javaPrimitiveType)
                setShuffle.invoke(ctrl.transportControls, newMode)
            } catch (e: Exception) {
                try {
                    ctrl.transportControls.sendCustomAction("ACTION_SHUFFLE", null)
                } catch (ex: Exception) {
                    // ignore
                }
            }
        }
    }

    /**
     * Media Control: Toggle Repeat Mode
     */
    fun toggleRepeat(context: Context) {
        val current = _playbackStateFlow.value.repeatMode
        val newMode = when (current) {
            REPEAT_NONE -> REPEAT_ALL
            REPEAT_ALL -> REPEAT_ONE
            else -> REPEAT_NONE
        }
        _playbackStateFlow.value = _playbackStateFlow.value.copy(repeatMode = newMode)

        val ctrl = activeController
        if (ctrl != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val setRepeat = ctrl.transportControls.javaClass.getMethod("setRepeatMode", Int::class.javaPrimitiveType)
                setRepeat.invoke(ctrl.transportControls, newMode)
            } catch (e: Exception) {
                try {
                    ctrl.transportControls.sendCustomAction("ACTION_REPEAT", null)
                } catch (ex: Exception) {
                    // ignore
                }
            }
        }
    }

    /**
     * Media Control: Seek To
     */
    fun seekTo(positionMs: Long) {
        val ctrl = activeController
        if (ctrl != null) {
            try {
                ctrl.transportControls.seekTo(positionMs)
                _playbackStateFlow.value = _playbackStateFlow.value.copy(
                    positionMs = positionMs,
                    lastUpdateTimeMs = SystemClock.elapsedRealtime()
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed seekTo: ${e.message}")
            }
        }
    }

    private fun sendMediaKeyEvent(context: Context, keyCode: Int) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            val eventTime = SystemClock.uptimeMillis()
            val downEvent = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0)
            val upEvent = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0)
            audioManager.dispatchMediaKeyEvent(downEvent)
            audioManager.dispatchMediaKeyEvent(upEvent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed dispatchMediaKeyEvent: ${e.message}")
        }
    }
}
