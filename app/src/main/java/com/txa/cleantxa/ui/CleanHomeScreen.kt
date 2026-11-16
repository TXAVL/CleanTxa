package com.txa.cleantxa.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
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
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.txa.cleantxa.NgonNgu
import com.txa.cleantxa.adb.ThaoTacTv
import com.txa.cleantxa.dulieu.PhatHienLauncher
import com.txa.cleantxa.dulieu.THEMES
import com.txa.cleantxa.dulieu.tenDep
import com.txa.cleantxa.t

@Composable
fun CleanHomeScreen(vm: PhienLamViec) {
    val tabs = listOf(
        t("Giao diện", "Look"),
        t("Home", "Home"),
        t("Tuỳ chỉnh", "Customise"),
    )

    var showWeatherDialog by remember { mutableStateOf(false) }
    var showIptvDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LauncherCard(vm)
            XemTruoc(vm)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tabs.forEachIndexed { idx, label ->
                    FilterChip(
                        selected = vm.theHome == idx,
                        onClick = { vm.theHome = idx },
                        label = { Text(label, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            when (vm.theHome) {
                0 -> ThemesTab(vm)
                1 -> HomeTab(vm, onOpenIptv = { showIptvDialog = true })
                else -> TuyChinhTab(vm, onOpenWeather = { showWeatherDialog = true })
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
private fun ChonKieuHome(vm: PhienLamViec) {
    TxaCard(title = t("Chọn kiểu Home", "Choose the Home"), icon = Icons.Default.Tune) {
        Text(
            t(
                "Hai Home cùng có trong app. Chọn một rồi bấm Áp dụng; Home kia sẽ được gỡ khỏi tivi.",
                "Both Homes ship with the app. Pick one and press Apply; the other is removed from the TV.",
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = !vm.homeTxa,
                onClick = { vm.chonHomeTxa(false) },
                label = { Text(t("Home sạch (CleanYourTV)", "Clean Home (CleanYourTV)")) },
            )
            FilterChip(
                selected = vm.homeTxa,
                onClick = { vm.chonHomeTxa(true) },
                label = { Text(t("CleanTxa Home (tuỳ biến)", "CleanTxa Home (custom)")) },
            )
        }
        if (vm.homeTxa) {
            Spacer(Modifier.height(8.dp))
            ToggleRow(
                title = t("Hàng tiện ích nhanh", "Quick actions row"),
                subtitle = t("Âm lượng, tắt tiếng, dọn RAM, màn hình chờ.", "Volume, mute, free RAM, screensaver."),
                checked = vm.txaNhanh,
                onCheckedChange = { vm.txaNhanh = it; vm.luuTuyChonTxa() },
            )
            ToggleRow(
                title = t("Hàng Mở gần đây", "Recent apps row"),
                checked = vm.txaGanDay,
                onCheckedChange = { vm.txaGanDay = it; vm.luuTuyChonTxa() },
            )
            ToggleRow(
                title = t("Hàng nguồn vào (HDMI, AV)", "Inputs row (HDMI, AV)"),
                checked = vm.txaNguon,
                onCheckedChange = { vm.txaNguon = it; vm.luuTuyChonTxa() },
            )
            ToggleRow(
                title = t("Nút Chế độ trẻ em", "Kids mode button"),
                subtitle = t(
                    "Trên tivi: giữ OK trên app > Thêm vào danh sách trẻ em; giữ OK trên nút Trẻ em để đặt mã PIN.",
                    "On the TV: hold OK on an app > Add to kids list; hold OK on the Kids button to set a PIN.",
                ),
                checked = vm.txaNutTreEm,
                onCheckedChange = { vm.txaNutTreEm = it; vm.luuTuyChonTxa() },
            )
            Text(
                t(
                    "Không dùng được trên tivi Xiaomi bản Trung Quốc (hãng chỉ nhận Home trong danh sách của họ); ở đó app tự dùng Home sạch dành cho Xiaomi.",
                    "Not available on China-market Xiaomi TVs (the maker only accepts Homes on its own list); there the app uses the Xiaomi clean Home instead.",
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ThemesTab(vm: PhienLamViec) {
    ChonKieuHome(vm)

    // Trình chọn ảnh của hệ thống (thư viện ảnh, Google Ảnh…), không phải trình chọn file. Máy cũ tự lùi về hộp chọn ảnh thường.
    val pickWallpaper = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) vm.chonAnhNen(uri)
    }
    val pickRotating = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) vm.themAnhXoay(uris)
    }

    TxaCard(
        title = t("Chọn giao diện (${THEMES.size} kiểu)", "Choose theme (${THEMES.size} styles)"),
        icon = Icons.Default.Palette,
    ) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            THEMES.forEach { th ->
                val selected = vm.themeChon.id == th.id
                val colors = th.mau.map { Color(it) }.let { if (it.size >= 2) it else it + it }
                Column(Modifier.width(150.dp).clickable { vm.chonTheme(th) }) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(92.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Brush.linearGradient(colors))
                            .border(
                                if (selected) 3.dp else 1.dp,
                                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(14.dp),
                            ),
                    ) {
                        Row(Modifier.align(Alignment.BottomStart).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            repeat(4) { n ->
                                Box(
                                    Modifier
                                        .size(width = 26.dp, height = 12.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color.White.copy(alpha = 0.9f - n * 0.2f)),
                                )
                            }
                        }
                        if (selected) {
                            Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(20.dp))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (NgonNgu.tiengViet) th.tenVi else th.tenEn,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
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
            val anhChinh = anhXemTruoc(vm.anhNenRieng, 4)
            if (anhChinh != null) {
                Image(
                    bitmap = anhChinh,
                    contentDescription = t("Ảnh nền đã chọn", "Chosen wallpaper"),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)),
                )
                if (vm.anhXoay.isNotEmpty()) {
                    Text(
                        t("Ảnh tự đổi: ${vm.anhXoay.size + 1} ảnh", "Rotating photos: ${vm.anhXoay.size + 1}"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        vm.anhXoay.forEach { b ->
                            anhXemTruoc(b, 8)?.let {
                                Image(
                                    bitmap = it,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(width = 64.dp, height = 36.dp).clip(RoundedCornerShape(8.dp)),
                                )
                            }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { pickWallpaper.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.weight(1f)) {
                    Text(t("Chọn từ điện thoại", "From phone"))
                }
                OutlinedButton(onClick = { vm.moAnhTrenTv() }, modifier = Modifier.weight(1f)) {
                    Text(t("Từ tivi / USB", "From TV / USB"))
                }
            }
            OutlinedButton(
                onClick = { pickRotating.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
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
private fun HomeTab(vm: PhienLamViec, onOpenIptv: () -> Unit) {
    TxaCard(
        title = t("Dock (tối đa $DOCK_TOI_DA, theo thứ tự chạm)", "Dock (up to $DOCK_TOI_DA, in tap order)"),
        icon = Icons.Default.Apps,
    ) {
        // App đã chọn đứng trước theo thứ tự chạm (số 1, 2, 3…), rồi tới các app còn lại. Chạm để thêm / bỏ.
        val ungVien = vm.dock.toList() + vm.appGanDuoc.filter { it !in vm.dock }
        if (ungVien.isEmpty()) {
            Text(t("Chưa có app nào để chọn.", "No apps to pick yet."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ungVien.forEach { pkg ->
                val thuTu = vm.dock.indexOf(pkg)
                val chon = thuTu >= 0
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (chon) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .border(
                            if (chon) 1.5.dp else 0.dp,
                            if (chon) MaterialTheme.colorScheme.primary else Color.Transparent,
                            RoundedCornerShape(14.dp),
                        )
                        .clickable { if (chon) vm.dock.remove(pkg) else if (vm.dock.size < DOCK_TOI_DA) vm.dock.add(pkg) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(if (chon) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (chon) Text("${thuTu + 1}", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(tenDep(pkg), fontWeight = FontWeight.SemiBold)
                        Text(
                            pkg,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TuyChinhTab(vm: PhienLamViec, onOpenWeather: () -> Unit) {
    if (vm.homeTxa) TxaHomeTuyChinh(vm)

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

    SettingsTab(vm)
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

/** Xem trước màn Home theo giao diện đang chọn: đồng hồ, dock, mô tả giao diện. */
@Composable
private fun XemTruoc(vm: PhienLamViec) {
    val th = vm.themeChon
    val mau = th.mau.map { Color(it) }.let { if (it.size >= 2) it else it + it }
    // Với CleanTxa Home, xem trước theo các công tắc đồng hồ: ẩn / hiện, giây, ngày, 12 / 24 giờ.
    val hienDongHo = !vm.homeTxa || vm.txaDongHo
    val gio24 = !vm.homeTxa || vm.txa24h
    val coGiay = vm.homeTxa && vm.txaGiay
    val hienNgay = hienDongHo && (!vm.homeTxa || vm.txaNgay)
    val gio = remember(gio24, coGiay) {
        java.time.LocalTime.now().format(
            java.time.format.DateTimeFormatter.ofPattern(
                when { gio24 && coGiay -> "HH:mm:ss"; gio24 -> "HH:mm"; coGiay -> "h:mm:ss a"; else -> "h:mm a" },
            ),
        )
    }
    val ngay = remember(hienNgay) {
        val vi = java.util.Locale.getDefault().language == "vi"
        java.time.LocalDate.now().format(
            java.time.format.DateTimeFormatter.ofPattern(if (vi) "EEEE, dd/MM/yyyy" else "EEEE, d MMMM yyyy", if (vi) java.util.Locale("vi", "VN") else java.util.Locale.getDefault()),
        )
    }
    val anh = anhXemTruoc(vm.anhNenRieng, 4)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(mau)),
        ) {
            if (anh != null) {
                // Đã chọn ảnh riêng: ảnh thay nền giao diện, phủ lớp tối đúng bằng mức "Làm tối hình nền".
                Image(
                    bitmap = anh,
                    contentDescription = null,
                    contentScale = if (vm.anhVuaKhung) ContentScale.Fit else ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = (vm.lamToiNen / 100f).coerceIn(0f, 0.8f))))
            }
            Column(Modifier.padding(18.dp)) {
                if (hienDongHo) Text(gio, color = Color.White, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Light)
                if (hienNgay) {
                    Text(
                        ngay + if (vm.amLich) " · " + t("âm lịch", "lunar") else "",
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            Row(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(5) { n ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.92f - n * 0.16f)),
                    )
                }
            }
        }
        Text(
            (if (NgonNgu.tiengViet) th.tenVi else th.tenEn) + " · " + (if (NgonNgu.tiengViet) th.moTaVi else th.moTaEn),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Nhận diện launcher của tivi: có phải một launcher của CleanTxa không, và báo nếu tivi đang dùng / còn cài launcher
 * khác (chúng có thể giành nút Home).
 */
@Composable
private fun LauncherCard(vm: PhienLamViec) {
    val tt = vm.trangThai ?: return
    val kq = PhatHienLauncher.phanLoai(tt.homeHienTai, tt.coHome)
    if (kq.homeHienTai.isEmpty()) return
    val dich = if (vm.homeTxa) ThaoTacTv.PKG_HOME_TXA else ThaoTacTv.PKG_HOME
    val khop = kq.homeHienTai == dich || (!vm.homeTxa && kq.homeHienTai == ThaoTacTv.PKG_HOME_XIAOMI)
    val canhBao = kq.dangDungLauncherKhac || kq.launcherKhacConLai.isNotEmpty()
    TxaCard(
        title = t("Launcher trên tivi", "Launcher on the TV"),
        icon = if (canhBao) Icons.Default.Warning else Icons.Default.Home,
    ) {
        val ten = tenDep(kq.homeHienTai)
        if (kq.laCuaApp) {
            Text(t("Đang dùng $ten, launcher của CleanTxa.", "Using $ten, a CleanTxa launcher."), fontWeight = FontWeight.Medium)
            if (!khop) {
                Text(
                    t("Bấm nút cài ở cuối màn sẽ đổi sang ${tenDep(dich)}.", "Pressing the install button below will switch to ${tenDep(dich)}."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (kq.dangDungLauncherKhac) {
            Text(
                t("Tivi đang dùng launcher khác: $ten (${kq.homeHienTai}), không phải của CleanTxa.", "The TV is using another launcher: $ten (${kq.homeHienTai}), not a CleanTxa one."),
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                t(
                    "Khi cài, ${tenDep(dich)} được đặt làm mặc định. Nếu nút Home vẫn mở launcher cũ, app sẽ thử xử lý (một số hãng không cho đổi).",
                    "When you install, ${tenDep(dich)} is set as the default. If the Home button still opens the old launcher, the app will try to deal with it (some makers don't allow a change).",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(t("Home hiện tại: $ten.", "Current Home: $ten."))
        }
        if (kq.launcherKhacConLai.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                t("Trên tivi còn launcher khác: ", "Other launchers on the TV: ") + kq.launcherKhacConLai.joinToString { tenDep(it) } +
                    t(". Chúng có thể giành nút Home sau khi cài.", ". They may take over the Home button after you install."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** Giải mã ảnh nền đã chuẩn bị (JPEG 1080p) thành ảnh nhỏ để xem trước; [thu] = hệ số thu nhỏ (4 = 1/4 mỗi chiều). */
@Composable
private fun anhXemTruoc(bytes: ByteArray?, thu: Int): ImageBitmap? = remember(bytes) {
    bytes?.let {
        runCatching {
            BitmapFactory.decodeByteArray(it, 0, it.size, BitmapFactory.Options().apply { inSampleSize = thu })?.asImageBitmap()
        }.getOrNull()
    }
}

/** Tuỳ chỉnh riêng của CleanTxa Home: đồng hồ & ngày, cỡ ô app, các nút trên thanh trên cùng. Chỉ hiện khi đang chọn kiểu Home này. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TxaHomeTuyChinh(vm: PhienLamViec) {
    TxaCard(title = t("Đồng hồ & ngày", "Clock & date"), icon = Icons.Default.Tune) {
        ToggleRow(
            title = t("Hiện đồng hồ", "Show the clock"),
            subtitle = t("Ẩn thì ẩn luôn dòng ngày.", "Hiding it also hides the date line."),
            checked = vm.txaDongHo,
            onCheckedChange = { vm.txaDongHo = it; vm.luuTuyChonTxa() },
        )
        if (vm.txaDongHo) {
            ToggleRow(
                title = t("Hiện giây", "Show seconds"),
                checked = vm.txaGiay,
                onCheckedChange = { vm.txaGiay = it; vm.luuTuyChonTxa() },
            )
            ToggleRow(
                title = t("Hiện ngày", "Show the date"),
                subtitle = t("Bật \"Hiện lịch âm\" bên dưới để thêm ngày âm lịch.", "Turn on \"Show Vietnamese lunar date\" below to add the lunar date."),
                checked = vm.txaNgay,
                onCheckedChange = { vm.txaNgay = it; vm.luuTuyChonTxa() },
            )
            ToggleRow(
                title = t("Giờ 24 giờ", "24-hour clock"),
                checked = vm.txa24h,
                onCheckedChange = { vm.txa24h = it; vm.luuTuyChonTxa() },
            )
        }
    }

    TxaCard(title = t("Ô ứng dụng & thanh trên", "App tiles & top bar"), icon = Icons.Default.Apps) {
        Text(t("Cỡ ô ứng dụng", "App tile size"), style = MaterialTheme.typography.bodyMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("small" to t("Nhỏ", "Small"), "medium" to t("Vừa", "Medium"), "large" to t("Lớn", "Large")).forEach { (khoa, nhan) ->
                FilterChip(
                    selected = vm.txaCoO == khoa,
                    onClick = { vm.txaCoO = khoa; vm.luuTuyChonTxa() },
                    label = { Text(nhan) },
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        ToggleRow(
            title = t("Nút Wi-Fi", "Wi-Fi button"),
            checked = vm.txaNutWifi,
            onCheckedChange = { vm.txaNutWifi = it; vm.luuTuyChonTxa() },
        )
        ToggleRow(
            title = t("Nút Cài đặt", "Settings button"),
            checked = vm.txaNutCaiDat,
            onCheckedChange = { vm.txaNutCaiDat = it; vm.luuTuyChonTxa() },
        )
        ToggleRow(
            title = t("Hiện địa chỉ IP của tivi", "Show the TV's IP address"),
            checked = vm.txaHienIp,
            onCheckedChange = { vm.txaHienIp = it; vm.luuTuyChonTxa() },
        )
    }
}
