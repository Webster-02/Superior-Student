package com.superiorstudent.app.ui

import com.superiorstudent.app.data.model.InvoiceData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class HomeDisplayRulesTest {
    private fun invoice(payable: String, dueDate: String, status: String = "unpaid", number: String = "INV-1") =
        InvoiceData(number, "", dueDate, "", "", "", "", "", payable, status, "")

    @Test fun avatarInitialsUseFirstAndLastNameOrSingleLetter() {
        assertEquals("AK", HomeDisplayRules.initials("Awais Khan"))
        assertEquals("MK", HomeDisplayRules.initials("Muhammad Awais Khan"))
        assertEquals("A", HomeDisplayRules.initials("Awais"))
        assertEquals("S", HomeDisplayRules.initials(""))
    }

    @Test fun attendanceBandsUseEightyAndSeventyFivePercentBoundaries() {
        assertEquals(AttendanceBand.SAFE, HomeDisplayRules.attendanceBand(80.0))
        assertEquals(AttendanceBand.NEAR_LIMIT, HomeDisplayRules.attendanceBand(75.0))
        assertEquals(AttendanceBand.NEAR_LIMIT, HomeDisplayRules.attendanceBand(79.9))
        assertEquals(AttendanceBand.SHORT, HomeDisplayRules.attendanceBand(74.99))
        assertNull(HomeDisplayRules.attendanceBand(Double.NaN))
        assertNull(HomeDisplayRules.attendanceBand(101.0))
    }

    @Test fun feeDueRequiresPositiveAmountAndRecognisableDateAndExcludesPaidRows() {
        assertNotNull(HomeDisplayRules.feeDue(listOf(invoice("Rs. 45,000", "12 Nov 2026"))))
        assertNull(HomeDisplayRules.feeDue(listOf(invoice("0", "12 Nov 2026"))))
        assertNull(HomeDisplayRules.feeDue(listOf(invoice("Rs. 500", "not a date"))))
        assertNull(HomeDisplayRules.feeDue(listOf(invoice("Rs. 500", "12 Nov 2026", "paid"))))
    }

    @Test fun feeLabelsNeverInventAnAmountWhenSourceIsInvalid() {
        assertEquals("Rs 45,000", HomeDisplayRules.payableLabel("45000"))
        assertEquals("Rs. 45,000", HomeDisplayRules.payableLabel("Rs. 45,000"))
        assertNull(HomeDisplayRules.payableLabel("N/A"))
    }
}
