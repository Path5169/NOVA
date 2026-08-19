package com.nova.app.feature.private_space.notes

import android.content.Context
import com.nova.app.feature.private_space.security.NovaVaultCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * NOVA Private Notes — stored as a single encrypted JSON document (Keystore-backed AES-256-GCM
 * via [NovaVaultCrypto]), inside the same protected local storage as the Vault. Never synced,
 * never networked.
 */
class NotesRepository(context: Context) {

    private val crypto = NovaVaultCrypto(context)

    private fun load(): MutableList<PrivateNote> {
        val bytes = crypto.readDecrypted(crypto.notesStoreFile()) ?: return mutableListOf()
        val array = JSONArray(String(bytes, Charsets.UTF_8))
        val notes = mutableListOf<PrivateNote>()
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            val tags = mutableListOf<String>()
            val tagArray = o.optJSONArray("tags")
            if (tagArray != null) for (t in 0 until tagArray.length()) tags.add(tagArray.getString(t))
            notes.add(
                PrivateNote(
                    id = o.getString("id"),
                    title = o.getString("title"),
                    text = o.getString("text"),
                    tags = tags,
                    createdAtMillis = o.getLong("createdAtMillis"),
                    modifiedAtMillis = o.getLong("modifiedAtMillis")
                )
            )
        }
        return notes
    }

    private fun save(notes: List<PrivateNote>) {
        val array = JSONArray()
        notes.forEach { note ->
            array.put(
                JSONObject().apply {
                    put("id", note.id)
                    put("title", note.title)
                    put("text", note.text)
                    put("tags", JSONArray(note.tags))
                    put("createdAtMillis", note.createdAtMillis)
                    put("modifiedAtMillis", note.modifiedAtMillis)
                }
            )
        }
        crypto.writeEncrypted(crypto.notesStoreFile(), array.toString().toByteArray(Charsets.UTF_8))
    }

    suspend fun list(query: String = ""): List<PrivateNote> = withContext(Dispatchers.IO) {
        val notes = load().sortedByDescending { it.modifiedAtMillis }
        if (query.isBlank()) notes
        else notes.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.text.contains(query, ignoreCase = true) ||
                it.tags.any { tag -> tag.contains(query, ignoreCase = true) }
        }
    }

    suspend fun upsert(id: String?, title: String, text: String, tags: List<String>): PrivateNote = withContext(Dispatchers.IO) {
        val notes = load()
        val now = System.currentTimeMillis()
        val note = if (id != null) {
            val existing = notes.first { it.id == id }
            existing.copy(title = title, text = text, tags = tags, modifiedAtMillis = now)
        } else {
            PrivateNote(UUID.randomUUID().toString(), title, text, tags, now, now)
        }
        notes.removeAll { it.id == note.id }
        notes.add(note)
        save(notes)
        note
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val notes = load()
        notes.removeAll { it.id == id }
        save(notes)
    }
}
