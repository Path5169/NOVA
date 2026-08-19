package com.nova.app.feature.tools

import android.util.Base64
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nova.app.ui.components.NovaCard
import com.nova.app.ui.theme.NovaAccent
import com.nova.app.ui.theme.NovaTextPrimary
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.UUID

// ---------- Base64 ----------

@Composable
fun Base64ToolScreen() {
    var input by remember { mutableStateOf("") }
    var encoding by remember { mutableStateOf(true) }
    var output by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    fun run() {
        error = false
        output = try {
            if (encoding) {
                Base64.encodeToString(input.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            } else {
                String(Base64.decode(input, Base64.DEFAULT), Charsets.UTF_8)
            }
        } catch (e: Exception) {
            error = true
            "Invalid Base64 input"
        }
    }

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Base64 Encode/Decode", style = MaterialTheme.typography.titleLarge, color = NovaTextPrimary)

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(selected = encoding, onClick = { encoding = true; run() }, label = { Text("Encode") })
            FilterChip(selected = !encoding, onClick = { encoding = false; run() }, label = { Text("Decode") })
        }

        ToolTextField(value = input, onValueChange = { input = it; run() }, label = "Input")
        ToolResultCard(title = if (encoding) "BASE64" else "DECODED TEXT", value = output)
    }
}

// ---------- URL encode/decode ----------

@Composable
fun UrlEncodeToolScreen() {
    var input by remember { mutableStateOf("") }
    var encoding by remember { mutableStateOf(true) }
    var output by remember { mutableStateOf("") }

    fun run() {
        output = try {
            if (encoding) URLEncoder.encode(input, "UTF-8") else URLDecoder.decode(input, "UTF-8")
        } catch (e: Exception) {
            "Invalid input for URL ${if (encoding) "encoding" else "decoding"}"
        }
    }

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("URL Encode/Decode", style = MaterialTheme.typography.titleLarge, color = NovaTextPrimary)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(selected = encoding, onClick = { encoding = true; run() }, label = { Text("Encode") })
            FilterChip(selected = !encoding, onClick = { encoding = false; run() }, label = { Text("Decode") })
        }
        ToolTextField(value = input, onValueChange = { input = it; run() }, label = "Input")
        ToolResultCard(title = if (encoding) "ENCODED" else "DECODED", value = output)
    }
}

// ---------- JSON formatter / validator ----------

@Composable
fun JsonFormatterScreen() {
    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var isValid by remember { mutableStateOf<Boolean?>(null) }
    var errorMessage by remember { mutableStateOf("") }

    fun format() {
        if (input.isBlank()) {
            output = ""; isValid = null; return
        }
        try {
            val parsed = JSONTokener(input).nextValue()
            output = when (parsed) {
                is JSONObject -> parsed.toString(2)
                is JSONArray -> parsed.toString(2)
                else -> parsed.toString()
            }
            isValid = true
        } catch (e: Exception) {
            isValid = false
            errorMessage = e.message ?: "Invalid JSON"
            output = ""
        }
    }

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("JSON Formatter", style = MaterialTheme.typography.titleLarge, color = NovaTextPrimary)
        ToolTextField(value = input, onValueChange = { input = it; format() }, label = "Paste JSON", isError = isValid == false)

        when (isValid) {
            true -> ToolResultCard(title = "FORMATTED · VALID", value = output)
            false -> NovaCard {
                Text("✕ Invalid JSON", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(6.dp))
                Text(errorMessage, style = MaterialTheme.typography.bodyMedium, color = NovaTextPrimary.copy(alpha = 0.6f))
            }
            null -> {}
        }
    }
}

// ---------- UUID generator ----------

@Composable
fun UuidGeneratorScreen() {
    var uuids by remember { mutableStateOf(listOf(UUID.randomUUID().toString())) }

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("UUID Generator", style = MaterialTheme.typography.titleLarge, color = NovaTextPrimary)

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { uuids = listOf(UUID.randomUUID().toString()) + uuids },
                colors = ButtonDefaults.buttonColors(containerColor = NovaAccent)
            ) { Text("Generate", color = androidx.compose.ui.graphics.Color(0xFF0A0D12)) }
            OutlinedButton(onClick = {
                uuids = (1..10).map { UUID.randomUUID().toString() } + uuids
            }) { Text("Generate ×10") }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(uuids) { id -> ToolResultCard(title = "UUID v4", value = id) }
        }
    }
}

// ---------- Hash generator ----------

private val hashAlgorithms = listOf("MD5", "SHA-1", "SHA-256", "SHA-512")

@Composable
fun HashGeneratorScreen() {
    var input by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(mapOf<String, String>()) }

    fun run() {
        results = hashAlgorithms.associateWith { algo ->
            if (input.isEmpty()) "" else {
                val digest = MessageDigest.getInstance(algo).digest(input.toByteArray(Charsets.UTF_8))
                digest.joinToString("") { "%02x".format(it) }
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Hash Generator", style = MaterialTheme.typography.titleLarge, color = NovaTextPrimary)
        ToolTextField(value = input, onValueChange = { input = it; run() }, label = "Input text", minLines = 2)

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(hashAlgorithms) { algo ->
                ToolResultCard(title = algo, value = results[algo] ?: "")
            }
        }
    }
}
