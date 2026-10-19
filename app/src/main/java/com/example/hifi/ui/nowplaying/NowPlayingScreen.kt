package com.example.hifi.ui.nowplaying

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.example.hifi.metadata.TechFormat
import com.example.hifi.ui.MainViewModel
import com.example.hifi.ui.Screen

@Composable
fun NowPlayingScreen(vm: MainViewModel) {
    val track by vm.currentTrack.collectAsState()
    val state by vm.playbackState.collectAsState()
    val usb by vm.usb.collectAsState()
    val colors = MaterialTheme.colorScheme

    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(0f) }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                "Library",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.clickable { vm.navigate(Screen.Library) }.padding(8.dp)
            )
        }

        Spacer(Modifier.weight(1f))

        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).background(colors.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (track?.artworkUri != null) {
                AsyncImage(
                    model = track?.artworkUri,
                    contentDescription = "Album artwork",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text("No artwork", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(28.dp))

        Text(
            track?.title ?: "Nothing playing",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        track?.let {
            Spacer(Modifier.height(4.dp))
            Text(it.artist ?: "Unknown artist", style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(it.album ?: "Unknown album", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        Spacer(Modifier.height(20.dp))

        val duration = state.durationMs.coerceAtLeast(1L)
        val shownPos = if (dragging) (dragValue * duration).toLong() else state.positionMs
        Slider(
            value = if (dragging) dragValue else (state.positionMs.toFloat() / duration).coerceIn(0f, 1f),
            onValueChange = { dragging = true; dragValue = it },
            onValueChangeFinished = { vm.seekTo((dragValue * duration).toLong()); dragging = false },
            enabled = track != null,
            colors = SliderDefaults.colors(
                thumbColor = colors.primary,
                activeTrackColor = colors.primary,
                inactiveTrackColor = colors.outline
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(shownPos), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            Text(formatTime(state.durationMs), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        }

        Spacer(Modifier.height(16.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToggleLabel("Shuffle", state.shuffle) { vm.toggleShuffle() }
            Box(Modifier.clickable { vm.previous() }.padding(12.dp)) { SkipIcon(colors.onSurface, forward = false) }
            Box(Modifier.clickable { vm.togglePlayPause() }.padding(12.dp)) {
                if (state.isPlaying) PauseIcon(colors.onSurface, 32.dp) else PlayIcon(colors.onSurface, 32.dp)
            }
            Box(Modifier.clickable { vm.next() }.padding(12.dp)) { SkipIcon(colors.onSurface, forward = true) }
            val repeatLabel = if (state.repeatMode == Player.REPEAT_MODE_ONE) "Repeat 1" else "Repeat"
            ToggleLabel(repeatLabel, state.repeatMode != Player.REPEAT_MODE_OFF) { vm.cycleRepeat() }
        }

        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Vol", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            Slider(
                value = state.volume,
                onValueChange = { vm.setVolume(it) },
                colors = SliderDefaults.colors(
                    thumbColor = colors.onSurfaceVariant,
                    activeTrackColor = colors.onSurfaceVariant,
                    inactiveTrackColor = colors.outline
                ),
                modifier = Modifier.weight(1f).padding(start = 8.dp)
            )
        }

        Spacer(Modifier.weight(1f))

        // Technical information: deliberately subtle, and never fabricated.
        Column(Modifier.padding(bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            track?.let { t ->
                Text(TechFormat.summary(t), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                TechFormat.channelsLabel(t.channels)?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                }
            }
            Text(
                usb?.name ?: "Default output",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant
            )
            usb?.maxSampleRateHz?.let {
                Text(
                    "Device reports up to ${TechFormat.formatKhz(it)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ToggleLabel(text: String, active: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        textAlign = TextAlign.Center,
        color = if (active) colors.onSurface else colors.onSurfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.width(64.dp).clickable(onClick = onClick).padding(vertical = 8.dp)
    )
}

private fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
