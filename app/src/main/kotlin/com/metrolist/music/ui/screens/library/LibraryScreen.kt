/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.library

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.metrolist.music.LocalNavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.constants.AlbumViewTypeKey
import com.metrolist.music.constants.ChipSortTypeKey
import com.metrolist.music.constants.LibraryFilter
import com.metrolist.music.constants.LibraryViewType
import com.metrolist.music.constants.PlaylistViewTypeKey
import com.metrolist.music.ui.component.ChipsRow
import com.metrolist.music.ui.menu.SpotifyImportDialog
import com.metrolist.music.utils.SpotifyImporter
import com.metrolist.music.utils.rememberEnumPreference

private val SpotifyGreen = Color(0xFF1ED760)

@Composable
fun LibraryScreen() {
    val navController = LocalNavController.current
    var filterType by rememberEnumPreference(ChipSortTypeKey, LibraryFilter.LIBRARY)
    var libraryViewType by rememberEnumPreference(AlbumViewTypeKey, LibraryViewType.GRID)
    var playlistViewType by rememberEnumPreference(PlaylistViewTypeKey, LibraryViewType.GRID)

    val filterContent = @Composable {
        Row {
            ChipsRow(
                chips = listOf(
                    LibraryFilter.PLAYLISTS to stringResource(R.string.filter_playlists),
                    LibraryFilter.SONGS to stringResource(R.string.filter_songs),
                    LibraryFilter.ALBUMS to stringResource(R.string.filter_albums),
                    LibraryFilter.ARTISTS to stringResource(R.string.filter_artists),
                    LibraryFilter.PODCASTS to stringResource(R.string.filter_podcasts),
                ),
                currentValue = filterType,
                onValueUpdate = {
                    filterType = if (filterType == it) LibraryFilter.LIBRARY else it
                },
                modifier = Modifier.weight(1f),
            )
        }
    }

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var showSpotifyImportDialog by rememberSaveable { mutableStateOf(false) }
    var spotifyImportPrefill by rememberSaveable { mutableStateOf<String?>(null) }
    var clipboardSpotifyLink by remember { mutableStateOf<String?>(null) }
    var dismissedClipboardLink by rememberSaveable { mutableStateOf<String?>(null) }

    // Catches a Spotify playlist link the user just copied in another app (e.g. Spotify
    // itself) and offers to import it as soon as they come back to the Library tab.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    val text = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
                    val link = text?.takeIf { SpotifyImporter.extractPlaylistId(it) != null }
                    if (link != null && link != dismissedClipboardLink && link != clipboardSpotifyLink) {
                        clipboardSpotifyLink = link
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (showSpotifyImportDialog) {
        SpotifyImportDialog(
            isVisible = showSpotifyImportDialog,
            prefilledLink = spotifyImportPrefill,
            onDismiss = { showSpotifyImportDialog = false },
            onPlaylistCreated = { playlistId ->
                navController.navigate("local_playlist/$playlistId")
            },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (filterType) {
            LibraryFilter.LIBRARY -> LibraryMixScreen(
                navController = navController,
                filterContent = filterContent,
                viewType = libraryViewType,
                onViewTypeChange = { libraryViewType = it },
            )
            LibraryFilter.PLAYLISTS -> LibraryPlaylistsScreen(
                navController = navController,
                filterContent = filterContent,
                viewType = playlistViewType,
                onViewTypeChange = { playlistViewType = it },
            )
            LibraryFilter.SONGS -> LibrarySongsScreen(
                navController,
                { filterType = LibraryFilter.LIBRARY },
            )
            LibraryFilter.ALBUMS -> LibraryAlbumsScreen(
                navController,
                { filterType = LibraryFilter.LIBRARY },
            )
            LibraryFilter.ARTISTS -> LibraryArtistsScreen(
                navController,
                { filterType = LibraryFilter.LIBRARY },
            )
            LibraryFilter.PODCASTS -> LibraryPodcastsScreen(
                navController,
                { filterType = LibraryFilter.LIBRARY },
            )
        }

        // Keeps the last non-null link around through the exit fade, so the banner's
        // own content doesn't blank out mid-animation when clipboardSpotifyLink clears.
        var bannerLink by remember { mutableStateOf<String?>(null) }
        clipboardSpotifyLink?.let { bannerLink = it }

        AnimatedVisibility(
            visible = clipboardSpotifyLink != null,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                )
                .padding(12.dp)
                .fillMaxWidth(),
        ) {
            val link = bannerLink
            val glassShape = RoundedCornerShape(16.dp)
            val accent = MaterialTheme.colorScheme.primary
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = 20.dp, shape = glassShape, ambientColor = accent, spotColor = accent)
                    .clip(glassShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f))
                    .border(1.5.dp, Color.White.copy(alpha = 0.5f), glassShape),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.spotify_link_detected),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        dismissedClipboardLink = link
                        clipboardSpotifyLink = null
                    }) {
                        Text(stringResource(R.string.ignore_action))
                    }
                    TextButton(
                        colors = ButtonDefaults.textButtonColors(contentColor = SpotifyGreen),
                        onClick = {
                            dismissedClipboardLink = link
                            clipboardSpotifyLink = null
                            spotifyImportPrefill = link
                            showSpotifyImportDialog = true
                        },
                    ) {
                        Text(stringResource(R.string.import_action))
                    }
                }
            }
        }

        // Always visible, on every Library filter tab -- not just Playlists.
        val smallFabShape = RoundedCornerShape(14.dp)
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                )
                .padding(16.dp)
                .shadow(elevation = 22.dp, shape = smallFabShape, ambientColor = SpotifyGreen, spotColor = SpotifyGreen),
        ) {
            SmallFloatingActionButton(
                onClick = {
                    spotifyImportPrefill = null
                    showSpotifyImportDialog = true
                },
                shape = smallFabShape,
                containerColor = SpotifyGreen,
                contentColor = Color.White,
            ) {
                Icon(
                    painter = painterResource(R.drawable.link),
                    contentDescription = stringResource(R.string.import_from_spotify),
                )
            }
        }
    }
}
