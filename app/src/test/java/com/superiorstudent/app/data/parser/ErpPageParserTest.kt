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

        val result = ErpPageParser.parse(payload)

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
    fun parsesPaidAndUnpaidInvoicesAndOnlyKeepsTrustedPdfRoutes() {
        val payload = """
            {
              "cards": [], "tables": [], "records": [], "lines": [],
              "overallAttendance": null,
              "invoices": [
                {
                  "number": "INV/2026/001",
                  "invoiceDate": "2026-09-24",
                  "dueDate": "2026-10-01",
                  "term": "FALL 2026",
                  "semester": "2nd",
                  "receiptFor": "Tuition Fee",
                  "barcode": "12345678",
                  "totalAmount": "35000.0",
                  "payable": "0.0",
                  "status": "paid",
                  "downloadUrl": "https://erp.superior.edu.pk/student/invoices/download/123"
                },
                {
                  "number": "INV/2026/002",
                  "invoiceDate": "2026-09-25",
                  "dueDate": "2026-10-02",
                  "term": "FALL 2026",
                  "semester": "2nd",
                  "receiptFor": "Tuition Fee",
                  "barcode": "12345679",
                  "totalAmount": "2000.0",
                  "payable": "2000.0",
                  "status": "unpaid",
                  "downloadUrl": "https://example.com/student/invoices/download/124"
                }
              ]
            }
        """.trimIndent()

        val result = ErpPageParser.parse(payload)

        assertNotNull(result)
        assertEquals(2, result!!.invoices.size)
        assertEquals("paid", result.invoices[0].status)
        assertEquals("35000.0", result.invoices[0].totalAmount)
        assertEquals(
            "https://erp.superior.edu.pk/student/invoices/download/123",
            result.invoices[0].downloadUrl
        )
        assertEquals("unpaid", result.invoices[1].status)
        assertEquals("", result.invoices[1].downloadUrl)
    }

    @Test
    fun parsesGenericProfileFieldsForAnyProgramme() {
        val payload = """
            {
              "cards": [],
              "tables": [],
              "lines": [],
              "overallAttendance": null,
              "records": [
                {"headers":["Field","Value"],"values":["Student Name","Student Example"]},
                {"headers":["Field","Value"],"values":["Registration No","REG-EXAMPLE"]},
                {"headers":["Field","Value"],"values":["Faculty","Faculty Example"]},
                {"headers":["Field","Value"],"values":["Program","Example Degree"]},
                {"headers":["Field","Value"],"values":["Current Semester","2nd"]},
                {"headers":["Field","Value"],"values":["Email","student@example.edu"]}
              ]
            }
        """.trimIndent()

        val result = ErpPageParser.parse(payload)

        assertNotNull(result)
        assertEquals(6, result!!.records.size)
        assertEquals("Student Name", result.records[0].values[0])
        assertEquals("Example Degree", result.records[3].values[1])
        assertEquals("Email", result.records[5].values[0])
    }

    @Test
    fun parsesNestedSemesterSummariesAndCourseGradesWithoutMixingHeaders() {
        val payload = """
            {
              "cards": [],
              "tables": [{
                "title": "",
                "headers": ["Term", "Grade Points", "Total Credits", "Earned Credits", "GPA", "CGPA"],
                "rows": [
                  ["SPRING 2026", "66.62", "20.0", "20.0", "", ""],
                  ["Course", "Credits", "Marks Obtained", "Grade Pts", "Mid Grade", "Final Grade"],
                  ["General Pathology (Theory)", "2.0", "62.0", "4.0", "", "C"]
                ]
              }],
              "lines": [],
              "overallAttendance": null,
              "records": [],
              "academicResults": [
                {
                  "term": "SPRING 2026",
                  "gradePoints": "66.62",
                  "totalCredits": "20.0",
                  "earnedCredits": "20.0",
                  "gpa": "",
                  "cgpa": "",
                  "courses": [{
                    "course": "General Pathology (Theory)",
                    "credits": "2.0",
                    "marksObtained": "62.0",
                    "gradePoints": "4.0",
                    "midGrade": "",
                    "finalGrade": "C"
                  }]
                },
                {
                  "term": "FALL 2026",
                  "gradePoints": "0.0",
                  "totalCredits": "18.0",
                  "earnedCredits": "0",
                  "gpa": "",
                  "cgpa": "",
                  "courses": [{
                    "course": "Functional English",
                    "credits": "3.0",
                    "marksObtained": "0.0",
                    "gradePoints": "0.0",
                    "midGrade": "",
                    "finalGrade": ""
                  }]
                }
              ]
            }
        """.trimIndent()

        val result = ErpPageParser.parse(payload)

        assertNotNull(result)
        assertEquals(2, result!!.academicResults.size)
        val spring = result.academicResults[0]
        assertEquals("SPRING 2026", spring.term)
        assertEquals("66.62", spring.gradePoints)
        assertEquals("20.0", spring.totalCredits)
        assertEquals("", spring.gpa)
        assertEquals(1, spring.courses.size)
        assertEquals("General Pathology (Theory)", spring.courses[0].course)
        assertEquals("62.0", spring.courses[0].marksObtained)
        assertEquals("4.0", spring.courses[0].gradePoints)
        assertEquals("C", spring.courses[0].finalGrade)

        val fall = result.academicResults[1]
        assertEquals("FALL 2026", fall.term)
        assertEquals("", fall.cgpa)
        assertEquals("", fall.courses[0].finalGrade)
    }

    @Test
    fun malformedJsonIsRejectedSafely() {
        assertNull(ErpPageParser.parse("{ not-json"))
    }
}
