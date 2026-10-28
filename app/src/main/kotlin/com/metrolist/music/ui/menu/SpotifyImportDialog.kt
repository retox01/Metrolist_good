/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.menu

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.LocalDatabase
import com.metrolist.music.R
import com.metrolist.music.db.entities.PlaylistEntity
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.ui.component.TextFieldDialog
import com.metrolist.music.utils.SpotifyImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

@Composable
fun SpotifyImportDialog(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    prefilledLink: String? = null,
    onPlaylistCreated: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()

    var showLinkDialog by remember { mutableStateOf(isVisible) }
    var isImporting by remember { mutableStateOf(false) }
    var matchedCount by remember { mutableIntStateOf(0) }
    var totalCount by remember { mutableIntStateOf(0) }
    var coverArtUrl by remember { mutableStateOf<String?>(null) }

    showLinkDialog = isVisible && !isImporting

    if (showLinkDialog) {
        TextFieldDialog(
            icon = { Icon(painter = painterResource(R.drawable.link), contentDescription = null) },
            title = { Text(text = stringResource(R.string.import_from_spotify)) },
            placeholder = { Text(stringResource(R.string.spotify_playlist_link)) },
            initialTextFieldValue = TextFieldValue(prefilledLink ?: ""),
            isInputValid = { SpotifyImporter.extractPlaylistId(it) != null },
            onDismiss = onDismiss,
            // The OK button must not call onDismiss itself: that would unmount this
            // composable (parent renders it behind `if (show)`) while the import
            // coroutine below is still running.
            autoDismiss = false,
            onDone = { link ->
                isImporting = true
                coroutineScope.launch {
                    val playlist = SpotifyImporter.fetchPlaylist(link).getOrElse {
                        Toast.makeText(context, R.string.spotify_import_failed_fetch, Toast.LENGTH_LONG).show()
                        isImporting = false
                        onDismiss()
                        return@launch
                    }

                    coverArtUrl = playlist.coverArtUrl
                    totalCount = playlist.tracks.size
                    matchedCount = 0
                    val matchedSongIds = mutableListOf<String>()
                    var skipped = 0

                    withContext(Dispatchers.IO) {
                        playlist.tracks.forEach { track ->
                            val query = if (track.artist.isNotBlank()) "${track.artist} ${track.title}" else track.title
                            val candidates =
                                YouTube
                                    .search(query, YouTube.SearchFilter.FILTER_SONG)
                                    .getOrNull()
                                    ?.items
                                    ?.filterIsInstance<SongItem>()
                                    ?.take(5)
                                    .orEmpty()
                            // The top search hit isn't always the right song -- a generic title
                            // ("Intro", a common word, a one-word title) can rank a cover, a
                            // remix, or an unrelated video first. Score every candidate on title
                            // + artist + duration closeness instead of trusting result order.
                            val match =
                                candidates
                                    .map { it to matchScore(track, it) }
                                    .maxByOrNull { it.second }
                                    ?.takeIf { it.second >= MIN_MATCH_SCORE }
                                    ?.first
                            if (match != null) {
                                database.query { insert(match.toMediaMetadata()) }
                                matchedSongIds.add(match.id)
                            } else {
                                skipped++
                            }
                            matchedCount++
                        }
                    }

                    val newPlaylist =
                        PlaylistEntity(
                            name = playlist.name,
                            bookmarkedAt = LocalDateTime.now(),
                            isEditable = true,
                            thumbnailUrl = playlist.coverArtUrl,
                        )
                    database.query { insert(newPlaylist) }
                    val fullPlaylist = database.playlist(newPlaylist.id).firstOrNull()
                    if (fullPlaylist != null) {
                        database.addSongsToPlaylist(fullPlaylist, matchedSongIds.map { it to null })
                    }

                    Toast
                        .makeText(
                            context,
                            context.getString(
                                R.string.spotify_import_done,
                                playlist.name,
                                matchedSongIds.size,
                                skipped,
                            ),
                            Toast.LENGTH_LONG,
                        ).show()

                    isImporting = false
                    onPlaylistCreated?.invoke(newPlaylist.id)
                    onDismiss()
                }
            },
        )
    }

    if (isImporting) {
        Dialog(onDismissRequest = {}) {
            val glassShape = RoundedCornerShape(20.dp)
            val accent = MaterialTheme.colorScheme.primary
            Box(
                modifier = Modifier
                    .shadow(elevation = 28.dp, shape = glassShape, ambientColor = accent, spotColor = accent)
                    .clip(glassShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f))
                    .border(
                        width = 1.5.dp,
                        brush = Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.5f), Color.White.copy(alpha = 0.12f)),
                        ),
                        shape = glassShape,
                    )
                    .padding(28.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    coverArtUrl?.let { url ->
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)).padding(bottom = 4.dp),
                        )
                    }
                    CircularProgressIndicator(modifier = Modifier.padding(bottom = 16.dp, top = 8.dp))
                    Text(
                        text =
                            if (totalCount == 0) {
                                stringResource(R.string.spotify_import_fetching)
                            } else {
                                stringResource(R.string.spotify_import_matching, matchedCount, totalCount)
                            },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

private const val MIN_MATCH_SCORE = 0.45

private fun matchScore(track: SpotifyImporter.SpotifyTrack, candidate: SongItem): Double {
    val titleSim = stringSimilarity(track.title, candidate.title)
    val artistSim =
        if (track.artist.isBlank()) {
            1.0
        } else {
            candidate.artists.maxOfOrNull { stringSimilarity(track.artist, it.name) } ?: 0.0
        }
    val targetSeconds = track.durationMs / 1000
    val durationScore =
        candidate.duration?.let { candidateSeconds ->
            if (targetSeconds <= 0) {
                0.5
            } else {
                (1.0 - (kotlin.math.abs(candidateSeconds - targetSeconds).toDouble() / targetSeconds)).coerceIn(0.0, 1.0)
            }
        } ?: 0.5
    return titleSim * 0.5 + artistSim * 0.3 + durationScore * 0.2
}

private fun stringSimilarity(a: String, b: String): Double {
    val s1 = a.trim().lowercase()
    val s2 = b.trim().lowercase()
    if (s1 == s2) return 1.0
    if (s1.isEmpty() || s2.isEmpty()) return 0.0
    if (s1.contains(s2) || s2.contains(s1)) return 0.85
    val maxLength = maxOf(s1.length, s2.length)
    return 1.0 - (levenshteinDistance(s1, s2).toDouble() / maxLength)
}

private fun levenshteinDistance(a: String, b: String): Int {
    val dp = Array(a.length + 1) { IntArray(b.length + 1) }
    for (i in 0..a.length) dp[i][0] = i
    for (j in 0..b.length) dp[0][j] = j
    for (i in 1..a.length) {
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            dp[i][j] = minOf(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
        }
    }
    return dp[a.length][b.length]
}
