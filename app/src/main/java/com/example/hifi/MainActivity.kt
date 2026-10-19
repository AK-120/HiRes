package com.example.hifi

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.hifi.ui.MainViewModel
import com.example.hifi.ui.Screen
import com.example.hifi.ui.library.LibraryScreen
import com.example.hifi.ui.library.SourcesScreen
import com.example.hifi.ui.nowplaying.NowPlayingScreen
import com.example.hifi.ui.theme.HiFiTheme

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        setContent { HiFiTheme { Root() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Spotify sign-in returns here via com.example.hifi://spotify-callback. */
    private fun handleIntent(intent: Intent?) {
        intent?.data?.let { vm.onSpotifyCallback(it) }
    }

    @Composable
    private fun Root() {
        val screen by vm.screen.collectAsState()
        val audioPermission =
            if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
            else Manifest.permission.READ_EXTERNAL_STORAGE

        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { vm.refresh() }

        LaunchedEffect(Unit) {
            val needed = buildList {
                add(audioPermission)
                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            }
            val missing = needed.filter {
                ContextCompat.checkSelfPermission(this@MainActivity, it) != PackageManager.PERMISSION_GRANTED
            }
            if (missing.isEmpty()) vm.refresh() else launcher.launch(missing.toTypedArray())
        }

        BackHandler(enabled = screen != Screen.NowPlaying) {
            vm.navigate(if (screen == Screen.Sources) Screen.Library else Screen.NowPlaying)
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .systemBarsPadding()
        ) {
            when (screen) {
                Screen.NowPlaying -> NowPlayingScreen(vm)
                Screen.Library -> LibraryScreen(vm)
                Screen.Sources -> SourcesScreen(vm)
            }
        }
    }
}
