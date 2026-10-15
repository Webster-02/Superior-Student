package com.superiorstudent.app.feature.studentconnect

import com.superiorstudent.app.feature.studentconnect.data.StudentPublicIdGenerator
import com.superiorstudent.app.feature.studentconnect.data.StudentPublicIdFormat
import com.superiorstudent.app.feature.studentconnect.data.FriendRequestStatus
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

    @Test
    fun friendRequestIdsAreStableAndDirectional() {
        assertEquals("studentA_studentB", FriendRequestStatus.documentId("studentA", "studentB"))
        assertTrue(FriendRequestStatus.documentId("studentA", "studentB") !=
            FriendRequestStatus.documentId("studentB", "studentA"))
    }

    @Test
    fun friendRequestTransitionsOnlyAllowExpectedParticipantActions() {
        assertTrue(FriendRequestStatus.canTransition(
            "studentB", "studentA", "studentB",
            FriendRequestStatus.PENDING, FriendRequestStatus.ACCEPTED
        ))
        assertTrue(FriendRequestStatus.canTransition(
            "studentB", "studentA", "studentB",
            FriendRequestStatus.PENDING, FriendRequestStatus.REJECTED
        ))
        assertTrue(FriendRequestStatus.canTransition(
            "studentA", "studentA", "studentB",
            FriendRequestStatus.PENDING, FriendRequestStatus.CANCELLED
        ))
        assertTrue(FriendRequestStatus.canTransition(
            "studentA", "studentA", "studentB",
            FriendRequestStatus.REJECTED, FriendRequestStatus.PENDING
        ))
        assertTrue(!FriendRequestStatus.canTransition(
            "studentA", "studentA", "studentB",
            FriendRequestStatus.PENDING, FriendRequestStatus.ACCEPTED
        ))
        assertTrue(!FriendRequestStatus.canTransition(
            "outsider", "studentA", "studentB",
            FriendRequestStatus.PENDING, FriendRequestStatus.REJECTED
        ))
    }
}
