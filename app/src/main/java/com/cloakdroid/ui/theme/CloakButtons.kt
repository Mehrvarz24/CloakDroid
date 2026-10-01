package com.cloakdroid.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * CloakDroid button kit — "Spotify dark" aesthetic with a moving brand glow:
 *  - GradientButton: indigo→violet gradient fill, animated diagonal shine
 *    sweep, press-scale via spring, subtle outer glow while pressed.
 *  - GhostButton: glassy outline variant for secondary actions.
 *
 * Implementation notes:
 *  - The shine sweep is drawn with drawBehind + an infinite translate
 *    animation of a narrow translucent white band; cheap (no RenderScript,
 *    no RenderEffect) and safe on Samsung/Exynos devices where blur of
 *    composables is unreliable.
 *  - Press feedback uses graphicsLayer scale animated with a spring —
 *    standard Material Expressive "squish".
 */
object CloakButtons {

    private val Gradient = Brush.linearGradient(
        colors = listOf(CloakColors.Primary, CloakColors.BrandGradient[1]),
        start = Offset.Zero,
        end = Offset.Infinite
    )

    @Composable
    fun GradientButton(
        text: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        height: Dp = 52.dp,
        shape: Shape = RoundedCornerShape(18.dp),
        animatedShine: Boolean = true
    ) {
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (pressed) 0.96f else 1f,
            animationSpec = spring(dampingRatio = 0.55f, stiffness = 500f),
            label = "pressScale"
        )

        val shine = if (animatedShine && enabled) {
            val t = rememberInfiniteTransition(label = "shine")
            val progress by t.animateFloat(
                initialValue = -0.6f,
                targetValue = 1.6f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2600, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "shineX"
            )
            progress
        } else 2f // off-screen: no shine

        Button(
            onClick = onClick,
            enabled = enabled,
            interactionSource = interaction,
            shape = shape,
            contentPadding = PaddingValues(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = Color.White,
                disabledContainerColor = CloakColors.Elevated,
                disabledContentColor = CloakColors.TextFaint
            ),
            modifier = modifier
                .height(height)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .drawBehind {
                    // Glow halo when pressed (drawn cheaply as alpha gradient)
                    if (pressed) {
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    CloakColors.Primary.copy(alpha = 0.35f),
                                    Color.Transparent
                                ),
                                center = center,
                                radius = size.maxDimension * 0.9f
                            )
                        )
                    }
                    // Base gradient fill
                    drawRect(brush = if (enabled) Gradient else Brush.linearGradient(
                        listOf(CloakColors.Elevated, CloakColors.Elevated)
                    ))
                    // Animated diagonal shine band
                    if (enabled && animatedShine) {
                        val bandWidth = size.width * 0.35f
                        val x = size.width * shine
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.14f),
                                    Color.Transparent
                                ),
                                start = Offset(x, 0f),
                                end = Offset(x + bandWidth, size.height)
                            )
                        )
                    }
                }
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 20.dp)) {
                Text(
                    text = text,
                    fontSize = 16.sp,
                    letterSpacing = 0.3.sp
                )
            }
        }
    }

    @Composable
    fun GhostButton(
        text: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        height: Dp = 48.dp,
        shape: Shape = RoundedCornerShape(16.dp)
    ) {
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (pressed) 0.97f else 1f,
            animationSpec = spring(dampingRatio = 0.55f, stiffness = 500f),
            label = "pressScale"
        )

        Button(
            onClick = onClick,
            enabled = enabled,
            interactionSource = interaction,
            shape = shape,
            contentPadding = PaddingValues(),
            border = BorderStroke(1.dp, CloakColors.GlassBorder),
            colors = ButtonDefaults.buttonColors(
                containerColor = CloakColors.GlassFill,
                contentColor = CloakColors.TextHigh,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = CloakColors.TextFaint
            ),
            modifier = modifier
                .height(height)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(text = text, fontSize = 15.sp)
            }
        }
    }
}
