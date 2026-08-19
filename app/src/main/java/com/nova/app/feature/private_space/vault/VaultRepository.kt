package com.nova.app.feature.private_space.vault

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.nova.app.feature.private_space.security.NovaVaultCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * NOVA Private Vault — LOCAL ONLY.
 *
 * Every imported item's bytes are encrypted at rest with [NovaVaultCrypto] (Android Keystore
 * backed AES-256-GCM via AndroidX Security). The item index (titles, types, sizes, timestamps)
 * is itself stored as one more encrypted file, never a plaintext database. Nothing in this
 * class makes a network call, and nothing here ever will — that's the entire point of the
 * Vault. Content only leaves this class when the user explicitly taps Export or Share.
 */
class VaultRepository(private val context: Context) {

    private val crypto = NovaVaultCrypto(context)
    private fun indexFile(): File = File(context.filesDir.resolve("nova_private_vault"), "index.enc")

    private fun loadIndex(): MutableList<VaultItem> {
        val bytes = crypto.readDecrypted(indexFile()) ?: return mutableListOf()
        val array = JSONArray(String(bytes, Charsets.UTF_8))
        val items = mutableListOf<VaultItem>()
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            items.add(
                VaultItem(
                    id = o.getString("id"),
                    type = VaultItemType.valueOf(o.getString("type")),
                    title = o.getString("title"),
                    mimeType = o.getString("mimeType"),
                    sizeBytes = o.getLong("sizeBytes"),
                    createdAtMillis = o.getLong("createdAtMillis"),
                    storedFileName = o.getString("storedFileName"),
                    bookmarkUrl = o.optString("bookmarkUrl", "").ifEmpty { null }
                )
            )
        }
        return items
    }

    private fun saveIndex(items: List<VaultItem>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("type", item.type.name)
                    put("title", item.title)
                    put("mimeType", item.mimeType)
                    put("sizeBytes", item.sizeBytes)
                    put("createdAtMillis", item.createdAtMillis)
                    put("storedFileName", item.storedFileName)
                    if (item.bookmarkUrl != null) put("bookmarkUrl", item.bookmarkUrl)
                }
            )
        }
        crypto.writeEncrypted(indexFile(), array.toString().toByteArray(Charsets.UTF_8))
    }

    suspend fun listItems(query: String = ""): List<VaultItem> = withContext(Dispatchers.IO) {
        val items = loadIndex().sortedByDescending { it.createdAtMillis }
        if (query.isBlank()) items
        else items.filter { it.title.contains(query, ignoreCase = true) }
    }

    /** Imports a file the user picked via SAF (Storage Access Framework) into the encrypted vault. */
    suspend fun importFromUri(uri: Uri, type: VaultItemType): Result<VaultItem> = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val displayName = queryDisplayName(uri) ?: "Imported file"
            val mimeType = resolver.getType(uri) ?: "application/octet-stream"
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext Result.failure(IllegalStateException("Couldn't read the selected file."))

            val id = UUID.randomUUID().toString()
            val storedFileName = "$id.bin"
            crypto.writeEncrypted(crypto.vaultFile(storedFileName), bytes)

            val item = VaultItem(
                id = id,
                type = type,
                title = displayName,
                mimeType = mimeType,
                sizeBytes = bytes.size.toLong(),
                createdAtMillis = System.currentTimeMillis(),
                storedFileName = storedFileName
            )
            val items = loadIndex()
            items.add(item)
            saveIndex(items)
            Result.success(item)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Saves a plain-text private note or bookmark URL directly (no file picker needed). */
    suspend fun addTextItem(title: String, text: String, type: VaultItemType, bookmarkUrl: String? = null): Result<VaultItem> =
        withContext(Dispatchers.IO) {
            try {
                val id = UUID.randomUUID().toString()
                val storedFileName = "$id.bin"
                val bytes = text.toByteArray(Charsets.UTF_8)
                crypto.writeEncrypted(crypto.vaultFile(storedFileName), bytes)

                val item = VaultItem(
                    id = id,
                    type = type,
                    title = title.ifBlank { "Untitled" },
                    mimeType = "text/plain",
                    sizeBytes = bytes.size.toLong(),
                    createdAtMillis = System.currentTimeMillis(),
                    storedFileName = storedFileName,
                    bookmarkUrl = bookmarkUrl
                )
                val items = loadIndex()
                items.add(item)
                saveIndex(items)
                Result.success(item)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun readContent(item: VaultItem): ByteArray? = withContext(Dispatchers.IO) {
        crypto.readDecrypted(crypto.vaultFile(item.storedFileName))
    }

    suspend fun delete(item: VaultItem) = withContext(Dispatchers.IO) {
        crypto.deleteEncrypted(crypto.vaultFile(item.storedFileName))
        val items = loadIndex()
        items.removeAll { it.id == item.id }
        saveIndex(items)
    }

    /** Decrypts an item and writes plaintext bytes to [destination] (a URI the user chose to export to). */
    suspend fun exportTo(item: VaultItem, destination: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val bytes = readContent(item) ?: return@withContext Result.failure(IllegalStateException("Item not found."))
            context.contentResolver.openOutputStream(destination)?.use { it.write(bytes) }
                ?: return@withContext Result.failure(IllegalStateException("Couldn't open the export destination."))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
