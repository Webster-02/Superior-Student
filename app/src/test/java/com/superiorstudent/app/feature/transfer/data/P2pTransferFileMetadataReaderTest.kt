package com.superiorstudent.app.feature.transfer.data

import org.junit.Assert.assertEquals
import org.junit.Test

class P2pTransferFileMetadataReaderTest {
    @Test fun formatsKnownFileSizes() {
        assertEquals("0 B", P2pTransferFileMetadataReader.formatBytes(0L))
        assertEquals("1023 B", P2pTransferFileMetadataReader.formatBytes(1023L))
        assertEquals("1 KB", P2pTransferFileMetadataReader.formatBytes(1024L))
        assertEquals("1 MB", P2pTransferFileMetadataReader.formatBytes(1024L * 1024L))
    }

    @Test fun handlesUnknownFileSize() {
        assertEquals("Size unknown", P2pTransferFileMetadataReader.formatBytes(-1L))
    }
}
