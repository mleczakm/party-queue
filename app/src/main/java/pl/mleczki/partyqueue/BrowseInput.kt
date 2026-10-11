package pl.mleczki.partyqueue

import java.net.URLEncoder

/** Turns what the host typed or pasted into the address bar into a page to open. */
object BrowseInput {

    private val URL_LIKE = Regex("""^(https?://|(www\.|m\.|music\.)?(youtube\.com|youtu\.be)/)""", RegexOption.IGNORE_CASE)

    /** A YouTube link goes to its mobile page (a playlist link to the playlist, not to the video it was copied from); anything else is a search. */
    fun resolve(input: String): String? {
        val text = input.trim()
        if (text.isEmpty()) return null
        if (URL_LIKE.containsMatchIn(text)) {
            val playlist = YouTubeClient.parsePlaylistId(text)
            val video = YouTubeClient.parseVideoId(text)
            val isPlaylistPage = "/playlist" in text
            return when {
                playlist != null && (isPlaylistPage || video == null) -> "https://m.youtube.com/playlist?list=$playlist"
                video != null -> "https://m.youtube.com/watch?v=$video" + if (playlist != null) "&list=$playlist" else ""
                else -> text.replace(Regex("""^https?://(www\.)?youtube\.com""", RegexOption.IGNORE_CASE), "https://m.youtube.com")
                    .let { if (it.startsWith("http", ignoreCase = true)) it else "https://$it" }
            }
        }
        return "https://m.youtube.com/results?search_query=" + URLEncoder.encode(text, "UTF-8")
    }

    /** What the address bar shows for a page: no scheme, no mobile prefix. */
    fun display(url: String): String =
        url.removePrefix("https://").removePrefix("http://").removePrefix("www.").removePrefix("m.")
}
