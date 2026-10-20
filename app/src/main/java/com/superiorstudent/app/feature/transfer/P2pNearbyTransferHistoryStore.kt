package com.superiorstudent.app.feature.transfer

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * Persists completed nearby transfer metadata locally. It stores document URIs and metadata only;
 * transferred file bytes remain in the user's chosen storage provider.
 */
internal class P2pNearbyTransferHistoryStore(context: Context) {
    private val directory = File(context.noBackupFilesDir, DIRECTORY_NAME)
    private val atomicFile: AtomicFile by lazy { AtomicFile(File(directory, FILE_NAME)) }

    @Synchronized
    fun list(): List<P2pNearbyTransferHistoryRecord> = readRecords()
        .sortedByDescending { it.completedAtEpochMillis }

    @Synchronized
    fun add(record: P2pNearbyTransferHistoryRecord) {
        require(record.isValid()) { "Invalid nearby transfer history record." }
        val next = (listOf(record) + readRecords().filterNot { it.transferId == record.transferId })
            .take(MAX_RECORDS)
        writeRecords(next)
    }

    @Synchronized
    fun remove(transferId: String) {
        val current = readRecords()
        val next = current.filterNot { it.transferId == transferId }
        if (next.size != current.size) writeRecords(next)
    }

    private fun readRecords(): List<P2pNearbyTransferHistoryRecord> {
        if (!directory.isDirectory || !atomicFile.baseFile.exists()) return emptyList()
        return try {
            val json = atomicFile.openRead().bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
            val array = json.optJSONArray("records") ?: JSONArray()
            buildList {
                for (index in 0 until array.length()) {
                    val record = P2pNearbyTransferHistoryRecord.fromJson(array.optJSONObject(index) ?: continue)
                    if (record != null) add(record)
                }
            }.distinctBy { it.transferId }
        } catch (_: Exception) {
            runCatching { atomicFile.delete() }
            emptyList()
        }
    }

    private fun writeRecords(records: List<P2pNearbyTransferHistoryRecord>) {
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw IOException("Could not create local nearby-transfer history directory.")
        }
        val array = JSONArray()
        records.take(MAX_RECORDS).forEach { array.put(it.toJson()) }
        val bytes = JSONObject().put("version", 1).put("records", array)
            .toString().toByteArray(Charsets.UTF_8)
        val output = atomicFile.startWrite()
        try {
            output.write(bytes)
            output.fd.sync()
            atomicFile.finishWrite(output)
        } catch (error: Exception) {
            atomicFile.failWrite(output)
            throw IOException("Could not save local nearby-transfer history.", error)
        } finally {
            bytes.fill(0)
        }
    }

    private companion object {
        const val DIRECTORY_NAME = "p2p-nearby-history"
        const val FILE_NAME = "history.json"
        const val MAX_RECORDS = 100
    }
}

internal data class P2pNearbyTransferHistoryRecord(
    val transferId: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val completedAtEpochMillis: Long,
    val direction: String,
    val contentUri: String
) {
    fun isValid(): Boolean =
        transferId.matches(Regex("^[A-Za-z0-9_-]{8,128}$")) &&
            fileName.trim().length in 1..180 &&
            fileName != "." && fileName != ".." &&
            fileName.none { it == '/' || it == '\\' || it.isISOControl() } &&
            mimeType.trim().length in 1..160 &&
            mimeType.none { it.isISOControl() } &&
            sizeBytes > 0L &&
            completedAtEpochMillis > 0L &&
            direction in setOf(SENT, RECEIVED) &&
            contentUri.length in 1..4096 &&
            contentUri.startsWith("content://")

    fun toJson(): JSONObject = JSONObject()
        .put("transferId", transferId)
        .put("fileName", fileName)
        .put("mimeType", mimeType)
        .put("sizeBytes", sizeBytes)
        .put("completedAtEpochMillis", completedAtEpochMillis)
        .put("direction", direction)
        .put("contentUri", contentUri)

    companion object {
        const val SENT = "SENT"
        const val RECEIVED = "RECEIVED"

        fun fromJson(json: JSONObject): P2pNearbyTransferHistoryRecord? {
            val record = try {
                P2pNearbyTransferHistoryRecord(
                    transferId = json.getString("transferId"),
                    fileName = json.getString("fileName"),
                    mimeType = json.getString("mimeType"),
                    sizeBytes = json.getLong("sizeBytes"),
                    completedAtEpochMillis = json.getLong("completedAtEpochMillis"),
                    direction = json.getString("direction"),
                    contentUri = json.getString("contentUri")
                )
            } catch (_: Exception) {
                return null
            }
            return record.takeIf { it.isValid() }
        }
    }
}
