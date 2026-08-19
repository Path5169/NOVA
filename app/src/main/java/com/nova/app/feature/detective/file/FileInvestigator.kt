package com.nova.app.feature.detective.file

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.text.DateFormat
import java.util.Date

data class FileInspectionResult(
    val name: String,
    val extension: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256: String,
    val md5: String,
    val lastModified: String?,
    val exif: Map<String, String>
)

/**
 * Reads only the file the user explicitly selects via the system document picker (Storage
 * Access Framework). NOVA never enumerates or accesses arbitrary files on the device.
 */
object FileInvestigator {

    suspend fun inspect(context: Context, uri: Uri): FileInspectionResult = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var name = "unknown"
        var size = 0L
        var lastModified: String? = null
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            val modifiedIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            if (cursor.moveToFirst()) {
                if (nameIdx >= 0) name = cursor.getString(nameIdx) ?: name
                if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
                if (modifiedIdx >= 0 && !cursor.isNull(modifiedIdx)) {
                    lastModified = DateFormat.getDateTimeInstance().format(Date(cursor.getLong(modifiedIdx)))
                }
            }
        }
        val mimeType = resolver.getType(uri) ?: "application/octet-stream"
        val extension = name.substringAfterLast('.', missingDelimiterValue = "—")

        val sha256Digest = MessageDigest.getInstance("SHA-256")
        val md5Digest = MessageDigest.getInstance("MD5")
        var actualSize = 0L
        resolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                sha256Digest.update(buffer, 0, read)
                md5Digest.update(buffer, 0, read)
                actualSize += read
            }
        }
        val sha256 = sha256Digest.digest().joinToString("") { "%02x".format(it) }
        val md5 = md5Digest.digest().joinToString("") { "%02x".format(it) }

        val exif = mutableMapOf<String, String>()
        if (mimeType.startsWith("image/")) {
            try {
                resolver.openInputStream(uri)?.use { input ->
                    val exifInterface = ExifInterface(input)
                    listOf(
                        "Make" to ExifInterface.TAG_MAKE,
                        "Model" to ExifInterface.TAG_MODEL,
                        "Date taken" to ExifInterface.TAG_DATETIME_ORIGINAL,
                        "Width" to ExifInterface.TAG_IMAGE_WIDTH,
                        "Height" to ExifInterface.TAG_IMAGE_LENGTH,
                        "Orientation" to ExifInterface.TAG_ORIENTATION,
                        "F-number" to ExifInterface.TAG_F_NUMBER,
                        "ISO" to ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
                        "Software" to ExifInterface.TAG_SOFTWARE
                    ).forEach { (label, tag) ->
                        exifInterface.getAttribute(tag)?.let { exif[label] = it }
                    }
                    val latLong = FloatArray(2)
                    if (exifInterface.getLatLong(latLong)) {
                        exif["GPS latitude"] = latLong[0].toString()
                        exif["GPS longitude"] = latLong[1].toString()
                    }
                }
            } catch (e: Exception) {
                // Not all images carry readable EXIF (stripped, re-encoded, unsupported format) — skip silently.
            }
        }

        FileInspectionResult(
            name = name,
            extension = extension,
            mimeType = mimeType,
            sizeBytes = if (actualSize > 0) actualSize else size,
            sha256 = sha256,
            md5 = md5,
            lastModified = lastModified,
            exif = exif
        )
    }
}
