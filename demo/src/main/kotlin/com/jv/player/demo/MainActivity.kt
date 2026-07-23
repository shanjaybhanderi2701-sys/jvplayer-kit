package com.jv.player.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/**
 * Wave 0 placeholder. The demo installs and launches on a physical device with an
 * empty shell — no product/player code yet. Later waves embed the SDK here to play
 * the plain test-media matrix (plan §4).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DemoPlaceholder()
                }
            }
        }
    }
}

@Composable
private fun DemoPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("demo_root"),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "jvplayer-kit demo — Wave 0 shell")
    }
}
