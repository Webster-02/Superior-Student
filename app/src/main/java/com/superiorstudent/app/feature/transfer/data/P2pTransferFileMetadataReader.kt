package com.superiorstudent.app.feature.transfer.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.text.DecimalFormat

/**
 * Reads file metadata through Android's Storage Access Framework without copying the
 * complete file into memory. The URI remains local to this app and is not a transferable URL.
 * Actual encrypted sending is implemented by a separate transport layer.
 */
data class P2pTransferFileMetadata(
    val uri: Uri,
    val fileName: String,
    val sizeBytes: Long,
    val mimeType: String
)

object P2pTransferFileMetadataReader {
    private const val MAX_FILE_NAME_LENGTH = 180

    fun read(context: Context, uri: Uri): P2pTransferFileMetadata? {
        val resolver = context.contentResolver
        var fileName: String? = null
        var size: Long? = null

        resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) fileName = cursor.getString(nameIndex)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }

        val normalizedName = fileName?.trim()?.takeIf { name ->
            name.isNotEmpty() && name.length <= MAX_FILE_NAME_LENGTH &&
                name != "." && name != ".." &&
                name.none { ch -> ch == '/' || ch == '\\' || ch.isISOControl() }
        } ?: return null

        val mimeType = resolver.getType(uri)?.trim()?.takeIf { type ->
            type.isNotEmpty() && type.length <= 160 && type.none { it.isISOControl() }
        } ?: "application/octet-stream"

        val descriptor = resolver.openAssetFileDescriptor(uri, "r") ?: return null
        val descriptorLength = descriptor.use { it.length }
        val knownSize = size?.takeIf { it >= 0L }
            ?: descriptorLength.takeIf { it >= 0L }
            ?: -1L
        if (size != null && size!! >= 0L && descriptorLength >= 0L && size != descriptorLength) return null

        return P2pTransferFileMetadata(uri, normalizedName, knownSize, mimeType)
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 0L) return "Size unknown"
        if (bytes < 1024L) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unit = -1
        while (value >= 1024.0 && unit < units.lastIndex) {
            value /= 1024.0
            unit++
        }
        return DecimalFormat("#,##0.##").format(value) + " " + units[unit]
    }
}
