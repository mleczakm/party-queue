package pl.mleczki.partyqueue

import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension

/**
 * Two GeckoView sessions share one bridge add-on:
 *  - the player session plays the queue and is never touched by the user,
 *  - the browse session is where the host looks around YouTube to pick songs and playlists.
 * The add-on tells them apart by the "pq" URL parameter, so browsing can never disturb playback.
 */
class PlayerBridge(private val runtime: GeckoRuntime) : PlayerPort {

    val session: GeckoSession = newSession()
    var party: PartyController? = null

    private val main = Handler(Looper.getMainLooper())
    private var port: WebExtension.Port? = null
    private var ready = false
    private var pendingVideo: String? = null
    private var idle = true

    private var browse: GeckoSession? = null
    private val _browseUrl = MutableStateFlow("")
    val browseUrl: StateFlow<String> = _browseUrl.asStateFlow()
    private var pendingImport: CompletableDeferred<String>? = null
    private var pendingBrowse: String? = null
    private val _signedIn = MutableStateFlow(false)
    /** Whether the browse session is signed in to Google (decided by the page itself). */
    val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()

    init {
        // Android (or Gecko itself) may kill the content process; bring the current track back.
        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onCrash(session: GeckoSession) = recover("crash")
            override fun onKill(session: GeckoSession) = recover("kill")
        }
    }

    private fun newSession(): GeckoSession = GeckoSession().also { s ->
        s.permissionDelegate = object : GeckoSession.PermissionDelegate {
            override fun onContentPermissionRequest(
                session: GeckoSession,
                perm: GeckoSession.PermissionDelegate.ContentPermission,
            ): GeckoResult<Int>? {
                // Without this Gecko refuses autoplay and the queue would stall after every song.
                val autoplay = perm.permission == GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE ||
                    perm.permission == GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE
                return GeckoResult.fromValue(
                    if (autoplay) GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW
                    else GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY
                )
            }
        }
        s.open(runtime)
    }

    private fun recover(why: String) {
        Log.w(TAG, "content process $why, reopening session")
        main.postDelayed({
            session.open(runtime)
            party?.currentVideoId()?.let { load(it) }
        }, 800)
    }

    val messageDelegate = object : WebExtension.MessageDelegate {
        override fun onConnect(port: WebExtension.Port) {
            this@PlayerBridge.port = port
            port.setDelegate(object : WebExtension.PortDelegate {
                override fun onPortMessage(message: Any, port: WebExtension.Port) {
                    (message as? JSONObject)?.let(::onPageMessage)
                }

                override fun onDisconnect(port: WebExtension.Port) {
                    if (this@PlayerBridge.port === port) this@PlayerBridge.port = null
                }
            })
            Log.i(TAG, "bridge connected")
        }
    }

    private fun onPageMessage(m: JSONObject) {
        when (m.optString("type")) {
            "log" -> Log.i(TAG, "page(${m.optString("mode")}): ${m.optString("msg")}")
            "playlistData" -> pendingImport?.complete(m.optString("data"))
            "login" -> if (m.optString("mode") == "browse") _signedIn.value = m.optBoolean("signedIn")
            else -> if (m.optString("mode") == "player") party?.onPlayerMessage(m)
        }
    }

    /** Add-ons are installed asynchronously; pages must not load before they are active. */
    fun markReady() = main.post {
        ready = true
        val v = pendingVideo
        pendingVideo = null
        if (v != null) load(v) else showIdle()
        browse?.loadUri(pendingBrowse ?: YOUTUBE_HOME)
        pendingBrowse = null
    }

    /** A calm page instead of an empty grey surface while nothing is playing. */
    private fun showIdle() {
        idle = true
        session.loadUri("http://127.0.0.1:${PartyServer.PORT}/idle")
    }

    override fun load(videoId: String) {
        main.post {
            if (!ready) {
                pendingVideo = videoId
            } else {
                idle = false
                session.loadUri("https://m.youtube.com/watch?v=$videoId&pq=1")
            }
        }
    }

    override fun command(cmd: String, arg: Double) {
        if (cmd == "stop") {
            main.post { if (ready) showIdle() }
            return
        }
        main.post {
            port?.postMessage(JSONObject().put("cmd", cmd).put("arg", arg).put("target", "player"))
        }
    }

    /** A freshly attached GeckoView does not repaint a static page by itself. */
    fun onViewAttached() {
        main.post {
            session.setActive(true)
            if (ready && idle) showIdle()
        }
    }

    // ---------------------------------------------------------------- browsing

    /** Created on first use so a host who never browses pays nothing for a second page. */
    fun browseSession(): GeckoSession {
        browse?.let { return it }
        val s = newSession()
        s.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean,
            ) {
                Log.i(TAG, "browse url: $url")
                _browseUrl.value = url.orEmpty().removeSuffix("&pqimport=1").removeSuffix("?pqimport=1")
            }
        }
        browse = s
        if (ready) s.loadUri(pendingBrowse ?: YOUTUBE_HOME).also { pendingBrowse = null }
        return s
    }

    fun browseTo(url: String) {
        main.post {
            val session = browseSession()
            if (ready) session.loadUri(url) else pendingBrowse = url
        }
    }

    /**
     * Reads a playlist the way the logged-in browser sees it (works for private lists after signing in
     * on the YouTube tab): reloads the page and takes the data YouTube embedded in it.
     */
    suspend fun scrapePlaylist(url: String): String {
        val wait = CompletableDeferred<String>()
        pendingImport = wait
        val sep = if ('?' in url) '&' else '?'
        main.post { browseSession().loadUri("$url${sep}pqimport=1") }
        try {
            val data = withTimeout(30_000) { wait.await() }
            if (data.isBlank()) throw PartyException("Nie udało się odczytać playlisty ze strony")
            return data
        } finally {
            pendingImport = null
        }
    }

    private companion object {
        const val TAG = "PartyQueue"
        const val YOUTUBE_HOME = "https://m.youtube.com/"
    }
}
