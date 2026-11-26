package com.txa.cleantxa.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.txa.cleantxa.t

@Composable
fun TvInfoScreen(vm: PhienLamViec) {
    val tv = vm.thongTin ?: return
    val tt = vm.trangThai

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (vm.khongPhaiTivi && !vm.xacNhanLaTivi) {
            TxaCard(
                title = t("Thiết bị này giống điện thoại / máy tính bảng", "This looks like a phone or tablet"),
                icon = Icons.Default.Warning,
                highlighted = true,
            ) {
                Text(
                    t(
                        "CleanTxa được thiết kế cho Android TV / Google TV. Dọn nhầm điện thoại có thể tắt nhầm thành phần hệ thống.",
                        "CleanTxa is built for Android TV / Google TV. Cleaning a phone may disable important system features."
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.veManChinh() }) { Text(t("Ngắt kết nối", "Disconnect")) }
                    OutlinedButton(onClick = { vm.xacNhanLaTivi = true }) { Text(t("Đây là Android TV, tiếp tục", "It's a TV, continue")) }
                }
            }
        }

        if (vm.muiGioLech.isNotBlank()) {
            TxaCard(
                title = t("Giờ tivi đang lệch múi giờ", "TV timezone mismatch"),
                icon = Icons.Default.Schedule,
            ) {
                Text(
                    t(
                        "Tivi đang ở múi giờ ${vm.muiGioLech}, điện thoại ở ${vm.muiGioDienThoai}. Lệch giờ có thể làm đồng hồ và thời tiết trên Home sạch hiển thị sai.",
                        "TV is in ${vm.muiGioLech}, phone is in ${vm.muiGioDienThoai}. This can affect the clock and weather."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { vm.datGioNhuDienThoai() }, enabled = !vm.dangBan) {
                    Text(t("Đặt giờ tivi theo điện thoại", "Sync TV timezone to phone"))
                }
            }
        }

        TxaCard(
            title = tv.tenHienThi.ifBlank { tv.model },
            subtitle = "Android ${tv.android} (SDK ${tv.sdk}) · " +
                if (!vm.dangAdb && vm.dangLienKetNgam) t("🔗 Liên kết ngầm Wi-Fi (${vm.ip}) · Không cần ADB", "🔗 Wi-Fi Background Link (${vm.ip}) · No ADB")
                else "${vm.ip}:${vm.cong}",
            icon = Icons.Default.Tv,
            highlighted = true,
        ) {
            if (tt != null) {
                Text(
                    t(
                        "Đã cài ${tt.caiDat.size} gói · Đang tắt ${tt.daTat.size} gói · Home hiện tại: ${tt.homeHienTai.ifBlank { "?" }}",
                        "${tt.caiDat.size} packages · ${tt.daTat.size} disabled · Current Home: ${tt.homeHienTai.ifBlank { "?" }}"
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            vm.boNho?.let { (free, total) ->
                if (total > 0) {
                    val used = (total - free).coerceAtLeast(0)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        t(
                            "Bộ nhớ trống: ${com.txa.cleantxa.TxaFormat.bytes(free)} / ${com.txa.cleantxa.TxaFormat.bytes(total)}",
                            "Free storage: ${com.txa.cleantxa.TxaFormat.bytes(free)} / ${com.txa.cleantxa.TxaFormat.bytes(total)}"
                        ),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { (used.toFloat() / total.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { vm.donBoNhoDem() }, enabled = !vm.dangBan && vm.dangAdb) {
                            Icon(Icons.Default.CleaningServices, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(t("Xoá cache", "Clear cache"))
                        }
                        OutlinedButton(onClick = { vm.moAppChiemCho() }, enabled = !vm.dangBan && vm.dangAdb) {
                            Icon(Icons.Default.Storage, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(t("App chiếm chỗ", "App storage"))
                        }
                    }
                }
            }
        }

        var hoiKhoiDongLai by remember { mutableStateOf(false) }

        TxaCard(
            title = t("Tối ưu & Tăng tốc TV", "TV Speed & Optimization"),
            subtitle = t("Giảm hiệu ứng chuyển cảnh giúp TV phản hồi nhanh hơn, dọn RAM nền hoặc khởi động lại nhanh.", "Reduce transition animations for snappier UI, trim background RAM, or reboot quickly."),
        ) {
            Text(
                t("Tốc độ hiệu ứng hệ thống", "System animation scale"),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            val curScale = vm.tiLeHieuUng?.toDoubleOrNull()
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    "0" to t("0x (Tắt)", "0x (Off)"),
                    "0.5" to t("0.5x (Nhanh)", "0.5x (Fast)"),
                    "1" to t("1x (Mặc định)", "1x (Default)"),
                ).forEach { (scale, label) ->
                    val active = curScale != null && kotlin.math.abs(curScale - scale.toDouble()) < 0.05
                    FilterChip(
                        selected = active,
                        onClick = { vm.datTiLeHieuUng(scale) },
                        enabled = !vm.dangBan && vm.dangAdb,
                        leadingIcon = { Icon(Icons.Default.Speed, null, Modifier.size(16.dp)) },
                        label = { Text(label) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = { vm.giaiPhongRamNen() }, enabled = !vm.dangBan) {
                    Icon(Icons.Default.Memory, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(t("Giải phóng RAM nền", "Free background RAM"))
                }
                OutlinedButton(onClick = { hoiKhoiDongLai = true }, enabled = !vm.dangBan && vm.dangAdb) {
                    Icon(Icons.Default.RestartAlt, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(t("Khởi động lại TV", "Reboot TV"))
                }
            }
        }

        if (hoiKhoiDongLai) {
            AlertDialog(
                onDismissRequest = { hoiKhoiDongLai = false },
                title = { Text(t("Khởi động lại TV?", "Reboot TV?")) },
                text = {
                    Text(
                        t(
                            "TV sẽ khởi động lại ngay và ngắt kết nối ADB tạm thời. Sau khi TV bật lại, bạn kết nối lại như bình thường.",
                            "The TV will reboot immediately and ADB will disconnect temporarily. Reconnect once the TV boots back up."
                        )
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            hoiKhoiDongLai = false
                            vm.khoiDongLaiTv()
                        },
                    ) {
                        Text(t("Khởi động lại", "Reboot"))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { hoiKhoiDongLai = false }) {
                        Text(t("Huỷ", "Cancel"))
                    }
                },
            )
        }

        val soMau = vm.tatCaMau.size.takeIf { it > 0 } ?: 513
        TxaCard(
            title = t("Danh sách mẫu cho tivi này", "TV model profile"),
            subtitle = t("So với $soMau danh sách đã thử từ cộng đồng CleanYourTV.", "Matched against $soMau tested community lists."),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                vm.ungVien.forEach { (m, score) ->
                    ProfileOptionRow(
                        selected = vm.mauChon?.id == m.id,
                        title = m.ten,
                        subtitle = t("Khớp cao (điểm $score) · ${m.goi.size} gói", "Strong match ($score) · ${m.goi.size} packages"),
                        onClick = { vm.mauChon = m },
                    )
                }
                if (vm.ungVien.isEmpty() && vm.ganGiong.isNotEmpty()) {
                    Text(
                        t("Mẫu gần giống:", "Similar models:"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    vm.ganGiong.forEach { m ->
                        ProfileOptionRow(
                            selected = vm.mauChon?.id == m.id,
                            title = m.ten,
                            subtitle = t("Cùng hãng · ${m.goi.size} gói", "Same brand · ${m.goi.size} packages"),
                            onClick = { vm.mauChon = m },
                        )
                    }
                }
                ProfileOptionRow(
                    selected = vm.mauChon == null,
                    title = t("Dùng luật chung an toàn", "Use safe general rules"),
                    subtitle = t("Chỉ gợi ý những gói được cộng đồng tắt nhiều và giữ nguyên các gói quan trọng.", "Suggests widely disabled packages while protecting core components."),
                    onClick = { vm.mauChon = null },
                )
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { vm.moChonMau = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Search, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(t("Tìm trong tất cả ${vm.tatCaMau.size} mẫu", "Browse all ${vm.tatCaMau.size} profiles"))
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!vm.dangAdb && vm.dangLienKetNgam) {
                Text(
                    t(
                        "Đang điều khiển qua Liên kết ngầm Wi-Fi (ADB đang tắt): bạn có thể dùng đầy đủ Remote, gõ tiếng Việt, cài APK, giải phóng RAM và Tuỳ chỉnh giao diện Home sạch. Chỉ khi cần tắt / bật lại gói hệ thống của hãng mới cần bật tạm ADB.",
                        "Connected via Wi-Fi Background Link (ADB is off): Remote, Vietnamese typing, APK install, RAM boost, and Clean Home customisation all work directly. Turn on ADB only when disabling or restoring system packages."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Button(
                onClick = { vm.lapKeHoach() },
                enabled = !vm.dangBan && !vm.chanKhongPhaiTivi && vm.dangAdb,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.CleaningServices, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (!vm.dangAdb && vm.dangLienKetNgam) t("Chọn gói cần tắt (cần bật ADB)", "Select packages to disable (needs ADB)")
                    else t("Bước tiếp: Chọn gói cần tắt", "Next: Select packages to disable")
                )
            }

            if (!vm.khongCaiHome) {
                OutlinedButton(
                    onClick = { if (vm.daCoHomeSach) vm.moDoiTheme() else vm.boQuaDon() },
                    enabled = !vm.dangBan && !vm.chanKhongPhaiTivi && (vm.dangAdb || vm.daCoHomeSach),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Home, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (vm.daCoHomeSach) t("Tuỳ chỉnh Home sạch", "Customize Clean Home")
                        else t("Bỏ qua dọn, chỉ cài Home sạch", "Skip cleaning, install Clean Home")
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { vm.moRemote() },
                    enabled = !vm.dangBan,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.SettingsRemote, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(t("Remote", "Remote"))
                }
                OutlinedButton(
                    onClick = { vm.moKhoaTre() },
                    enabled = !vm.dangBan && vm.dangAdb,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.ChildCare, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(t("Khoá trẻ em", "Kids lock"))
                }
            }

            OutlinedButton(
                onClick = {
                    val sl = vm.saoLuu
                    if (sl != null) vm.moKhoiPhuc(sl) else vm.man = Man.KHOI_PHUC
                },
                enabled = !vm.dangBan && vm.dangAdb,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Restore, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(t("Khôi phục / Bật lại từng app", "Restore / Re-enable apps"))
            }

            // Tivi đã dọn / đã có Home sạch thì cho xuất báo cáo chẩn đoán ngay sau khi kết nối từ điện thoại.
            if (com.txa.cleantxa.ManHinh.laTv || vm.daCoHomeSach || vm.saoLuu != null || vm.coDauCleanTv) {
                OutlinedButton(
                    onClick = { vm.xuatBaoCaoChanDoan() },
                    enabled = !vm.dangBan && vm.dangAdb,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Share, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(t("Xuất báo cáo chẩn đoán", "Export diagnostic report"))
                }
            }
        }
    }

    if (vm.moChonMau) {
        AllProfilesDialog(vm)
    }
}

@Composable
private fun ProfileOptionRow(
    selected: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AllProfilesDialog(vm: PhienLamViec) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, vm.tatCaMau) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) vm.tatCaMau
        else vm.tatCaMau.filter { it.ten.lowercase().contains(q) || it.id.lowercase().contains(q) }
    }
    AlertDialog(
        onDismissRequest = { vm.moChonMau = false },
        title = { Text(t("Chọn mẫu tivi (${vm.tatCaMau.size})", "Select TV profile (${vm.tatCaMau.size})")) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(t("Tìm hãng hoặc model…", "Search brand or model…")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.height(340.dp)) {
                    items(filtered, key = { it.id }) { m ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vm.mauChon = m
                                    vm.moChonMau = false
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            Text(m.ten, fontWeight = FontWeight.Medium)
                            Text("${m.id} · ${m.goi.size} ${t("gói", "packages")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.moChonMau = false }) { Text(t("Đóng", "Close")) }
        },
    )
}

