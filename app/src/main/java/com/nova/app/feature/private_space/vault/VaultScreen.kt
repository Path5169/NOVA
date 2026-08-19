package com.nova.app.feature.private_space.vault

import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nova.app.core.rememberNovaHaptics
import com.nova.app.ui.components.*
import com.nova.app.ui.theme.*
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(viewModel: VaultViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val haptics = rememberNovaHaptics()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedItem by remember { mutableStateOf<VaultItem?>(null) }
    var pendingImportType by remember { mutableStateOf(VaultItemType.DOCUMENT) }
    var showAddSheet by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            haptics.tap()
            viewModel.importFile(uri, pendingImportType)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri: Uri? ->
        val item = selectedItem
        if (uri != null && item != null) {
            viewModel.exportTo(item, uri) { success ->
                Toast.makeText(context, if (success) "Exported" else "Export failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(state.error) {
        if (state.error != null) {
            Toast.makeText(context, state.error, Toast.LENGTH_SHORT).show()
            viewModel.clearError()
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column {
                    Text("Vault", style = MaterialTheme.typography.headlineMedium, color = NovaTextPrimary)
                    Spacer(Modifier.height(4.dp))
                    NovaChip("LOCAL ONLY — never uploaded", status = NovaStatus.GOOD)
                }
            }

            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    label = { Text("Search vault") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NovaAccent, cursorColor = NovaAccent)
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ImportChip("Import file") {
                        pendingImportType = VaultItemType.DOCUMENT
                        importLauncher.launch(arrayOf("*/*"))
                    }
                    ImportChip("Import image") {
                        pendingImportType = VaultItemType.IMAGE
                        importLauncher.launch(arrayOf("image/*"))
                    }
                    ImportChip("Add note/bookmark") {
                        showAddSheet = true
                    }
                }
            }

            if (state.loading) {
                item { NovaCard { NovaLoadingState("Reading vault…") } }
            } else if (state.items.isEmpty()) {
                item { NovaCard { NovaUnavailableState("Nothing in the vault yet. Import a file or add a note to get started.") } }
            } else {
                itemsIndexed(state.items) { index, item ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(tween(260, delayMillis = index * 40)) +
                            slideInVertically(tween(260, delayMillis = index * 40)) { it / 5 }
                    ) {
                        VaultRow(item, onClick = { selectedItem = item }, onDelete = {
                            haptics.warning()
                            viewModel.delete(item)
                        })
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }

    val current = selectedItem
    if (current != null) {
        VaultItemPreviewSheet(
            item = current,
            viewModel = viewModel,
            onDismiss = { selectedItem = null },
            onExport = { exportLauncher.launch(current.title) },
            onDelete = {
                viewModel.delete(current)
                selectedItem = null
            }
        )
    }

    if (showAddSheet) {
        AddNoteOrBookmarkSheet(
            onDismiss = { showAddSheet = false },
            onSaveNote = { title, text ->
                viewModel.addNoteOrBookmark(title, text, VaultItemType.TEXT_NOTE)
                showAddSheet = false
            },
            onSaveBookmark = { title, url ->
                viewModel.addNoteOrBookmark(title, url, VaultItemType.BOOKMARK, bookmarkUrl = url)
                showAddSheet = false
            }
        )
    }
}

@Composable
private fun ImportChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .background(NovaSurface)
            .border(1.dp, NovaSurfaceOutline, androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = NovaAccent)
    }
}

@Composable
private fun VaultRow(item: VaultItem, onClick: () -> Unit, onDelete: () -> Unit) {
    val (typeColor, typeLabel) = typeMeta(item.type)
    NovaCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                        .background(typeColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(iconFor(item.type), contentDescription = null, tint = typeColor, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.title, style = MaterialTheme.typography.bodyLarge, color = NovaTextPrimary, maxLines = 1)
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            typeLabel.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = typeColor
                        )
                        Text(
                            "  ·  ${formatSize(item.sizeBytes)}  ·  ${DateFormat.getDateInstance().format(Date(item.createdAtMillis))}",
                            style = MaterialTheme.typography.labelSmall,
                            color = NovaTextTertiary
                        )
                    }
                }
            }
            Icon(
                Icons.Filled.DeleteOutline,
                contentDescription = "Delete",
                tint = NovaTextTertiary,
                modifier = Modifier.size(20.dp).clickable(onClick = onDelete)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaultItemPreviewSheet(
    item: VaultItem,
    viewModel: VaultViewModel,
    onDismiss: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    var textContent by remember(item.id) { mutableStateOf<String?>(null) }
    var imageBitmap by remember(item.id) { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(item.id) {
        val bytes = viewModel.readContent(item) ?: return@LaunchedEffect
        when (item.type) {
            VaultItemType.TEXT_NOTE, VaultItemType.BOOKMARK -> textContent = String(bytes, Charsets.UTF_8)
            VaultItemType.IMAGE -> imageBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            else -> Unit
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = NovaSurface) {
        Column(Modifier.padding(20.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleLarge, color = NovaTextPrimary)
            Spacer(Modifier.height(4.dp))
            Text("${item.mimeType} · ${formatSize(item.sizeBytes)}", style = MaterialTheme.typography.bodySmall, color = NovaTextTertiary)
            Spacer(Modifier.height(16.dp))

            when {
                imageBitmap != null -> Image(
                    bitmap = imageBitmap!!.asImageBitmap(),
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxWidth().height(240.dp)
                )
                textContent != null -> NovaCard {
                    Text(textContent!!, style = MaterialTheme.typography.bodyMedium, color = NovaTextSecondary)
                }
                else -> NovaUnavailableState("No inline preview available for this file type. Use Export to view it in another app.")
            }

            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = onExport, colors = ButtonDefaults.filledTonalButtonColors(containerColor = NovaSurfaceRaised)) {
                    Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Export")
                }
                FilledTonalButton(onClick = onDelete, colors = ButtonDefaults.filledTonalButtonColors(containerColor = NovaSurfaceRaised)) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = null, tint = NovaBad, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Delete", color = NovaBad)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddNoteOrBookmarkSheet(
    onDismiss: () -> Unit,
    onSaveNote: (title: String, text: String) -> Unit,
    onSaveBookmark: (title: String, url: String) -> Unit
) {
    var isBookmark by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = NovaSurface) {
        Column(Modifier.padding(20.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(selected = !isBookmark, onClick = { isBookmark = false }, label = { Text("Note") })
                FilterChip(selected = isBookmark, onClick = { isBookmark = true }, label = { Text("Bookmark") })
            }
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text("Title") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NovaAccent, cursorColor = NovaAccent)
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = body, onValueChange = { body = it },
                label = { Text(if (isBookmark) "URL" else "Note text") },
                modifier = Modifier.fillMaxWidth(), minLines = if (isBookmark) 1 else 4,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NovaAccent, cursorColor = NovaAccent)
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    if (isBookmark) onSaveBookmark(title, body) else onSaveNote(title, body)
                },
                colors = ButtonDefaults.buttonColors(containerColor = NovaAccent),
                enabled = body.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save to Vault", color = NovaBackground) }
            Spacer(Modifier.height(12.dp))
        }
    }
}

private fun iconFor(type: VaultItemType) = when (type) {
    VaultItemType.IMAGE -> Icons.Filled.Image
    VaultItemType.DOCUMENT -> Icons.Filled.Description
    VaultItemType.TEXT_NOTE -> Icons.Filled.Notes
    VaultItemType.BOOKMARK -> Icons.Filled.Bookmark
    VaultItemType.OTHER -> Icons.Filled.InsertDriveFile
}

private fun typeMeta(type: VaultItemType): Pair<androidx.compose.ui.graphics.Color, String> = when (type) {
    VaultItemType.IMAGE -> NovaAccent to "Image"
    VaultItemType.DOCUMENT -> NovaWarn to "Document"
    VaultItemType.TEXT_NOTE -> NovaGood to "Note"
    VaultItemType.BOOKMARK -> androidx.compose.ui.graphics.Color(0xFF7C9CE8) to "Bookmark"
    VaultItemType.OTHER -> NovaNeutral to "File"
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
}
