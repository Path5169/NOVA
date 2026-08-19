package com.nova.app.feature.detective.url

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nova.app.core.rememberNovaHaptics
import com.nova.app.feature.detective.DetectiveAction
import com.nova.app.feature.detective.DetectiveFinding
import com.nova.app.feature.detective.DetectiveReport
import com.nova.app.feature.detective.DetectiveReportCard
import com.nova.app.feature.tools.copyToClipboard
import com.nova.app.ui.components.NovaCard
import com.nova.app.ui.components.NovaLoadingState
import com.nova.app.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UrlInspectorScreen() {
    val context = LocalContext.current
    val haptics = rememberNovaHaptics()
    val scope = rememberCoroutineScope()

    var input by remember { mutableStateOf("") }
    var checkRedirects by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<UrlInspectionResult?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column {
            Text("URL Inspector", style = MaterialTheme.typography.headlineMedium, color = NovaTextPrimary)
            Text("Don't blindly trust it. Inspect it.", style = MaterialTheme.typography.bodyMedium, color = NovaTextTertiary)
        }

        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("Paste a URL") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NovaAccent, cursorColor = NovaAccent)
        )

        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Checkbox(
                checked = checkRedirects, onCheckedChange = { checkRedirects = it },
                colors = CheckboxDefaults.colors(checkedColor = NovaAccent)
            )
            Text("Check for redirects (requires a live connection)", style = MaterialTheme.typography.bodyMedium, color = NovaTextSecondary)
        }

        Button(
            onClick = {
                haptics.tap()
                loading = true
                result = null
                scope.launch {
                    result = UrlInspector.inspect(input.trim(), checkRedirects)
                    loading = false
                }
            },
            enabled = input.isNotBlank() && !loading,
            colors = ButtonDefaults.buttonColors(containerColor = NovaAccent),
            modifier = Modifier.fillMaxWidth()
        ) { Text("INSPECT", color = NovaBackground) }

        if (loading) NovaCard { NovaLoadingState("Analyzing URL structure…") }

        result?.let { r ->
            DetectiveReportCard(
                DetectiveReport(
                    verdict = r.verdict,
                    subjectLabel = "${r.scheme}://${r.host}${if (r.port !in listOf(80, 443)) ":${r.port}" else ""}${r.path}",
                    findings = r.findings,
                    explanation = r.explanation,
                    actions = listOf(
                        DetectiveAction("Copy") {
                            copyToClipboard(context, "URL", input.trim())
                            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                        },
                        DetectiveAction("Open anyway") {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(input.trim())))
                            } catch (e: Exception) {
                                Toast.makeText(context, "Couldn't open this URL", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                )
            )

            NovaCard {
                Text("Structure", style = MaterialTheme.typography.titleMedium, color = NovaTextPrimary)
                Spacer(Modifier.height(10.dp))
                StructureRow("Scheme", r.scheme)
                StructureRow("Domain", r.registrableDomain)
                StructureRow("Subdomain", r.subdomain ?: "—")
                StructureRow("Port", "${r.port}")
                StructureRow("Path", r.path.ifEmpty { "/" })
                StructureRow("Query params", if (r.queryParams.isEmpty()) "None" else r.queryParams.joinToString { it.first })
                StructureRow("Certificate issuer", r.certificateIssuer ?: "Not checked / unavailable")
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun StructureRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = NovaTextTertiary)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = NovaTextPrimary, maxLines = 1)
    }
}
