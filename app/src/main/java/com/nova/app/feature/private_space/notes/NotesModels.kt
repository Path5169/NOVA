package com.nova.app.feature.private_space.notes

data class PrivateNote(
    val id: String,
    val title: String,
    val text: String,
    val tags: List<String>,
    val createdAtMillis: Long,
    val modifiedAtMillis: Long
)
