package pl.mleczki.partyqueue

import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom

enum class Role { GUEST, HOST }

enum class Source { PLAYLIST, GUEST, HOST }

data class Meta(
    val videoId: String,
    val title: String,
    val channel: String = "",
    val duration: String? = null,
)

data class Track(
    val uid: String,
    val videoId: String,
    val title: String,
    val channel: String,
    val duration: String?,
    val addedBy: String,
    val source: Source,
    /** Tracks approved by hosts jump ahead of the bulk playlist, in FIFO order. */
    val priority: Boolean,
)

data class Proposal(
    val id: String,
    val meta: Meta,
    val byId: String,
    val byName: String,
    val at: Long,
)

data class Guest(
    val id: String,
    val name: String,
    val role: Role,
    val token: String,
    val hostRequested: Boolean = false,
)

data class PlayerInfo(
    val status: String = "idle", // idle | loading | playing | paused | ad
    val posMs: Long = 0,
    val durMs: Long = 0,
)

data class Snapshot(
    val current: Track? = null,
    val queue: List<Track> = emptyList(),
    val history: List<Track> = emptyList(),
    val proposals: List<Proposal> = emptyList(),
    val guests: List<Guest> = emptyList(),
    val online: Set<String> = emptySet(),
    val player: PlayerInfo = PlayerInfo(),
    val joinOpen: Boolean = true,
    val playlistTitle: String? = null,
    val playlistUrl: String = "",
    val repeat: Boolean = true,
    val notice: String? = null,
    /** The phone's media volume in percent; -1 when unknown. */
    val volume: Int = -1,
)

private val rng = SecureRandom()
private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"

fun randomToken(len: Int): String =
    buildString { repeat(len) { append(ALPHABET[rng.nextInt(ALPHABET.length)]) } }

fun Track.thumb() = "https://i.ytimg.com/vi/$videoId/mqdefault.jpg"

fun Meta.toJson(): JSONObject = JSONObject()
    .put("vid", videoId).put("title", title).put("channel", channel).put("dur", duration ?: "")
    .put("thumb", "https://i.ytimg.com/vi/$videoId/mqdefault.jpg")

fun Track.toJson(): JSONObject = JSONObject()
    .put("uid", uid).put("vid", videoId).put("title", title).put("channel", channel)
    .put("dur", duration ?: "").put("by", addedBy).put("src", source.name).put("thumb", thumb())

fun Proposal.toJson(): JSONObject = meta.toJson()
    .put("id", id).put("byId", byId).put("by", byName)

fun Guest.toJson(online: Boolean): JSONObject = JSONObject()
    .put("id", id).put("name", name).put("role", role.name)
    .put("hostRequested", hostRequested).put("online", online)

fun <T> List<T>.toJsonArray(f: (T) -> JSONObject): JSONArray = JSONArray().also { a -> forEach { a.put(f(it)) } }

fun Track.toStoreJson(): JSONObject = JSONObject()
    .put("uid", uid).put("vid", videoId).put("title", title).put("channel", channel)
    .put("dur", duration ?: JSONObject.NULL).put("by", addedBy).put("src", source.name).put("priority", priority)

fun trackFromStoreJson(o: JSONObject): Track = Track(
    uid = o.getString("uid"),
    videoId = o.getString("vid"),
    title = o.getString("title"),
    channel = o.optString("channel"),
    duration = if (o.isNull("dur")) null else o.getString("dur"),
    addedBy = o.optString("by"),
    source = Source.valueOf(o.getString("src")),
    priority = o.optBoolean("priority"),
)
