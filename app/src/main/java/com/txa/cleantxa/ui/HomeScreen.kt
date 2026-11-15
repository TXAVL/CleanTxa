package com.txa.cleantxa.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.txa.cleantxa.BuildConfig
import com.txa.cleantxa.ManHinh
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
