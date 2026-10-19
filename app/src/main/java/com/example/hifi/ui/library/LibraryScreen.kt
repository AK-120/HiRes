package com.example.hifi.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.hifi.music.Track
import com.example.hifi.ui.MainViewModel
import com.example.hifi.ui.Screen

private enum class Tab(val label: String) {
    Songs("Songs"), Albums("Albums"), Artists("Artists"), Playlists("Playlists"), Recent("Recent")
}

private sealed interface Detail {
    data class OfAlbum(val id: Long, val title: String) : Detail
    data class OfArtist(val name: String) : Detail
}

@Composable
fun LibraryScreen(vm: MainViewModel) {
    val library by vm.library.collectAsState()
    val loading by vm.loading.collectAsState()
    val current by vm.currentTrack.collectAsState()
    val playback by vm.playbackState.collectAsState()
    val colors = MaterialTheme.colorScheme

    var tab by remember { mutableStateOf(Tab.Songs) }
    var detail by remember { mutableStateOf<Detail?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (detail != null) "Back" else "Now Playing",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.clickable {
                    if (detail != null) detail = null else vm.navigate(Screen.NowPlaying)
                }.padding(8.dp)
            )
            Text(
                "Sources",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.clickable { vm.navigate(Screen.Sources) }.padding(8.dp)
            )
        }

        Text(
            when (val d = detail) {
                is Detail.OfAlbum -> d.title
                is Detail.OfArtist -> d.name
                null -> "Library"
            },
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (detail == null) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Tab.entries.forEach { t ->
                    Text(
                        t.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (t == tab) colors.onSurface else colors.onSurfaceVariant,
                        modifier = Modifier.clickable { tab = t }.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }
        }
        HorizontalDivider(color = colors.outline)

        Box(Modifier.weight(1f).fillMaxWidth()) {
            val songs: List<Track>? = when (val d = detail) {
                is Detail.OfAlbum -> library.tracks.filter { it.albumId == d.id }
                    .sortedBy { it.trackNumber ?: Int.MAX_VALUE }
                is Detail.OfArtist -> library.tracks.filter { it.artist == d.name }
                null -> when (tab) {
                    Tab.Songs -> library.tracks
                    Tab.Recent -> vm.recent()
                    else -> null
                }
            }

            when {
                songs != null -> {
                    if (songs.isEmpty()) {
                        Empty(
                            when {
                                loading -> "Scanning…"
                                tab == Tab.Recent && detail == null -> "Nothing played yet"
                                else -> "No music found on this device"
                            }
                        )
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            itemsIndexed(songs, key = { _, t -> t.id }) { i, t ->
                                TrackRow(t, t.id == current?.id) { vm.play(songs, i) }
                            }
                        }
                    }
                }
                tab == Tab.Albums -> {
                    if (library.albums.isEmpty()) Empty(if (loading) "Scanning…" else "No albums") else
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(library.albums, key = { it.id }) { a ->
                                Row(
                                    Modifier.fillMaxWidth()
                                        .clickable { detail = Detail.OfAlbum(a.id, a.title) }
                                        .padding(horizontal = 20.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Artwork(a.artworkUri)
                                    Column(Modifier.padding(start = 14.dp)) {
                                        Text(a.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            listOfNotNull(a.artist, "${a.trackCount} tracks").joinToString(" · "),
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
                    if (library.artists.isEmpty()) Empty(if (loading) "Scanning…" else "No artists") else
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(library.artists, key = { it.name }) { a ->
                                Column(
                                    Modifier.fillMaxWidth()
                                        .clickable { detail = Detail.OfArtist(a.name) }
                                        .padding(horizontal = 20.dp, vertical = 12.dp)
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
                else -> Empty("Playlists are planned for a later version")
            }
        }

        // Mini player
        current?.let { t ->
            HorizontalDivider(color = colors.outline)
            Row(
                Modifier.fillMaxWidth()
                    .clickable { vm.navigate(Screen.NowPlaying) }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(t.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(t.artist ?: "Unknown artist", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 1)
                }
                Text(
                    if (playback.isPlaying) "Pause" else "Play",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clickable { vm.togglePlayPause() }.padding(8.dp)
                )
            }
        }
    }
}

@Composable
private fun TrackRow(t: Track, isCurrent: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Artwork(t.artworkUri)
        Column(Modifier.padding(start = 14.dp)) {
            Text(
                t.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isCurrent) colors.onSurface else colors.onSurface.copy(alpha = 0.9f),
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                listOfNotNull(t.artist, t.album).joinToString(" · ").ifEmpty { "Unknown" },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun Artwork(uri: String?) {
    Box(Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (uri != null) {
            AsyncImage(
                model = uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
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
