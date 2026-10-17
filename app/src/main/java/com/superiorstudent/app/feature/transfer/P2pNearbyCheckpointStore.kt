package com.superiorstudent.app.feature.transfer

import android.content.Context
import android.util.AtomicFile
import com.superiorstudent.app.feature.transfer.security.P2pEncryptedChunkStream
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * Keeps only local checkpoint metadata and partial plaintext files on-device.
 * The directory lives under noBackupFilesDir: neither file bytes nor transfer records are sent
 * to Firebase or included in Android's normal app-data backup.
 */
internal class P2pNearbyCheckpointStore(context: Context) {
    private val directory = File(context.noBackupFilesDir, DIRECTORY_NAME)
    private val atomicIndex: AtomicFile by lazy { AtomicFile(File(directory, INDEX_NAME)) }

    internal data class RestoredCheckpoint(
        val offer: P2pNearbyOffer,
        val tempFile: File
    )

    @Synchronized
    fun createTempFile(): File {
        ensureDirectory()
        return File.createTempFile(TEMP_PREFIX, TEMP_SUFFIX, directory)
    }

    @Synchronized
    fun save(tempFile: File, offer: P2pNearbyOffer) {
        require(P2pNearbyTransferProtocol.isValidOffer(offer)) { "Invalid nearby transfer offer." }
        require(isOwnedFile(tempFile) && tempFile.isFile) { "Checkpoint file is outside the private transfer directory." }
        require(
            P2pEncryptedChunkStream.isValidCheckpoint(
                tempFile.length(), offer.sizeBytes, offer.chunkSize
            )
        ) { "Checkpoint file is not aligned to an authenticated chunk boundary." }

        ensureDirectory()
        val record = P2pNearbyCheckpointRecord(
            transferId = offer.transferId,
            fileName = offer.fileName,
            sizeBytes = offer.sizeBytes,
            mimeType = offer.mimeType,
            sha256 = offer.sha256,
            chunkSize = offer.chunkSize,
            senderPublicKeyBase64 = offer.senderPublicKeyBase64,
            tempFileName = tempFile.name,
            savedAtEpochMillis = System.currentTimeMillis()
        )
        val index = readIndex()
        index.put(offer.transferId, record.toJson())
        writeIndex(index)
    }

    /**
     * Rehydrates valid checkpoints after the Activity or process has been recreated.
     * Invalid, expired, truncated and unindexed partial files are discarded.
     */
    @Synchronized
    fun restore(): List<RestoredCheckpoint> {
        if (!directory.isDirectory) return emptyList()

        val index = readIndex()
        val now = System.currentTimeMillis()
        val keepFiles = mutableSetOf<String>()
        val restored = mutableListOf<RestoredCheckpoint>()
        var changed = false

        val keys = index.keys().asSequence().toList()
        keys.forEach { transferId ->
            val record = index.optJSONObject(transferId)
                ?.let { P2pNearbyCheckpointRecord.fromJson(transferId, it) }
            val tempFile = record?.let { fileForName(it.tempFileName) }
            val age = record?.let { now - it.savedAtEpochMillis }
            val fresh = age != null && age >= -CLOCK_SKEW_ALLOWANCE_MS && age <= MAX_AGE_MS
            val validFile = record != null && tempFile != null && tempFile.isFile &&
                P2pEncryptedChunkStream.isValidCheckpoint(
                    tempFile.length(), record.sizeBytes, record.chunkSize
                )
            if (record != null && tempFile != null && fresh && validFile &&
                P2pNearbyTransferProtocol.isValidOffer(record.toOffer())
            ) {
                keepFiles += tempFile.name
                restored += RestoredCheckpoint(record.toOffer(), tempFile)
            } else {
                index.remove(transferId)
                tempFile?.delete()
                changed = true
            }
        }

        cleanupOrphanFiles(keepFiles)
        if (changed) writeIndex(index)
        return restored
    }

    /** Removes metadata. The Activity owns deleting the associated partial file. */
    @Synchronized
    fun remove(transferId: String) {
        if (!directory.isDirectory) return
        try {
            val index = readIndex()
            if (index.has(transferId)) {
                index.remove(transferId)
                writeIndex(index)
            }
        } catch (_: Exception) {
            // Any stale entry is removed when restore() validates its missing/invalid file.
        }
    }

    private fun ensureDirectory() {
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw IOException("Could not create private nearby-transfer checkpoint directory.")
        }
    }

    private fun readIndex(): JSONObject {
        if (!directory.isDirectory || !atomicIndex.baseFile.exists()) return JSONObject()
        return try {
            atomicIndex.openRead().bufferedReader(Charsets.UTF_8).use { reader ->
                JSONObject(reader.readText())
            }
        } catch (_: Exception) {
            atomicIndex.delete()
            JSONObject()
        }
    }

    private fun writeIndex(index: JSONObject) {
        ensureDirectory()
        val bytes = index.toString().toByteArray(Charsets.UTF_8)
        val output = atomicIndex.startWrite()
        try {
            output.write(bytes)
            output.fd.sync()
            atomicIndex.finishWrite(output)
        } catch (error: Exception) {
            atomicIndex.failWrite(output)
            throw IOException("Could not safely persist nearby-transfer checkpoint metadata.", error)
        } finally {
            bytes.fill(0)
        }
    }

    private fun isOwnedFile(file: File): Boolean {
        return try {
            file.canonicalFile.parentFile == directory.canonicalFile &&
                SAFE_TEMP_NAME.matches(file.name)
        } catch (_: Exception) {
            false
        }
    }

    private fun fileForName(name: String): File? {
        if (!SAFE_TEMP_NAME.matches(name)) return null
        val candidate = File(directory, name)
        return if (isOwnedFile(candidate)) candidate else null
    }

    private fun cleanupOrphanFiles(knownNames: Set<String>) {
        directory.listFiles()?.forEach { file ->
            if (SAFE_TEMP_NAME.matches(file.name) && file.name !in knownNames) {
                runCatching { file.delete() }
            }
        }
    }

    private companion object {
        const val DIRECTORY_NAME = "p2p-nearby-checkpoints"
        const val INDEX_NAME = "index.json"
        const val TEMP_PREFIX = "p2p-nearby-"
        const val TEMP_SUFFIX = ".part"
        const val MAX_AGE_MS = 7L * 24L * 60L * 60L * 1000L
        const val CLOCK_SKEW_ALLOWANCE_MS = 5L * 60L * 1000L
        val SAFE_TEMP_NAME = Regex("^p2p-nearby-[A-Za-z0-9_-]{1,80}\\.part$")
    }
}

/** Pure local metadata record, kept separate so serialization/validation can be unit-tested. */
internal data class P2pNearbyCheckpointRecord(
    val transferId: String,
    val fileName: String,
    val sizeBytes: Long,
    val mimeType: String,
    val sha256: String,
    val chunkSize: Int,
    val senderPublicKeyBase64: String,
    val tempFileName: String,
    val savedAtEpochMillis: Long
) {
    fun toOffer() = P2pNearbyOffer(
        transferId = transferId,
        fileName = fileName,
        sizeBytes = sizeBytes,
        mimeType = mimeType,
        sha256 = sha256,
        chunkSize = chunkSize,
        senderPublicKeyBase64 = senderPublicKeyBase64
    )

    fun toJson(): JSONObject = JSONObject()
        .put("transferId", transferId)
        .put("fileName", fileName)
        .put("sizeBytes", sizeBytes)
        .put("mimeType", mimeType)
        .put("sha256", sha256)
        .put("chunkSize", chunkSize)
        .put("senderPublicKeyBase64", senderPublicKeyBase64)
        .put("tempFileName", tempFileName)
        .put("savedAtEpochMillis", savedAtEpochMillis)

    companion object {
        private val safeTempName = Regex("^p2p-nearby-[A-Za-z0-9_-]{1,80}\\.part$")

        fun fromJson(expectedTransferId: String, json: JSONObject): P2pNearbyCheckpointRecord? {
            val record = try {
                P2pNearbyCheckpointRecord(
                    transferId = json.getString("transferId"),
                    fileName = json.getString("fileName"),
                    sizeBytes = json.getLong("sizeBytes"),
                    mimeType = json.getString("mimeType"),
                    sha256 = json.getString("sha256"),
                    chunkSize = json.getInt("chunkSize"),
                    senderPublicKeyBase64 = json.getString("senderPublicKeyBase64"),
                    tempFileName = json.getString("tempFileName"),
                    savedAtEpochMillis = json.getLong("savedAtEpochMillis")
                )
            } catch (_: Exception) {
                return null
            }
            if (record.transferId != expectedTransferId ||
                !safeTempName.matches(record.tempFileName) ||
                record.savedAtEpochMillis <= 0L ||
                !P2pNearbyTransferProtocol.isValidOffer(record.toOffer())
            ) {
                return null
            }
            return record
        }
    }
}
