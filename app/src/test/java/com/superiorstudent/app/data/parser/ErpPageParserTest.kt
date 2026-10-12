package com.superiorstudent.app.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ErpPageParserTest {

    @Test
    fun parsesAttendanceDetailCountsAndClassHistory() {
        val payload = """
            {
              "cards": [],
              "tables": [{
                "title": "Attendance",
                "headers": ["Sr. no", "Date", "Status"],
                "rows": [["1", "2026-09-15", "P"], ["2", "2026-09-22", "A"]]
              }],
              "records": [],
              "lines": [],
              "overallAttendance": null,
              "attendanceDetail": {
                "course": "Functional English",
                "code": "HOQ061140",
                "conducted": 2,
                "attended": 1,
                "percentage": 50.0,
                "sessions": [
                  {"date": "2026-09-15", "status": "P"},
                  {"date": "2026-09-22", "status": "A"}
                ]
              },
              "attendanceDetailLinks": [
                {"course": "Functional English", "code": "HOQ061140",
                 "url": "https://erp.superior.edu.pk/student/attendancedetail/id/204263",
                 "percentage": 50.0},
                {"course": "Untrusted", "code": "",
                 "url": "https://example.com/student/attendancedetail/id/1",
                 "percentage": 100.0}
              ]
            }
        """.trimIndent()

        val result = ErpPageParser.parseOrThrow(payload)

        assertNotNull(result)
        assertEquals("Functional English", result!!.attendanceDetail!!.course)
        assertEquals(2, result.attendanceDetail!!.conducted)
        assertEquals(1, result.attendanceDetail!!.attended)
        assertEquals(2, result.attendanceDetail!!.sessions.size)
        assertEquals("A", result.attendanceDetail!!.sessions[1].status)
        assertEquals(1, result.attendanceDetailLinks.size)
        assertTrue(result.attendanceDetailLinks[0].url.startsWith("https://erp.superior.edu.pk/"))
    }

    @Test
    fun malformedJsonIsRejectedSafely() {
        assertNull(ErpPageParser.parse("{ not-json"))
    }
}
