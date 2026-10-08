package pl.mleczki.partyqueue

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.mozilla.geckoview.GeckoPreferenceController
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import java.io.File

/** The media volume of the phone (the one the volume keys change). */
private class PhoneVolume(private val audio: AudioManager) : VolumeControl {
    private val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)

    override fun percent() = (audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100f / max).roundToInt()

    override fun setPercent(percent: Int) {
        runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, (percent * max / 100f).roundToInt(), 0) }
    }
}

class PartyApp : Application() {

    lateinit var runtime: GeckoRuntime
        private set
    lateinit var party: PartyController
        private set
    lateinit var player: PlayerBridge
        private set
    lateinit var server: PartyServer
        private set

    private val _themeMode = MutableStateFlow(ThemeMode.AUTO)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    /** Auto → light → dark → auto; remembered between runs. */
    fun cycleTheme() {
        val next = _themeMode.value.next()
        _themeMode.value = next
        getSharedPreferences("ui", MODE_PRIVATE).edit().putString("theme", next.name).apply()
    }

    fun isPartyReady() = ::party.isInitialized

    override fun onCreate() {
        super.onCreate()
        // Gecko child processes (gpu, tab, crashhelper) instantiate this class too;
        // everything below must only run in the main process.
        if (getProcessName() != packageName) return

        val profile = File(filesDir, "gecko-profile").apply { mkdirs() }
        File(profile, "user.js").writeText(GECKO_PREFS)
        runtime = GeckoRuntime.create(
            this,
            GeckoRuntimeSettings.Builder().arguments(arrayOf("-profile", profile.absolutePath)).build()
        )
        applyGeckoPrefs()
        _themeMode.value = runCatching {
            ThemeMode.valueOf(getSharedPreferences("ui", MODE_PRIVATE).getString("theme", null) ?: "AUTO")
        }.getOrDefault(ThemeMode.AUTO)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        party = PartyController(getSharedPreferences("party", MODE_PRIVATE), YouTubeClient(), scope)
        player = PlayerBridge(this, runtime).also {
            it.party = party
            party.player = it
        }
        party.volumeControl = PhoneVolume(getSystemService(AudioManager::class.java))
        party.refreshVolume()
        // The volume keys change it as well: keep the guests' sliders in step.
        ContextCompat.registerReceiver(this, object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = party.refreshVolume()
        }, IntentFilter("android.media.VOLUME_CHANGED_ACTION"), ContextCompat.RECEIVER_NOT_EXPORTED)
        server = PartyServer(this, party).also { it.start() }
        // Keep the spare player page loaded with the song that comes next.
        scope.launch {
            party.state.map { it.queue.firstOrNull()?.videoId }.distinctUntilChanged().collect { player.preload(it) }
        }
        installExtensions()
    }

    private fun applyGeckoPrefs() {
        val user = GeckoPreferenceController.PREF_BRANCH_USER
        fun logPref(name: String) = GeckoPreferenceController.getGeckoPref(name).accept(
            { p -> Log.i(TAG, "pref $name = ${p?.value}") },
            { e -> Log.w(TAG, "pref $name unreadable: $e") }
        )
        listOf("media.autoplay.default" to 0, "media.autoplay.blocking_policy" to 0).forEach { (k, v) ->
            GeckoPreferenceController.setGeckoPref(k, v, user).accept(
                { logPref(k) },
                { e -> Log.w(TAG, "pref $k rejected: $e") }
            )
        }
        GeckoPreferenceController.setGeckoPref("media.block-autoplay-until-in-foreground", false, user)
        GeckoPreferenceController.setGeckoPref("media.suspend-bkgnd-video.enabled", false, user)
    }

    private fun installExtensions() {
        var pending = EXTENSIONS.size
        fun done() {
            if (--pending == 0) player.markReady()
        }
        for ((id, dir) in EXTENSIONS) {
            runtime.webExtensionController
                .ensureBuiltIn("resource://android/assets/extensions/$dir/", id)
                .accept(
                    { ext ->
                        Log.i(TAG, "extension ready: ${ext?.id} ${ext?.metaData?.name}")
                        if (id == BRIDGE_ID) ext?.setMessageDelegate(player.messageDelegate, "browser")
                        done()
                    },
                    { e ->
                        Log.e(TAG, "extension failed: $id", e)
                        done()
                    }
                )
        }
    }

    companion object {
        const val TAG = "PartyQueue"

        /** Tracks must start and keep playing without taps, with the screen off. */
        private val GECKO_PREFS = """
            user_pref("media.autoplay.default", 0);
            user_pref("media.autoplay.blocking_policy", 0);
            user_pref("media.block-autoplay-until-in-foreground", false);
            user_pref("media.suspend-bkgnd-video.enabled", false);
        """.trimIndent()
        private const val BRIDGE_ID = "bridge@partyqueue.mleczki.pl"
        private val EXTENSIONS = listOf(
            "uBlock0@raymondhill.net" to "ublock",
            "play-youtube-video-in-background@labinator.com" to "bgplay",
            BRIDGE_ID to "bridge",
        )
    }
}
