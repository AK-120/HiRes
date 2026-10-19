package com.example.hifi.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.hifi.music.Album
import com.example.hifi.music.Playlist
import com.example.hifi.music.SOURCE_LOCAL
import com.example.hifi.music.SOURCE_SPOTIFY
import com.example.hifi.music.Track
import com.example.hifi.ui.MainViewModel
import com.example.hifi.ui.Screen
import com.example.hifi.ui.components.Artwork
import com.example.hifi.ui.components.StatusLine
import com.example.hifi.ui.components.formatDuration
import com.example.hifi.ui.nowplaying.PauseIcon
import com.example.hifi.ui.nowplaying.PlayIcon

private enum class Tab(val label: String) {
    Songs("Songs"), Albums("Albums"), Artists("Artists"), Playlists("Playlists"), Recent("Recent")
}

private sealed interface Detail {
    data class OfAlbum(val album: Album) : Detail
    data class OfArtist(val name: String) : Detail
    data class OfPlaylist(val playlist: Playlist) : Detail
}

@Composable
fun LibraryScreen(vm: MainViewModel) {
    val library by vm.library.collectAsState()
    val loading by vm.loading.collectAsState()
    val browse by vm.browse.collectAsState()
    val connected by vm.spotifyConnected.collectAsState()
    val current by vm.currentTrack.collectAsState()
    val playback by vm.playbackState.collectAsState()
    val status by vm.status.collectAsState()
    val colors = MaterialTheme.colorScheme

    var tab by remember { mutableStateOf(Tab.Songs) }
    var detail by remember { mutableStateOf<Detail?>(null) }
    var detailTracks by remember { mutableStateOf<List<Track>?>(null) }

    LaunchedEffect(browse) {
        detail = null
        if (browse == SOURCE_SPOTIFY && tab == Tab.Recent) tab = Tab.Songs
    }
    LaunchedEffect(detail) {
        detailTracks = null
        detailTracks = when (val d = detail) {
            null -> null
            is Detail.OfAlbum -> vm.albumTracks(d.album)
            is Detail.OfArtist -> vm.artistTracks(d.name)
            is Detail.OfPlaylist -> vm.playlistTracks(d.playlist.id)
        }
    }

    Column(Modifier.fillMaxSize()) {
        // top bar
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (detail != null) "‹  BACK" else "‹  NOW PLAYING",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.clickable {
                    if (detail != null) detail = null else vm.navigate(Screen.NowPlaying)
                }.padding(12.dp)
            )
            Text(
                "SOURCES",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.clickable { vm.navigate(Screen.Sources) }.padding(12.dp)
            )
        }

        // title
        val title = when (val d = detail) {
            is Detail.OfAlbum -> d.album.title
            is Detail.OfArtist -> d.name
            is Detail.OfPlaylist -> d.playlist.name
            null -> "Library"
        }
        Text(
            title,
            style = MaterialTheme.typography.displaySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
        )

        if (detail == null) {
            // source switch
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                SourceChip("LOCAL", browse == SOURCE_LOCAL) { vm.setBrowse(SOURCE_LOCAL) }
                SourceChip("SPOTIFY", browse == SOURCE_SPOTIFY) {
                    if (connected) vm.setBrowse(SOURCE_SPOTIFY) else vm.navigate(Screen.Sources)
                }
            }
            // tabs
            val tabs = if (browse == SOURCE_SPOTIFY) Tab.entries - Tab.Recent else Tab.entries
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp)
            ) {
                tabs.forEach { t ->
                    Column(Modifier.clickable { tab = t }.padding(horizontal = 8.dp, vertical = 6.dp)) {
                        Text(
                            t.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (t == tab) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (t == tab) colors.onSurface else colors.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        Box(
                            Modifier.fillMaxWidth().height(2.dp)
                                .background(if (t == tab) colors.onSurface else colors.background)
                        )
                    }
                }
            }
        } else {
            val list = detailTracks
            if (!list.isNullOrEmpty()) {
                Text(
                    "PLAY ALL  ·  ${list.size} TRACKS",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurface,
                    modifier = Modifier.clickable { vm.play(list, 0) }.padding(horizontal = 24.dp, vertical = 12.dp)
                )
            }
        }
        HorizontalDivider(color = colors.outline)

        // content
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val d = detail
            val songs: List<Track>? = when {
                d != null -> detailTracks
                tab == Tab.Songs -> library.tracks
                tab == Tab.Recent -> vm.recent()
                else -> null
            }
            val numbered = d is Detail.OfAlbum

            when {
                d != null && songs == null -> Empty("Loading…")
                songs != null -> {
                    if (songs.isEmpty()) {
                        Empty(
                            when {
                                d != null -> "No tracks"
                                loading -> "Scanning…"
                                tab == Tab.Recent -> "Nothing played yet"
                                browse == SOURCE_SPOTIFY -> "No saved songs"
                                else -> "No music found on this device"
                            }
                        )
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            itemsIndexed(songs, key = { _, t -> t.id }) { i, t ->
                                TrackRow(t, t.id == current?.id, numbered) { vm.play(songs, i) }
                            }
                        }
                    }
                }
                tab == Tab.Albums -> {
                    if (library.albums.isEmpty()) {
                        Empty(if (loading) "Scanning…" else "No albums")
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(20.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(22.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(library.albums, key = { it.id }) { a ->
                                Column(Modifier.clickable { detail = Detail.OfAlbum(a) }) {
                                    Artwork(a.artworkUri, Modifier.fillMaxWidth().aspectRatio(1f))
                                    Spacer(Modifier.height(8.dp))
                                    Text(a.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        a.artist ?: "${a.trackCount} tracks",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.onSurfaceVariant,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
                tab == Tab.Artists -> {
                    if (library.artists.isEmpty()) {
                        Empty(if (loading) "Scanning…" else "No artists")
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(library.artists, key = { it.name }) { a ->
                                Column(
                                    Modifier.fillMaxWidth()
                                        .clickable { detail = Detail.OfArtist(a.name) }
                                        .padding(horizontal = 24.dp, vertical = 14.dp)
                                ) {
                                    Text(a.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${a.albumCount} albums · ${a.trackCount} tracks",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
                else -> { // Playlists
                    if (browse == SOURCE_LOCAL) {
                        Empty("Local playlists are planned for a later version")
                    } else if (library.playlists.isEmpty()) {
                        Empty(if (loading) "Loading…" else "No playlists")
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(library.playlists, key = { it.id }) { p ->
                                Row(
                                    Modifier.fillMaxWidth()
                                        .clickable { detail = Detail.OfPlaylist(p) }
                                        .padding(horizontal = 24.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Artwork(p.artworkUri, size = 48.dp)
                                    Column(Modifier.padding(start = 14.dp)) {
                                        Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${p.trackCount} tracks", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        StatusLine(status) { vm.dismissStatus() }

        // mini player
        current?.let { t ->
            HorizontalDivider(color = colors.outline)
            Row(
                Modifier.fillMaxWidth()
                    .clickable { vm.navigate(Screen.NowPlaying) }
                    .padding(start = 24.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Artwork(t.artworkUri, size = 40.dp)
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text(t.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(t.artist ?: "Unknown artist", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(Modifier.clickable { vm.togglePlayPause() }.padding(16.dp)) {
                    if (playback.isPlaying) PauseIcon(colors.onSurface, 18.dp) else PlayIcon(colors.onSurface, 18.dp)
                }
            }
        }
    }
}

@Composable
private fun SourceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) colors.onSurface else colors.onSurfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 8.dp)
    )
}

@Composable
private fun TrackRow(t: Track, isCurrent: Boolean, numbered: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (numbered) {
            Text(
                t.trackNumber?.toString() ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.width(32.dp)
            )
        } else {
            Artwork(t.artworkUri, size = 48.dp)
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                t.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                listOfNotNull(t.artist, t.album.takeUnless { numbered }).joinToString(" · ").ifEmpty { "Unknown" },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        if (t.durationMs > 0) {
            Text(
                formatDuration(t.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
    }
}

@Composable
private fun Empty(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
