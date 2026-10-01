package com.cloakdroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.cloakdroid.ui.AppNavHost
import com.cloakdroid.ui.intro.IntroScreen
import com.cloakdroid.ui.theme.CloakDroidTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CloakDroidTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    IntroThenApp()
                }
            }
        }
    }
}

/** Shows the 1.2s animated intro, then fades into the main app. */
@Composable
private fun IntroThenApp() {
    var showIntro by remember { mutableStateOf(true) }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = !showIntro,
            enter = fadeIn(tween(durationMillis = 300))
        ) {
            AppNavHost()
        }
        if (showIntro) {
            IntroScreen(
                modifier = Modifier.fillMaxSize(),
                onFinished = { showIntro = false }
            )
        }
    }
}
