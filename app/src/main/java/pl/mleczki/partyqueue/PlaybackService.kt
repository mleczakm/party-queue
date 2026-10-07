package pl.mleczki.partyqueue

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.IBinder
import android.os.PowerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps the process (GeckoView, later the HTTP server) alive while the screen is off.
 */
class PlaybackService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var base: Notification? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Party Queue", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Party Queue")
            .setContentText("Odtwarzanie działa w tle")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        base = notification
        (application as? PartyApp)?.takeIf { it.isPartyReady() }?.let { app ->
            scope.launch {
                app.party.state.map { it.current?.title }.distinctUntilChanged().collect { title ->
                    val n = Notification.Builder(this@PlaybackService, CHANNEL)
                        .setSmallIcon(android.R.drawable.ic_media_play)
                        .setContentTitle(title ?: "Party Queue")
                        .setContentText(if (title != null) "Odtwarzanie działa w tle" else "Kolejka czeka na utwory")
                        .setContentIntent(open)
                        .setOngoing(true)
                        .build()
                    getSystemService(NotificationManager::class.java).notify(1, n)
                }
            }
        }

        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "partyqueue:playback")
            .apply { acquire(MAX_PARTY_MS) }
        wifiLock = applicationContext.getSystemService(WifiManager::class.java)
            .createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "partyqueue:wifi")
            .apply { acquire() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        wakeLock?.takeIf { it.isHeld }?.release()
        wifiLock?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }

    private companion object {
        const val CHANNEL = "playback"
        /** Safety net: no party lasts longer than this; the wake lock must not outlive a forgotten session. */
        const val MAX_PARTY_MS = 12 * 60 * 60 * 1000L
    }
}
