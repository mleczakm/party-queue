package pl.mleczki.partyqueue

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
 * Three GeckoView sessions share one bridge add-on:
 *  - two player pages ("a" and "b") that take turns: while one plays, the other already holds the next song,
 *    paused and silent, so the change of song is instant even when YouTube is slow to start a video
 *    (it deliberately delays people who block ads by several seconds);
 *  - a browse page where the host looks around YouTube to pick songs and playlists.
 * The add-on tells them apart by the "pq" URL parameter, so browsing can never disturb playback.
 */
class PlayerBridge(private val context: Context, private val runtime: GeckoRuntime) : PlayerPort {

    /** One player page. */
    private inner class Slot(val id: String) {
        val session: GeckoSession = newSession().also(::prioritise)

        /** The page has loaded and reported in, so a song can be loaded inside it without a reload. */
        var alive = false

        /** The song this page holds or is loading. */
        var videoId: String? = null

        /** Standby only: enough of the song is buffered to start it instantly. */
        var ready = false
        var pendingLoad: String? = null
    }

    var party: PartyController? = null

    private val main = Handler(Looper.getMainLooper())
    private var port: WebExtension.Port? = null
    private var ready = false
    private var pendingVideo: String? = null
    private var idle = true

    private val a = Slot("a")
    private var b: Slot? = null
    private var active: Slot = a
    private val spare: Slot get() = if (active === a) b() else a

    private fun b(): Slot = b ?: Slot("b").also { b = it; watchProcess(it) }

    private val _visible = MutableStateFlow(a.session)

    /** The player page shown in the host's small preview. */
    val visibleSession: StateFlow<GeckoSession> = _visible.asStateFlow()

    private var wantedPreload: String? = null

    /** A manual change of a song that was not prepared: it loads on the spare page, then swaps in with a fade. */
    private class PendingFade(val slot: Slot, val videoId: String, val fadeMs: Int)
    private var pendingFade: PendingFade? = null

    /** The spare page is still fading its old song out until then; it cannot take a new song before. */
    private var spareBusyUntil = 0L
    private var activePlaying = false
    private var loadStartedAt = 0L
    private var loadStartedId = ""

    private var browse: GeckoSession? = null
    private val _browseUrl = MutableStateFlow("")
    val browseUrl: StateFlow<String> = _browseUrl.asStateFlow()
    private var pendingImport: CompletableDeferred<String>? = null
    private var pendingBrowse: String? = null
    private val _signedIn = MutableStateFlow(false)

    /** Whether the browse session is signed in to Google (decided by the page itself). */
    val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()

    init {
        watchProcess(a)
    }

    // ---------------------------------------------------------------- sessions

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

    /**
     * The queue's pages must keep running with the screen off: without this hint Android treats their content
     * process as a background tab, slows it down and kills it.
     */
    private fun prioritise(s: GeckoSession) {
        s.setPriorityHint(GeckoSession.PRIORITY_HIGH)
        s.setActive(true)
    }

    /** Android (or Gecko itself) may kill a content process; bring the page back. */
    private fun watchProcess(slot: Slot) {
        slot.session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onCrash(session: GeckoSession) = recover(slot, "crash")
            override fun onKill(session: GeckoSession) = recover(slot, "kill")
        }
    }

    private fun recover(slot: Slot, why: String) {
        Log.w(TAG, "content process of slot ${slot.id} $why, reopening")
        main.postDelayed({
            slot.session.open(runtime)
            prioritise(slot.session)
            slot.alive = false
            slot.ready = false
            if (slot === active) {
                party?.currentVideoId()?.let { hardLoad(slot, it, standby = false) }
            } else {
                slot.videoId = null
                maybePreload()
            }
        }, 800)
    }

    // ---------------------------------------------------------------- messages from the pages

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

    private fun slotOf(id: String): Slot? = when (id) {
        "a" -> a
        "b" -> b
        else -> null
    }

    private fun onPageMessage(m: JSONObject) {
        when (m.optString("type")) {
            "log" -> Log.i(TAG, "page(${m.optString("mode")}${m.optString("slot")}): ${m.optString("msg")}")
            "playlistData" -> pendingImport?.complete(m.optString("data"))
            "login" -> if (m.optString("mode") == "browse") _signedIn.value = m.optBoolean("signedIn")
            else -> if (m.optString("mode") == "player") onPlayerPageMessage(m)
        }
    }

    private fun onPlayerPageMessage(m: JSONObject) {
        val slot = slotOf(m.optString("slot", "a")) ?: return
        slot.alive = true
        val vid = m.optString("videoId")
        when (m.optString("type")) {
            "loadAck" -> if (vid == slot.pendingLoad) {
                if (m.optBoolean("ok")) slot.pendingLoad = null else hardLoad(slot, vid, standby = slot !== active)
            }
            "standbyReady" -> if (slot !== active && vid == slot.videoId) {
                slot.ready = true
                Log.i(TAG, "slot ${slot.id} holds $vid, ready")
                pendingFade?.takeIf { it.slot === slot && it.videoId == vid }?.let {
                    pendingFade = null
                    loadStartedAt = SystemClock.elapsedRealtime()
                    loadStartedId = vid
                    swapTo(slot, it.fadeMs)
                }
            }
            else -> if (slot === active) {
                if (m.optString("type") == "state") {
                    if (vid == slot.pendingLoad) slot.pendingLoad = null
                    activePlaying = m.optString("state") == "playing" && !m.optBoolean("ad")
                    if (activePlaying && vid == loadStartedId && loadStartedAt != 0L) {
                        Log.i(TAG, "STARTUP ${SystemClock.elapsedRealtime() - loadStartedAt} ms for $vid")
                        loadStartedAt = 0L
                    }
                    if (activePlaying) maybePreload()
                }
                party?.onPlayerMessage(m)
            }
        }
    }

    // ---------------------------------------------------------------- PlayerPort

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
        a.alive = false
        b?.alive = false
        a.videoId = null
        b?.videoId = null
        activePlaying = false
        active.session.loadUri("http://127.0.0.1:${PartyServer.PORT}/idle")
    }

    override fun load(videoId: String, fadeMs: Int) {
        main.post {
            if (!ready) {
                pendingVideo = videoId
                return@post
            }
            pendingFade = null
            val fade = if (fadeMs > 0 && activePlaying && !idle) fadeMs else 0
            idle = false
            loadStartedAt = SystemClock.elapsedRealtime()
            loadStartedId = videoId
            val prepared = b?.let { spare.takeIf { s -> s.videoId == videoId && s.ready } }
            if (prepared != null) {
                activePlaying = false
                swapTo(prepared, fade)
            } else if (fade > 0) {
                // The old song keeps playing until the new one is loaded; then they cross over.
                val target = spare
                pendingFade = PendingFade(target, videoId, fade)
                target.ready = false
                loadInto(target, videoId, standby = true)
                main.postDelayed({
                    if (pendingFade?.videoId == videoId) {
                        Log.w(TAG, "faded change of $videoId took too long; loading it directly")
                        pendingFade = null
                        activePlaying = false
                        loadInto(active, videoId, standby = false)
                    }
                }, FADE_WAIT_MS)
            } else {
                activePlaying = false
                loadInto(active, videoId, standby = false)
            }
        }
    }

    /** The next song is already buffered on the spare page: make that page the one that plays. */
    private fun swapTo(next: Slot, fadeMs: Int = 0) {
        val old = active
        Log.i(TAG, "swap ${old.id} -> ${next.id} for ${next.videoId}" + if (fadeMs > 0) " (fade $fadeMs ms)" else "")
        activePlaying = false
        send(old, if (fadeMs > 0) "fadeOut" else "deactivate") { it.put("fade", fadeMs) }
        send(next, "activate") { it.put("fade", fadeMs) }
        spareBusyUntil = if (fadeMs > 0) SystemClock.elapsedRealtime() + fadeMs + 600 else 0L
        active = next
        next.ready = false
        old.ready = false
        old.videoId = null
        prioritise(next.session)
        _visible.value = next.session
        // The prepared page starts playing at once; the next preload waits until it reports in.
    }

    /** The song after the current one; null when the queue is empty. Called whenever the head of the queue changes. */
    fun preload(videoId: String?) {
        main.post {
            wantedPreload = videoId
            maybePreload()
        }
    }

    private fun maybePreload() {
        if (!ready || idle || !activePlaying || pendingFade != null) return
        val wait = spareBusyUntil - SystemClock.elapsedRealtime()
        if (wait > 0) {
            main.postDelayed(::maybePreload, wait + 50)
            return
        }
        val id = wantedPreload
        if (id == null) {
            b?.let { it.videoId = null; it.ready = false }
            return
        }
        val s = spare
        if (s.videoId == id) return
        s.ready = false
        loadInto(s, id, standby = true)
    }

    private fun loadInto(slot: Slot, videoId: String, standby: Boolean) {
        slot.videoId = videoId
        slot.ready = false
        if (slot === active) prioritise(slot.session)
        if (slot.alive && port != null) {
            Log.i(TAG, "load $videoId in place (slot ${slot.id}${if (standby) ", standby" else ""})")
            slot.pendingLoad = videoId
            send(slot, "loadVideo") { it.put("id", videoId).put("standby", standby) }
            // If the page does not answer soon, load the song the slow way.
            main.postDelayed({
                if (slot.pendingLoad == videoId) {
                    Log.w(TAG, "in-place load of $videoId timed out; reloading slot ${slot.id}")
                    hardLoad(slot, videoId, standby)
                }
            }, IN_PLACE_TIMEOUT_MS)
        } else {
            hardLoad(slot, videoId, standby)
        }
    }

    private fun hardLoad(slot: Slot, videoId: String, standby: Boolean) {
        Log.i(TAG, "load $videoId (new page, slot ${slot.id}${if (standby) ", standby" else ""})")
        slot.pendingLoad = null
        slot.alive = false
        slot.videoId = videoId
        slot.session.loadUri("https://m.youtube.com/watch?v=$videoId&pq=${slot.id}${if (standby) "&standby=1" else ""}")
    }

    private fun send(slot: Slot, cmd: String, extra: ((JSONObject) -> JSONObject)? = null) {
        val msg = JSONObject().put("cmd", cmd).put("slot", slot.id)
        port?.postMessage(extra?.invoke(msg) ?: msg)
    }

    override fun command(cmd: String, arg: Double) {
        if (cmd == "stop") {
            main.post {
                wantedPreload = null
                if (ready) showIdle()
            }
            return
        }
        main.post { send(active, cmd) { it.put("arg", arg) } }
    }

    /** A freshly attached GeckoView does not repaint a static page by itself. */
    fun onViewAttached() {
        main.post {
            active.session.setActive(true)
            if (ready && idle) showIdle()
        }
    }

    // ---------------------------------------------------------------- browsing

    /** Created on first use so a host who never browses pays nothing for another page. */
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
        const val IN_PLACE_TIMEOUT_MS = 12_000L
        private const val FADE_WAIT_MS = 25_000L
        const val YOUTUBE_HOME = "https://m.youtube.com/"
    }
}
