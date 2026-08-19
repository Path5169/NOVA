package com.nova.app.feature.detective

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nova.app.ui.components.NovaSectionHeader
import com.nova.app.ui.components.NovaStatus
import com.nova.app.ui.components.toColor
import com.nova.app.ui.theme.*

/** Overall verdict banner shown at the top of a Detective Report — never "malicious", only observed signal strength. */
enum class DetectiveVerdict(val label: String, val status: NovaStatus) {
    SAFE_LOOKING("SAFE-LOOKING", NovaStatus.GOOD),
    REVIEW("REVIEW", NovaStatus.WARN),
    SUSPICIOUS_PATTERN("SUSPICIOUS PATTERN", NovaStatus.BAD)
}

/** A single line in the FINDINGS list — check for normal, warning for worth-reviewing. */
data class DetectiveFinding(val label: String, val positive: Boolean, val detail: String? = null)

/** An action offered at the bottom of a report card. */
data class DetectiveAction(val label: String, val onClick: () -> Unit)

data class DetectiveReport(
    val verdict: DetectiveVerdict,
    val subjectLabel: String,
    val findings: List<DetectiveFinding>,
    val explanation: String,
    val actions: List<DetectiveAction> = emptyList()
)

/**
 * The signature "case file" surface for Digital Detective. Distinct from a plain NovaCard on
 * purpose — a colored severity stripe down the left edge, a large animated verdict readout, and
 * a staggered reveal of findings, so a scan result reads as evidence being laid out rather than
 * a settings row.
 */
@Composable
fun DetectiveReportCard(report: DetectiveReport) {
    val accent = report.verdict.status.toColor()

    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(16.dp))
            .background(NovaSurface)
            .border(1.dp, NovaSurfaceOutline, RoundedCornerShape(16.dp))
    ) {
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(accent)
        )
        Column(Modifier.padding(18.dp)) {
            VerdictHeadline(report.verdict)
            Spacer(Modifier.height(6.dp))
            Text(report.subjectLabel, style = MaterialTheme.typography.bodyMedium, color = NovaTextTertiary, maxLines = 2)

            Spacer(Modifier.height(20.dp))
            NovaSectionHeader("Findings")
            Spacer(Modifier.height(10.dp))
            report.findings.forEachIndexed { index, finding ->
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(240, delayMillis = index * 70)) +
                        slideInHorizontally(tween(240, delayMillis = index * 70)) { -it / 6 }
                ) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
                        Icon(
                            if (finding.positive) Icons.Filled.CheckCircle else Icons.Filled.WarningAmber,
                            contentDescription = null,
                            tint = if (finding.positive) NovaGood else NovaWarn,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(finding.label, style = MaterialTheme.typography.bodyMedium, color = NovaTextSecondary)
                            if (finding.detail != null) {
                                Text(finding.detail, style = MaterialTheme.typography.labelSmall, color = NovaTextTertiary)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(NovaSurfaceRaised)
                    .padding(14.dp)
            ) {
                Column {
                    NovaSectionHeader("What this means")
                    Spacer(Modifier.height(8.dp))
                    Text(report.explanation, style = MaterialTheme.typography.bodyMedium, color = NovaTextSecondary)
                }
            }

            if (report.actions.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    report.actions.forEach { action -> ActionPill(action) }
                }
            }
        }
    }
}

@Composable
private fun VerdictHeadline(verdict: DetectiveVerdict) {
    val accent = verdict.status.toColor()
    val infinite = rememberInfiniteTransition(label = "verdictPulse")
    val pulse = infinite.animateFloat(
        initialValue = 0.5f,
        targetValue = if (verdict == DetectiveVerdict.SAFE_LOOKING) 0.5f else 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "verdictPulseAlpha"
    ).value

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = pulse))
        )
        Spacer(Modifier.width(10.dp))
        Text(
            verdict.label,
            style = MaterialTheme.typography.titleLarge,
            color = accent,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ActionPill(action: DetectiveAction) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(NovaAccent.copy(alpha = 0.12f))
            .border(1.dp, NovaAccent.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .clickable(onClick = action.onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(action.label.uppercase(), style = MaterialTheme.typography.labelMedium, color = NovaAccent, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(2.dp))
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = NovaAccent, modifier = Modifier.size(14.dp))
    }
}
