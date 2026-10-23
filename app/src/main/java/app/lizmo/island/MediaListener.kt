package app.lizmo.island

import android.content.ComponentName
import android.service.notification.NotificationListenerService

/**
 * Enabling this listener (Settings > Notification access) is what grants access to
 * other apps' media sessions. It also hosts the overlay, so no foreground service is needed.
 */
class MediaListener : NotificationListenerService() {
    companion object {
        @Volatile
        var instance: MediaListener? = null
    }

    private var tracker: MediaTracker? = null
    private var island: IslandController? = null

    override fun onListenerConnected() {
        instance = this
        if (tracker != null) return
        val t = MediaTracker(this, ComponentName(this, MediaListener::class.java)) { island?.update() }
        val i = IslandController(this, t)
        tracker = t
        island = i
        t.start()
        applyPrefs()
    }

    override fun onListenerDisconnected() = shutdown()

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    fun applyPrefs() {
        val i = island ?: return
        i.enabled = Prefs.enabled(this)
        i.reloadTheme()
        i.update()
    }

    private fun shutdown() {
        tracker?.stop()
        island?.destroy()
        tracker = null
        island = null
        instance = null
    }
}
