package com.example.audioambientglow.util

import java.util.Calendar
import java.util.Locale

/**
 * High-accuracy Chinese Lunar Calendar (農曆) Converter
 * Supports years 2020 - 2035 with exact Tiangan/Dizhi, Lunar Month, Day, and Zodiac.
 */
object LunarCalendarUtil {

    private val LUNAR_MONTH_NAMES = arrayOf(
        "正月", "二月", "三月", "四月", "五月", "六月",
        "七月", "八月", "九月", "十月", "冬月", "臘月"
    )

    private val LUNAR_DAY_NAMES = arrayOf(
        "初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十",
        "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十",
        "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十"
    )

    private val TIAN_GAN = arrayOf("甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸")
    private val DI_ZHI = arrayOf("子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥")
    private val ZODIAC = arrayOf("鼠", "牛", "虎", "兔", "龍", "蛇", "馬", "羊", "猴", "雞", "狗", "豬")

    // Hex encoded lunar calendar bitmask data (1900 - 2040)
    // Bits 0-3: Leap month (0 = none), Bits 4-15: 12 month days (1 = 30d, 0 = 29d), Bits 16-19: Leap month days (1 = 30d, 0 = 29d)
    private val LUNAR_INFO = longArrayOf(
        0x04bd8, 0x04ae0, 0x0a570, 0x054d5, 0x0d260, 0x0d950, 0x16554, 0x056a0, 0x09ad0, 0x055d2, // 1900-1909
        0x04ae0, 0x0a5b6, 0x0a4d0, 0x0d250, 0x1d255, 0x0b540, 0x0d6a0, 0x0ada2, 0x095b0, 0x14977, // 1910-1919
        0x04970, 0x0a4b0, 0x0b4b5, 0x06a50, 0x06d40, 0x1ab54, 0x02b60, 0x09570, 0x052f2, 0x04970, // 1920-1929
        0x06566, 0x0d4a0, 0x0ea50, 0x06e95, 0x05ad0, 0x02b60, 0x186e3, 0x092e0, 0x1c8d7, 0x0c950, // 1930-1939
        0x0d4a0, 0x1d8a6, 0x0b550, 0x056a0, 0x1a5b4, 0x025d0, 0x092d0, 0x0d2b2, 0x0a950, 0x0b557, // 1940-1949
        0x06ca0, 0x0b550, 0x15355, 0x04da0, 0x0a5b0, 0x14573, 0x052b0, 0x0a9a8, 0x0e950, 0x06aa0, // 1950-1959
        0x0aea6, 0x0ab50, 0x04b60, 0x0aae4, 0x0a570, 0x05260, 0x0f263, 0x0d950, 0x05b57, 0x056a0, // 1960-1969
        0x096d0, 0x04dd5, 0x04ad0, 0x0a4d0, 0x0d4d4, 0x0d250, 0x0d558, 0x0b540, 0x0b6a0, 0x195a6, // 1970-1979
        0x095b0, 0x049b0, 0x0a974, 0x0a4b0, 0x0b27a, 0x06a50, 0x06d40, 0x0af46, 0x0ab60, 0x09570, // 1980-1989
        0x04af5, 0x04970, 0x064b0, 0x074a3, 0x0ea50, 0x06b58, 0x055c0, 0x0ab60, 0x096d5, 0x092e0, // 1990-1999
        0x0c960, 0x0d954, 0x0d4a0, 0x0da50, 0x07552, 0x056a0, 0x0abb7, 0x025d0, 0x092d0, 0x0cab5, // 2000-2009
        0x0a950, 0x0b4a0, 0x0baa4, 0x0ad50, 0x055d9, 0x04ba0, 0x0a5b0, 0x15176, 0x052b0, 0x0a930, // 2010-2019
        0x07954, 0x06aa0, 0x0ad50, 0x05b52, 0x04b60, 0x0a6e6, 0x0a4e0, 0x0d260, 0x0ea65, 0x0d530, // 2020-2029
        0x05aa0, 0x076a3, 0x096d0, 0x04afb, 0x04ad0, 0x0a4d0, 0x1d0b6, 0x0d250, 0x0d520, 0x0dd45  // 2030-2039
    )

    private fun getYearDays(year: Int): Int {
        var sum = 348
        val info = LUNAR_INFO[year - 1900]
        var i = 0x8000
        while (i > 0x8) {
            if ((info and i.toLong()) != 0L) sum += 1
            i = i shr 1
        }
        return sum + getLeapDays(year)
    }

    private fun getLeapMonth(year: Int): Int {
        return (LUNAR_INFO[year - 1900] and 0xf).toInt()
    }

    private fun getLeapDays(year: Int): Int {
        if (getLeapMonth(year) != 0) {
            return if ((LUNAR_INFO[year - 1900] and 0x10000L) != 0L) 30 else 29
        }
        return 0
    }

    private fun getMonthDays(year: Int, month: Int): Int {
        return if ((LUNAR_INFO[year - 1900] and (0x10000L shr month)) == 0L) 29 else 30
    }

    data class LunarDate(
        val year: Int,
        val month: Int,
        val day: Int,
        val isLeap: Boolean,
        val ganzhiYear: String,
        val zodiac: String,
        val formatted: String
    )

    /**
     * Convert Gregorian calendar date to Chinese Lunar Calendar
     */
    fun getLunarDate(calendar: Calendar = Calendar.getInstance()): LunarDate {
        val baseDate = Calendar.getInstance().apply {
            set(1900, 0, 31, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }

        var offset = ((calendar.timeInMillis - baseDate.timeInMillis) / 86400000L).toInt()

        var year = 1900
        var daysInYear = 0
        while (year < 2040 && offset > 0) {
            daysInYear = getYearDays(year)
            offset -= daysInYear
            year++
        }

        if (offset < 0) {
            offset += daysInYear
            year--
        }

        val leapMonth = getLeapMonth(year)
        var isLeap = false
        var month = 1
        var daysInMonth = 0

        while (month <= 12 && offset > 0) {
            if (leapMonth > 0 && month == (leapMonth + 1) && !isLeap) {
                --month
                isLeap = true
                daysInMonth = getLeapDays(year)
            } else {
                daysInMonth = getMonthDays(year, month)
            }

            if (isLeap && month == (leapMonth + 1)) isLeap = false

            offset -= daysInMonth
            if (!isLeap) month++
        }

        if (offset == 0 && leapMonth > 0 && month == leapMonth + 1) {
            if (isLeap) {
                isLeap = false
            } else {
                isLeap = true
                --month
            }
        }

        if (offset < 0) {
            offset += daysInMonth
            --month
        }

        val day = offset + 1

        val ganZhiIdx = (year - 4) % 60
        val tianGan = TIAN_GAN[ganZhiIdx % 10]
        val diZhi = DI_ZHI[ganZhiIdx % 12]
        val shengXiao = ZODIAC[(year - 4) % 12]
        val ganzhiYear = "$tianGan$diZhi${shengXiao}年"

        val leapPrefix = if (isLeap) "閏" else ""
        val monthStr = LUNAR_MONTH_NAMES.getOrElse(month - 1) { "${month}月" }
        val dayStr = LUNAR_DAY_NAMES.getOrElse(day - 1) { "${day}日" }

        val formatted = "歲次${tianGan}${diZhi}年 $leapPrefix$monthStr$dayStr"

        return LunarDate(
            year = year,
            month = month,
            day = day,
            isLeap = isLeap,
            ganzhiYear = ganzhiYear,
            zodiac = shengXiao,
            formatted = formatted
        )
    }
}
