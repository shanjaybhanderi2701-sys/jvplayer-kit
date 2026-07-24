@file:OptIn(UnstableApi::class)
@file:Suppress("MagicNumber") // Demo harness: inline dp/timing values are fine here.

package com.jv.player.demo

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.jv.player.core.JvPlayer
import com.jv.player.demo.source.FilePlaybackSource
import kotlinx.coroutines.delay
import java.io.File
import kotlin.random.Random

/**
 * Wave 1 demo (APP-583 plan §4). Proves the SDK plays the plain test-media matrix
 * (~1 min / ~20 min / ~3 hr) end-to-end through the [FilePlaybackSource] seam, with a
 * random-seek control to verify seek-through-the-seam on the long file. No UI polish —
 * the styled control surface lands in Wave 2 (`:player-ui`).
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
                    .padding(vertical = 16.dp),
            )
        }
    }
}

@Composable
private fun PlayerScreen(file: File, onBack: () -> Unit) {
    val context = LocalContext.current
    val player = remember(file) { JvPlayer.create(context) }

    DisposableEffect(player) {
        player.setSource(FilePlaybackSource(file))
        onDispose { player.release() }
    }

    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    LaunchedEffect(player) {
        while (true) {
            positionMs = player.currentPositionMs.coerceAtLeast(0L)
            durationMs = player.durationMs.coerceAtLeast(0L)
            delay(250)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx -> PlayerView(ctx).apply { this.player = player.player } },
                update = { view -> view.player = player.player },
            )
        }
        Text(
            text = "${file.name}\n${formatMs(positionMs)} / ${formatMs(durationMs)}",
            modifier = Modifier.padding(16.dp).testTag("player_position"),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Button(onClick = { player.play() }) { Text("Play") }
            Button(onClick = { player.pause() }) { Text("Pause") }
            Button(
                onClick = {
                    val duration = player.durationMs
                    if (duration > 0L) player.seekTo(Random.nextLong(duration))
                },
            ) { Text("Random seek") }
            Button(onClick = onBack) { Text("Back") }
        }
    }
}

private fun mediaDir(context: Context): File =
    File(context.getExternalFilesDir(null), "media").apply { if (!exists()) mkdirs() }

private fun discoverMediaFiles(context: Context): List<File> =
    mediaDir(context).listFiles()
        ?.filter { it.isFile }
        ?.sortedBy { it.name }
        ?: emptyList()

private fun formatMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
