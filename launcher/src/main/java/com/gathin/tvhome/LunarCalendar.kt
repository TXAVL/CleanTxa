package com.gathin.tvhome

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.floor

/**
 * Vietnamese Lunar Calendar converter (Ho Ngoc Duc algorithm, UTC+7).
 */
object LunarCalendar {
    private val CAN = arrayOf("Giáp", "Ất", "Bính", "Đinh", "Mậu", "Kỷ", "Canh", "Tân", "Nhâm", "Quý")
    private val CHI = arrayOf("Tý", "Sửu", "Dần", "Mão", "Thìn", "Tỵ", "Ngọ", "Mùi", "Thân", "Dậu", "Tuất", "Hợi")

    fun formatToday(cal: Calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"))): String {
        val dd = cal.get(Calendar.DAY_OF_MONTH)
        val mm = cal.get(Calendar.MONTH) + 1
        val yy = cal.get(Calendar.YEAR)
        val (ld, lm, ly, leap) = solarToLunar(dd, mm, yy)
        val canYear = CAN[(ly + 6) % 10]
        val chiYear = CHI[(ly + 8) % 12]
        val leapText = if (leap) " (nhuận)" else ""
        return "Âm lịch $ld/$lm$leapText · Năm $canYear $chiYear"
    }

    data class LunarDate(val day: Int, val month: Int, val year: Int, val leap: Boolean)

    private fun jdFromDate(dd: Int, mm: Int, yy: Int): Int {
        val a = (14 - mm) / 12
        val y = yy + 4800 - a
        val m = mm + 12 * a - 3
        var jd = dd + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045
        if (jd < 2299161) {
            jd = dd + (153 * m + 2) / 5 + 365 * y + y / 4 - 32083
        }
        return jd
    }

    private fun newMoon(k: Int): Double {
        val t = k / 1236.85
        val t2 = t * t
        val t3 = t2 * t
        val dr = Math.PI / 180.0
        var jd1 = 2415020.75933 + 29.53058868 * k + 0.0001178 * t2 - 0.000000155 * t3
        jd1 += 0.00033 * kotlin.math.sin((166.56 + 132.87 * t - 0.009173 * t2) * dr)
        val m = 359.2242 + 29.10535608 * k - 0.0000333 * t2 - 0.00000347 * t3
        val mpr = 306.0253 + 385.81691806 * k + 0.0107306 * t2 + 0.00001236 * t3
        val f = 21.2964 + 390.67050646 * k - 0.0016528 * t2 - 0.00000239 * t3
        var c1 = (0.1734 - 0.000393 * t) * kotlin.math.sin(m * dr) + 0.0021 * kotlin.math.sin(2 * m * dr)
        c1 = c1 - 0.4068 * kotlin.math.sin(mpr * dr) + 0.0161 * kotlin.math.sin(2 * mpr * dr)
        c1 -= 0.0004 * kotlin.math.sin(3 * mpr * dr)
        c1 = c1 + 0.0104 * kotlin.math.sin(2 * f * dr) - 0.0051 * kotlin.math.sin((m + mpr) * dr)
        c1 = c1 - 0.0074 * kotlin.math.sin((m - mpr) * dr) + 0.0004 * kotlin.math.sin((2 * f + m) * dr)
        c1 = c1 - 0.0004 * kotlin.math.sin((2 * f - m) * dr) - 0.0006 * kotlin.math.sin((2 * f + mpr) * dr)
        c1 = c1 + 0.0010 * kotlin.math.sin((2 * f - mpr) * dr) + 0.0005 * kotlin.math.sin((2 * mpr + m) * dr)
        val deltaT = if (t < -11) 0.001 + 0.000839 * t + 0.0002261 * t2 - 0.00000845 * t3 - 0.000000081 * t * t3
        else -0.000278 + 0.000265 * t + 0.000262 * t2
        return jd1 + c1 - deltaT
    }

    private fun sunLongitude(jdn: Double): Double {
        val t = (jdn - 2451545.0) / 36525.0
        val t2 = t * t
        val dr = Math.PI / 180.0
        val m = 357.52910 + 35999.05030 * t - 0.0001559 * t2 - 0.00000048 * t * t2
        val l0 = 280.46645 + 36000.76983 * t + 0.0003032 * t2
        var dl = (1.914600 - 0.004817 * t - 0.000014 * t2) * kotlin.math.sin(dr * m)
        dl += (0.019993 - 0.000101 * t) * kotlin.math.sin(dr * 2 * m) + 0.000290 * kotlin.math.sin(dr * 3 * m)
        var l = (l0 + dl) * dr
        l -= Math.PI * 2 * floor(l / (Math.PI * 2))
        return floor(l / Math.PI * 6)
    }

    private fun getNewMoonDay(k: Int, timeZone: Double): Int = floor(newMoon(k) + 0.5 + timeZone / 24.0).toInt()
    private fun getSunLongitude(dayNumber: Int, timeZone: Double): Int = sunLongitude(dayNumber - 0.5 - timeZone / 24.0).toInt()

    private fun getLunarMonth11(yy: Int, timeZone: Double): Int {
        val off = jdFromDate(31, 12, yy) - 2415021
        val k = floor(off / 29.530588853).toInt()
        var nm = getNewMoonDay(k, timeZone)
        if (getSunLongitude(nm, timeZone) >= 9) nm = getNewMoonDay(k - 1, timeZone)
        return nm
    }

    private fun getLeapMonthOffset(a11: Int, timeZone: Double): Int {
        val k = floor((a11 - 2415021.076998695) / 29.530588853 + 0.5).toInt()
        var last: Int
        var i = 1
        var arc = getSunLongitude(getNewMoonDay(k + i, timeZone), timeZone)
        do {
            last = arc
            i++
            arc = getSunLongitude(getNewMoonDay(k + i, timeZone), timeZone)
        } while (arc != last && i < 14)
        return i - 1
    }

    fun solarToLunar(dd: Int, mm: Int, yy: Int, timeZone: Double = 7.0): LunarDate {
        val dayNumber = jdFromDate(dd, mm, yy)
        val k = floor((dayNumber - 2415021.076998695) / 29.530588853).toInt()
        var monthStart = getNewMoonDay(k + 1, timeZone)
        if (monthStart > dayNumber) monthStart = getNewMoonDay(k, timeZone)
        var a11 = getLunarMonth11(yy, timeZone)
        var b11 = a11
        var lunarYear: Int
        if (a11 >= monthStart) {
            lunarYear = yy
            a11 = getLunarMonth11(yy - 1, timeZone)
        } else {
            lunarYear = yy + 1
            b11 = getLunarMonth11(yy + 1, timeZone)
        }
        val lunarDay = dayNumber - monthStart + 1
        val diff = floor((monthStart - a11) / 29.0).toInt()
        var lunarLeap = false
        var lunarMonth = diff + 11
        if (b11 - a11 > 365) {
            val leapMonthDiff = getLeapMonthOffset(a11, timeZone)
            if (diff >= leapMonthDiff) {
                lunarMonth = diff + 10
                if (diff == leapMonthDiff) lunarLeap = true
            }
        }
        if (lunarMonth > 12) lunarMonth -= 12
        if (lunarMonth >= 11 && diff < 4) lunarYear -= 1
        return LunarDate(lunarDay, lunarMonth, lunarYear, lunarLeap)
    }
}

