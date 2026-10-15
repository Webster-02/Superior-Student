package com.superiorstudent.app.feature.studentconnect

import com.superiorstudent.app.feature.studentconnect.data.StudentPublicIdGenerator
import com.superiorstudent.app.feature.studentconnect.data.StudentPublicIdFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudentPublicIdGeneratorTest {
    @Test
    fun generatedIdsUseTheExpectedShareableFormat() {
        repeat(100) {
            val id = StudentPublicIdGenerator.generate()
            assertTrue(StudentPublicIdFormat.isValid(id))
            assertEquals(11, id.length)
        }
    }

    @Test
    fun publicIdsAreTrimmedAndNormalizedForSearch() {
        assertEquals("UNI-F8738X8", StudentPublicIdFormat.normalize("  uni-f8738x8 "))
        assertTrue(StudentPublicIdFormat.isValid("uni-f8738x8"))
    }

    @Test
    fun searchRejectsPartialOrMalformedIds() {
        assertTrue(!StudentPublicIdFormat.isValid("UNI-123"))
        assertTrue(!StudentPublicIdFormat.isValid("UNI-IO01ABC"))
        assertTrue(!StudentPublicIdFormat.isValid("another-student"))
    }
}
