package com.nova.app.feature.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.nova.app.ui.components.NovaCard
import com.nova.app.ui.theme.NovaAccent
import com.nova.app.ui.theme.NovaTextPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    monospace: Boolean = true,
    minLines: Int = 3,
    isError: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        minLines = minLines,
        maxLines = 8,
        isError = isError,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = NovaAccent,
            cursorColor = NovaAccent
        )
    )
}

@Composable
fun ToolResultCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    NovaCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = NovaTextPrimary.copy(alpha = 0.5f))
            Text(
                "COPY",
                style = MaterialTheme.typography.labelMedium,
                color = NovaAccent,
                modifier = Modifier.clickable {
                    copyToClipboard(context, title, value)
                    Toast.makeText(context, "$title copied", Toast.LENGTH_SHORT).show()
                }
            )
        }
        Spacer(Modifier.height(8.dp))
        SelectionContainerText(value)
    }
}

@Composable
private fun SelectionContainerText(value: String) {
    androidx.compose.foundation.text.selection.SelectionContainer {
        Text(
            value.ifBlank { "—" },
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            color = NovaTextPrimary
        )
    }
}

fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}
