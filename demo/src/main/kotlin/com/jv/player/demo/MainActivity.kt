@file:Suppress("MagicNumber") // Demo harness: inline dp/timing values are fine here.

package com.jv.player.demo

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.jv.player.core.JvPlayer
import com.jv.player.demo.source.FilePlaybackSource
import com.jv.player.ui.PlayerSurface
import java.io.File

/**
 * Wave 2 demo (APP-583 plan §4, APP-587). The file picker feeds the `:player-ui`
 * [PlayerSurface] — the real SurfaceView-backed control surface (§5.3) with its default
 * controls (play/pause, seekbar, time, resize) and gesture layer. This is where the
 * acceptance criterion "full controls usable in :demo on a physical device" is proven:
 * every critical mechanic (seekbar release-commit, ±10s, tap-to-toggle-chrome, double-tap
 * seek, pinch/resize) is reachable here.
 *
 * The demo owns the [JvPlayer] engine lifecycle (create + `setSource` + `release`); the
 * surface only binds a controller over it. The engine drives any [FilePlaybackSource]
 * through the same seam a real host app uses — the surface never sees a file or path.
 *
 * Test media is not bundled (large, license-clear, kept out of git — plan §7.2). Push
 * files to the app's external files dir, e.g.:
 *   adb push short.mp4 /sdcard/Android/data/com.jv.player.demo/files/media/
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DemoApp()
                }
            }
        }
    }
}

@Composable
private fun DemoApp() {
    val context = LocalContext.current
    var selected by remember { mutableStateOf<File?>(null) }
    val mediaFiles = remember { discoverMediaFiles(context) }

    Box(modifier = Modifier.fillMaxSize().testTag("demo_root")) {
        val current = selected
        if (current == null) {
            FilePicker(files = mediaFiles, mediaDir = mediaDir(context)) { selected = it }
        } else {
            PlayerScreen(file = current, onBack = { selected = null })
        }
    }
}

@Composable
private fun FilePicker(files: List<File>, mediaDir: File, onPick: (File) -> Unit) {
    if (files.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("No test media found.\nPush files to:\n${mediaDir.absolutePath}")
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        items(files) { file ->
            Text(
                text = file.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(file) }
                    .padding(vertical = 16.dp)
                    .testTag("media_item"),
            )
        }
    }
}

/**
 * Hosts the `:player-ui` control surface over an engine bound to [file]. The engine is
 * created/released with this composition (so leaving the screen tears the player down —
 * relevant to the no-leak / 3hr-stability criterion), and the screen is kept awake while
 * a video is on so a long unattended playback run doesn't sleep.
 */
@Composable
private fun PlayerScreen(file: File, onBack: () -> Unit) {
    val context = LocalContext.current
    val engine = remember(file) { JvPlayer.create(context) }

    DisposableEffect(engine) {
        engine.setSource(FilePlaybackSource(file))
        onDispose { engine.release() }
    }

    // Keep the screen on while a video is on screen (long-file stability run, §Acceptance).
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // Route the system back button to the same "return to picker" action the top bar drives.
    BackHandler(onBack = onBack)

    PlayerSurface(
        engine = engine,
        title = file.name,
        onBack = onBack,
        modifier = Modifier.fillMaxSize(),
    )
}

private fun mediaDir(context: Context): File =
    File(context.getExternalFilesDir(null), "media").apply { if (!exists()) mkdirs() }

private fun discoverMediaFiles(context: Context): List<File> =
    mediaDir(context).listFiles()
        ?.filter { it.isFile }
        ?.sortedBy { it.name }
        ?: emptyList()
