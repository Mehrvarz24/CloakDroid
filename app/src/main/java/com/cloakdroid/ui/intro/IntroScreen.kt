package com.cloakdroid.ui.intro

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.material3.Text
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloakdroid.R
import com.cloakdroid.ui.theme.CloakColors
import com.cloakdroid.ui.theme.InterFontFamily
import kotlinx.coroutines.delay

private const val INTRO_DURATION_MS = 1600L
private const val LOGO_ANIM_MS = 900

/**
 * Minimal warm intro: the app logo scales from 0.8 to 1.0 and fades in on the
 * warm charcoal background, then the whole screen fades out into the app.
 */
@Composable
fun IntroScreen(
    modifier: Modifier = Modifier,
    onFinished: () -> Unit = {}
) {
    var visible by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(INTRO_DURATION_MS)
        visible = false
        delay(450) // allow fade-out transition to finish before unmount
        onFinished()
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        exit = fadeOut(tween(durationMillis = 450))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CloakColors.Background),
            contentAlignment = Alignment.Center
        ) {
            var started by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { started = true }
            val progress by animateFloatAsState(
                targetValue = if (started) 1f else 0f,
                animationSpec = tween(durationMillis = LOGO_ANIM_MS),
                label = "logoIntro"
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = painterResource(R.drawable.ic_app_logo),
                    contentDescription = "CloakDroid logo",
                    modifier = Modifier
                        .size(160.dp)
                        .graphicsLayer {
                            scaleX = 0.8f + 0.2f * progress
                            scaleY = 0.8f + 0.2f * progress
                            alpha = progress
                        }
                )
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = "CloakDroid",
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 22.sp,
                    color = CloakColors.TextHigh,
                    modifier = Modifier
                        .alpha(progress)
                        .graphicsLayer {
                            translationY = (1f - progress) * 24f
                        }
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Private browsing, cloaked.",
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 14.sp,
                    color = CloakColors.TextMuted,
                    modifier = Modifier.alpha(progress)
                )
            }
        }
    }
}
