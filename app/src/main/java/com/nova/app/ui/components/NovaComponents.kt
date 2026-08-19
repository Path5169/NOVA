package com.nova.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nova.app.ui.theme.*
import kotlinx.coroutines.delay

/** Status of a metric or diagnostic — drives the accent dot color. Never decorative-only. */
enum class NovaStatus { GOOD, WARN, BAD, NEUTRAL, UNAVAILABLE }

fun NovaStatus.toColor(): Color = when (this) {
    NovaStatus.GOOD -> NovaGood
    NovaStatus.WARN -> NovaWarn
    NovaStatus.BAD -> NovaBad
    NovaStatus.NEUTRAL -> NovaNeutral
    NovaStatus.UNAVAILABLE -> NovaTextTertiary
}

/**
 * Base surface card used throughout NOVA. Flat, bordered, no gradients/glow by default.
 * When tappable, it settles with a small physical scale-down on press — "cards respond to
 * touch" per the NOVA design brief — rather than a static ripple alone.
 */
@Composable
fun NovaCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "novaCardScale"
    )
    // A faint accent-tinted border on press — reinforces the physical "settle" of the scale
    // without introducing glow/gradient decoration outside of genuine state.
    val borderColor by animateFloatAsState(
        targetValue = if (pressed && onClick != null) 1f else 0f,
        animationSpec = tween(if (pressed) 90 else 220, easing = FastOutSlowInEasing),
        label = "novaCardBorder"
    )
    val outline = androidx.compose.ui.graphics.lerp(NovaSurfaceOutline, NovaAccentDim.copy(alpha = 0.6f), borderColor)

    val base = Modifier
        .fillMaxWidth()
        .then(if (onClick != null) Modifier.scale(scale) else Modifier)
        .clip(RoundedCornerShape(16.dp))
        .background(NovaSurface)
        .border(1.dp, outline, RoundedCornerShape(16.dp))
        .then(if (onClick != null) Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        ) else Modifier)
        .then(modifier)

    Column(modifier = base.padding(18.dp), content = content)
}

/** Small uppercase mono label used above section groups — the "instrumentation" typographic tell. */
@Composable
fun NovaSectionHeader(title: String, trailing: String? = null, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = NovaTextTertiary
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelMedium,
                color = NovaTextTertiary
            )
        }
    }
}

/** A single labeled numeric/text readout with a status dot — the core "instrument" primitive. */
@Composable
fun NovaMetric(
    label: String,
    value: String,
    unit: String? = null,
    status: NovaStatus = NovaStatus.NEUTRAL,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(status.toColor())
            )
            Spacer(Modifier.width(10.dp))
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = NovaTextSecondary)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = NovaMonoFont),
                color = NovaTextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            if (unit != null) {
                Spacer(Modifier.width(4.dp))
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = NovaTextTertiary,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
    }
}

/** Large hero readout for a single primary value (e.g. current dB, lux, latency). */
@Composable
fun NovaBigReadout(
    value: String,
    unit: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.Start) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(text = value, style = NovaReadoutStyle, color = NovaAccent)
            Spacer(Modifier.width(6.dp))
            Text(
                text = unit,
                style = MaterialTheme.typography.bodyMedium,
                color = NovaTextTertiary,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = NovaTextTertiary)
    }
}

/** Navigable row entry, e.g. a module shortcut or tool list item. */
@Composable
fun NovaListRow(
    title: String,
    subtitle: String? = null,
    icon: @Composable (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "novaRowScale"
    )
    // The chevron nudges forward on press — a small "this is about to navigate" cue.
    val chevronOffset by animateFloatAsState(
        targetValue = if (pressed) 3f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "novaRowChevron"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Box(Modifier.padding(end = 14.dp)) { icon() }
            }
            Column {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = NovaTextPrimary)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = NovaTextTertiary)
                }
            }
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = NovaTextTertiary,
            modifier = Modifier
                .size(20.dp)
                .offset(x = chevronOffset.dp)
        )
    }
}

/** Explains a runtime permission in plain language before requesting it — never requested silently. */
@Composable
fun NovaPermissionExplainer(
    icon: String,
    title: String,
    explanation: String,
    actionLabel: String,
    onRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    NovaCard(modifier = modifier) {
        Text("$icon $title", style = MaterialTheme.typography.titleMedium, color = NovaTextPrimary)
        Spacer(Modifier.height(8.dp))
        Text(explanation, style = MaterialTheme.typography.bodyMedium, color = NovaTextSecondary)
        Spacer(Modifier.height(14.dp))
        Text(
            text = actionLabel.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = NovaAccent,
            modifier = Modifier.clickable(onClick = onRequest)
        )
    }
}

/** Used when Android simply does not expose a capability — never faked, always explained in-UI. */
@Composable
fun NovaUnavailableState(reason: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = NovaTextTertiary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(reason, style = MaterialTheme.typography.bodyMedium, color = NovaTextTertiary)
    }
}

@Composable
fun NovaLoadingState(label: String = "Reading…", modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = NovaAccent)
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = NovaTextTertiary)
    }
}

/** Small pill tag — used for permission labels, categories, and other short metadata. */
@Composable
fun NovaChip(
    label: String,
    status: NovaStatus = NovaStatus.NEUTRAL,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(status.toColor().copy(alpha = 0.12f))
            .border(1.dp, status.toColor().copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = status.toColor()
        )
    }
}

/** Thin animated progress bar used for scan states — communicates real progress, not a fake spinner. */
@Composable
fun NovaProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(targetValue = progress.coerceIn(0f, 1f), animationSpec = tween(220), label = "novaProgress")
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(NovaSurfaceOutline)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animated)
                .clip(RoundedCornerShape(2.dp))
                .background(NovaAccent)
        )
    }
}

// ---------------------------------------------------------------------------------------
// Motion primitives — small, reusable building blocks that give the rest of the app a
// consistent, physical feel. Every animation here is tied to a real state change (a value
// updating, an item entering/leaving, a status flipping) — never motion for its own sake.
// ---------------------------------------------------------------------------------------

/**
 * Wraps content in a staggered fade + rise entrance, keyed by [index] within its group
 * (a list, a column of cards). Used on first composition of a screen or when a fresh batch
 * of items appears, so a stack of cards reads as "arriving" rather than popping in at once.
 */
@Composable
fun NovaEntrance(
    modifier: Modifier = Modifier,
    index: Int = 0,
    play: Boolean = true,
    content: @Composable AnimatedVisibilityScope.() -> Unit
) {
    var shown by remember { mutableStateOf(!play) }
    LaunchedEffect(play) {
        if (play) {
            delay(30L * index.coerceIn(0, 14))
            shown = true
        }
    }
    AnimatedVisibility(
        visible = shown,
        modifier = modifier,
        enter = fadeIn(tween(320, easing = EaseOutCubic)) +
            slideInVertically(tween(380, easing = FastOutSlowInEasing)) { it / 5 },
        content = content
    )
}

/** A numeric readout that counts smoothly toward [value] instead of snapping — used for
 * live stats/tallies where the delta itself is worth seeing. */
@Composable
fun NovaAnimatedCounter(
    value: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    color: Color = NovaTextPrimary
) {
    val animated by animateIntAsState(
        targetValue = value,
        animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing),
        label = "novaAnimatedCounter"
    )
    Text(
        text = animated.toString(),
        style = style.copy(fontFamily = NovaMonoFont),
        color = color,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
    )
}

/** [NovaMetric] variant whose value counts up/down when it changes, for readouts backed by a
 * running total (blocks, queries, hits) rather than a static reading. */
@Composable
fun NovaAnimatedMetric(
    label: String,
    value: Int,
    status: NovaStatus = NovaStatus.NEUTRAL,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NovaPulsingDot(color = status.toColor(), pulsing = status == NovaStatus.GOOD && value > 0, size = 7.dp)
            Spacer(Modifier.width(6.dp))
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = NovaTextSecondary)
        }
        NovaAnimatedCounter(value = value, style = MaterialTheme.typography.titleMedium, color = NovaTextPrimary)
    }
}

/** A status dot with an optional soft outward pulse — reserved for genuinely "live" states
 * (Shield active, a sensor streaming) rather than decoration. */
@Composable
fun NovaPulsingDot(
    color: Color,
    pulsing: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 8.dp
) {
    val infinite = rememberInfiniteTransition(label = "novaPulsingDot")
    val progress by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing)),
        label = "novaPulsingDotProgress"
    )
    Box(modifier = modifier.size(size * 2.6f), contentAlignment = Alignment.Center) {
        if (pulsing) {
            Box(
                Modifier
                    .size(size)
                    .scale(1f + progress * 1.6f)
                    .clip(CircleShape)
                    .background(color.copy(alpha = (1f - progress) * 0.5f))
            )
        }
        Box(Modifier.size(size).clip(CircleShape).background(color))
    }
}

/** Cross-fades/slides between two short status strings (e.g. "INACTIVE" → "ACTIVE") instead
 * of snapping, so a state flip reads as a transition rather than a layout jump. */
@Composable
fun NovaStatusLabel(
    text: String,
    color: Color,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = text to color,
        modifier = modifier,
        transitionSpec = {
            (fadeIn(tween(220, easing = FastOutSlowInEasing)) +
                slideInVertically(tween(220, easing = FastOutSlowInEasing)) { it / 3 }) togetherWith
                (fadeOut(tween(140)) +
                    slideOutVertically(tween(140)) { -it / 3 })
        },
        label = "novaStatusLabel"
    ) { (label, tint) ->
        Text(label, style = style, color = tint, fontWeight = FontWeight.SemiBold)
    }
}
