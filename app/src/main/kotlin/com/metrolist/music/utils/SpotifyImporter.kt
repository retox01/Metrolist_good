/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Reads a public Spotify playlist from just its share link — no Spotify developer
 * account or API key needed. Spotify's own embed page (used for the "embed" widget
 * other sites paste in) renders the full track list server-side into a plain,
 * unauthenticated HTML page, so we scrape that instead of calling the real Web API
 * (which requires a registered client id/secret).
 */
object SpotifyImporter {
    data class SpotifyTrack(
        val title: String,
        val artist: String,
        val durationMs: Long,
    )

    data class SpotifyPlaylist(
        val name: String,
        val coverArtUrl: String?,
        val tracks: List<SpotifyTrack>,
    )

    private val client by lazy { HttpClient(OkHttp) }

    private const val DESKTOP_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    // Matches both playlist and album links/URIs — Spotify's embed page serves the exact
    // same entity/trackList shape for either, just under a different path segment.
    private val linkRegex =
        Regex("""(?:open\.spotify\.com(?:/intl-\w+)?/(playlist|album)/([a-zA-Z0-9]+)|spotify:(playlist|album):([a-zA-Z0-9]+))""")

    private val nextDataRegex =
        Regex("""<script id="__NEXT_DATA__"[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)

    private data class SpotifyLink(val type: String, val id: String)

    private fun matchLink(url: String): SpotifyLink? {
        val match = linkRegex.find(url.trim()) ?: return null
        val type = match.groupValues[1].ifBlank { match.groupValues[3] }
        val id = match.groupValues[2].ifBlank { match.groupValues[4] }
        return SpotifyLink(type, id)
    }

    fun extractPlaylistId(url: String): String? = matchLink(url)?.id

    /**
     * shortcut: the embed page truncates very large playlists/albums to roughly the
     * first 100 tracks. Switch to the real Web API (client-credentials OAuth, needs a
     * Spotify developer app) if full-length ones ever matter.
     */
    suspend fun fetchPlaylist(url: String): Result<SpotifyPlaylist> = runCatching {
        val link = matchLink(url) ?: error("Not a Spotify playlist/album link")
        val html =
            client
                .get("https://open.spotify.com/embed/${link.type}/${link.id}") {
                    header("User-Agent", DESKTOP_USER_AGENT)
                }.bodyAsText()

        val dataJson =
            nextDataRegex.find(html)?.groupValues?.get(1)
                ?: error("Spotify page layout changed, couldn't find playlist data")

        val entity =
            Json
                .parseToJsonElement(dataJson)
                .jsonObject["props"]
                ?.jsonObject?.get("pageProps")
                ?.jsonObject?.get("state")
                ?.jsonObject?.get("data")
                ?.jsonObject?.get("entity")
                ?.jsonObject
                ?: error("Spotify page didn't contain playlist data")

        val name = entity["name"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank) ?: "Spotify Playlist"
        val coverArtUrl =
            entity["coverArt"]
                ?.jsonObject?.get("sources")
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject?.get("url")
                ?.jsonPrimitive?.contentOrNull
        val tracks =
            entity["trackList"]?.jsonArray.orEmpty().mapNotNull { trackEl ->
                val track = trackEl.jsonObject
                val title = track["title"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                val artist = track["subtitle"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val duration = track["duration"]?.jsonPrimitive?.longOrNull ?: 0L
                SpotifyTrack(title = title, artist = artist, durationMs = duration)
            }

        if (tracks.isEmpty()) error("Playlist/album is empty or private")
        SpotifyPlaylist(name = name, coverArtUrl = coverArtUrl, tracks = tracks)
    }
}
