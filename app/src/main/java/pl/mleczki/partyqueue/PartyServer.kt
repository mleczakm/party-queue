package pl.mleczki.partyqueue

import android.content.Context
import android.util.Log
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.header
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.time.Duration.Companion.seconds

private fun tokenOf(call: ApplicationCall): String? =
    call.request.header("Authorization")?.removePrefix("Bearer ")?.trim()

private suspend fun respondJson(call: ApplicationCall, status: HttpStatusCode, body: Any?) {
    call.response.header("Cache-Control", "no-store")
    call.respondText(body?.toString() ?: "{}", ContentType.Application.Json, status)
}

/** Authenticates, parses the JSON body and maps domain errors to 4xx answers. */
private suspend fun handle(party: PartyController, call: ApplicationCall, hostOnly: Boolean = false, block: suspend (Guest, JSONObject) -> Any?) {
    val user = party.guestByToken(tokenOf(call))
    if (user == null) {
        return respondJson(call, HttpStatusCode.Unauthorized, JSONObject().put("error", "Zaloguj się ponownie"))
    }
    if (hostOnly && user.role != Role.HOST) {
        return respondJson(call, HttpStatusCode.Forbidden, JSONObject().put("error", "Wymagane uprawnienia hosta"))
    }
    try {
        val body = runCatching { JSONObject(call.receiveText().ifBlank { "{}" }) }.getOrDefault(JSONObject())
        val result = block(user, body)
        respondJson(call, HttpStatusCode.OK, result ?: JSONObject().put("ok", true))
    } catch (e: PartyException) {
        respondJson(call, HttpStatusCode.BadRequest, JSONObject().put("error", e.message))
    } catch (e: IllegalArgumentException) {
        respondJson(call, HttpStatusCode.BadRequest, JSONObject().put("error", e.message ?: "Błędne dane"))
    } catch (e: Exception) {
        Log.w("PartyQueue", "request failed", e)
        respondJson(call, HttpStatusCode.InternalServerError, JSONObject().put("error", e.message ?: "Błąd serwera"))
    }
}


/** The whole guest/co-host API. Separate from [PartyServer] so tests can run it without Android. */
fun Application.partyModule(party: PartyController, indexHtml: String) {
    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 30.seconds
    }
    routing {
        // Shown in the host's player pane while nothing is playing.
        get("/idle") {
            call.respondText(PartyServer.IDLE_HTML, ContentType.Text.Html)
        }

        get("/") {
            call.response.header("Cache-Control", "no-store")
            call.respondText(indexHtml, ContentType.Text.Html)
        }

        post("/api/join") {
            try {
                val body = JSONObject(call.receiveText().ifBlank { "{}" })
                val g = party.join(body.optString("name"), body.optString("secret"))
                respondJson(call, HttpStatusCode.OK, JSONObject().put("token", g.token).put("id", g.id))
            } catch (e: PartyException) {
                respondJson(call, HttpStatusCode.Forbidden, JSONObject().put("error", e.message))
            }
        }

        get("/api/state") {
            val user = party.guestByToken(tokenOf(call))
            if (user == null) respondJson(call, HttpStatusCode.Unauthorized, JSONObject().put("error", "Zaloguj się ponownie"))
            else respondJson(call, HttpStatusCode.OK, party.jsonFor(user.id))
        }

        get("/api/search") {
            val q = call.request.queryParameters["q"].orEmpty()
            handle(party, call) { _, _ ->
                if (q.isBlank()) JSONArray() else {
                    val videoId = YouTubeClient.parseVideoId(q)
                    val list = if (videoId != null) listOf(party.yt.meta(videoId)) else party.yt.search(q)
                    JSONObject().put("results", list.toJsonArray { it.toJson() })
                }
            }
        }

        post("/api/propose") {
            handle(party, call) { user, body ->
                val id = YouTubeClient.parseVideoId(body.optString("videoId"))
                    ?: throw PartyException("To nie wygląda na film z YouTube")
                if (user.role == Role.HOST && body.optBoolean("next")) {
                    party.enqueue(party.yt.meta(id), user.name, Source.HOST, playNext = true)
                } else {
                    party.propose(id, user)
                }
                null
            }
        }

        post("/api/hostrequest") {
            handle(party, call) { user, _ -> party.requestHost(user.id); null }
        }

        // ---- host-only
        post("/api/proposals/{id}/{action}") {
            handle(party, call, hostOnly = true) { _, _ ->
                val id = call.parameters["id"].orEmpty()
                when (call.parameters["action"]) {
                    "approve" -> party.approve(id)
                    "reject" -> party.reject(id)
                    else -> throw PartyException("Nieznana akcja")
                }
                null
            }
        }

        post("/api/queue/{uid}/{action}") {
            handle(party, call, hostOnly = true) { _, _ ->
                val uid = call.parameters["uid"].orEmpty()
                when (call.parameters["action"]) {
                    "remove" -> party.remove(uid)
                    "up" -> party.move(uid, -1)
                    "down" -> party.move(uid, 1)
                    "playnow" -> party.playNow(uid)
                    else -> throw PartyException("Nieznana akcja")
                }
                null
            }
        }

        post("/api/player/{cmd}") {
            handle(party, call, hostOnly = true) { _, body ->
                when (call.parameters["cmd"]) {
                    "toggle" -> party.togglePlay()
                    "next" -> party.next()
                    "prev" -> party.previous()
                    "seek" -> party.seek(body.optLong("ms"))
                    else -> throw PartyException("Nieznana komenda")
                }
                null
            }
        }

        post("/api/playlist") {
            handle(party, call, hostOnly = true) { _, body ->
                party.loadPlaylist(body.optString("url"), body.optBoolean("shuffle"))
                null
            }
        }

        post("/api/guests/{id}/{action}") {
            handle(party, call, hostOnly = true) { _, _ ->
                val id = call.parameters["id"].orEmpty()
                when (call.parameters["action"]) {
                    "approve" -> party.approveHost(id)
                    "reject" -> party.rejectHost(id)
                    "demote" -> party.demote(id)
                    "kick" -> party.kick(id)
                    else -> throw PartyException("Nieznana akcja")
                }
                null
            }
        }

        webSocket("/ws") {
            val user = party.guestByToken(call.request.queryParameters["token"])
            if (user == null) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "unauthorized"))
                return@webSocket
            }
            party.setOnline(user.id, true)
            val pusher = launch {
                party.state.collect { send(Frame.Text(party.jsonFor(user.id))) }
            }
            try {
                for (frame in incoming) { /* clients only listen */ }
            } finally {
                pusher.cancel()
                party.setOnline(user.id, false)
            }
        }
    }
}


/** Runs the guest-facing server inside the app. All routing lives in [partyModule]. */
class PartyServer(
    context: Context,
    private val party: PartyController,
    val port: Int = PORT,
) {
    private val indexHtml = context.assets.open("guest/index.html").bufferedReader().use { it.readText() }
    private var engine: io.ktor.server.engine.EmbeddedServer<*, *>? = null

    fun start() {
        engine = embeddedServer(CIO, port = port, host = "0.0.0.0") { partyModule(party, indexHtml) }.start(wait = false)
        Log.i("PartyQueue", "server listening on :$port")
    }

    fun stop() {
        engine?.stop(300, 1000)
    }

    companion object {
        const val PORT = 8080

        internal val IDLE_HTML = """<!doctype html><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <body style="margin:0;height:100vh;display:flex;flex-direction:column;align-items:center;justify-content:center;
            background:#14151a;color:#9aa0b4;font-family:system-ui,sans-serif;text-align:center">
            <div style="font-size:min(22px,26vh);color:#eceef4">Party Queue</div>
            <div style="font-size:min(16px,19vh)">Wczytaj playlistę albo dodaj utwór.</div></body>"""
    }
}
