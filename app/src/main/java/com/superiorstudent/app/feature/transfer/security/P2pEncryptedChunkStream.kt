package com.superiorstudent.app.feature.transfer.security

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import javax.crypto.SecretKey

/**
 * Bounded, authenticated chunk framing shared by sender and receiver.
 *
 * Caller streams from Android ContentResolver and sends one returned frame per DataChannel
 * message. Receiver should write into a temporary destination and promote it only after finish().
 */
object P2pEncryptedChunkStream {
    private const val MAGIC = 0x50325031 // P2P1
    private const val VERSION: Byte = 1
    private const val TYPE_CHUNK: Byte = 1
    private const val TYPE_END: Byte = 2
    private const val HEADER_BYTES = 4 + 1 + 1 + 8 + 4
    const val DEFAULT_CHUNK_BYTES = 32 * 1024
    const val MAX_CHUNK_BYTES = 64 * 1024
    private const val TAG_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val DIGEST_BYTES = 32

    data class TransferDescriptor(
        val transferId: String,
        val fileName: String,
        val mimeType: String,
        val totalBytes: Long,
        val sha256Hex: String,
        val chunkSize: Int = DEFAULT_CHUNK_BYTES
    ) {
        init {
            require(transferId.matches(Regex("^[A-Za-z0-9_-]{16,80}$"))) { "Invalid transfer ID." }
            require(fileName.isNotBlank() && fileName.length <= 180) { "Invalid filename." }
            require(fileName.none { it == '/' || it == '\\' || it.isISOControl() }) { "Invalid filename." }
            require(mimeType.isNotBlank() && mimeType.length <= 160) { "Invalid MIME type." }
            require(totalBytes >= 0L) { "Invalid file length." }
            require(sha256Hex.matches(Regex("^[A-Fa-f0-9]{64}$"))) { "Invalid file SHA-256." }
            require(chunkSize in 1024..MAX_CHUNK_BYTES) { "Unsupported chunk size." }
        }
    }

    /** Encrypts the next bounded block. Returns null at EOF; call finishFrame() once after EOF. */
    class Sender(
        private val input: InputStream,
        private val key: SecretKey,
        private val descriptor: TransferDescriptor
    ) : AutoCloseable {
        private var sequence = 0L
        private var bytesRead = 0L
        private val digest = MessageDigest.getInstance("SHA-256")
        private var finished = false
        private var endFrameCreated = false
        private var endFrameSent = false

        fun nextFrame(): ByteArray? {
            check(!finished && !endFrameCreated) { "Sender is already finished." }
            val block = readAtMost(input, descriptor.chunkSize)
            if (block.isEmpty()) {
                require(bytesRead == descriptor.totalBytes) { "Source file is shorter than declared." }
                return null
            }
            bytesRead = Math.addExact(bytesRead, block.size.toLong())
            require(bytesRead <= descriptor.totalBytes) { "Source file grew during transfer." }
            digest.update(block)
            val metadata = chunkMetadata(descriptor, sequence, bytesRead - block.size, block.size)
            val encrypted = P2pTransferCrypto.encryptChunk(
                key, descriptor.transferId, sequence, block, metadata
            )
            val frame = encodeChunk(sequence, block.size, encrypted.nonce, encrypted.ciphertext)
            sequence++
            block.fill(0)
            return frame
        }

        fun finishFrame(): ByteArray {
            check(!finished && !endFrameCreated) { "Sender is already finished." }
            endFrameCreated = true
            require(bytesRead == descriptor.totalBytes) { "Source file length changed during transfer." }
            val actual = digest.digest().toHex()
            require(actual.equals(descriptor.sha256Hex, ignoreCase = true)) {
                "Source file SHA-256 changed after metadata was prepared."
            }
            return encodeEnd(sequence, bytesRead, actual)
        }

        fun markEndFrameSent() {
            check(endFrameCreated && !finished && !endFrameSent) { "End frame has not been created or was already sent." }
            endFrameSent = true
            finished = true
        }

        override fun close() {
            finished = true
            input.close()
        }
    }

    /**
     * Receiver writes authenticated plaintext to a temporary output only. finish() is mandatory
     * before the caller can mark the transfer complete or promote the destination file.
     */
    class Receiver(
        private val output: OutputStream,
        private val key: SecretKey,
        private val descriptor: TransferDescriptor
    ) : AutoCloseable {
        private var expectedSequence = 0L
        private var bytesWritten = 0L
        private val digest = MessageDigest.getInstance("SHA-256")
        private var completed = false
        private var failed = false

        fun acceptFrame(frame: ByteArray) {
            check(!completed && !failed) { "Receiver is no longer accepting frames." }
            try {
                require(frame.size in HEADER_BYTES..(HEADER_BYTES + NONCE_BYTES + MAX_CHUNK_BYTES + TAG_BYTES + 8 + DIGEST_BYTES)) {
                    "Frame size is invalid."
                }
                val buffer = ByteBuffer.wrap(frame).order(ByteOrder.BIG_ENDIAN)
                require(buffer.int == MAGIC) { "Unexpected P2P frame magic." }
                require(buffer.get() == VERSION) { "Unsupported P2P frame version." }
                when (buffer.get()) {
                    TYPE_CHUNK -> acceptChunk(buffer, frame)
                    TYPE_END -> acceptEnd(buffer)
                    else -> throw IllegalArgumentException("Unknown P2P frame type.")
                }
            } catch (error: Exception) {
                failed = true
                throw error
            }
        }

        private fun acceptChunk(buffer: ByteBuffer, frame: ByteArray) {
            require(frame.size >= HEADER_BYTES + NONCE_BYTES + TAG_BYTES) { "Truncated chunk frame." }
            val sequence = buffer.long
            val plaintextLength = buffer.int
            require(sequence == expectedSequence) { "Chunk sequence gap or replay." }
            require(plaintextLength in 1..descriptor.chunkSize) { "Chunk length is invalid." }
            require(frame.size == HEADER_BYTES + NONCE_BYTES + plaintextLength + TAG_BYTES) {
                "Chunk frame length does not match its header."
            }
            require(bytesWritten + plaintextLength <= descriptor.totalBytes) { "Received more bytes than declared." }

            val nonce = ByteArray(NONCE_BYTES).also(buffer::get)
            val ciphertext = ByteArray(plaintextLength + TAG_BYTES).also(buffer::get)
            val aad = chunkMetadata(descriptor, sequence, bytesWritten, plaintextLength)
            val plaintext = P2pTransferCrypto.decryptChunk(
                key, descriptor.transferId, sequence, nonce, ciphertext, aad
            )
            try {
                require(plaintext.size == plaintextLength) { "Decrypted length mismatch." }
                output.write(plaintext)
                digest.update(plaintext)
                bytesWritten = Math.addExact(bytesWritten, plaintext.size.toLong())
                expectedSequence++
            } finally {
                plaintext.fill(0)
                ciphertext.fill(0)
                nonce.fill(0)
            }
        }

        private fun acceptEnd(buffer: ByteBuffer) {
            // Header format: magic(4), version(1), type(1), sequence(8), payloadLength(4).
            // End payload is exactly: bytesWritten(8) + SHA-256(32).
            val sequenceInHeader = buffer.long
            val payloadLength = buffer.int
            require(sequenceInHeader == expectedSequence) { "End frame sequence does not match." }
            require(payloadLength == 8 + DIGEST_BYTES && buffer.remaining() == payloadLength) {
                "End frame length is invalid."
            }
            val declaredBytes = buffer.long
            val endDigest = ByteArray(DIGEST_BYTES).also(buffer::get).toHex()
            require(declaredBytes == bytesWritten && bytesWritten == descriptor.totalBytes) {
                "Received file length does not match expected length."
            }
            val actualDigest = digest.digest().toHex()
            require(actualDigest.equals(endDigest, ignoreCase = true)) { "End-frame SHA-256 mismatch." }
            require(actualDigest.equals(descriptor.sha256Hex, ignoreCase = true)) {
                "Received file SHA-256 does not match the offer."
            }
            output.flush()
            completed = true
        }

        fun finish() {
            check(!failed) { "Receive failed; discard the temporary output." }
            check(completed) { "Transfer ended without an authenticated completion frame." }
        }

        override fun close() {
            output.close()
        }

        val bytesReceived: Long get() = bytesWritten
        val isComplete: Boolean get() = completed && !failed
    }

    private fun encodeChunk(sequence: Long, plainLength: Int, nonce: ByteArray, ciphertext: ByteArray): ByteArray {
        require(nonce.size == NONCE_BYTES && ciphertext.size == plainLength + TAG_BYTES)
        return ByteBuffer.allocate(HEADER_BYTES + NONCE_BYTES + ciphertext.size)
            .order(ByteOrder.BIG_ENDIAN)
            .putInt(MAGIC).put(VERSION).put(TYPE_CHUNK)
            .putLong(sequence).putInt(plainLength)
            .put(nonce).put(ciphertext).array()
    }

    private fun encodeEnd(sequence: Long, bytes: Long, sha256Hex: String): ByteArray {
        val digest = sha256Hex.hexToBytes()
        require(digest.size == DIGEST_BYTES)
        return ByteBuffer.allocate(HEADER_BYTES + 8 + DIGEST_BYTES)
            .order(ByteOrder.BIG_ENDIAN)
            .putInt(MAGIC).put(VERSION).put(TYPE_END)
            .putLong(sequence).putInt(8 + DIGEST_BYTES).putLong(bytes).put(digest).array()
    }

    private fun chunkMetadata(
        descriptor: TransferDescriptor,
        sequence: Long,
        offset: Long,
        plaintextLength: Int
    ): ByteArray {
        val id = descriptor.transferId.toByteArray(Charsets.UTF_8)
        val name = descriptor.fileName.toByteArray(Charsets.UTF_8)
        val mime = descriptor.mimeType.toByteArray(Charsets.UTF_8)
        return ByteBuffer.allocate(4 + id.size + 4 + name.size + 4 + mime.size + 8 + 8 + 4)
            .order(ByteOrder.BIG_ENDIAN)
            .putInt(id.size).put(id)
            .putInt(name.size).put(name)
            .putInt(mime.size).put(mime)
            .putLong(descriptor.totalBytes).putLong(offset).putInt(plaintextLength)
            .array()
    }

    private fun readAtMost(input: InputStream, max: Int): ByteArray {
        val output = ByteArrayOutputStream(max)
        val buffer = ByteArray(minOf(8192, max))
        while (output.size() < max) {
            val read = input.read(buffer, 0, minOf(buffer.size, max - output.size()))
            if (read < 0) break
            if (read == 0) {
                val one = input.read()
                if (one < 0) break
                output.write(one)
            } else {
                output.write(buffer, 0, read)
            }
        }
        buffer.fill(0)
        return output.toByteArray()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun String.hexToBytes(): ByteArray {
        require(length % 2 == 0 && all { it in "0123456789abcdefABCDEF" })
        return ByteArray(length / 2) { index ->
            ((this[index * 2].digitToInt(16) shl 4) or this[index * 2 + 1].digitToInt(16)).toByte()
        }
    }
}
