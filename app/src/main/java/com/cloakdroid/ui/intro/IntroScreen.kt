package com.cloakdroid.ui.intro

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloakdroid.ui.theme.CloakColors
import kotlinx.coroutines.delay

private const val INTRO_DURATION_MS = 1200L
private const val LETTER_STAGGER_MS = 60L
private const val LETTER_ANIM_MS = 350

/**
 * Animated 1.2s intro: gradient background with a slowly moving radial glow,
 * a pulsing shield glyph, and the app name "CloakDroid" fading/sliding in
 * letter by letter. The whole screen then fades out into the app.
 */
@Composable
fun IntroScreen(
    modifier: Modifier = Modifier,
    onFinished: () -> Unit = {}
) {
    var visible by remember { mutableStateOf(true) }

    // Drive the total intro duration; parent shows this while visible.
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
        IntroContent()
    }
}

@Composable
private fun IntroContent() {
    val transition = rememberInfiniteTransition(label = "introGlow")
    val glowPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowPhase"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(CloakColors.BackgroundDeep, CloakColors.Background)
                )
            )
            .drawBehind {
                // Slowly moving radial indigo glow (no blur — gradient only).
                val cx = size.width * (0.25f + 0.5f * glowPhase)
                val cy = size.height * (0.35f + 0.15f * glowPhase)
                val radius = size.minDimension * 0.85f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            CloakColors.Primary.copy(alpha = 0.28f),
                            Color(0xFF8B5CF6).copy(alpha = 0.10f),
                            Color.Transparent
                        ),
                        center = Offset(cx, cy),
                        radius = radius
                    ),
                    radius = radius,
                    center = Offset(cx, cy)
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PulsingShield()
            Spacer(modifier = Modifier.height(24.dp))
            AnimatedAppName()
            Spacer(modifier = Modifier.height(10.dp))
            FadeInAfter(delayMs = LETTER_STAGGER_MS * "CloakDroid".length + 200L) {
                Text(
                    text = "Private browsing, cloaked.",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = CloakColors.TextMuted,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun PulsingShield() {
    val transition = rememberInfiniteTransition(label = "shieldPulse")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shieldPulse"
    )
    Box(
        modifier = Modifier
            .size(96.dp)
            .graphicsLayer {
                scaleX = pulse
                scaleY = pulse
            }
            .background(
                Brush.linearGradient(CloakColors.BrandGradient),
                shape = RoundedCornerShape(28.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(44.dp)
        )
    }
}

@Composable
private fun AnimatedAppName() {
    val letters = "CloakDroid".toList()
    androidx.compose.foundation.layout.Row {
        letters.forEachIndexed { index, ch ->
            LetterAnim(delayMs = index * LETTER_STAGGER_MS, letter = ch)
        }
    }
}

@Composable
private fun LetterAnim(delayMs: Long, letter: Char) {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMs)
        started = true
    }
    val progress by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(durationMillis = LETTER_ANIM_MS),
        label = "letterAlpha"
    )
    Text(
        text = letter.toString(),
        fontSize = 44.sp,
        fontWeight = FontWeight.Bold,
        color = CloakColors.TextHigh,
        modifier = Modifier
            .graphicsLayer {
                alpha = progress
                translationY = (1f - progress) * 40f
            }
            .padding(horizontal = 0.5.dp)
    )
}

@Composable
private fun FadeInAfter(delayMs: Long, content: @Composable () -> Unit) {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMs)
        started = true
    }
    val alpha by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "taglineAlpha"
    )
    Box(modifier = Modifier.alpha(alpha)) { content() }
}

