package pl.mleczki.partyqueue

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PartyServerTest {

    private val source = FakeSource().also { s -> (1..3).forEach { s.add(meta(it)) } }
    private val party = PartyController(FakePrefs(), source, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        .also { it.player = FakePlayer() }

    private fun server(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application { partyModule(party, "<html>guest page</html>") }
        block()
    }

    private suspend fun ApplicationTestBuilder.call(path: String, token: String?, body: String? = null) =
        if (body == null && path.startsWith("/api/state") || path.startsWith("/api/search")) {
            client.get(path) { token?.let { header("Authorization", "Bearer $it") } }
        } else {
            client.post(path) {
                token?.let { header("Authorization", "Bearer $it") }
                setBody(body ?: "{}")
            }
        }

    private suspend fun ApplicationTestBuilder.join(name: String = "Ola"): JSONObject {
        val r = client.post("/api/join") { setBody("""{"name":"$name","secret":"${party.state.value.joinSecret}"}""") }
        assertEquals(HttpStatusCode.OK, r.status)
        return JSONObject(r.bodyAsText())
    }

    @Test
    fun `serves the guest page and the idle page without authentication`() = server {
        assertEquals("<html>guest page</html>", client.get("/").bodyAsText())
        assertTrue(client.get("/idle").bodyAsText().contains("Party Queue"))
    }

    @Test
    fun `api calls need a valid token`() = server {
        assertEquals(HttpStatusCode.Unauthorized, call("/api/state", null).status)
        assertEquals(HttpStatusCode.Unauthorized, call("/api/state", "bogus").status)
        assertEquals(HttpStatusCode.Unauthorized, call("/api/propose", "bogus", """{"videoId":"${meta(1).videoId}"}""").status)
    }

    @Test
    fun `joining needs the right secret and closes on demand`() = server {
        val bad = client.post("/api/join") { setBody("""{"name":"Ola","secret":"nope"}""") }
        assertEquals(HttpStatusCode.Forbidden, bad.status)
        party.setJoinOpen(false)
        val closed = client.post("/api/join") { setBody("""{"name":"Ola","secret":"${party.state.value.joinSecret}"}""") }
        assertEquals(HttpStatusCode.Forbidden, closed.status)
        party.setJoinOpen(true)
        val token = join()["token"] as String
        assertEquals(HttpStatusCode.OK, call("/api/state", token).status)
    }

    @Test
    fun `guests cannot use host endpoints but the host can`() = server {
        val guest = join()["token"] as String
        listOf("/api/player/next", "/api/proposals/x/approve", "/api/queue/x/remove", "/api/guests/x/kick", "/api/playlist").forEach {
            assertEquals(it, HttpStatusCode.Forbidden, call(it, guest).status)
        }
        assertEquals(HttpStatusCode.OK, call("/api/player/next", party.hostGuest.token).status)
    }

    @Test
    fun `a proposal travels from guest to queue through the host`() = server {
        val guest = join()
        val token = guest["token"] as String
        val vid = meta(1).videoId
        assertEquals(HttpStatusCode.OK, call("/api/propose", token, """{"videoId":"$vid"}""").status)

        val hostView = JSONObject(call("/api/state", party.hostGuest.token).bodyAsText())
        val proposalId = hostView.getJSONArray("proposals").getJSONObject(0).getString("id")
        assertEquals(HttpStatusCode.OK, call("/api/proposals/$proposalId/approve", party.hostGuest.token).status)

        val after = JSONObject(call("/api/state", token).bodyAsText())
        assertEquals(vid, after.getJSONObject("now").getJSONObject("track").getString("vid")) // idle player starts it
        assertEquals(0, after.getJSONArray("proposals").length())
    }

    @Test
    fun `proposals accept links and reject nonsense`() = server {
        val token = join()["token"] as String
        val link = "https://www.youtube.com/watch?v=${meta(2).videoId}"
        assertEquals(HttpStatusCode.OK, call("/api/propose", token, """{"videoId":"$link"}""").status)
        assertEquals(HttpStatusCode.BadRequest, call("/api/propose", token, """{"videoId":"hello"}""").status)
        assertEquals(HttpStatusCode.BadRequest, call("/api/propose", token, "not json").status)
    }

    @Test
    fun `host rights can be requested, granted and used`() = server {
        val guest = join()
        val token = guest["token"] as String
        assertEquals(HttpStatusCode.Forbidden, call("/api/player/next", token).status)
        call("/api/hostrequest", token)
        assertEquals(HttpStatusCode.OK, call("/api/guests/${guest["id"]}/approve", party.hostGuest.token).status)
        assertEquals(HttpStatusCode.OK, call("/api/player/next", token).status)
        // a co-host can approve further co-hosts
        val other = join("Kuba")
        call("/api/hostrequest", other["token"] as String)
        assertEquals(HttpStatusCode.OK, call("/api/guests/${other["id"]}/approve", token).status)
        assertEquals(HttpStatusCode.OK, call("/api/player/next", other["token"] as String).status)
    }

    @Test
    fun `a kicked guest loses access immediately`() = server {
        val guest = join()
        val token = guest["token"] as String
        assertEquals(HttpStatusCode.OK, call("/api/state", token).status)
        call("/api/guests/${guest["id"]}/kick", party.hostGuest.token)
        assertEquals(HttpStatusCode.Unauthorized, call("/api/state", token).status)
    }

    @Test
    fun `search finds known songs and accepts a pasted link`() = server {
        val token = join()["token"] as String
        val byText = JSONObject(call("/api/search?q=song+1".replace('+', ' ').replace(" ", "%20"), token).bodyAsText())
        assertEquals(1, byText.getJSONArray("results").length())
        val byLink = JSONObject(call("/api/search?q=https://youtu.be/${meta(3).videoId}", token).bodyAsText())
        assertEquals(meta(3).videoId, byLink.getJSONArray("results").getJSONObject(0).getString("vid"))
    }
}
