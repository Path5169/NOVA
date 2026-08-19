package com.nova.app.feature.private_space.vault

enum class VaultItemType { IMAGE, DOCUMENT, TEXT_NOTE, BOOKMARK, OTHER }

data class VaultItem(
    val id: String,
    val type: VaultItemType,
    val title: String,
    val mimeType: String,
    val sizeBytes: Long,
    val createdAtMillis: Long,
    val storedFileName: String,
    /** Only populated for BOOKMARK items — the URL text itself, kept encrypted like everything else. */
    val bookmarkUrl: String? = null
)
