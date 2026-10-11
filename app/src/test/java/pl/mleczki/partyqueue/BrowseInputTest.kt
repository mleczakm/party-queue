package pl.mleczki.partyqueue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowseInputTest {

    @Test
    fun `blank input opens nothing`() {
        assertNull(BrowseInput.resolve("   "))
    }

    @Test
    fun `plain words search youtube`() {
        assertEquals("https://m.youtube.com/results?search_query=daft+punk+around", BrowseInput.resolve(" daft punk around "))
    }

    @Test
    fun `a video link opens the mobile watch page`() {
        assertEquals("https://m.youtube.com/watch?v=dQw4w9WgXcQ", BrowseInput.resolve("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=10"))
        assertEquals("https://m.youtube.com/watch?v=dQw4w9WgXcQ", BrowseInput.resolve("youtu.be/dQw4w9WgXcQ"))
    }

    @Test
    fun `a playlist link opens the playlist, also when copied from a video inside it`() {
        assertEquals("https://m.youtube.com/playlist?list=PLabcdefghij123", BrowseInput.resolve("https://www.youtube.com/playlist?list=PLabcdefghij123"))
        assertEquals("https://m.youtube.com/watch?v=dQw4w9WgXcQ&list=PLabcdefghij123", BrowseInput.resolve("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PLabcdefghij123"))
    }

    @Test
    fun `other youtube pages keep their path`() {
        assertEquals("https://m.youtube.com/@channel/videos", BrowseInput.resolve("https://www.youtube.com/@channel/videos"))
    }

    @Test
    fun `the address bar shows a short form`() {
        assertEquals("youtube.com/watch?v=x", BrowseInput.display("https://m.youtube.com/watch?v=x"))
    }
}
