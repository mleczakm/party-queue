package pl.mleczki.partyqueue

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PartyControllerTest {

    private class Rig(scope: CoroutineScope, val prefs: FakePrefs = FakePrefs(), playlist: List<Meta> = emptyList(), mix: List<Meta> = emptyList()) {
        val source = FakeSource(playlist, mix).also { s -> playlist.forEach { s.add(it) } }
        val player = FakePlayer()
        val party = PartyController(prefs, source, scope).also { it.player = player }
        val secret get() = party.state.value.joinSecret
        fun guest(name: String = "Ola"): Guest = party.join(name, secret)
        fun state() = party.state.value
    }

    private fun TestScope.rig(playlist: List<Meta> = emptyList(), prefs: FakePrefs = FakePrefs(), mix: List<Meta> = emptyList()) = Rig(backgroundScope, prefs, playlist, mix)

    private fun msg(type: String, vid: String, state: String = "", ad: Boolean = false, pos: Long = 0, dur: Long = 1000) =
        JSONObject().put("type", type).put("videoId", vid).put("state", state).put("ad", ad).put("pos", pos).put("dur", dur)

    // ------------------------------------------------------------ joining

    @Test
    fun `joining needs the current secret and an open party`() = runTest {
        val r = rig()
        try { r.party.join("Ola", "wrong"); fail() } catch (e: PartyException) { /* expected */ }
        r.party.setJoinOpen(false)
        try { r.party.join("Ola", r.secret); fail() } catch (e: PartyException) { /* expected */ }
        r.party.setJoinOpen(true)
        assertEquals("Ola", r.party.join("Ola", r.secret).name)
    }

    @Test
    fun `names are trimmed, limited and must not be blank`() = runTest {
        val r = rig()
        assertEquals("A B", r.party.join("  A    B  ", r.secret).name)
        assertEquals(24, r.party.join("x".repeat(80), r.secret).name.length)
        try { r.party.join("   ", r.secret); fail() } catch (e: PartyException) { /* expected */ }
    }

    @Test
    fun `rotating the secret invalidates the old code but keeps guests`() = runTest {
        val r = rig()
        val g = r.guest()
        val old = r.secret
        r.party.rotateSecret()
        assertFalse(old == r.secret)
        try { r.party.join("Late", old); fail() } catch (e: PartyException) { /* expected */ }
        assertNotNull(r.party.guestByToken(g.token))
    }

    // ------------------------------------------------------------ roles

    @Test
    fun `a guest can become co-host and be demoted again`() = runTest {
        val r = rig()
        val g = r.guest()
        r.party.requestHost(g.id)
        assertTrue(r.state().guests.single().hostRequested)
        r.party.approveHost(g.id)
        assertEquals(Role.HOST, r.party.guestByToken(g.token)!!.role)
        assertFalse(r.state().guests.single().hostRequested)
        r.party.demote(g.id)
        assertEquals(Role.GUEST, r.party.guestByToken(g.token)!!.role)
    }

    @Test
    fun `rejecting a host request keeps the guest a guest`() = runTest {
        val r = rig()
        val g = r.guest()
        r.party.requestHost(g.id)
        r.party.rejectHost(g.id)
        assertEquals(Role.GUEST, r.party.guestByToken(g.token)!!.role)
        assertFalse(r.state().guests.single().hostRequested)
    }

    @Test
    fun `kicking removes the token and pending proposals`() = runTest {
        val r = rig()
        val g = r.guest()
        r.source.add(meta(1))
        r.party.propose(meta(1).videoId, g)
        r.party.kick(g.id)
        assertNull(r.party.guestByToken(g.token))
        assertTrue(r.state().proposals.isEmpty())
    }

    @Test
    fun `the host app has its own token`() = runTest {
        val r = rig()
        assertEquals(Role.HOST, r.party.guestByToken(r.party.hostGuest.token)!!.role)
        assertNull(r.party.guestByToken("nope"))
        assertNull(r.party.guestByToken(null))
    }

    // ------------------------------------------------------------ proposals

    @Test
    fun `guest proposals wait for approval and hosts enqueue directly`() = runTest {
        val r = rig()
        r.source.add(meta(1)); r.source.add(meta(2))
        r.party.propose(meta(1).videoId, r.guest())
        assertEquals(1, r.state().proposals.size)
        assertTrue(r.state().queue.isEmpty())
        r.party.propose(meta(2).videoId, r.party.hostGuest)
        assertEquals(meta(2).videoId, r.state().current?.videoId) // idle player starts at once
    }

    @Test
    fun `a guest has a limit on pending proposals and cannot repeat one`() = runTest {
        val r = rig()
        val g = r.guest()
        (1..6).forEach { r.source.add(meta(it)) }
        r.party.propose(meta(1).videoId, g)
        try { r.party.propose(meta(1).videoId, g); fail("duplicate") } catch (e: PartyException) { /* expected */ }
        (2..5).forEach { r.party.propose(meta(it).videoId, g) }
        try { r.party.propose(meta(6).videoId, g); fail("limit") } catch (e: PartyException) { /* expected */ }
    }

    @Test
    fun `unknown videos are rejected`() = runTest {
        val r = rig()
        try { r.party.propose("v0000000099", r.guest()); fail() } catch (e: IllegalArgumentException) { /* expected */ }
    }

    @Test
    fun `approved songs go ahead of the playlist in first-come order`() = runTest {
        val playlist = (10..14).map(::meta)
        val r = rig(playlist)
        val g = r.guest()
        (1..2).forEach { r.source.add(meta(it)) }
        r.party.loadPlaylist("any", shuffleIt = false) // starts song 10, queue 11..14
        r.party.propose(meta(1).videoId, g); r.party.propose(meta(2).videoId, g)
        val ids = r.state().proposals.map { it.id }
        r.party.approve(ids[0]); r.party.approve(ids[1])
        assertEquals(listOf(1, 2, 11, 12, 13, 14).map { meta(it).videoId }, r.state().queue.map { it.videoId })
        assertEquals(Source.GUEST, r.state().queue.first().source)
    }

    @Test
    fun `rejecting drops the proposal without queueing`() = runTest {
        val r = rig()
        r.source.add(meta(1))
        r.party.propose(meta(1).videoId, r.guest())
        r.party.reject(r.state().proposals.single().id)
        assertTrue(r.state().proposals.isEmpty())
        assertTrue(r.state().queue.isEmpty() && r.state().current == null)
    }

    // ------------------------------------------------------------ queue & playback

    @Test
    fun `play next puts a song at the head of the queue`() = runTest {
        val r = rig((1..3).map(::meta))
        r.party.loadPlaylist("any", false) // current 1, queue 2,3
        r.party.enqueue(meta(9), "Host", Source.HOST, playNext = true)
        assertEquals(listOf(9, 2, 3).map { meta(it).videoId }, r.state().queue.map { it.videoId })
    }

    @Test
    fun `next advances, remembers history and previous comes back`() = runTest {
        val r = rig((1..3).map(::meta))
        r.party.loadPlaylist("any", false)
        assertEquals(listOf(meta(1).videoId), r.player.loads)
        r.party.next()
        assertEquals(meta(2).videoId, r.state().current?.videoId)
        r.party.previous()
        assertEquals(meta(1).videoId, r.state().current?.videoId)
        assertEquals(meta(2).videoId, r.state().queue.first().videoId) // the skipped song is not lost
        assertEquals(listOf(1, 2, 1).map { meta(it).videoId }, r.player.loads)
    }

    @Test
    fun `an empty queue stops the player unless repeat refills it`() = runTest {
        val r = rig(listOf(meta(1), meta(2)))
        r.party.loadPlaylist("any", false)
        r.party.setRepeat(false)
        r.party.next(); r.party.next()
        assertNull(r.state().current)
        assertEquals("idle", r.state().player.status)
        assertTrue(r.player.commands.any { it.first == "stop" })

        val r2 = rig(listOf(meta(1), meta(2)))
        r2.party.loadPlaylist("any", false) // repeat is on by default
        r2.party.next(); r2.party.next()
        assertEquals(meta(1).videoId, r2.state().current?.videoId)
    }

    @Test
    fun `move, remove and play now rearrange the queue`() = runTest {
        val r = rig((1..5).map(::meta))
        r.party.loadPlaylist("any", false) // current 1, queue 2..5
        val uid = { n: Int -> r.state().queue.first { it.videoId == meta(n).videoId }.uid }
        r.party.move(uid(4), -2)
        assertEquals(listOf(4, 2, 3, 5).map { meta(it).videoId }, r.state().queue.map { it.videoId })
        r.party.move(uid(4), -1) // already first: no change
        assertEquals(meta(4).videoId, r.state().queue.first().videoId)
        r.party.remove(uid(3))
        r.party.playNow(uid(5))
        assertEquals(meta(5).videoId, r.state().current?.videoId)
        assertEquals(listOf(4, 2).map { meta(it).videoId }, r.state().queue.map { it.videoId })
        assertEquals(meta(1).videoId, r.state().history.first().videoId)
    }

    @Test
    fun `toggle starts playback when idle and pauses or resumes otherwise`() = runTest {
        val r = rig()
        r.party.enqueue(meta(1), "Host", Source.HOST)
        r.party.next() // nothing left -> idle
        r.party.enqueue(meta(2), "Host", Source.HOST) // idle -> auto start
        r.party.onPlayerMessage(msg("state", meta(2).videoId, "playing"))
        r.party.togglePlay()
        assertEquals("pause", r.player.commands.last().first)
        r.party.onPlayerMessage(msg("state", meta(2).videoId, "paused"))
        r.party.togglePlay()
        assertEquals("play", r.player.commands.last().first)
    }

    // ------------------------------------------------------------ player reports

    @Test
    fun `player reports update status and the end of a track moves on`() = runTest {
        val r = rig((1..2).map(::meta))
        r.party.loadPlaylist("any", false)
        r.party.onPlayerMessage(msg("state", meta(1).videoId, "playing", pos = 1500, dur = 200_000))
        assertEquals("playing", r.state().player.status)
        assertEquals(1500, r.state().player.posMs)
        r.party.onPlayerMessage(msg("state", meta(1).videoId, "ended"))
        assertEquals(meta(2).videoId, r.state().current?.videoId)
    }

    @Test
    fun `the end of an advert does not skip the real song`() = runTest {
        val r = rig((1..2).map(::meta))
        r.party.loadPlaylist("any", false)
        r.party.onPlayerMessage(msg("state", meta(1).videoId, "ended", ad = true))
        assertEquals(meta(1).videoId, r.state().current?.videoId)
        assertEquals("ad", r.state().player.status)
    }

    @Test
    fun `reports about a different video are ignored`() = runTest {
        val r = rig((1..2).map(::meta))
        r.party.loadPlaylist("any", false)
        r.party.onPlayerMessage(msg("state", "zzzzzzzzzzz", "ended"))
        assertEquals(meta(1).videoId, r.state().current?.videoId)
    }

    @Test
    fun `a track that never starts is skipped with a notice`() = runTest {
        val r = rig((1..2).map(::meta))
        r.party.loadPlaylist("any", false)
        advanceTimeBy(46_000); runCurrent()
        assertEquals(meta(2).videoId, r.state().current?.videoId)
        assertNotNull(r.state().notice)
    }

    @Test
    fun `a playing track is not skipped by the watchdog`() = runTest {
        val r = rig((1..2).map(::meta))
        r.party.loadPlaylist("any", false)
        r.party.onPlayerMessage(msg("state", meta(1).videoId, "playing"))
        advanceTimeBy(60_000); runCurrent()
        assertEquals(meta(1).videoId, r.state().current?.videoId)
    }

    @Test
    fun `a player error skips to the next song`() = runTest {
        val r = rig((1..2).map(::meta))
        r.party.loadPlaylist("any", false)
        r.party.onPlayerMessage(msg("error", meta(1).videoId))
        advanceTimeBy(2_000); runCurrent()
        assertEquals(meta(2).videoId, r.state().current?.videoId)
    }

    @Test
    fun `clearing the queue keeps the current song and stops repeat from refilling`() = runTest {
        val r = rig((1..3).map(::meta))
        r.party.loadPlaylist("any", false) // playing 1, queue 2,3
        r.party.clearQueue()
        assertEquals(meta(1).videoId, r.state().current?.videoId)
        assertTrue(r.state().queue.isEmpty())
        assertNull(r.state().playlistTitle)
        r.party.next() // nothing queued and nothing to repeat
        assertNull(r.state().current)
    }

    @Test
    fun `a public mix is loaded from its video without a browser`() = runTest {
        val r = rig(mix = (1..3).map(::meta))
        r.party.loadWatchPlaylist("dQw4w9WgXcQ", "RDdQw4w9WgXcQ", false)
        assertEquals(meta(1).videoId, r.state().current?.videoId)
        assertEquals(listOf(2, 3).map { meta(it).videoId }, r.state().queue.map { it.videoId })
        assertEquals("Mix", r.state().playlistTitle)
    }

    @Test
    fun `a mix that needs sign-in is reported so the caller can read the page instead`() = runTest {
        val r = rig()
        try { r.party.loadWatchPlaylist("dQw4w9WgXcQ", "RDMM", false); fail() } catch (e: IllegalStateException) { /* expected */ }
        assertNull(r.state().current)
    }

    @Test
    fun `a watch page import uses the up-next panel and ignores recommendations`() = runTest {
        val r = rig()
        val page = JSONObject()
            .put("panel", JSONObject().put("playlistPanelRenderer", JSONObject().put("title", "Mix - X").put("contents", org.json.JSONArray()
                .put(JSONObject().put("playlistPanelVideoRenderer", JSONObject().put("videoId", "mmmmmmmmmm1").put("title", JSONObject().put("simpleText", "One"))))
                .put(JSONObject().put("playlistPanelVideoRenderer", JSONObject().put("videoId", "mmmmmmmmmm2").put("title", JSONObject().put("simpleText", "Two")))))))
            .put("related", JSONObject().put("videoRenderer", JSONObject().put("videoId", "rrrrrrrrrr1").put("title", JSONObject().put("simpleText", "Recommended"))))
        r.party.importPlaylistData("u", page.toString(), false)
        assertEquals("mmmmmmmmmm1", r.state().current?.videoId)
        assertEquals(listOf("mmmmmmmmmm2"), r.state().queue.map { it.videoId })
        assertEquals("Mix - X", r.state().playlistTitle)
    }

    // ------------------------------------------------------------ importing from a page

    private fun pageData(vararg ids: String, title: String = "My list") = JSONObject()
        .put("metadata", JSONObject().put("playlistMetadataRenderer", JSONObject().put("title", title)))
        .put("items", org.json.JSONArray().also { a ->
            ids.forEach { id ->
                a.put(JSONObject().put("playlistVideoRenderer", JSONObject().put("videoId", id)
                    .put("title", JSONObject().put("runs", org.json.JSONArray().put(JSONObject().put("text", "T $id"))))))
            }
        }).toString()

    @Test
    fun `a playlist read from a page replaces the queue but keeps the song that is playing`() = runTest {
        val r = rig((1..3).map(::meta))
        r.party.loadPlaylist("any", false) // playing 1, queue 2,3
        r.party.importPlaylistData("https://m.youtube.com/playlist?list=PLabc", pageData("aaaaaaaaaaa", "bbbbbbbbbbb"), false)
        assertEquals(meta(1).videoId, r.state().current?.videoId)
        assertEquals(listOf("aaaaaaaaaaa", "bbbbbbbbbbb"), r.state().queue.map { it.videoId })
        assertEquals("My list", r.state().playlistTitle)
    }

    @Test
    fun `importing page data starts playback when idle and rejects junk`() = runTest {
        val r = rig()
        r.party.importPlaylistData("u", pageData("aaaaaaaaaaa"), false)
        assertEquals("aaaaaaaaaaa", r.state().current?.videoId)
        try { r.party.importPlaylistData("u", "not json", false); fail() } catch (e: PartyException) { /* expected */ }
        try { r.party.importPlaylistData("u", "{}", false); fail() } catch (e: PartyException) { /* expected */ }
    }

    // ------------------------------------------------------------ what each viewer sees

    @Test
    fun `guests see only their own proposals and no guest list`() = runTest {
        val r = rig()
        val ola = r.guest("Ola"); val kuba = r.guest("Kuba")
        (1..2).forEach { r.source.add(meta(it)) }
        r.party.propose(meta(1).videoId, ola); r.party.propose(meta(2).videoId, kuba)

        val asOla = JSONObject(r.party.jsonFor(ola.id))
        assertEquals(1, asOla.getJSONArray("proposals").length())
        assertEquals(0, asOla.getJSONArray("guests").length())
        assertEquals("GUEST", asOla.getJSONObject("me").getString("role"))

        val asHost = JSONObject(r.party.jsonFor(PartyController.HOST_ID))
        assertEquals(2, asHost.getJSONArray("proposals").length())
        assertEquals(2, asHost.getJSONArray("guests").length())
        assertEquals("HOST", asHost.getJSONObject("me").getString("role"))
    }

    @Test
    fun `the guest view never leaks tokens`() = runTest {
        val r = rig()
        val g = r.guest()
        r.party.approveHost(g.id)
        val json = r.party.jsonFor(g.id)
        assertFalse(json.contains(g.token))
        assertFalse(json.contains(r.party.hostGuest.token))
        assertFalse(json.contains(r.secret))
    }

    // ------------------------------------------------------------ persistence

    @Test
    fun `guests, secret and the queue survive a restart`() = runTest {
        val prefs = FakePrefs()
        val first = rig((1..3).map(::meta), prefs)
        val g = first.guest()
        first.party.approveHost(g.id)
        first.party.loadPlaylist("https://example/list", false)
        advanceTimeBy(1_000); runCurrent()

        val second = rig(emptyList(), prefs)
        assertEquals(first.secret, second.secret)
        assertEquals(Role.HOST, second.party.guestByToken(g.token)!!.role)
        assertEquals(first.party.hostGuest.token, second.party.hostGuest.token)
        // The song that was playing comes back first, paused: the host presses play.
        assertEquals((1..3).map { meta(it).videoId }, second.state().queue.map { it.videoId })
        assertNull(second.state().current)
        assertTrue(second.player.loads.isEmpty())
    }
}
