package com.example.audioambientglow.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import com.example.audioambientglow.data.GlowDisplayMode
import com.example.audioambientglow.data.GlowPreferencesRepository
import com.example.audioambientglow.service.AodGlowActivity

class ScreenStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val prefsRepo = GlowPreferencesRepository.getInstance(context)
        val config = prefsRepo.getConfig()

        if (!config.isEnabled) return

        when (intent.action) {
            Intent.ACTION_SCREEN_OFF -> {
                // If AOD mode is enabled, check if music is currently active
                if (config.displayMode == GlowDisplayMode.AOD_ONLY || config.displayMode == GlowDisplayMode.ALWAYS_OVERLAY) {
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    val isMusicPlaying = audioManager?.isMusicActive ?: false

                    if (isMusicPlaying) {
                        // Start AOD Glow Activity on screen off
                        val aodIntent = Intent(context, AodGlowActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                        context.startActivity(aodIntent)
                    }
                }
            }
        }
    }
}
