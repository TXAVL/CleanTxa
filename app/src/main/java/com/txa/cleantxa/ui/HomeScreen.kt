package com.txa.cleantxa.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Phonelink
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.txa.cleantxa.BuildConfig
import com.txa.cleantxa.ManHinh
import com.txa.cleantxa.link.TxaLinkAccessibilityService
import com.txa.cleantxa.link.TxaLinkNotificationListener
import com.txa.cleantxa.link.TxaLinkService
import com.txa.cleantxa.link.TxaLinkStore
import com.txa.cleantxa.t
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(vm: PhienLamViec) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        vm.banMoi?.let { b ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Default.SystemUpdate, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text(
                            t("Có bản mới ${b.versionName}", "Update ${b.versionName} available"),
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (b.ghiChu.isNotBlank()) {
                            Text(
                                b.ghiChu,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    val daTaiSan = vm.apkDaTai != null
                    TextButton(onClick = { vm.capNhatApp() }, enabled = !vm.dangCapNhat) {
                        Text(
                            when {
                                vm.dangCapNhat -> t("Đang tải…", "Downloading…")
                                daTaiSan -> t("Cài đặt", "Install")
                                else -> t("Cập nhật", "Update")
                            }
                        )
                    }
                }
            }
        }

        if (vm.daTuTatAdb) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        t(
                            "ADB trên tivi đã tự tắt vì bạn rời app quá ${vm.phutTuTatAdb} phút. Muốn chỉnh tiếp thì vào Tuỳ chọn nhà phát triển trên tivi, bật lại Gỡ lỗi USB rồi kết nối lại.",
                            "ADB on the TV turned itself off because you left the app for over ${vm.phutTuTatAdb} min. To keep tweaking, turn USB debugging back on in the TV's Developer options and connect again."
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        // Hero card
        TxaCard(highlighted = true) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CleanTxaLogo(size = 56.dp)
                Column(Modifier.weight(1f)) {
                    Text(
                        t("Tivi hết\nquảng cáo", "No more\nTV ads"),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        lineHeight = 30.sp,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                t(
                    "Kết nối tivi qua Wi-Fi, tắt quảng cáo với app rác của hãng, rồi thay màn Home cho gọn. Muốn trả lại như cũ thì bấm Khôi phục.",
                    "Connect to the TV over Wi-Fi, switch off the maker's ads and junk apps, then give it a clean Home. Want it back? Press Restore."
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    vm.datLai()
                    vm.man = Man.KET_NOI
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.AutoAwesome, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (ManHinh.laTv) t("Dọn tivi này", "Clean this TV")
                    else t("Dọn một tivi", "Clean a TV")
                )
            }
            if (vm.ip.isNotBlank() && !ManHinh.laTv) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        vm.man = Man.KET_NOI
                        vm.ketNoi()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.SettingsRemote, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(t("Kết nối lại nhanh (${vm.ip})", "Quick reconnect (${vm.ip})"))
                }
            }
        }

        // Liên kết Điện thoại ↔ Tivi chạy ngầm không cần ADB (giống Microsoft Phone Link)
        if (ManHinh.laTv) {
            TheLienKetTrenTv()
        } else {
            TheLienKetDienThoai(vm)
        }

        // Quick Tools (shown on both TV Launcher and Phone app)
        TxaCard(
            title = t("Tiện ích nhanh", "Quick tools"),
            subtitle = t(
                "Mở nhanh tăng tốc TV, nhật ký hệ thống và kiểm tra bản mới.",
                "Quickly open TV speed boost, system logs, and update check.",
            ),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        if (vm.thongTin != null) {
                            vm.man = Man.THONG_TIN
                        } else if (ManHinh.laTv || vm.ip.isNotBlank()) {
                            vm.man = Man.KET_NOI
                            vm.ketNoi()
                        } else {
                            vm.datLai()
                            vm.man = Man.KET_NOI
                        }
                    },
                ) {
                    Icon(Icons.Default.Speed, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(t("Tăng tốc TV", "TV Speed Boost"))
                }
                OutlinedButton(onClick = { vm.moLogs() }) {
                    Icon(Icons.Default.Terminal, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(t("Nhật ký", "Logs"))
                }
                if (ManHinh.laTv) {
                    OutlinedButton(onClick = { vm.moManLoiQr() }) {
                        Icon(Icons.Default.QrCode2, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(t("Web QR (2311)", "Web QR (2311)"))
                    }
                }
                OutlinedButton(
                    onClick = { vm.kiemTraCapNhatThuCong() },
                    enabled = !vm.dangKiemTraCapNhat && !vm.dangCapNhat,
                ) {
                    Icon(Icons.Default.SystemUpdate, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (vm.dangKiemTraCapNhat) t("Đang kiểm tra…", "Checking…")
                        else t("Kiểm tra cập nhật", "Check updates")
                    )
                }
            }
        }

        // Prominent prerequisite reminder card (first-time TV Developer Mode)
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f)),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Default.WarningAmber,
                        null,
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                    Text(
                        t("Trước tiên: bật chế độ nhà phát triển trên tivi", "First: turn on developer mode on the TV"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    t(
                        "Không có bước này app không kết nối được tivi. Làm một lần, khoảng 1 phút: Cài đặt → Giới thiệu → bấm Bản dựng 7 lần → bật Gỡ lỗi qua USB.",
                        "The app can't reach the TV without this. One time, about a minute: Settings → About → press Build 7 times → turn on USB debugging."
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = { vm.moHuongDan() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = Color.Black,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        t("Xem hướng dẫn có hình (7 bước)", "Picture guide (7 steps)"),
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    t("Chỉ Android TV / Google TV. Samsung (Tizen), LG (webOS) không làm được.", "Android TV / Google TV only. Samsung (Tizen) and LG (webOS) are not supported."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Cleaned TVs / Backups
        if (vm.dsSaoLuu.isNotEmpty()) {
            Text(
                t("Tivi đã dọn", "Cleaned TVs"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp),
            )
            vm.dsSaoLuu.forEach { b ->
                TxaCard {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            Icons.Default.Tv,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                b.ten.ifBlank { b.khoa },
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                            Text(
                                "${b.ip} · ${formatDate(b.luc)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                t("tắt ${b.daTatBoiApp.size} gói", "${b.daTatBoiApp.size} disabled") +
                                    if (b.launcherDaCai) t(" · Home sạch", " · Clean Home") else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        OutlinedButton(onClick = { vm.moKhoiPhuc(b) }) {
                            Icon(Icons.Default.Restore, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(t("Khôi phục", "Restore"))
                        }
                        IconButton(onClick = { vm.kho.xoa(b.khoa); vm.taiLaiSaoLuu() }) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = t("Xoá", "Delete"),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        // Open-source licenses & attribution footer
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TextButton(onClick = { vm.moGiayPhep() }) {
                Icon(Icons.Default.Info, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(t("Giấy phép mã nguồn mở", "Open-source licenses"))
            }
            Text(
                "CleanTxa ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatDate(ms: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(ms))

/**
 * Thẻ hiển thị trên TIVI: khởi chạy ngầm TxaLinkService (cổng 2312), hướng dẫn cấp quyền 1 lần duy nhất,
 * và xác nhận ghép đôi điện thoại tin cậy (kiểu Microsoft Phone Link — tắt ADB vẫn kết nối được).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TheLienKetTrenTv() {
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var hasA11y by remember { mutableStateOf(TxaLinkAccessibilityService.isEnabled(ctx)) }
    var hasOverlay by remember { mutableStateOf(Settings.canDrawOverlays(ctx)) }
    var hasInstall by remember {
        mutableStateOf(Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls())
    }
    var localIp by remember { mutableStateOf(TxaLinkService.localIpv4()) }

    DisposableEffect(lifecycle) {
        TxaLinkStore.danhDauDaMoTrenTv(ctx)
        TxaLinkService.start(ctx)
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                TxaLinkService.start(ctx)
                hasA11y = TxaLinkAccessibilityService.isEnabled(ctx)
                hasOverlay = Settings.canDrawOverlays(ctx)
                hasInstall = Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls()
                localIp = TxaLinkService.localIpv4()
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    val trustRev = TxaLinkStore.trustRevision
    val trustedPhones = remember(trustRev) { TxaLinkStore.dsDienThoaiTinCay(ctx) }
    val pending = TxaLinkStore.pendingPair

    fun openSettingSafe(primary: Intent) {
        val ok = runCatching {
            ctx.startActivity(primary.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
        if (!ok) {
            runCatching {
                ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    // Hộp xác nhận yêu cầu liên kết từ điện thoại (hiện ngay trên màn chính TV nếu chưa có quyền vẽ đè hoặc đang mở app)
    if (pending != null) {
        TxaCard(
            title = t("Yêu cầu liên kết từ điện thoại", "Phone link request"),
            subtitle = "${pending.phoneName} (${pending.remoteIp})",
            icon = Icons.Default.Phonelink,
            highlighted = true,
        ) {
            Text(
                t(
                    "Mã xác nhận: ${pending.pinCode}\nChỉ bấm Cho phép nếu mã này khớp với màn hình điện thoại của bạn.",
                    "Verification code: ${pending.pinCode}\nOnly allow if this code matches your phone screen.",
                ),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { TxaLinkService.approvePendingFromUi(true) }) {
                    Icon(Icons.Default.CheckCircle, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(t("Tin tưởng & Cho phép", "Trust & Allow"))
                }
                OutlinedButton(onClick = { TxaLinkService.approvePendingFromUi(false) }) {
                    Icon(Icons.Default.Close, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(t("Từ chối", "Decline"))
                }
            }
        }
    }

    TxaCard(
        title = t("TxaLink · Cầu nối TV", "TxaLink · TV Bridge"),
        subtitle = t(
            "Đang chạy ngầm · ${localIp ?: "Wi-Fi"}:${TxaLinkStore.PORT} — Cấp quyền 1 lần ở đây để điện thoại đã tin cậy luôn tự kết nối & điều khiển TV kể cả khi tắt ADB.",
            "Running in background · ${localIp ?: "Wi-Fi"}:${TxaLinkStore.PORT} — Grant permissions once so trusted phones auto-connect even when ADB is off.",
        ),
        icon = Icons.Default.Phonelink,
        highlighted = true,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TvPermissionRow(
                title = t("1. Điều khiển Remote & Gõ tiếng Việt", "1. Remote Control & Vietnamese Typing"),
                desc = t(
                    "Cho phép bấm điều hướng D-Pad, Home, Back, Đa nhiệm và gõ chữ có dấu từ điện thoại.",
                    "Allows D-Pad, Home, Back, Recents, and accented text input from your phone.",
                ),
                granted = hasA11y,
                onGrant = { openSettingSafe(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
            )
            TvPermissionRow(
                title = t("2. Hiện hộp xác nhận khi chạy ngầm", "2. Background Pairing Prompt"),
                desc = t(
                    "Hiện bảng bấm \"Tin tưởng\" ngay trên màn đang xem khi ghép điện thoại mới lần đầu.",
                    "Shows the one-time \"Trust\" prompt over any screen when pairing a new phone.",
                ),
                granted = hasOverlay,
                onGrant = {
                    openSettingSafe(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${ctx.packageName}"),
                        )
                    )
                },
            )
            TvPermissionRow(
                title = t("3. Cài ứng dụng APK gửi từ điện thoại", "3. Install APKs Sent from Phone"),
                desc = t(
                    "Cho phép mở trình cài đặt khi bạn gửi file APK từ điện thoại sang TV.",
                    "Allows opening the package installer when you send an APK from your phone.",
                ),
                granted = hasInstall,
                onGrant = {
                    if (Build.VERSION.SDK_INT >= 26) {
                        openSettingSafe(
                            Intent(
                                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:${ctx.packageName}"),
                            )
                        )
                    }
                },
            )

            if (trustedPhones.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    t("Điện thoại đã tin cậy (${trustedPhones.size}):", "Trusted phones (${trustedPhones.size}):"),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    trustedPhones.forEach { ph ->
                        AssistChip(
                            onClick = { TxaLinkStore.xoaDienThoaiTinCay(ctx, ph.id) },
                            leadingIcon = { Icon(Icons.Default.Phonelink, null, Modifier.size(16.dp)) },
                            trailingIcon = { Icon(Icons.Default.Close, contentDescription = t("Bỏ tin cậy", "Remove"), modifier = Modifier.size(14.dp)) },
                            label = { Text(ph.name) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvPermissionRow(
    title: String,
    desc: String,
    granted: Boolean,
    onGrant: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (granted) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                if (granted) Icons.Default.CheckCircle else Icons.Default.WarningAmber,
                contentDescription = null,
                tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(20.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (granted) {
                Text(
                    t("Đã cấp", "Granted"),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                OutlinedButton(onClick = onGrant) {
                    Text(t("Cấp quyền", "Grant"))
                }
            }
        }
    }
}

/**
 * Thẻ hiển thị trên ĐIỆN THOẠI: tự quét cùng mạng Wi-Fi, tự kết nối ngầm tới TV đã tin cậy (kiểu Microsoft Phone Link),
 * và cho phép điều khiển Remote / Tuỳ chỉnh Home sạch / Dọn RAM ngay trên màn chính kể cả khi TV đã tắt hoàn toàn ADB.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TheLienKetDienThoai(vm: PhienLamViec) {
    val ctx = LocalContext.current
    val trustRev = TxaLinkStore.trustRevision
    val trustedTvs = remember(trustRev) { TxaLinkStore.dsTvTinCay(ctx) }
    var autoBg by remember { mutableStateOf(TxaLinkStore.tuKetNoiNgam(ctx)) }

    TxaCard(
        title = if (vm.dangLienKetNgam && vm.thongTin != null) {
            t("Đã liên kết ngầm: ${vm.thongTin!!.tenHienThi.ifBlank { vm.ip }}", "Linked: ${vm.thongTin!!.tenHienThi.ifBlank { vm.ip }}")
        } else {
            t("TxaLink · Điều khiển TV", "TxaLink · TV Remote")
        },
        subtitle = if (vm.dangLienKetNgam && vm.thongTin != null) {
            val ramTxt = vm.ramTv?.takeIf { it.second > 0 }?.let { (free, total) ->
                " · RAM ${com.txa.cleantxa.TxaFormat.bytes(free)}/${com.txa.cleantxa.TxaFormat.bytes(total)}"
            }.orEmpty()
            val curApp = vm.appDangMoTrenTv
                .takeIf { it.isNotBlank() && it != "android" }
                ?.let { " · Đang mở: ${com.txa.cleantxa.dulieu.tenDep(it)}" }
                .orEmpty()
            t(
                "Wi-Fi ${vm.ip}:${TxaLinkStore.PORT}$ramTxt$curApp",
                "Wi-Fi ${vm.ip}:${TxaLinkStore.PORT}$ramTxt",
            )
        } else {
            t(
                "Với TV đã cài Home sạch & mở app CleanTxa trên TV ít nhất 1 lần: tự nhận diện cùng mạng Wi-Fi, bắn YouTube/ảnh/tin nhắn giọng nói và điều khiển không cần ADB.",
                "For TVs with Clean Home & CleanTxa opened once: auto-discovers over Wi-Fi, beams links/photos/voice banners, and works without ADB.",
            )
        },
        icon = Icons.Default.Phonelink,
        highlighted = vm.dangLienKetNgam || vm.dsTvLink.isNotEmpty(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (vm.dangLienKetNgam && vm.thongTin != null) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(onClick = { vm.moRemote() }, enabled = !vm.dangBan) {
                        Icon(Icons.Default.AutoAwesome, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(t("Remote & Đặc quyền Link", "Remote & Link Exclusives"))
                    }
                    if (vm.daCoHomeSach) {
                        OutlinedButton(onClick = { vm.moDoiTheme() }, enabled = !vm.dangBan) {
                            Icon(Icons.Default.Home, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(t("Tuỳ chỉnh Home sạch", "Customize Home"))
                        }
                    }
                    OutlinedButton(onClick = { vm.giaiPhongRamNen() }, enabled = !vm.dangBan) {
                        Icon(Icons.Default.Memory, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(t("Dọn RAM", "Free RAM"))
                    }
                    OutlinedButton(onClick = { vm.man = Man.THONG_TIN }, enabled = !vm.dangBan) {
                        Icon(Icons.Default.Tv, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(t("Chi tiết TV", "TV Info"))
                    }
                    TextButton(onClick = { vm.ngatLienKetNgam() }, enabled = !vm.dangBan) {
                        Icon(Icons.Default.LinkOff, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(t("Ngắt", "Disconnect"))
                    }
                }
            } else {
                if (vm.dsTvLink.isNotEmpty()) {
                    vm.dsTvLink.forEach { d ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(Icons.Default.Tv, null, tint = MaterialTheme.colorScheme.primary)
                                Column(Modifier.weight(1f)) {
                                    Text(d.name, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "${d.ip} · Android ${d.androidVer}" +
                                            if (d.isTrusted) t(" · Đã tin cậy", " · Trusted") else t(" · Chưa ghép", " · Unpaired"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Button(
                                    onClick = { vm.chonTvLink(d, chuyenMan = !d.isTrusted) },
                                    enabled = !vm.dangBan,
                                ) {
                                    Icon(Icons.Default.Link, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (d.isTrusted) t("Kết nối ngầm", "Link") else t("Ghép tin cậy", "Trust"))
                                }
                            }
                        }
                    }
                } else if (trustedTvs.isNotEmpty()) {
                    Text(
                        t("Tivi đã tin cậy (tự kết nối ngay khi bật cùng mạng Wi-Fi):", "Trusted TVs (auto-connects when online on the same Wi-Fi):"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        trustedTvs.forEach { tv ->
                            AssistChip(
                                onClick = { vm.quetLinkNgam(tuKetNoi = true) },
                                leadingIcon = { Icon(Icons.Default.Tv, null, Modifier.size(16.dp)) },
                                trailingIcon = {
                                    IconButton(
                                        onClick = { TxaLinkStore.xoaTvTinCay(ctx, tv.tvId) },
                                        modifier = Modifier.size(18.dp),
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = t("Quên TV", "Forget"), modifier = Modifier.size(14.dp))
                                    }
                                },
                                label = { Text("${tv.name} (${tv.lastIp})") },
                            )
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = { vm.quetLinkNgam(tuKetNoi = autoBg) },
                        enabled = !vm.dangQuetLink && !vm.dangBan,
                    ) {
                        if (vm.dangQuetLink) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (vm.dangQuetLink) t("Đang quét mạng…", "Scanning…")
                            else t("Quét tìm TV chạy ngầm", "Scan for TVs")
                        )
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    t("Tự động kết nối ngầm khi mở app", "Auto-connect silently on launch"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = autoBg,
                    onCheckedChange = {
                        autoBg = it
                        TxaLinkStore.datTuKetNoiNgam(ctx, it)
                        if (it && !vm.dangLienKetNgam) vm.quetLinkNgam(tuKetNoi = true)
                    },
                )
            }

            var mirrorNotif by remember { mutableStateOf(TxaLinkStore.phanChieuThongBao(ctx)) }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    t(
                        "Hiện thông báo & cuộc gọi đến của điện thoại trên TV",
                        "Show phone notifications & incoming calls on TV",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = mirrorNotif,
                    onCheckedChange = {
                        mirrorNotif = it
                        TxaLinkStore.datPhanChieuThongBao(ctx, it)
                        if (it && !TxaLinkNotificationListener.daCapQuyen(ctx)) {
                            TxaLinkNotificationListener.moCaiDatQuyen(ctx)
                        }
                    },
                )
            }
        }
    }

    vm.maGhepLinkDangCho?.let { pin ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(t("Mã xác nhận liên kết: $pin", "Link verification code: $pin")) },
            text = {
                Text(
                    t(
                        "Nhìn lên màn hình tivi và bấm \"Tin tưởng & Cho phép\" trên hộp thoại có mã $pin.\n\n(Chỉ cần xác nhận một lần duy nhất — từ lần sau hai máy sẽ tự kết nối ngầm khi chung mạng Wi-Fi).",
                        "Look at your TV screen and select \"Trust & Allow\" on the prompt showing code $pin.\n\n(One-time setup — next time both devices will auto-connect silently over Wi-Fi).",
                    )
                )
            },
            confirmButton = {},
        )
    }
}
