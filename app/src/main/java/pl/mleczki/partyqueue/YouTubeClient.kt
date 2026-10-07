package pl.mleczki.partyqueue

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * Key-less access to public YouTube data by reading the same pages a browser would.
 * The page structure changes now and then, so every parser is defensive.
 */
/** What the rest of the app needs from YouTube; faked in tests. */
interface MetaSource {
    suspend fun search(query: String): List<Meta>
    suspend fun playlist(urlOrId: String): Pair<String, List<Meta>>
    suspend fun meta(videoId: String): Meta
}

class YouTubeClient : MetaSource {

    private val cache = ConcurrentHashMap<String, Meta>()
    private val searchCache = ConcurrentHashMap<String, Pair<Long, List<Meta>>>()

    fun remember(m: Meta) {
        cache[m.videoId] = m
    }

    override suspend fun search(query: String): List<Meta> = withContext(Dispatchers.IO) {
        val q = query.trim().take(100)
        searchCache[q]?.let { (at, list) -> if (System.currentTimeMillis() - at < 10 * 60_000) return@withContext list }
        val html = http("https://www.youtube.com/results?search_query=${URLEncoder.encode(q, "UTF-8")}")
        val result = YouTubeParser.extractVideos(YouTubeParser.initialData(html)).take(20)
        result.forEach(::remember)
        searchCache[q] = System.currentTimeMillis() to result
        result
    }

    override suspend fun playlist(urlOrId: String): Pair<String, List<Meta>> = withContext(Dispatchers.IO) {
        val id = parsePlaylistId(urlOrId) ?: throw IllegalArgumentException("To nie wygląda na playlistę YouTube")
        val html = http("https://www.youtube.com/playlist?list=$id")
        val data = YouTubeParser.initialData(html)
        val videos = YouTubeParser.extractVideos(data)
        if (videos.isEmpty()) throw IllegalStateException("Playlista jest pusta, prywatna albo niedostępna")
        videos.forEach(::remember)
        val title = YouTubeParser.playlistTitle(data)
            ?: Regex("<title>(.*?)</title>").find(html)?.groupValues?.get(1)?.removeSuffix(" - YouTube")
            ?: "Playlista"
        title to videos
    }

    override suspend fun meta(videoId: String): Meta {
        cache[videoId]?.let { return it }
        return withContext(Dispatchers.IO) {
            val m = try {
                val url = URLEncoder.encode("https://www.youtube.com/watch?v=$videoId", "UTF-8")
                val o = JSONObject(http("https://www.youtube.com/oembed?format=json&url=$url"))
                Meta(videoId, o.optString("title", videoId), o.optString("author_name"))
            } catch (_: Exception) {
                try {
                    val html = http("https://www.youtube.com/watch?v=$videoId")
                    val title = Regex("<title>(.*?)</title>").find(html)?.groupValues?.get(1)
                        ?.removeSuffix(" - YouTube")?.let(::unescape)
                    if (title.isNullOrBlank()) throw IllegalStateException()
                    Meta(videoId, title)
                } catch (_: Exception) {
                    throw IllegalArgumentException("Nie znaleziono filmu")
                }
            }
            remember(m)
            m
        }
    }

    private fun http(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        c.setRequestProperty("User-Agent", UA)
        c.setRequestProperty("Accept-Language", "pl-PL,pl;q=0.9,en;q=0.8")
        c.setRequestProperty("Cookie", "SOCS=CAI; CONSENT=YES+cb")
        try {
            if (c.responseCode !in 200..299) throw IllegalStateException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun unescape(s: String) = s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
        .replace("&lt;", "<").replace("&gt;", ">")

    companion object {
        private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36"
        private val ID_RE = Regex("^[A-Za-z0-9_-]{11}$")
        private val PLAYLIST_RE = Regex("^[A-Za-z0-9_-]{10,64}$")

        /** Accepts a bare id or any common YouTube link form. */
        fun parseVideoId(input: String): String? {
            val s = input.trim()
            if (ID_RE.matches(s)) return s
            val m = Regex("""(?:v=|youtu\.be/|/shorts/|/embed/|/live/)([A-Za-z0-9_-]{11})""").find(s)
            return m?.groupValues?.get(1)
        }

        fun parsePlaylistId(input: String): String? {
            val s = input.trim()
            Regex("""[?&]list=([A-Za-z0-9_-]+)""").find(s)?.let { return it.groupValues[1] }
            return s.takeIf { PLAYLIST_RE.matches(it) && (it.startsWith("PL") || it.startsWith("UU") || it.startsWith("OLAK") || it.startsWith("FL")) }
        }
    }
}
