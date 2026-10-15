package com.superiorstudent.app.feature.studentconnect

import com.superiorstudent.app.feature.studentconnect.data.StudentPublicIdGenerator
import com.superiorstudent.app.feature.studentconnect.data.StudentPublicIdFormat
import com.superiorstudent.app.feature.studentconnect.data.FriendRequestStatus
import com.superiorstudent.app.feature.studentconnect.data.StudentConnectionFilter
import com.superiorstudent.app.feature.studentconnect.data.StudentNotificationIds
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
    fun connectionFilterMatchesNameOrPublicIdIgnoringCase() {
        assertTrue(StudentConnectionFilter.matches("awais", "Awais Khan", "UNI-ABC2345"))
        assertTrue(StudentConnectionFilter.matches("uni-abc", "Awais Khan", "UNI-ABC2345"))
        assertTrue(!StudentConnectionFilter.matches("fatima", "Awais Khan", "UNI-ABC2345"))
    }

    @Test
    fun blankConnectionFilterMatchesEveryConnection() {
        assertTrue(StudentConnectionFilter.matches("", "Awais Khan", "UNI-ABC2345"))
        assertTrue(StudentConnectionFilter.matches("   ", "Awais Khan", "UNI-ABC2345"))
    }

    @Test
    fun friendRequestIdsAreStableAndDirectional() {
        assertEquals("studentA_studentB", FriendRequestStatus.documentId("studentA", "studentB"))
        assertTrue(FriendRequestStatus.documentId("studentA", "studentB") !=
            FriendRequestStatus.documentId("studentB", "studentA"))
    }

    @Test
    fun studentNotificationIdsAreDeterministicPerRequestEvent() {
        assertEquals("studentA_studentB_request", StudentNotificationIds.requestReceived("studentA_studentB"))
        assertEquals("studentA_studentB_accepted", StudentNotificationIds.requestAccepted("studentA_studentB"))
        assertTrue(StudentNotificationIds.requestReceived("studentA_studentB") !=
            StudentNotificationIds.requestAccepted("studentA_studentB"))
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
        assertTrue(FriendRequestStatus.canTransition(
            "studentA", "studentA", "studentB",
            FriendRequestStatus.ACCEPTED, FriendRequestStatus.REMOVED
        ))
        assertTrue(FriendRequestStatus.canTransition(
            "studentB", "studentA", "studentB",
            FriendRequestStatus.ACCEPTED, FriendRequestStatus.REMOVED
        ))
        assertTrue(!FriendRequestStatus.canTransition(
            "outsider", "studentA", "studentB",
            FriendRequestStatus.ACCEPTED, FriendRequestStatus.REMOVED
        ))
        assertTrue(!FriendRequestStatus.canTransition(
            "studentA", "studentA", "studentB",
            FriendRequestStatus.PENDING, FriendRequestStatus.REMOVED
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
