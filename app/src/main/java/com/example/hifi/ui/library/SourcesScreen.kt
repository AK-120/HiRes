package com.example.hifi.ui.library

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.hifi.ui.MainViewModel
import com.example.hifi.ui.Screen
import com.example.hifi.ui.components.StatusLine

/** Sources and accounts. Anything unavailable is labelled as such. */
@Composable
fun SourcesScreen(vm: MainViewModel) {
    val colors = MaterialTheme.colorScheme
    val connected by vm.spotifyConnected.collectAsState()
    val status by vm.status.collectAsState()

    Column(Modifier.fillMaxSize()) {
        Text(
            "‹  LIBRARY",
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp, top = 4.dp)
                .clickable { vm.navigate(Screen.Library) }.padding(12.dp)
        )
        Text(
            "Sources",
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
        )
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = colors.outline)

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            vm.providers.forEach { p ->
                val isSpotify = p.id == "spotify"
                val isLocal = p.id == "local"
                val on = if (isSpotify) connected else p.isAvailable
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        if (on) "●" else "○",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.width(28.dp)
                    )
                    Column(Modifier.weight(1f)) {
                        Text(p.displayName, style = MaterialTheme.typography.titleMedium)
                        val sub = when {
                            isLocal -> "On this device"
                            isSpotify && !vm.spotifyConfigured -> "Not configured. Add a Spotify Client ID (see README)."
                            isSpotify && connected -> "Connected. Spotify plays the audio; this app controls it."
                            isSpotify -> "Not connected"
                            else -> "Not available yet"
                        }
                        Text(sub, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                        if (isSpotify) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Needs the Spotify app and a Premium account for playback control.",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (connected) {
                                    OutlineButton("DISCONNECT") { vm.disconnectSpotify() }
                                } else {
                                    OutlineButton("CONNECT") { vm.connectSpotify() }
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(color = colors.outline)
            }
        }
        StatusLine(status) { vm.dismissStatus() }
    }
}

@Composable
private fun OutlineButton(label: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = colors.onSurface,
        modifier = Modifier
            .border(1.dp, colors.outline, RoundedCornerShape(2.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    )
}
