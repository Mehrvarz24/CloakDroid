package com.cloakdroid.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * CloakDroid UI kit:
 *  - GlassCard: glassmorphic container (white/5 fill, 10% border, 16dp radius)
 *  - PillBadge: rounded-full capsule for tags / status / dates
 *  - MetallicHeadline: large gradient display text
 *  - StatusDot: glowing colored dot
 */
object CloakUI {

    /** Glassmorphic card: subtle white fill + hairline border. */
    @Composable
    fun GlassCard(
        modifier: Modifier = Modifier,
        cornerRadius: Dp = 16.dp,
        content: @Composable ColumnScope.() -> Unit
    ) {
        Column(
            modifier = modifier
                .clip(RoundedCornerShape(cornerRadius))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
                .border(1.dp, CloakColors.GlassBorder, RoundedCornerShape(cornerRadius))
                .padding(16.dp),
            content = content
        )
    }

    /** Pill-shaped capsule badge. */
    @Composable
    fun PillBadge(
        text: String,
        modifier: Modifier = Modifier,
        color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
        container: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        dotColor: Color? = null
    ) {
        Row(
            modifier = modifier
                .clip(RoundedCornerShape(50))
                .background(container)
                .padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (dotColor != null) {
                StatusDot(dotColor, size = 7.dp)
                Row(modifier = Modifier.padding(start = 6.dp)) {}
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    /** Metallic gradient display headline with a soft brand glow. */
    @Composable
    fun MetallicHeadline(
        text: String,
        modifier: Modifier = Modifier
    ) {
        Column(modifier = modifier) {
            // NOTE: the previous variant rendered the same text twice, the copy
            // behind with Modifier.blur(22.dp). On several devices (notably
            // Samsung Exynos, e.g. SM-A725F) RenderEffect-based blur of text
            // renders as an unreadable smudged strip instead of a glow, so the
            // glow layer was replaced with a plain translucent shadow.
            Text(
                text = text,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
                modifier = Modifier.graphicsLayer(alpha = 0.25f)
            )
            Text(
                text = text,
                style = androidx.compose.ui.text.TextStyle(
                    brush = Brush.linearGradient(CloakColors.Metallic),
                    fontSize = MaterialTheme.typography.displaySmall.fontSize,
                    lineHeight = MaterialTheme.typography.displaySmall.lineHeight,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
                    letterSpacing = MaterialTheme.typography.displaySmall.letterSpacing
                )
            )
        }
    }

    @Composable
    fun StatusDot(color: Color, size: Dp = 8.dp) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(color)
        )
    }
}

