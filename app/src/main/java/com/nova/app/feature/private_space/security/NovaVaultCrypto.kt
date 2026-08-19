package com.nova.app.feature.private_space.security

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import java.io.File

/**
 * Wraps AndroidX Security's [EncryptedFile] + [MasterKey], which in turn is backed by the
 * Android Keystore (AES-256-GCM, hardware-backed where the device supports it).
 *
 * NOVA never implements its own cryptography. This class only chooses *where* encrypted bytes
 * live on local storage — the actual key generation, storage, and AES/GCM operations are done
 * entirely by AndroidX Security + the Android Keystore.
 *
 * Everything this touches stays under the app's private storage (`filesDir`). Nothing here
 * uploads, syncs, or transmits data anywhere — NOVA Private is local-only by construction.
 */
class NovaVaultCrypto(private val context: Context) {

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private fun vaultRoot(): File = File(context.filesDir, "nova_private_vault").apply { mkdirs() }
    private fun notesRoot(): File = File(context.filesDir, "nova_private_notes").apply { mkdirs() }

    fun vaultFile(fileName: String): File = File(vaultRoot(), fileName)
    fun notesStoreFile(): File = File(notesRoot(), "notes.enc")

    /** Encrypts [plainBytes] into [target] using the Keystore-backed master key. Overwrites if present. */
    fun writeEncrypted(target: File, plainBytes: ByteArray) {
        if (target.exists()) target.delete()
        val encryptedFile = EncryptedFile.Builder(
            context,
            target,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()
        encryptedFile.openFileOutput().use { it.write(plainBytes) }
    }

    /** Decrypts [source] back to plaintext bytes. Returns null if the file doesn't exist. */
    fun readDecrypted(source: File): ByteArray? {
        if (!source.exists()) return null
        val encryptedFile = EncryptedFile.Builder(
            context,
            source,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()
        return encryptedFile.openFileInput().use { it.readBytes() }
    }

    fun deleteEncrypted(target: File) {
        if (target.exists()) target.delete()
    }

    fun listVaultFiles(): List<File> = vaultRoot().listFiles()?.toList().orEmpty()
}
