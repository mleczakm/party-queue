package pl.mleczki.partyqueue

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Pure parsing of the JSON YouTube embeds in its pages. Kept free of I/O so it can be unit tested. */
object YouTubeParser {

    private val ID_RE = Regex("^[A-Za-z0-9_-]{11}$")

    fun initialData(html: String): JSONObject {
        val marker = html.indexOf("var ytInitialData")
        if (marker < 0) throw IllegalStateException("Nieoczekiwana odpowiedź YouTube")
        val start = html.indexOf('{', marker)
        return JSONObject(JSONTokener(html.substring(start)))
    }

    fun playlistTitle(data: JSONObject): String? {
        val sb = ArrayList<String>()
        walk(data) { key, v ->
            if (v is JSONObject) {
                if (key == "playlistMetadataRenderer") v.optString("title").takeIf { it.isNotBlank() }?.let(sb::add)
                if (key == "pageHeaderViewModel") (v.path("title", "dynamicTextViewModel", "text", "content") as? String)?.let(sb::add)
            }
        }
        return sb.firstOrNull()
    }

    fun extractVideos(data: JSONObject): List<Meta> {
        val out = LinkedHashMap<String, Meta>()
        walk(data) { key, v ->
            if (v !is JSONObject) return@walk
            val m = when (key) {
                "videoRenderer", "playlistVideoRenderer" -> fromRenderer(v)
                "lockupViewModel" -> fromLockup(v)
                else -> null
            }
            if (m != null && m.videoId !in out) out[m.videoId] = m
        }
        return out.values.toList()
    }

    private fun fromRenderer(v: JSONObject): Meta? {
        val id = v.optString("videoId").takeIf { ID_RE.matches(it) } ?: return null
        if (v.has("isPlayable") && !v.optBoolean("isPlayable", true)) return null
        val title = (v.path("title", "runs", 0, "text") ?: v.path("title", "simpleText")) as? String ?: return null
        val channel = (v.path("ownerText", "runs", 0, "text") ?: v.path("shortBylineText", "runs", 0, "text")) as? String ?: ""
        val dur = v.path("lengthText", "simpleText") as? String
        return Meta(id, title, channel, dur)
    }

    private fun fromLockup(v: JSONObject): Meta? {
        if (v.optString("contentType") != "LOCKUP_CONTENT_TYPE_VIDEO") return null
        val id = v.optString("contentId").takeIf { ID_RE.matches(it) } ?: return null
        val md = v.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel") ?: return null
        val title = md.path("title", "content") as? String ?: return null
        val channel = md.path("metadata", "contentMetadataViewModel", "metadataRows", 0, "metadataParts", 0, "text", "content") as? String ?: ""
        var dur: String? = null
        val overlays = v.path("contentImage", "thumbnailViewModel", "overlays") as? JSONArray
        if (overlays != null) for (i in 0 until overlays.length()) {
            dur = overlays.optJSONObject(i)?.path("thumbnailBottomOverlayViewModel", "badges", 0, "thumbnailBadgeViewModel", "text") as? String
            if (dur != null) break
        }
        return Meta(id, title, channel, dur)
    }

    private fun walk(node: Any?, visit: (String, Any?) -> Unit) {
        when (node) {
            is JSONObject -> for (k in node.keys()) {
                val child = node.opt(k)
                visit(k, child)
                walk(child, visit)
            }
            is JSONArray -> for (i in 0 until node.length()) walk(node.opt(i), visit)
        }
    }

    private fun JSONObject.path(vararg keys: Any): Any? {
        var cur: Any? = this
        for (k in keys) {
            cur = when {
                k is String && cur is JSONObject -> cur.opt(k)
                k is Int && cur is JSONArray -> cur.opt(k)
                else -> return null
            }
        }
        return cur
    }

}
