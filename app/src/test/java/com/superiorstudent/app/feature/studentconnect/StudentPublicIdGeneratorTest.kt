package com.superiorstudent.app.feature.studentconnect

import com.superiorstudent.app.feature.studentconnect.data.StudentPublicIdGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudentPublicIdGeneratorTest {
    @Test
    fun generatedIdsUseTheExpectedShareableFormat() {
        repeat(100) {
            val id = StudentPublicIdGenerator.generate()
            assertTrue(id.matches(Regex("^UNI-[A-HJ-NP-Z2-9]{7}$")))
            assertEquals(11, id.length)
        }
    }
}
