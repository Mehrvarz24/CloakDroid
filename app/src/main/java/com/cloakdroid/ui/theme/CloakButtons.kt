package com.cloakdroid.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * CloakDroid button kit — soft launch-style buttons:
 *  - GradientButton: gentle warm caramel→cream gradient, subtle press-scale,
 *    no glow/shine. Calm, launch-button aesthetic.
 *  - GhostButton: glassy outline variant for secondary actions.
 */
object CloakButtons {

    @Composable
    private fun softGradient(): Brush = Brush.horizontalGradient(CloakColors.BrandGradient)

    @Composable
    fun GradientButton(
        text: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        height: Dp = 46.dp,
        shape: Shape = RoundedCornerShape(14.dp),
        animatedShine: Boolean = false
    ) {
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (pressed) 0.98f else 1f,
            animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
            label = "pressScale"
        )
        val gradient = softGradient()

        Button(
            onClick = onClick,
            enabled = enabled,
            interactionSource = interaction,
            shape = shape,
            contentPadding = PaddingValues(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = Color(0xFF231A0E),
                disabledContainerColor = CloakColors.Elevated,
                disabledContentColor = CloakColors.TextFaint
            ),
            modifier = modifier
                .height(height)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .background(
                    brush = if (enabled) gradient
                    else Brush.horizontalGradient(listOf(CloakColors.Elevated, CloakColors.Elevated)),
                    shape = shape
                )
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 18.dp)) {
                Text(
                    text = text,
                    fontFamily = com.cloakdroid.ui.theme.InterFontFamily,
                    fontSize = 15.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
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
        height: Dp = 44.dp,
        shape: Shape = RoundedCornerShape(14.dp)
    ) {
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (pressed) 0.98f else 1f,
            animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
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
                Text(
                    text = text,
                    fontFamily = com.cloakdroid.ui.theme.InterFontFamily,
                    fontSize = 15.sp
                )
            }
        }
    }
}
