package com.txa.cleantxa

import java.util.Locale

/**
 * Bộ định dạng chuẩn của CleanTxa cho tốc độ mạng, đơn vị dung lượng,
 * số thập phân 2 chữ số (%.2f) và thời lượng ước tính.
 */
object TxaFormat {
    private const val KB = 1024.0
    private const val MB = 1024.0 * 1024.0
    private const val GB = 1024.0 * 1024.0 * 1024.0
    private const val TB = 1024.0 * 1024.0 * 1024.0 * 1024.0

    /** Định dạng số thực với 2 chữ số thập phân (%.2f). */
    fun number2f(value: Double): String = String.format(Locale.US, "%.2f", value)

    fun number2f(value: Float): String = number2f(value.toDouble())

    fun number2f(value: Number): String = number2f(value.toDouble())

    /** Định dạng dung lượng kèm đơn vị chuẩn (B, KB, MB, GB, TB) với 2 chữ số thập phân. */
    fun bytes(bytes: Long): String {
        val b = bytes.coerceAtLeast(0L).toDouble()
        return when {
            b < KB -> "${b.toLong()} B"
            b < MB -> "${number2f(b / KB)} KB"
            b < GB -> "${number2f(b / MB)} MB"
            b < TB -> "${number2f(b / GB)} GB"
            else -> "${number2f(b / TB)} TB"
        }
    }

    /** Định dạng tốc độ truyền tải (B/s, KB/s, MB/s, GB/s) với 2 chữ số thập phân. */
    fun speed(bytesPerSec: Long, zeroText: String = "…"): String {
        if (bytesPerSec <= 0L) return zeroText
        return "${bytes(bytesPerSec)}/s"
    }

    /**
     * Định dạng thời lượng theo phút; tự động chuyển sang giờ + phút nếu >= 60 phút.
     */
    fun durationMinutes(minutes: Int, vietnamese: Boolean = NgonNgu.tiengViet): String {
        val safe = minutes.coerceAtLeast(1)
        if (safe < 60) {
            return if (vietnamese) "$safe phút" else "$safe min"
        }
        val hours = safe / 60
        val rem = safe % 60
        return if (rem == 0) {
            if (vietnamese) "$hours giờ" else "$hours hr"
        } else {
            if (vietnamese) "$hours giờ $rem phút" else "$hours hr $rem min"
        }
    }

    /** Định dạng thời lượng theo giây (dùng cho ETA tải xuống). */
    fun durationSeconds(seconds: Long, vietnamese: Boolean = NgonNgu.tiengViet): String {
        val s = seconds.coerceAtLeast(0L)
        if (s < 60L) return "${s}s"
        val mins = s / 60L
        val remS = s % 60L
        if (mins < 60L) {
            return if (remS == 0L) "${mins}m" else "${mins}m ${remS}s"
        }
        val hours = mins / 60L
        val remM = mins % 60L
        return if (remM == 0L) {
            if (vietnamese) "${hours} giờ" else "${hours}h"
        } else {
            if (vietnamese) "${hours} giờ ${remM} phút" else "${hours}h ${remM}m"
        }
    }
}

