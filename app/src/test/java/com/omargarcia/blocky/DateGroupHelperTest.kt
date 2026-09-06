package com.omargarcia.blocky

import com.omargarcia.blocky.utils.DateGroupHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale

class DateGroupHelperTest {

    private val fixedLocale = Locale.US
    // Fixed reference time: 2026-09-06 12:00:00 UTC
    private val calendar = Calendar.getInstance(fixedLocale).apply {
        set(2026, Calendar.SEPTEMBER, 6, 12, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }
    private val nowMillis = calendar.timeInMillis

    private val helper = DateGroupHelper(
        todayText = "Today",
        yesterdayText = "Yesterday",
        locale = fixedLocale,
        nowMillis = nowMillis
    )

    @Test
    fun testGetDateGroupTitle_today() {
        // Exactly now
        assertEquals("Today", helper.getDateGroupTitle(nowMillis))
        // Earlier today at 01:00 AM
        val todayMorning = Calendar.getInstance(fixedLocale).apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 1)
        }.timeInMillis
        assertEquals("Today", helper.getDateGroupTitle(todayMorning))
    }

    @Test
    fun testGetDateGroupTitle_yesterday() {
        val yesterdayMillis = Calendar.getInstance(fixedLocale).apply {
            timeInMillis = nowMillis
            add(Calendar.DAY_OF_YEAR, -1)
            set(Calendar.HOUR_OF_DAY, 14)
        }.timeInMillis
        assertEquals("Yesterday", helper.getDateGroupTitle(yesterdayMillis))
    }

    @Test
    fun testGetDateGroupTitle_sameYearPastDate() {
        val pastDateSameYear = Calendar.getInstance(fixedLocale).apply {
            timeInMillis = nowMillis
            set(2026, Calendar.JULY, 15, 10, 30, 0)
        }.timeInMillis
        assertEquals("July 15", helper.getDateGroupTitle(pastDateSameYear))
    }

    @Test
    fun testGetDateGroupTitle_differentYearPastDate() {
        val pastYearDate = Calendar.getInstance(fixedLocale).apply {
            timeInMillis = nowMillis
            set(2025, Calendar.DECEMBER, 25, 8, 0, 0)
        }.timeInMillis
        assertEquals("December 25, 2025", helper.getDateGroupTitle(pastYearDate))
    }

    @Test
    fun testMatchesSearchQuery_byPhoneNumber() {
        val phone = "+525512345678"
        assertTrue(helper.matchesSearchQuery(phone, nowMillis, "1234"))
        assertTrue(helper.matchesSearchQuery(phone, nowMillis, "5255"))
        assertFalse(helper.matchesSearchQuery(phone, nowMillis, "9999"))
    }

    @Test
    fun testMatchesSearchQuery_byGroupTitle() {
        val phone = "+525512345678"
        assertTrue(helper.matchesSearchQuery(phone, nowMillis, "today"))
    }

    @Test
    fun testMatchesSearchQuery_byDateString() {
        val pastDate = Calendar.getInstance(fixedLocale).apply {
            set(2026, Calendar.MARCH, 10, 12, 0, 0)
        }.timeInMillis
        val phone = "+525512345678"
        assertTrue(helper.matchesSearchQuery(phone, pastDate, "march"))
        assertTrue(helper.matchesSearchQuery(phone, pastDate, "2026"))
    }
}
