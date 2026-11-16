package com.txa.cleantxa.dulieu

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import java.util.Locale
import java.util.function.Consumer
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Lấy vị trí hiện tại của điện thoại để chọn nơi hiện thời tiết trên tivi. Chỉ chạy khi người dùng bấm nút và đã cấp quyền;
 * toạ độ không gửi đi đâu ngoài việc ghi vào cấu hình Home trên tivi của chính họ.
 */
object ViTriHienTai {
    private const val TUOI_TOI_DA_MS = 2 * 3600_000L

    fun coQuyen(ctx: Context): Boolean =
        ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Vị trí đã biết còn mới thì dùng luôn; không thì hỏi vị trí hiện tại (Android 11+, tối đa 12 giây). null = không lấy được. */
    @SuppressLint("MissingPermission")
    suspend fun lay(ctx: Context): Location? = withContext(Dispatchers.IO) {
        if (!coQuyen(ctx)) return@withContext null
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return@withContext null
        val bat = runCatching { lm.getProviders(true) }.getOrDefault(emptyList())
        val daBiet = bat.mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }.maxByOrNull { it.time }
        if (daBiet != null && System.currentTimeMillis() - daBiet.time < TUOI_TOI_DA_MS) return@withContext daBiet
        if (Build.VERSION.SDK_INT >= 30) {
            val nguon = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER).firstOrNull { it in bat }
            if (nguon != null) {
                val moi = withTimeoutOrNull(12_000) {
                    suspendCancellableCoroutine<Location?> { cont ->
                        val huy = CancellationSignal()
                        cont.invokeOnCancellation { huy.cancel() }
                        runCatching {
                            lm.getCurrentLocation(nguon, huy, ctx.mainExecutor, Consumer<Location?> { cont.resume(it) })
                        }.onFailure { cont.resume(null) }
                    }
                }
                if (moi != null) return@withContext moi
            }
        }
        daBiet
    }

    /**
     * Tên hiện trên tivi cho toạ độ này: phường/xã gần nhất trong 30 km (ở Việt Nam), không thì địa danh do hệ thống tra
     * (Geocoder), không thì "Vị trí hiện tại".
     */
    @Suppress("DEPRECATION")
    suspend fun tenGoiY(ctx: Context, lat: Double, lon: Double, xa: List<XaPhuong.Xa>, tiengViet: Boolean): String =
        withContext(Dispatchers.IO) {
            XaPhuong.gan(xa, lat, lon)?.let { return@withContext it.tenNgan }
            val diaDanh = runCatching {
                if (!Geocoder.isPresent()) null
                else Geocoder(ctx, if (tiengViet) Locale("vi") else Locale.getDefault()).getFromLocation(lat, lon, 1)?.firstOrNull()
                    ?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
            }.getOrNull()
            diaDanh?.takeIf { it.isNotBlank() } ?: if (tiengViet) "Vị trí hiện tại" else "Current location"
        }
}
