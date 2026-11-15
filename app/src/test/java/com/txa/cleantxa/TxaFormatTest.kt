package com.txa.cleantxa

import org.junit.Assert.assertEquals
import org.junit.Test

class TxaFormatTest {
    @Test
    fun number2f_formatsTwoDecimals() {
        assertEquals("0.00", TxaFormat.number2f(0.0))
        assertEquals("12.35", TxaFormat.number2f(12.346))
        assertEquals("5.50", TxaFormat.number2f(5.5f))
    }

    @Test
    fun bytes_formatsUnitsWithTwoDecimals() {
        assertEquals("512 B", TxaFormat.bytes(512L))
        assertEquals("1.50 KB", TxaFormat.bytes(1536L))
        assertEquals("2.50 MB", TxaFormat.bytes((2.5 * 1024 * 1024).toLong()))
        assertEquals("1.25 GB", TxaFormat.bytes((1.25 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun speed_formatsPerSecond() {
        assertEquals("…", TxaFormat.speed(0L))
        assertEquals("800 B/s", TxaFormat.speed(800L))
        assertEquals("250.00 KB/s", TxaFormat.speed(250L * 1024L))
        assertEquals("12.50 MB/s", TxaFormat.speed((12.5 * 1024 * 1024).toLong()))
    }

    @Test
    fun durationMinutes_formatsUnderAndOver60() {
        assertEquals("1 phút", TxaFormat.durationMinutes(1, vietnamese = true))
        assertEquals("45 min", TxaFormat.durationMinutes(45, vietnamese = false))
        assertEquals("1 giờ", TxaFormat.durationMinutes(60, vietnamese = true))
        assertEquals("1 hr", TxaFormat.durationMinutes(60, vietnamese = false))
        assertEquals("1 giờ 25 phút", TxaFormat.durationMinutes(85, vietnamese = true))
        assertEquals("2 hr 5 min", TxaFormat.durationMinutes(125, vietnamese = false))
    }

    @Test
    fun durationSeconds_formatsSecondsMinutesHours() {
        assertEquals("42s", TxaFormat.durationSeconds(42L, vietnamese = true))
        assertEquals("2m 15s", TxaFormat.durationSeconds(135L, vietnamese = false))
        assertEquals("1 giờ 5 phút", TxaFormat.durationSeconds(3900L, vietnamese = true))
        assertEquals("1h 5m", TxaFormat.durationSeconds(3900L, vietnamese = false))
    }
}

