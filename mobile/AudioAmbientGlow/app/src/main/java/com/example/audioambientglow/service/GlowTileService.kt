package com.example.audioambientglow.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.example.audioambientglow.data.GlowPreferencesRepository

@RequiresApi(Build.VERSION_CODES.N)
class GlowTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val prefs = GlowPreferencesRepository.getInstance(this)
        val current = prefs.getConfig()

        // Ensure ambient glow is enabled
        if (!current.isEnabled) {
            prefs.updateConfig { it.copy(isEnabled = true) }
            GlowOverlayService.start(this)
        }

        // Directly launch AOD Ambient Glow Screen over any current app
        val aodIntent = Intent(this, AodGlowActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    aodIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(aodIntent)
            }
        } catch (e: Exception) {
            // Fallback direct start
            aodIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(aodIntent)
        }

        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val prefs = GlowPreferencesRepository.getInstance(this)
        val isEnabled = prefs.getConfig().isEnabled

        tile.state = if (isEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "息屏氣氛燈 AOD"
        tile.updateTile()
    }
}
