package com.txa.cleantxa.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.txa.cleantxa.NgonNgu
import com.txa.cleantxa.dulieu.THEMES
import com.txa.cleantxa.dulieu.tenDep
import com.txa.cleantxa.t

@Composable
fun CleanHomeScreen(vm: PhienLamViec) {
    val tabs = listOf(
        t("Giao diện", "Themes"),
        t("Nội dung & Kênh", "Content & Channels"),
        t("Tuỳ chỉnh", "Settings"),
    )

    var showWeatherDialog by remember { mutableStateOf(false) }
    var showIptvDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = vm.theHome.coerceIn(0, 2)) {
            tabs.forEachIndexed { idx, label ->
                Tab(
                    selected = vm.theHome == idx,
                    onClick = { vm.theHome = idx },
                    text = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (vm.theHome) {
                0 -> ThemesTab(vm)
                1 -> ContentTab(
                    vm = vm,
                    onOpenWeather = { showWeatherDialog = true },
                    onOpenIptv = { showIptvDialog = true },
                )
                else -> SettingsTab(vm)
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (vm.cauHinhBao.isNotBlank()) {
                    Text(vm.cauHinhBao, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                Button(
                    onClick = {
                        if (vm.daCoHomeSach) vm.apDungTheme() else vm.caiHomeSach()
                    },
                    enabled = !vm.dangBan,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Home, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (vm.daCoHomeSach) t("Áp dụng lên tivi", "Apply to TV")
                        else t("Cài Home sạch & Áp dụng", "Install Clean Home & Apply")
                    )
                }
            }
        }
    }

    CleanHomeDialogs(
        vm = vm,
        showWeatherDialog = showWeatherDialog,
        onDismissWeather = { showWeatherDialog = false },
        showIptvDialog = showIptvDialog,
        onDismissIptv = { showIptvDialog = false },
    )
}

@Composable
private fun ThemesTab(vm: PhienLamViec) {
    val pickWallpaper = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) vm.chonAnhNen(uri)
    }
    val pickRotating = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.themAnhXoay(uris)
    }

    TxaCard(
        title = t("Chọn giao diện (${THEMES.size} kiểu)", "Choose theme (${THEMES.size} styles)"),
        icon = Icons.Default.Palette,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            THEMES.forEach { th ->
                val selected = vm.themeChon.id == th.id
                val colors = th.mau.map { Color(it) }.let { if (it.size >= 2) it else it + it }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(12.dp),
                        )
                        .clickable { vm.chonTheme(th) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier
                            .size(width = 56.dp, height = 36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Brush.linearGradient(colors)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) {
                            Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(if (NgonNgu.tiengViet) th.tenVi else th.tenEn, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (NgonNgu.tiengViet) th.moTaVi else th.moTaEn,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    TxaCard(
        title = t("Ảnh nền riêng", "Custom wallpaper"),
        subtitle = t("Chọn từ điện thoại, tivi hoặc USB (tối đa 20 ảnh tự đổi).", "Pick from phone, TV or USB (up to 20 rotating photos)."),
        icon = Icons.Default.Image,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { pickWallpaper.launch(arrayOf("image/*")) }, modifier = Modifier.weight(1f)) {
                    Text(t("Chọn từ điện thoại", "From phone"))
                }
                OutlinedButton(onClick = { vm.moAnhTrenTv() }, modifier = Modifier.weight(1f)) {
                    Text(t("Từ tivi / USB", "From TV / USB"))
                }
            }
            OutlinedButton(
                onClick = { pickRotating.launch(arrayOf("image/*")) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(t("Thêm ảnh xoay vòng (${vm.anhXoay.size}/$ANH_XOAY_TOI_DA)", "Add rotating photos (${vm.anhXoay.size}/$ANH_XOAY_TOI_DA)"))
            }
            if (vm.anhNenRieng != null || vm.anhXoay.isNotEmpty()) {
                ToggleRow(
                    title = t("Để vừa khung không cắt (viền mờ)", "Fit inside frame (blurred edges)"),
                    checked = vm.anhVuaKhung,
                    onCheckedChange = { vm.doiVuaKhung(it) },
                )
                TextButton(onClick = { vm.boAnhRieng() }) {
                    Text(t("Xoá ảnh nền riêng", "Remove custom wallpaper"), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContentTab(
    vm: PhienLamViec,
    onOpenWeather: () -> Unit,
    onOpenIptv: () -> Unit,
) {
    TxaCard(
        title = t("Lời chào & Lịch âm", "Greeting & Lunar calendar"),
    ) {
        OutlinedTextField(
            value = vm.tenChao,
            onValueChange = { vm.tenChao = it },
            label = { Text(t("Tên gia đình / lời chào", "Greeting name")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        ToggleRow(
            title = t("Ẩn lời chào", "Hide greeting"),
            checked = vm.anLoiChao,
            onCheckedChange = { vm.anLoiChao = it },
        )
        ToggleRow(
            title = t("Hiện lịch âm (có can chi)", "Show Vietnamese lunar date"),
            checked = vm.amLich,
            onCheckedChange = { vm.amLich = it },
        )
        ToggleRow(
            title = t("Nút Đang phát (tạm dừng app phát tiếng)", "Now Playing button"),
            checked = vm.nutDangPhat,
            onCheckedChange = { vm.nutDangPhat = it },
        )
    }

    TxaCard(
        title = t("Thời tiết trên màn chính", "Weather on Home"),
        icon = Icons.Default.WbSunny,
    ) {
        Text(
            vm.thanhPho?.let { "${it.ten} (${it.lat}, ${it.lon})" } ?: t("Chưa bật thời tiết", "Weather off"),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onOpenWeather) {
                Text(t("Chọn phường/xã hoặc thành phố", "Pick ward or city"))
            }
            if (vm.thanhPho != null) {
                TextButton(onClick = { vm.thanhPho = null }) {
                    Text(t("Tắt", "Off"))
                }
            }
        }
    }

    TxaCard(
        title = t("Kênh truyền hình (${vm.kenhChon.size}/$KENH_TOI_DA)", "TV channels (${vm.kenhChon.size}/$KENH_TOI_DA)"),
        subtitle = t("VTVgo, FPT Play, IPTV (.m3u) hiện thành ô logo ngay trên Home.", "VTVgo, FPT Play, IPTV (.m3u) tiles right on Home."),
        icon = Icons.Default.LiveTv,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { vm.moHopKenh() }, modifier = Modifier.weight(1f)) {
                Text(t("VTVgo / FPT", "VTVgo / FPT"))
            }
            OutlinedButton(onClick = onOpenIptv, modifier = Modifier.weight(1f)) {
                Text(t("IPTV (.m3u)", "IPTV (.m3u)"))
            }
        }
        if (vm.kenhChon.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                vm.kenhChon.forEach { k ->
                    AssistChip(
                        onClick = { vm.kenhChon.remove(k) },
                        label = { Text(k.ten) },
                        trailingIcon = { Icon(Icons.Default.Close, null, Modifier.size(14.dp)) },
                    )
                }
            }
        }
    }

    TxaCard(
        title = t("Hàng ứng dụng trên Home (${vm.dock.size}/$DOCK_TOI_DA)", "Home Dock apps (${vm.dock.size}/$DOCK_TOI_DA)"),
    ) {
        if (vm.dock.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                vm.dock.forEachIndexed { idx, pkg ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(tenDep(pkg), modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                        IconButton(onClick = {
                            if (idx > 0) {
                                val p = vm.dock.removeAt(idx)
                                vm.dock.add(idx - 1, p)
                            }
                        }, enabled = idx > 0) {
                            Icon(Icons.Default.ArrowUpward, null, Modifier.size(18.dp))
                        }
                        IconButton(onClick = {
                            if (idx < vm.dock.lastIndex) {
                                val p = vm.dock.removeAt(idx)
                                vm.dock.add(idx + 1, p)
                            }
                        }, enabled = idx < vm.dock.lastIndex) {
                            Icon(Icons.Default.ArrowDownward, null, Modifier.size(18.dp))
                        }
                        IconButton(onClick = { vm.dock.removeAt(idx) }) {
                            Icon(Icons.Default.Close, null, Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
        val candidates = vm.appGanDuoc.filter { it !in vm.dock }
        if (candidates.isNotEmpty() && vm.dock.size < DOCK_TOI_DA) {
            Spacer(Modifier.height(8.dp))
            Text(t("Bấm để thêm:", "Tap to add:"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                candidates.take(15).forEach { pkg ->
                    AssistChip(onClick = { if (vm.dock.size < DOCK_TOI_DA) vm.dock.add(pkg) }, label = { Text(tenDep(pkg)) })
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsTab(vm: PhienLamViec) {
    val importConfig = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) vm.nhapCauHinh(uri)
    }

    TxaCard(title = t("Bố cục & Hiển thị", "Layout & Display"), icon = Icons.Default.Settings) {
        Text(t("Số app mỗi hàng: ${if (vm.soCot == 0) t("Mặc định", "Default") else vm.soCot}", "Apps per row: ${if (vm.soCot == 0) t("Default", "Default") else vm.soCot}"), style = MaterialTheme.typography.bodyMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0, 4, 5, 6, 7, 8).forEach { col ->
                FilterChip(
                    selected = vm.soCot == col,
                    onClick = { vm.soCot = col },
                    label = { Text(if (col == 0) t("Mặc định", "Auto") else "$col") },
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        ToggleRow(t("Tất cả app một hàng", "Single-row app bar"), checked = vm.motHang, onCheckedChange = { vm.motHang = it })
        ToggleRow(t("Ô icon đều nhau", "Uniform icon tiles"), checked = vm.oDeuNhau, onCheckedChange = { vm.oDeuNhau = it })
        ToggleRow(t("Chữ trắng sáng", "Bright white text"), checked = vm.chuTrang, onCheckedChange = { vm.chuTrang = it })
        ToggleRow(t("Nút Wi-Fi / Cài đặt sát mép trên", "Compact top buttons"), checked = vm.gonNut, onCheckedChange = { vm.gonNut = it })
        ToggleRow(t("Hiện nút Cổng HDMI (Input)", "Show HDMI Input button"), checked = vm.nutInput, onCheckedChange = { vm.nutInput = it })
        ToggleRow(t("Hiện nút Hẹn giờ tắt trên Home", "Show Sleep timer button on Home"), checked = vm.nutHenTat, onCheckedChange = { vm.nutHenTat = it })
    }

    TxaCard(title = t("Nút Home & Phím Remote", "Home button & Remote keys"), icon = Icons.Default.Key) {
        ToggleRow(
            title = t("Bắt phím Home về Home sạch", "Intercept Home key to Clean Home"),
            subtitle = t("Hữu ích khi tivi không cho tắt Home gốc.", "Helpful when stock Home cannot be disabled."),
            checked = vm.nutHome,
            onCheckedChange = { vm.nutHome = it },
        )
        ToggleRow(
            title = t("Giữ nút âm lượng tăng giảm liên tục", "Continuous volume hold"),
            checked = vm.giuAmLuong,
            onCheckedChange = { vm.giuAmLuong = it },
        )
        ToggleRow(
            title = t("Tự mở Home sạch khi bật tivi", "Auto-open Clean Home at power-on"),
            checked = vm.luonVeHome,
            onCheckedChange = { vm.luonVeHome = it },
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { vm.doPhimRemote() },
            enabled = !vm.dangBan && !vm.dangDoPhim,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(t("Dò một nút trên remote (20 giây)", "Learn a remote key (20s)"))
        }
        if (vm.phimVuaDo != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                t("Chọn app mở khi bấm nút vừa dò:", "Choose app for the learned key:"),
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                vm.appGanDuoc.take(12).forEach { pkg ->
                    AssistChip(onClick = { vm.ganPhim(pkg) }, label = { Text(tenDep(pkg)) })
                }
            }
        }
    }

    TxaCard(title = t("Màn chờ, DNS & Nâng cao", "Screensaver, DNS & Advanced"), icon = Icons.Default.Tune) {
        ToggleRow(
            title = t("Màn chờ động (đồng hồ trên ảnh trôi chậm)", "Animated screensaver"),
            checked = vm.manChoDong,
            onCheckedChange = { vm.manChoDong = it },
        )
        ToggleRow(
            title = t("DNS chặn quảng cáo (AdGuard)", "AdGuard Ad-blocking DNS"),
            checked = vm.dnsChanQc,
            onCheckedChange = { vm.dnsChanQc = it },
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { vm.xuatCauHinhTv() }, enabled = !vm.dangBan, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Save, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(t("Lưu cấu hình", "Export"))
            }
            OutlinedButton(onClick = { importConfig.launch(arrayOf("application/json", "*/*")) }, enabled = !vm.dangBan, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.FileOpen, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(t("Nạp cấu hình", "Import"))
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { vm.moNangCao = true }, modifier = Modifier.fillMaxWidth()) {
            Text(t("90+ Tuỳ chọn nâng cao", "90+ Advanced options"))
        }
    }
}

