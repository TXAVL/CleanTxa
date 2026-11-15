package com.txa.cleantxa

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Báo "có bản mới" bằng thông báo Android cả khi app đang đóng.
 * WorkManager kiểm tra GitHub Releases mỗi 1 giờ một lần, mỗi bản chỉ báo một lần.
 */
object ThongBaoCapNhat {
    private const val KENH = "cap-nhat"
    const val MO_CAP_NHAT = "mo_cap_nhat"

    fun hen(ctx: Context) {
        val viec = PeriodicWorkRequestBuilder<KiemTraBanMoi>(1, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            "kiem-tra-ban-moi",
            ExistingPeriodicWorkPolicy.UPDATE,
            viec,
        )
    }

    fun bao(ctx: Context, b: CapNhat.BanMoi, daTaiSan: Boolean = false) {
        val p = ctx.getSharedPreferences("ota", Context.MODE_PRIVATE)
        if (p.getInt("da_bao", 0) >= b.versionCode) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(KENH, t("Bản mới của app", "App updates"), NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val mo = PendingIntent.getActivity(
            ctx,
            1,
            Intent(ctx, MainActivity::class.java)
                .putExtra(MO_CAP_NHAT, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val suffix = if (daTaiSan) {
            t("Đã tải sẵn APK trong nền — chạm để cài đặt ngay.", "APK pre-downloaded in background — tap to install now.")
        } else {
            t("Chạm để cập nhật.", "Tap to update.")
        }
        val tb = NotificationCompat.Builder(ctx, KENH)
            .setSmallIcon(R.drawable.ic_thong_bao)
            .setContentTitle(t("CleanTxa ${b.versionName} đã có", "CleanTxa ${b.versionName} is out"))
            .setContentText(b.ghiChu.ifBlank { suffix })
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    (if (b.ghiChu.isNotBlank()) b.ghiChu + "\n" else "") + suffix
                )
            )
            .setColor(0xFF2EC4B6.toInt())
            .setContentIntent(mo)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(1, tb) }
        p.edit().putInt("da_bao", b.versionCode).apply()
    }
}

class KiemTraBanMoi(ctx: Context, ts: WorkerParameters) : CoroutineWorker(ctx, ts) {
    override suspend fun doWork(): Result {
        NgonNgu.nap(applicationContext)
        runCatching { CapNhat.dongBoTuCleanYourTv(applicationContext) }
        val b = runCatching { CapNhat.kiemTraApp() }.getOrElse { return Result.retry() } ?: return Result.success()
        val preDownloaded = CapNhat.taiApkNeuChuaCo(applicationContext, b)
        ThongBaoCapNhat.bao(applicationContext, b, daTaiSan = preDownloaded != null)
        return Result.success()
    }
}

