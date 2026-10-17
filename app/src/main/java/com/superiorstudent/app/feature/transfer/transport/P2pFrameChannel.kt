package com.superiorstudent.app.feature.transfer.transport

import java.io.Closeable

/**
 * Duplex message channel for already AEAD-encrypted file frames.
 * Implementations must bound frame sizes and use reliable, ordered delivery.
 */
interface P2pFrameChannel : Closeable {
    val transferId: String
    fun sendFrame(frame: ByteArray): Boolean
    /** Returns null on read timeout; implementations throw on hard EOF or I/O failure. */
    fun receiveFrame(timeoutMillis: Int): ByteArray?
    fun isOpen(): Boolean
}
