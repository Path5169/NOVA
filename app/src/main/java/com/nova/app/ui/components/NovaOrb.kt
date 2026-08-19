package com.nova.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.nova.app.ui.theme.NovaAccent
import com.nova.app.ui.theme.NovaBad
import com.nova.app.ui.theme.NovaTextPrimary
import com.nova.app.ui.theme.NovaTextTertiary
import com.nova.app.ui.theme.NovaWarn
import kotlin.math.cos
import kotlin.math.sin

/** What the orb is currently reflecting — drives color and animation speed, nothing decorative-only. */
enum class NovaOrbState { IDLE, WORKING, GOOD, WARN, BAD }

private fun NovaOrbState.color(): Color = when (this) {
    NovaOrbState.IDLE -> NovaAccent
    NovaOrbState.WORKING -> NovaAccent
    NovaOrbState.GOOD -> NovaAccent
    NovaOrbState.WARN -> NovaWarn
    NovaOrbState.BAD -> NovaBad
}

/**
 * NOVA's visual centerpiece. A breathing core with a thin rotating ring — the "this is a real
 * environment, not a launcher" signal the spec asks for. Deliberately restrained: one accent
 * color, no particle effects, no neon bloom. Tapping it is the fastest way to run a scan.
 *
 * [label]/[sublabel] sit inside the ring so the orb still communicates real state (e.g. "12
 * modules • all normal") rather than existing as pure decoration.
 */
@Composable
fun NovaOrb(
    state: NovaOrbState,
    label: String,
    sublabel: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val color = state.color()
    val infinite = rememberInfiniteTransition(label = "novaOrb")

    val breathSpeed = if (state == NovaOrbState.WORKING) 700 else 2600
    val breath by infinite.animateFloat(
        initialValue = 0.94f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(breathSpeed, easing = LinearEasing), RepeatMode.Reverse),
        label = "novaOrbBreath"
    )

    val ringRotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            tween(if (state == NovaOrbState.WORKING) 2200 else 9000, easing = LinearEasing)
        ),
        label = "novaOrbRing"
    )

    val glowAlpha by animateFloatAsState(
        targetValue = if (state == NovaOrbState.WARN || state == NovaOrbState.BAD) 0.5f else 0.28f,
        animationSpec = tween(300),
        label = "novaOrbGlow"
    )

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(180.dp)
            .then(
                if (onClick != null)
                    Modifier.clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(180.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val coreRadius = (size.minDimension / 2f) * 0.52f * breath

            // Soft ambient glow behind the core
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = glowAlpha), Color.Transparent),
                    center = center,
                    radius = coreRadius * 2.2f
                ),
                radius = coreRadius * 2.2f,
                center = center
            )

            // Core disc
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = 0.9f), color.copy(alpha = 0.35f)),
                    center = center,
                    radius = coreRadius
                ),
                radius = coreRadius,
                center = center
            )

            // Thin rotating instrument ring with tick marks
            val ringRadius = size.minDimension / 2f * 0.88f
            drawCircle(
                color = color.copy(alpha = 0.22f),
                radius = ringRadius,
                center = center,
                style = Stroke(width = 1.6.dp.toPx())
            )

            val tickCount = 24
            for (i in 0 until tickCount) {
                val angleDeg = ringRotation + (360f / tickCount) * i
                val angleRad = Math.toRadians(angleDeg.toDouble())
                val isMajor = i % 6 == 0
                val inner = ringRadius - (if (isMajor) 8.dp.toPx() else 4.dp.toPx())
                val outer = ringRadius
                val start = Offset(
                    x = center.x + (cos(angleRad) * inner).toFloat(),
                    y = center.y + (sin(angleRad) * inner).toFloat()
                )
                val end = Offset(
                    x = center.x + (cos(angleRad) * outer).toFloat(),
                    y = center.y + (sin(angleRad) * outer).toFloat()
                )
                drawLine(
                    color = color.copy(alpha = if (isMajor) 0.65f else 0.25f),
                    start = start,
                    end = end,
                    strokeWidth = if (isMajor) 1.6.dp.toPx() else 1.dp.toPx()
                )
            }
        }

        Box(contentAlignment = Alignment.Center) {
            androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = label, style = MaterialTheme.typography.titleMedium, color = NovaTextPrimary)
                Text(text = sublabel, style = MaterialTheme.typography.labelSmall, color = NovaTextTertiary)
            }
        }
    }
}
