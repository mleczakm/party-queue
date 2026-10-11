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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
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
            .setSmallIcon(R.drawable.ic_stat_party)
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
                        .setSmallIcon(R.drawable.ic_stat_party)
                        .setContentTitle(title ?: "Party Queue")
                        .setContentText(if (title != null) "Odtwarzanie działa w tle" else "Kolejka czeka na utwory")
                        .setContentIntent(open)
                        .setOngoing(true)
                        .build()
                    getSystemService(NotificationManager::class.java).notify(1, n)
                }
            }
        }

        val power = getSystemService(PowerManager::class.java)
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "partyqueue:playback")
        wifiLock = applicationContext.getSystemService(WifiManager::class.java)
            .createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "partyqueue:wifi")
        // The locks are held only while they are of use: the CPU while a song plays or guests are connected, the
        // Wi-Fi radio at full attention only while guests are connected. A forgotten, paused party lets go of both.
        (application as? PartyApp)?.takeIf { it.isPartyReady() }?.let { app ->
            scope.launch {
                app.party.state
                    .map { s -> Needs(cpu = s.player.status in ACTIVE || s.online.isNotEmpty(), wifi = s.online.isNotEmpty()) }
                    .distinctUntilChanged()
                    .collectLatest { need ->
                        // Letting go waits a little, so a skip or a short pause does not flap the locks.
                        if (!need.cpu) delay(LET_GO_AFTER_MS)
                        setLock(wakeLock, need.cpu)
                        if (!need.wifi) delay(LET_GO_AFTER_MS)
                        setLock(wifiLock, need.wifi)
                    }
            }
        }
    }

    private data class Needs(val cpu: Boolean, val wifi: Boolean)

    private fun setLock(lock: Any?, on: Boolean) {
        when (lock) {
            is PowerManager.WakeLock -> if (on && !lock.isHeld) lock.acquire(MAX_PARTY_MS) else if (!on && lock.isHeld) lock.release()
            is WifiManager.WifiLock -> if (on && !lock.isHeld) lock.acquire() else if (!on && lock.isHeld) lock.release()
        }
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
        val ACTIVE = setOf("playing", "loading", "ad")
        const val LET_GO_AFTER_MS = 60_000L
        /** Safety net: no party lasts longer than this; the wake lock must not outlive a forgotten session. */
        const val MAX_PARTY_MS = 12 * 60 * 60 * 1000L
    }
}
