package pl.mleczki.partyqueue

import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

interface PlayerPort {
    /** [fadeMs] > 0: the song that is playing fades out while this one fades in (only for manual changes). */
    fun load(videoId: String, fadeMs: Int = 0)
    fun command(cmd: String, arg: Double = 0.0)
}

class PartyException(message: String) : Exception(message)

/**
 * Single source of truth for the party: queue, proposals, guests and what the player is doing.
 * Both the host UI and the HTTP server call into this class; the host is simply a guest with [Role.HOST].
 */
@OptIn(FlowPreview::class)
class PartyController(
    private val prefs: SharedPreferences,
    val yt: MetaSource,
    private val scope: CoroutineScope,
) {
    var player: PlayerPort? = null

    val hostGuest: Guest
    private val _state: MutableStateFlow<Snapshot>
    val state: StateFlow<Snapshot> get() = _state.asStateFlow()

    private var playlistTracks: List<Meta> = emptyList()
    private var shuffle = false
    private var watchdog: Job? = null
    private val onlineCounts = HashMap<String, Int>()

    init {
        val saved = prefs.getString("data", null)?.let { runCatching { JSONObject(it) }.getOrNull() }
        val hostToken = saved?.optString("hostToken").orEmpty().ifEmpty { randomToken(32) }
        hostGuest = Guest(HOST_ID, "Host", Role.HOST, hostToken)
        val guests = saved?.optJSONArray("guests")?.let { a ->
            (0 until a.length()).map { i ->
                val g = a.getJSONObject(i)
                Guest(g.getString("id"), g.getString("name"), Role.valueOf(g.getString("role")), g.getString("token"))
            }
        }.orEmpty()
        _state = MutableStateFlow(
            Snapshot(
                guests = guests,
                joinOpen = saved?.optBoolean("joinOpen", true) ?: true,
                repeat = saved?.optBoolean("repeat", true) ?: true,
                playlistUrl = saved?.optString("playlistUrl").orEmpty(),
            )
        )
        persist()
        restoreQueue()
        scope.launch {
            _state.map { it.current to it.queue }.distinctUntilChanged().debounce(400).collect { saveQueue() }
        }
    }

    /** After a restart the previous queue comes back paused; the host presses play. */
    private fun restoreQueue() {
        val saved = prefs.getString("queue", null)?.let { runCatching { JSONArray(it) }.getOrNull() } ?: return
        val tracks = (0 until saved.length()).mapNotNull { runCatching { trackFromStoreJson(saved.getJSONObject(it)) }.getOrNull() }
        _state.update { it.copy(queue = tracks) }
        val url = _state.value.playlistUrl
        if (url.isNotBlank()) {
            // Refill source for "repeat"; the queue itself is left alone.
            scope.launch {
                runCatching { yt.playlist(url) }.onSuccess { (title, videos) ->
                    playlistTracks = videos
                    _state.update { it.copy(playlistTitle = title) }
                }
            }
        }
    }

    private fun saveQueue() {
        val s = _state.value
        val a = JSONArray()
        (listOfNotNull(s.current) + s.queue).forEach { a.put(it.toStoreJson()) }
        prefs.edit().putString("queue", a.toString()).apply()
    }

    // ---------------------------------------------------------------- guests & access

    fun guestByToken(token: String?): Guest? {
        if (token.isNullOrEmpty()) return null
        if (token == hostGuest.token) return hostGuest
        return _state.value.guests.firstOrNull { it.token == token }
    }

    fun guestById(id: String): Guest? =
        if (id == HOST_ID) hostGuest else _state.value.guests.firstOrNull { it.id == id }

    /** Anyone who reaches the page and gives a name is a guest; the host can close the door with [setJoinOpen]. */
    fun join(name: String): Guest {
        val s = _state.value
        if (!s.joinOpen) throw PartyException("Dołączanie jest zamknięte")
        val clean = name.trim().replace(Regex("\\s+"), " ").take(24)
        if (clean.isEmpty()) throw PartyException("Podaj imię")
        val g = Guest(randomToken(8), clean, Role.GUEST, randomToken(32))
        _state.update { it.copy(guests = it.guests + g) }
        persist()
        return g
    }

    fun setOnline(id: String, online: Boolean) {
        synchronized(onlineCounts) {
            val n = (onlineCounts[id] ?: 0) + if (online) 1 else -1
            if (n <= 0) onlineCounts.remove(id) else onlineCounts[id] = n
            _state.update { it.copy(online = onlineCounts.keys.toSet()) }
        }
    }

    fun requestHost(id: String) = updateGuest(id) { if (it.role == Role.GUEST) it.copy(hostRequested = true) else it }
    fun approveHost(id: String) = updateGuest(id) { it.copy(role = Role.HOST, hostRequested = false) }
    fun rejectHost(id: String) = updateGuest(id) { it.copy(hostRequested = false) }
    fun demote(id: String) = updateGuest(id) { it.copy(role = Role.GUEST, hostRequested = false) }

    fun kick(id: String) {
        _state.update { s ->
            s.copy(guests = s.guests.filterNot { it.id == id }, proposals = s.proposals.filterNot { it.byId == id })
        }
        persist()
    }

    fun setJoinOpen(open: Boolean) {
        _state.update { it.copy(joinOpen = open) }
        persist()
    }

    private fun updateGuest(id: String, f: (Guest) -> Guest) {
        _state.update { s -> s.copy(guests = s.guests.map { if (it.id == id) f(it) else it }) }
        persist()
    }

    // ---------------------------------------------------------------- queue & proposals

    /** Guests create proposals; hosts enqueue directly. */
    suspend fun propose(videoId: String, by: Guest) {
        val meta = yt.meta(videoId)
        if (by.role == Role.HOST) {
            enqueue(meta, by.name, Source.HOST, atEnd = true)
            return
        }
        val s = _state.value
        if (s.proposals.count { it.byId == by.id } >= MAX_PENDING_PER_GUEST) {
            throw PartyException("Masz już $MAX_PENDING_PER_GUEST propozycji czekających na akceptację")
        }
        if (s.proposals.any { it.byId == by.id && it.meta.videoId == videoId }) {
            throw PartyException("Już to zaproponowałeś(-aś)")
        }
        val p = Proposal(randomToken(8), meta, by.id, by.name, System.currentTimeMillis())
        _state.update { it.copy(proposals = it.proposals + p) }
    }

    fun approve(proposalId: String) {
        val p = _state.value.proposals.firstOrNull { it.id == proposalId } ?: return
        _state.update { it.copy(proposals = it.proposals.filterNot { x -> x.id == proposalId }) }
        enqueue(p.meta, p.byName, Source.GUEST)
    }

    fun reject(proposalId: String) {
        _state.update { it.copy(proposals = it.proposals.filterNot { x -> x.id == proposalId }) }
    }

    /**
     * Adds a song. [playNext] puts it first, [atEnd] last (what the host's "Dodaj" does); by default (approved
     * proposals) it goes behind the other requested songs but before the rest of the playlist.
     */
    fun enqueue(meta: Meta, by: String, source: Source, playNext: Boolean = false, atEnd: Boolean = false) {
        val t = Track(randomToken(8), meta.videoId, meta.title, meta.channel, meta.duration, by, source, priority = true)
        _state.update { s ->
            val q = s.queue.toMutableList()
            val idx = when {
                playNext -> 0
                atEnd -> q.size
                else -> q.indexOfFirst { !it.priority }.let { if (it < 0) q.size else it }
            }
            q.add(idx, t)
            s.copy(queue = q)
        }
        if (_state.value.current == null) next()
    }

    fun remove(uid: String) {
        _state.update { s -> s.copy(queue = s.queue.filterNot { it.uid == uid }) }
    }

    fun move(uid: String, delta: Int) {
        _state.update { s ->
            val q = s.queue.toMutableList()
            val i = q.indexOfFirst { it.uid == uid }
            val j = (i + delta).coerceIn(0, (q.size - 1).coerceAtLeast(0))
            if (i < 0 || i == j) return@update s
            val t = q.removeAt(i)
            q.add(j, t)
            s.copy(queue = q)
        }
    }

    /** Puts a song at the very top of the queue (it stays there even when more songs are approved). */
    fun moveToFront(uid: String) {
        _state.update { s ->
            val t = s.queue.firstOrNull { it.uid == uid } ?: return@update s
            // Marked as a priority song so songs approved later line up behind it instead of jumping ahead.
            s.copy(queue = listOf(t.copy(priority = true)) + s.queue.filterNot { it.uid == uid })
        }
    }

    fun playNow(uid: String) {
        val t = _state.value.queue.firstOrNull { it.uid == uid } ?: return
        _state.update { s -> s.copy(queue = s.queue.filterNot { it.uid == uid }) }
        startTrack(t, pushCurrent = true)
    }

    suspend fun loadPlaylist(url: String, shuffleIt: Boolean) {
        val (title, videos) = yt.playlist(url)
        applyPlaylist(title, videos, url, shuffleIt)
    }

    suspend fun loadWatchPlaylist(videoId: String, listId: String, shuffleIt: Boolean) {
        val (title, videos) = yt.watchPlaylist(videoId, listId)
        applyPlaylist(title, videos, "https://m.youtube.com/watch?v=$videoId&list=$listId", shuffleIt)
    }

    /** Import from a playlist page the host's own browser loaded (also works for private lists after signing in). */
    fun importPlaylistData(url: String, data: String, shuffleIt: Boolean) {
        val json = try { JSONObject(data) } catch (e: Exception) { throw PartyException("Nie udało się odczytać playlisty ze strony") }
        // A watch page (Mix, or a playlist opened from a video) lists its songs in the "up next" panel.
        val videos = YouTubeParser.extractPlaylistPanel(json).ifEmpty { YouTubeParser.extractVideos(json) }
        if (videos.isEmpty()) throw PartyException("Na tej stronie nie ma żadnych utworów do zaimportowania")
        applyPlaylist(YouTubeParser.playlistTitle(json) ?: "Playlista", videos, url, shuffleIt)
    }

    private fun applyPlaylist(title: String, videos: List<Meta>, url: String, shuffleIt: Boolean) {
        playlistTracks = videos
        shuffle = shuffleIt
        val tracks = playlistOrder().map {
            Track(randomToken(8), it.videoId, it.title, it.channel, it.duration, "playlista", Source.PLAYLIST, priority = false)
        }
        _state.update { s ->
            s.copy(queue = s.queue.filter { it.priority } + tracks, playlistTitle = title, playlistUrl = url.trim(), notice = null)
        }
        persist()
        if (_state.value.current == null) next()
    }

    /** Empties the queue and forgets the playlist, so "repeat" cannot refill it. The song that is playing carries on. */
    fun clearQueue() {
        playlistTracks = emptyList()
        _state.update { it.copy(queue = emptyList(), playlistTitle = null, playlistUrl = "") }
        persist()
    }

    fun setRepeat(on: Boolean) {
        _state.update { it.copy(repeat = on) }
        persist()
    }

    private fun playlistOrder(): List<Meta> = if (shuffle) playlistTracks.shuffled() else playlistTracks

    // ---------------------------------------------------------------- playback

    /** The host (or a co-host) pressed "next": the old song fades away under the new one. */
    fun skip() = next(fade = true)

    fun next(fade: Boolean = false) {
        val audible = fade && _state.value.player.status == "playing"
        var started: Track? = null
        _state.update { s ->
            var q = s.queue
            if (q.isEmpty() && s.repeat && playlistTracks.isNotEmpty()) {
                q = playlistOrder().map {
                    Track(randomToken(8), it.videoId, it.title, it.channel, it.duration, "playlista", Source.PLAYLIST, false)
                }
            }
            started = q.firstOrNull()
            s.copy(
                history = (listOfNotNull(s.current) + s.history).take(HISTORY_MAX),
                current = started,
                queue = q.drop(1),
                player = PlayerInfo(status = if (started != null) "loading" else "idle"),
            )
        }
        val t = started
        if (t != null) launchTrack(t, audible) else {
            watchdog?.cancel()
            player?.command("stop")
        }
    }

    fun previous() {
        val prev = _state.value.history.firstOrNull() ?: return
        val audible = _state.value.player.status == "playing"
        _state.update { s ->
            s.copy(
                history = s.history.drop(1),
                queue = listOfNotNull(s.current) + s.queue,
                current = prev,
                player = PlayerInfo(status = "loading"),
            )
        }
        launchTrack(prev, audible)
    }

    private fun startTrack(t: Track, pushCurrent: Boolean) {
        val audible = pushCurrent && _state.value.player.status == "playing"
        _state.update { s ->
            s.copy(
                history = if (pushCurrent) (listOfNotNull(s.current) + s.history).take(HISTORY_MAX) else s.history,
                current = t,
                player = PlayerInfo(status = "loading"),
            )
        }
        launchTrack(t, audible)
    }

    private fun launchTrack(t: Track, fade: Boolean = false) {
        player?.load(t.videoId, if (fade) FADE_MS else 0)
        watchdog?.cancel()
        watchdog = scope.launch {
            delay(LOAD_TIMEOUT_MS)
            if (_state.value.current?.uid == t.uid && _state.value.player.status != "playing") {
                _state.update { it.copy(notice = "Pominięto: nie udało się odtworzyć „${t.title}”") }
                next()
            }
        }
    }

    fun togglePlay() {
        if (_state.value.current == null) {
            next()
            return
        }
        player?.command(if (_state.value.player.status == "playing") "pause" else "play")
    }

    fun seek(ms: Long) = player?.command("seek", ms.toDouble())

    fun currentVideoId(): String? = _state.value.current?.videoId

    fun dismissNotice() = _state.update { it.copy(notice = null) }

    /** Called by the player bridge for every report from the YouTube page. */
    fun onPlayerMessage(m: JSONObject) {
        val cur = _state.value.current ?: return
        val vid = m.optString("videoId")
        if (vid.isNotEmpty() && vid != cur.videoId) return
        when (m.optString("type")) {
            "error" -> scope.launch {
                delay(1500)
                if (_state.value.current?.uid == cur.uid) {
                    _state.update { it.copy(notice = "Pominięto: błąd odtwarzania „${cur.title}”") }
                    next()
                }
            }
            "state" -> {
                val st = m.optString("state")
                val ad = m.optBoolean("ad")
                val status = when {
                    ad -> "ad"
                    st == "playing" -> "playing"
                    st == "paused" -> "paused"
                    st == "ended" -> "paused"
                    else -> "loading"
                }
                _state.update {
                    it.copy(player = PlayerInfo(status, m.optLong("pos"), m.optLong("dur")))
                }
                if (st == "playing" && !ad) watchdog?.cancel()
                if (st == "ended" && !ad) next()
            }
        }
    }

    // ---------------------------------------------------------------- serialization

    /** State as seen by one participant: guests get a reduced view. */
    fun jsonFor(viewerId: String): String {
        val s = _state.value
        val me = guestById(viewerId)
        val isHost = me?.role == Role.HOST
        val o = JSONObject()
        o.put("me", JSONObject().put("id", me?.id ?: "").put("name", me?.name ?: "").put("role", me?.role?.name ?: "")
            .put("hostRequested", me?.hostRequested ?: false))
        o.put("joinOpen", s.joinOpen)
        o.put("now", JSONObject()
            .put("track", s.current?.toJson() ?: JSONObject.NULL)
            .put("status", s.player.status).put("pos", s.player.posMs).put("dur", s.player.durMs))
        o.put("queue", s.queue.toJsonArray { it.toJson() })
        o.put("playlist", s.playlistTitle ?: JSONObject.NULL)
        o.put("repeat", s.repeat)
        o.put("notice", s.notice ?: JSONObject.NULL)
        val visibleProposals = if (isHost) s.proposals else s.proposals.filter { it.byId == viewerId }
        o.put("proposals", visibleProposals.toJsonArray { it.toJson() })
        if (isHost) {
            o.put("guests", s.guests.toJsonArray { it.toJson(it.id in s.online) })
        } else {
            o.put("guests", JSONArray())
        }
        return o.toString()
    }

    private fun persist() {
        val s = _state.value
        val o = JSONObject()
            .put("hostToken", hostGuest.token)
            .put("joinOpen", s.joinOpen)
            .put("repeat", s.repeat)
            .put("playlistUrl", s.playlistUrl)
            .put("guests", s.guests.toJsonArray {
                JSONObject().put("id", it.id).put("name", it.name).put("role", it.role.name).put("token", it.token)
            })
        prefs.edit().putString("data", o.toString()).apply()
    }

    companion object {
        const val HOST_ID = "host"
        private const val MAX_PENDING_PER_GUEST = 5
        private const val HISTORY_MAX = 50
        // Generous: with the screen off a page can take a while to start, and skipping a song that is about to play is worse.
        private const val LOAD_TIMEOUT_MS = 45_000L

        /** How long the old song takes to fade out when a song is changed by hand. */
        const val FADE_MS = 2_000
    }
}
