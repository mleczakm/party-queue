package pl.mleczki.partyqueue

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeParserTest {

    private fun json(s: String) = JSONObject(s)

    @Test
    fun `reads videoRenderer from search results`() {
        val data = json("""{"contents":[{"videoRenderer":{"videoId":"aaaaaaaaaaa","title":{"runs":[{"text":"Hello"}]},
            "ownerText":{"runs":[{"text":"Chan"}]},"lengthText":{"simpleText":"3:21"}}}]}""")
        assertEquals(listOf(Meta("aaaaaaaaaaa", "Hello", "Chan", "3:21")), YouTubeParser.extractVideos(data))
    }

    @Test
    fun `reads playlistVideoRenderer and skips unplayable entries`() {
        val data = json("""{"x":[
            {"playlistVideoRenderer":{"videoId":"bbbbbbbbbbb","title":{"runs":[{"text":"Ok"}]},"shortBylineText":{"runs":[{"text":"By"}]},"isPlayable":true}},
            {"playlistVideoRenderer":{"videoId":"ccccccccccc","title":{"runs":[{"text":"[Deleted video]"}]},"isPlayable":false}}]}""")
        assertEquals(listOf("bbbbbbbbbbb"), YouTubeParser.extractVideos(data).map { it.videoId })
    }

    @Test
    fun `reads the newer lockupViewModel layout and ignores other lockup types`() {
        val data = json("""{"items":[
            {"lockupViewModel":{"contentType":"LOCKUP_CONTENT_TYPE_VIDEO","contentId":"ddddddddddd",
              "metadata":{"lockupMetadataViewModel":{"title":{"content":"New style"},
                "metadata":{"contentMetadataViewModel":{"metadataRows":[{"metadataParts":[{"text":{"content":"The Channel"}}]}]}}}},
              "contentImage":{"thumbnailViewModel":{"overlays":[{"thumbnailBottomOverlayViewModel":{"badges":[{"thumbnailBadgeViewModel":{"text":"4:05"}}]}}]}}}},
            {"lockupViewModel":{"contentType":"LOCKUP_CONTENT_TYPE_PLAYLIST","contentId":"PLxxxxxxxxxxxx"}}]}""")
        assertEquals(listOf(Meta("ddddddddddd", "New style", "The Channel", "4:05")), YouTubeParser.extractVideos(data))
    }

    @Test
    fun `deduplicates and rejects malformed ids`() {
        val data = json("""{"a":{"videoRenderer":{"videoId":"eeeeeeeeeee","title":{"runs":[{"text":"One"}]}}},
            "b":{"videoRenderer":{"videoId":"eeeeeeeeeee","title":{"runs":[{"text":"Dup"}]}}},
            "c":{"videoRenderer":{"videoId":"short","title":{"runs":[{"text":"Bad"}]}}}}""")
        val result = YouTubeParser.extractVideos(data)
        assertEquals(1, result.size)
        assertEquals("One", result[0].title)
    }

    @Test
    fun `reads only the up-next panel of a watch page, not the recommendations`() {
        val data = json("""{"watch":{"playlist":{"playlistPanelRenderer":{"title":"Mix - Some Song","contents":[
            {"playlistPanelVideoRenderer":{"videoId":"mmmmmmmmmm1","title":{"simpleText":"Mix one"},"shortBylineText":{"runs":[{"text":"A"}]},"lengthText":{"simpleText":"3:00"}}},
            {"playlistPanelVideoRenderer":{"videoId":"mmmmmmmmmm2","title":{"simpleText":"Mix two"}}}]}}},
            "related":[{"videoRenderer":{"videoId":"rrrrrrrrrr1","title":{"runs":[{"text":"Recommended"}]}}}]}""")
        assertEquals(listOf("mmmmmmmmmm1", "mmmmmmmmmm2"), YouTubeParser.extractPlaylistPanel(data).map { it.videoId })
        assertEquals("Mix one", YouTubeParser.extractPlaylistPanel(data)[0].title)
        assertEquals("Mix - Some Song", YouTubeParser.playlistTitle(data))
        assertEquals(0, YouTubeParser.extractPlaylistPanel(json("{}")).size)
    }

    @Test
    fun `finds the embedded initial data in a page`() {
        val html = """<html><script>var ytInitialData = {"k":{"videoRenderer":{"videoId":"fffffffffff","title":{"runs":[{"text":"T"}]}}}};</script><script>other()</script>"""
        assertEquals(1, YouTubeParser.extractVideos(YouTubeParser.initialData(html)).size)
    }

    @Test(expected = IllegalStateException::class)
    fun `rejects pages without initial data`() {
        YouTubeParser.initialData("<html>consent</html>")
    }

    @Test
    fun `reads playlist titles in both layouts`() {
        assertEquals("Old", YouTubeParser.playlistTitle(json("""{"metadata":{"playlistMetadataRenderer":{"title":"Old"}}}""")))
        assertEquals("New", YouTubeParser.playlistTitle(json("""{"h":{"pageHeaderViewModel":{"title":{"dynamicTextViewModel":{"text":{"content":"New"}}}}}}""")))
        assertNull(YouTubeParser.playlistTitle(json("{}")))
    }

    @Test
    fun `parses video ids from the usual link shapes`() {
        val id = "dQw4w9WgXcQ"
        listOf(
            id,
            "https://www.youtube.com/watch?v=$id",
            "https://m.youtube.com/watch?v=$id&list=PL123456789012",
            "https://youtu.be/$id?t=10",
            "https://www.youtube.com/shorts/$id",
            "  $id  ",
        ).forEach { assertEquals(it, id, YouTubeClient.parseVideoId(it)) }
        assertNull(YouTubeClient.parseVideoId("not a video"))
        assertNull(YouTubeClient.parseVideoId("https://example.com/"))
    }

    @Test
    fun `parses playlist ids`() {
        val id = "PLFgquLnL59alCl_2TQvOiD5Vgm1hCaGSI"
        assertEquals(id, YouTubeClient.parsePlaylistId("https://www.youtube.com/playlist?list=$id"))
        assertEquals(id, YouTubeClient.parsePlaylistId("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=$id"))
        assertEquals(id, YouTubeClient.parsePlaylistId(id))
        assertNull(YouTubeClient.parsePlaylistId("dQw4w9WgXcQ"))
        assertNull(YouTubeClient.parsePlaylistId(""))
    }
}
