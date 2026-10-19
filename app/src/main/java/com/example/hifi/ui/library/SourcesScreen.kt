package com.example.hifi.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.hifi.ui.MainViewModel
import com.example.hifi.ui.Screen

/** Lists sources. Streaming providers are shown as unavailable; nothing is faked. */
@Composable
fun SourcesScreen(vm: MainViewModel) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text(
            "Back",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.clickable { vm.navigate(Screen.Library) }.padding(vertical = 8.dp)
        )
        Text("Sources", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(vertical = 12.dp))

        vm.providers.forEach { p ->
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Text(
                    if (p.isAvailable) "✓" else "○",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.width(32.dp)
                )
                Column {
                    Text(p.displayName, style = MaterialTheme.typography.titleMedium)
                    if (!p.isAvailable) {
                        Text(
                            "Not available yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
