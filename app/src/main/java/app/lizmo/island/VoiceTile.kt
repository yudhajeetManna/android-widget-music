package app.lizmo.island

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Quick Settings tile: one tap to (re)arm the wake word, e.g. after phone boot
 * or when starting music.
 */
class VoiceTile : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        if (VoiceService.running) {
            Prefs.setVoiceOn(this, false)
            VoiceService.stop(this)
        } else if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            Prefs.setVoiceOn(this, true)
            try {
                VoiceService.start(this)
            } catch (e: Exception) {
                VoiceBus.say(this, "Open Lizmo to start voice listening")
            }
        } else {
            val i = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT >= 34) {
                val pi = android.app.PendingIntent.getActivity(
                    this, 0, i,
                    android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
                )
                startActivityAndCollapse(pi)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(i)
            }
        }
        refresh()
    }

    private fun refresh() {
        val t = qsTile ?: return
        t.state = if (VoiceService.running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        t.label = "Lizmo Voice"
        t.updateTile()
    }
}
