package com.nova.app.feature.private_space.notes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nova.app.core.rememberNovaHaptics
import com.nova.app.ui.components.*
import com.nova.app.ui.theme.*
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(viewModel: NotesViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val haptics = rememberNovaHaptics()
    var editing by remember { mutableStateOf<PrivateNote?>(null) }
    var creatingNew by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Notes", style = MaterialTheme.typography.headlineMedium, color = NovaTextPrimary)
                    NovaChip("LOCAL ONLY", status = NovaStatus.GOOD)
                }
            }
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    label = { Text("Search notes") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NovaAccent, cursorColor = NovaAccent)
                )
            }

            if (state.loading) {
                item { NovaCard { NovaLoadingState("Reading notes…") } }
            } else if (state.notes.isEmpty()) {
                item { NovaCard { NovaUnavailableState("No private notes yet. Tap + to write one.") } }
            } else {
                itemsIndexed(state.notes) { index, note ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(tween(260, delayMillis = index * 40)) +
                            slideInVertically(tween(260, delayMillis = index * 40)) { it / 5 }
                    ) {
                        NoteRow(note, onClick = { editing = note }, onDelete = {
                            haptics.warning()
                            viewModel.delete(note.id)
                        })
                    }
                }
            }
            item { Spacer(Modifier.height(64.dp)) }
        }

        FloatingActionButton(
            onClick = { haptics.tap(); creatingNew = true },
            containerColor = NovaAccent,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = "New note", tint = NovaBackground)
        }
    }

    if (editing != null || creatingNew) {
        NoteEditorSheet(
            note = editing,
            onDismiss = { editing = null; creatingNew = false },
            onSave = { title, text, tags ->
                viewModel.save(editing?.id, title, text, tags) {
                    editing = null; creatingNew = false
                }
            }
        )
    }
}

private val noteAccentPalette = listOf(NovaAccent, NovaWarn, androidx.compose.ui.graphics.Color(0xFF7C9CE8), androidx.compose.ui.graphics.Color(0xFFDA8FD6), NovaGood)

private fun accentFor(note: PrivateNote) = noteAccentPalette[Math.floorMod(note.id.hashCode(), noteAccentPalette.size)]

@Composable
private fun NoteRow(note: PrivateNote, onClick: () -> Unit, onDelete: () -> Unit) {
    val accent = accentFor(note)
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .background(NovaSurface)
            .border(1.dp, NovaSurfaceOutline, androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(accent))
        Row(
            Modifier.padding(18.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(Modifier.weight(1f)) {
                Text(note.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.bodyLarge, color = NovaTextPrimary, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                Text(note.text, style = MaterialTheme.typography.bodyMedium, color = NovaTextTertiary, maxLines = 2)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Edited ${DateFormat.getDateInstance().format(Date(note.modifiedAtMillis))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = NovaTextTertiary
                )
                if (note.tags.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        note.tags.take(4).forEach { tag ->
                            Box(
                                Modifier
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                    .background(accent.copy(alpha = 0.12f))
                                    .padding(horizontal = 9.dp, vertical = 4.dp)
                            ) {
                                Text(tag, style = MaterialTheme.typography.labelSmall, color = accent)
                            }
                        }
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
private fun NoteEditorSheet(
    note: PrivateNote?,
    onDismiss: () -> Unit,
    onSave: (title: String, text: String, tags: List<String>) -> Unit
) {
    var title by remember { mutableStateOf(note?.title.orEmpty()) }
    var text by remember { mutableStateOf(note?.text.orEmpty()) }
    var tagsText by remember { mutableStateOf(note?.tags?.joinToString(", ").orEmpty()) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = NovaSurface) {
        Column(Modifier.padding(20.dp)) {
            Text(if (note == null) "New note" else "Edit note", style = MaterialTheme.typography.titleLarge, color = NovaTextPrimary)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text("Title") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NovaAccent, cursorColor = NovaAccent)
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                label = { Text("Note") }, modifier = Modifier.fillMaxWidth(), minLines = 6,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NovaAccent, cursorColor = NovaAccent)
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = tagsText, onValueChange = { tagsText = it },
                label = { Text("Tags (comma separated)") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NovaAccent, cursorColor = NovaAccent)
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    val tags = tagsText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    onSave(title, text, tags)
                },
                colors = ButtonDefaults.buttonColors(containerColor = NovaAccent),
                enabled = text.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save", color = NovaBackground) }
            Spacer(Modifier.height(12.dp))
        }
    }
}
