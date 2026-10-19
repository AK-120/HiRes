package com.example.hifi.ui.nowplaying

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.example.hifi.metadata.TechFormat
import com.example.hifi.music.SOURCE_SPOTIFY
import com.example.hifi.ui.MainViewModel
import com.example.hifi.ui.Screen
import com.example.hifi.ui.components.Artwork
import com.example.hifi.ui.components.StatusLine
import com.example.hifi.ui.components.ThinSeekBar
import com.example.hifi.ui.components.formatDuration

@Composable
fun NowPlayingScreen(vm: MainViewModel) {
    val track by vm.currentTrack.collectAsState()
    val state by vm.playbackState.collectAsState()
    val usb by vm.usb.collectAsState()
    val active by vm.activeSource.collectAsState()
    val status by vm.status.collectAsState()
    val colors = MaterialTheme.colorScheme

    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(0f) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (active == SOURCE_SPOTIFY) "SPOTIFY" else "LOCAL",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(12.dp)
            )
            Text(
                "LIBRARY",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.clickable { vm.navigate(Screen.Library) }.padding(12.dp)
            )
        }

        Column(
            Modifier.weight(1f).padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Artwork(
                    track?.artworkUri,
                    Modifier.widthIn(max = 420.dp).fillMaxWidth().aspectRatio(1f)
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                track?.title ?: "Nothing playing",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            track?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    it.artist ?: "Unknown artist",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    it.album ?: "Unknown album",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(16.dp))

            val duration = state.durationMs.coerceAtLeast(1L)
            val shownPos = if (dragging) (dragValue * duration).toLong() else state.positionMs
            ThinSeekBar(
                progress = if (dragging) dragValue else state.positionMs.toFloat() / duration,
                onChange = { dragging = true; dragValue = it },
                onFinished = { vm.seekTo((it * duration).toLong()); dragging = false },
                enabled = track != null,
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatDuration(shownPos), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                Text(formatDuration(state.durationMs), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }

            Spacer(Modifier.height(12.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ToggleLabel("Shuffle", state.shuffle) { vm.toggleShuffle() }
                Box(Modifier.clickable { vm.previous() }.padding(14.dp)) {
                    SkipIcon(colors.onSurface, forward = false)
                }
                Box(
                    Modifier.size(64.dp).clip(CircleShape).background(colors.primary)
                        .clickable { vm.togglePlayPause() },
                    contentAlignment = Alignment.Center
                ) {
                    if (state.isPlaying) PauseIcon(colors.onPrimary, 22.dp) else PlayIcon(colors.onPrimary, 22.dp)
                }
                Box(Modifier.clickable { vm.next() }.padding(14.dp)) {
                    SkipIcon(colors.onSurface, forward = true)
                }
                ToggleLabel(
                    if (state.repeatMode == Player.REPEAT_MODE_ONE) "Repeat 1" else "Repeat",
                    state.repeatMode != Player.REPEAT_MODE_OFF
                ) { vm.cycleRepeat() }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("VOL", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                ThinSeekBar(
                    progress = state.volume,
                    onChange = { vm.setVolume(it) },
                    onFinished = { vm.setVolume(it) },
                    muted = true,
                    modifier = Modifier.weight(1f).padding(start = 12.dp)
                )
            }

            // Technical information: deliberately subtle, and never fabricated.
            Column(
                Modifier.padding(top = 8.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val t = track
                if (t != null && t.source == SOURCE_SPOTIFY) {
                    Tech("Spotify")
                    Tech(state.outputName?.let { "Playing on $it" } ?: "Spotify device")
                    Tech("Stream format is chosen by Spotify")
                } else {
                    t?.let {
                        Tech(TechFormat.summary(it))
                        TechFormat.channelsLabel(it.channels)?.let { c -> Tech(c) }
                    }
                    Tech(usb?.name ?: "Default output")
                    usb?.maxSampleRateHz?.let { Tech("Device reports up to ${TechFormat.formatKhz(it)}") }
                }
            }
        }

        StatusLine(status) { vm.dismissStatus() }
    }
}

@Composable
private fun Tech(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun ToggleLabel(text: String, active: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.width(64.dp).clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) colors.onSurface else colors.onSurfaceVariant.copy(alpha = 0.6f)
        )
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier.size(width = 14.dp, height = 2.dp)
                .background(if (active) colors.onSurface else colors.background)
        )
    }
}
