package com.example.audioambientglow.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class GlowPreferencesRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _configFlow = MutableStateFlow(loadConfig())
    val configFlow: StateFlow<GlowConfig> = _configFlow.asStateFlow()

    fun getConfig(): GlowConfig = _configFlow.value

    fun updateConfig(update: (GlowConfig) -> GlowConfig) {
        val newConfig = update(_configFlow.value)
        _configFlow.value = newConfig
        saveConfig(newConfig)
    }

    private fun loadConfig(): GlowConfig {
        return GlowConfig(
            isEnabled = prefs.getBoolean(KEY_IS_ENABLED, true),
            displayMode = try {
                GlowDisplayMode.valueOf(prefs.getString(KEY_DISPLAY_MODE, GlowDisplayMode.AOD_ONLY.name) ?: GlowDisplayMode.AOD_ONLY.name)
            } catch (e: Exception) {
                GlowDisplayMode.AOD_ONLY
            },
            glowThicknessDp = prefs.getFloat(KEY_THICKNESS, 16f),
            bloomFeatheringDp = prefs.getFloat(KEY_FEATHERING, 28f),
            cornerRadiusDp = prefs.getFloat(KEY_CORNER_RADIUS, 36f),
            baseSpeed = prefs.getFloat(KEY_BASE_SPEED, 0.6f),
            bassSpeedMultiplier = prefs.getFloat(KEY_BASS_BOOST, 3.5f),
            dynamicHueRangeDeg = prefs.getFloat(KEY_DYNAMIC_HUE_RANGE, 60f),
            attackTimeSeconds = prefs.getFloat(KEY_ATTACK_TIME, 0.9f),
            decayTimeSeconds = prefs.getFloat(KEY_DECAY_TIME, 1.1f),
            noiseGateThreshold = prefs.getFloat(KEY_NOISE_GATE, 0.06f),
            themePreset = try {
                GlowThemePreset.valueOf(prefs.getString(KEY_THEME, GlowThemePreset.CYBERPUNK.name) ?: GlowThemePreset.CYBERPUNK.name)
            } catch (e: Exception) {
                GlowThemePreset.CYBERPUNK
            },
            audioSourceType = try {
                AudioSourceType.valueOf(prefs.getString(KEY_AUDIO_SOURCE, AudioSourceType.SYSTEM_MEDIA_PLAYBACK.name) ?: AudioSourceType.SYSTEM_MEDIA_PLAYBACK.name)
            } catch (e: Exception) {
                AudioSourceType.SYSTEM_MEDIA_PLAYBACK
            },
            dynamicHueShiftEnabled = prefs.getBoolean(KEY_HUE_SHIFT, true),
            amoledPureBlackBackground = prefs.getBoolean(KEY_AMOLED_BLACK, true),
            antiBurnInEnabled = prefs.getBoolean(KEY_ANTI_BURN_IN, true),
            customColorA = prefs.getLong(KEY_COLOR_A, 0xFF00F5FFL),
            customColorB = prefs.getLong(KEY_COLOR_B, 0xFFFF007FL),
            customColorC = prefs.getLong(KEY_COLOR_C, 0xFFFFD700L),
            customColorD = prefs.getLong(KEY_COLOR_D, 0xFF7928CAL)
        )
    }

    private fun saveConfig(config: GlowConfig) {
        prefs.edit().apply {
            putBoolean(KEY_IS_ENABLED, config.isEnabled)
            putString(KEY_DISPLAY_MODE, config.displayMode.name)
            putFloat(KEY_THICKNESS, config.glowThicknessDp)
            putFloat(KEY_FEATHERING, config.bloomFeatheringDp)
            putFloat(KEY_CORNER_RADIUS, config.cornerRadiusDp)
            putFloat(KEY_BASE_SPEED, config.baseSpeed)
            putFloat(KEY_BASS_BOOST, config.bassSpeedMultiplier)
            putFloat(KEY_DYNAMIC_HUE_RANGE, config.dynamicHueRangeDeg)
            putFloat(KEY_ATTACK_TIME, config.attackTimeSeconds)
            putFloat(KEY_DECAY_TIME, config.decayTimeSeconds)
            putFloat(KEY_NOISE_GATE, config.noiseGateThreshold)
            putString(KEY_THEME, config.themePreset.name)
            putString(KEY_AUDIO_SOURCE, config.audioSourceType.name)
            putBoolean(KEY_HUE_SHIFT, config.dynamicHueShiftEnabled)
            putBoolean(KEY_AMOLED_BLACK, config.amoledPureBlackBackground)
            putBoolean(KEY_ANTI_BURN_IN, config.antiBurnInEnabled)
            putLong(KEY_COLOR_A, config.customColorA)
            putLong(KEY_COLOR_B, config.customColorB)
            putLong(KEY_COLOR_C, config.customColorC)
            putLong(KEY_COLOR_D, config.customColorD)
            apply()
        }
    }

    companion object {
        private const val PREFS_NAME = "audio_ambient_glow_prefs"
        private const val KEY_IS_ENABLED = "is_enabled"
        private const val KEY_DISPLAY_MODE = "display_mode"
        private const val KEY_THICKNESS = "glow_thickness_dp"
        private const val KEY_FEATHERING = "bloom_feathering_dp"
        private const val KEY_CORNER_RADIUS = "corner_radius_dp"
        private const val KEY_BASE_SPEED = "base_cruise_speed"
        private const val KEY_BASS_BOOST = "bass_boost_factor"
        private const val KEY_DYNAMIC_HUE_RANGE = "dynamic_hue_range_deg"
        private const val KEY_ATTACK_TIME = "attack_time_seconds"
        private const val KEY_DECAY_TIME = "decay_time_seconds"
        private const val KEY_NOISE_GATE = "noise_gate_threshold"
        private const val KEY_THEME = "theme_preset"
        private const val KEY_AUDIO_SOURCE = "audio_source_type"
        private const val KEY_HUE_SHIFT = "dynamic_hue_shift"
        private const val KEY_AMOLED_BLACK = "amoled_pure_black"
        private const val KEY_ANTI_BURN_IN = "anti_burn_in"
        private const val KEY_COLOR_A = "custom_color_a"
        private const val KEY_COLOR_B = "custom_color_b"
        private const val KEY_COLOR_C = "custom_color_c"
        private const val KEY_COLOR_D = "custom_color_d"

        @Volatile
        private var instance: GlowPreferencesRepository? = null

        fun getInstance(context: Context): GlowPreferencesRepository {
            return instance ?: synchronized(this) {
                instance ?: GlowPreferencesRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
