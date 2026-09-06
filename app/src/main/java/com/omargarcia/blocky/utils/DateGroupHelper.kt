package com.omargarcia.blocky.utils

import android.content.Context
import com.omargarcia.blocky.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * High-performance date grouping and search helper that avoids heavy, repetitive
 * allocations of [Calendar] and [SimpleDateFormat] on the Main Thread.
 *
 * Precomputes day boundary epochs (start of today and start of yesterday) and
 * caches day-formatted strings to process large lists in sub-millisecond time.
 */
class DateGroupHelper(
    val todayText: String,
    val yesterdayText: String,
    private val locale: Locale = Locale.getDefault(),
    nowMillis: Long = System.currentTimeMillis()
) {
    constructor(
        context: Context,
        locale: Locale = Locale.getDefault(),
        nowMillis: Long = System.currentTimeMillis()
    ) : this(
        todayText = context.getString(R.string.group_today),
        yesterdayText = context.getString(R.string.group_yesterday),
        locale = locale,
        nowMillis = nowMillis
    )

    val startOfTodayMillis: Long
    val startOfYesterdayMillis: Long
    val currentYear: Int

    init {
        val calendar = Calendar.getInstance(locale).apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        startOfTodayMillis = calendar.timeInMillis
        currentYear = calendar.get(Calendar.YEAR)

        calendar.add(Calendar.DAY_OF_YEAR, -1)
        startOfYesterdayMillis = calendar.timeInMillis
    }

    private val sameYearFormat = SimpleDateFormat("MMMM dd", locale)
    private val diffYearFormat = SimpleDateFormat("MMMM dd, yyyy", locale)
    private val searchFullFormat = SimpleDateFormat("yyyy-MM-dd MMMM dd yyyy", locale)

    private val tempCalendar = Calendar.getInstance(locale)
    private val dayFormatCache = HashMap<Long, String>()
    private val searchFormatCache = HashMap<Long, String>()

    /**
     * Returns the human-readable date group header ("Hoy", "Ayer", "Octubre 12", "Octubre 12, 2025").
     * Uses O(1) arithmetic comparisons for today and yesterday, and day-level caching for past dates.
     */
    fun getDateGroupTitle(timestamp: Long): String {
        if (timestamp >= startOfTodayMillis && timestamp < startOfTodayMillis + 86_400_000L) {
            return todayText
        }
        if (timestamp >= startOfYesterdayMillis && timestamp < startOfTodayMillis) {
            return yesterdayText
        }

        val dayKey = timestamp / 86_400_000L
        synchronized(dayFormatCache) {
            dayFormatCache[dayKey]?.let { return it }

            synchronized(tempCalendar) {
                tempCalendar.timeInMillis = timestamp
                val itemYear = tempCalendar.get(Calendar.YEAR)
                val date = Date(timestamp)
                val formatted = if (itemYear == currentYear) {
                    sameYearFormat.format(date)
                } else {
                    diffYearFormat.format(date)
                }
                dayFormatCache[dayKey] = formatted
                return formatted
            }
        }
    }

    /**
     * Efficiently checks if a phone number or formatted date matches the query.
     * Avoids instantiating SimpleDateFormat on every call.
     */
    fun matchesSearchQuery(phoneNumber: String, timestamp: Long, cleanQuery: String): Boolean {
        if (cleanQuery.isBlank()) return true
        if (phoneNumber.lowercase(locale).contains(cleanQuery)) {
            return true
        }

        val groupTitle = getDateGroupTitle(timestamp).lowercase(locale)
        if (groupTitle.contains(cleanQuery)) {
            return true
        }

        val dayKey = timestamp / 86_400_000L
        val fullDateStr = synchronized(searchFormatCache) {
            searchFormatCache.getOrPut(dayKey) {
                synchronized(searchFullFormat) {
                    searchFullFormat.format(Date(timestamp)).lowercase(locale)
                }
            }
        }
        return fullDateStr.contains(cleanQuery)
    }
}
