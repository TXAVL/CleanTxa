package com.txa.cleantxa.ui

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.txa.cleantxa.NgonNgu
import com.txa.cleantxa.dulieu.NangCao
import com.txa.cleantxa.dulieu.THANH_PHO
import com.txa.cleantxa.dulieu.ThanhPho
import com.txa.cleantxa.dulieu.ViTriHienTai
import com.txa.cleantxa.dulieu.XaPhuong
import com.txa.cleantxa.t
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CleanHomeDialogs(
    vm: PhienLamViec,
    showWeatherDialog: Boolean,
    onDismissWeather: () -> Unit,
    showIptvDialog: Boolean,
    onDismissIptv: () -> Unit,
) {
    if (showWeatherDialog) {
        WeatherLocationDialog(vm, onDismiss = onDismissWeather)
    }
    if (vm.moChonKenh) {
        ChannelPickerDialog(vm)
    }
    if (showIptvDialog) {
        IptvDialog(vm, onDismiss = onDismissIptv)
    }
    if (vm.moNangCao) {
        AdvancedOptionsDialog(vm)
    }
    vm.anhTrenTv?.let { paths ->
        AlertDialog(
            onDismissRequest = { vm.anhTrenTv = null },
            title = { Text(t("Ảnh trên tivi / USB", "Pictures on TV / USB")) },
            text = {
                LazyColumn(Modifier.height(300.dp)) {
                    if (paths.isEmpty()) {
                        item { Text(t("Không tìm thấy file ảnh nào trên tivi hoặc USB.", "No image files found on TV or USB.")) }
                    }
                    items(paths) { p ->
                        Text(
                            p,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { vm.chonAnhTrenTv(p) }
                                .padding(vertical = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.anhTrenTv = null }) { Text(t("Đóng", "Close")) } },
        )
    }
}

/**
 * Chọn nơi hiện thời tiết trên Home, hai bước: chọn tỉnh / thành phố trước, rồi phường / xã của tỉnh đó. Không muốn chọn
 * phường / xã thì chọn dòng cuối danh sách (tên tỉnh / thành phố). Hoặc bấm nút lấy vị trí hiện tại của điện thoại.
 * Vẫn gõ tìm thẳng phường / xã hoặc thành phố nước ngoài được ở bước đầu.
 */
@Composable
private fun WeatherLocationDialog(vm: PhienLamViec, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var wards by remember { mutableStateOf<List<XaPhuong.Xa>>(emptyList()) }
    var provinces by remember { mutableStateOf<List<XaPhuong.Tinh>>(emptyList()) }
    var tinhChon by remember { mutableStateOf<XaPhuong.Tinh?>(null) }
    var worldResults by remember { mutableStateOf<List<XaPhuong.Xa>?>(null) }
    var baoViTri by remember { mutableStateOf("") }
    var dangLayViTri by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val (tinh, xa) = withContext(Dispatchers.IO) { XaPhuong.napTinh(ctx) to XaPhuong.nap(ctx) }
        provinces = tinh
        wards = xa
    }

    fun chon(tp: ThanhPho) {
        vm.thanhPho = tp
        onDismiss()
    }

    fun dungViTri() {
        dangLayViTri = true
        baoViTri = ""
        scope.launch {
            val vt = ViTriHienTai.lay(ctx)
            if (vt == null) {
                dangLayViTri = false
                baoViTri = t(
                    "Không lấy được vị trí. Bật định vị (GPS) trên điện thoại rồi thử lại, hoặc chọn tỉnh / thành bên dưới.",
                    "Couldn't get your location. Turn on location (GPS) on the phone and try again, or pick a province / city below.",
                )
                return@launch
            }
            val ten = ViTriHienTai.tenGoiY(ctx, vt.latitude, vt.longitude, wards, NgonNgu.tiengViet)
            dangLayViTri = false
            // Làm tròn 3 chữ số thập phân (khoảng 100 m): đủ cho thời tiết, không lưu vị trí chính xác hơn mức cần.
            val lat = Math.round(vt.latitude * 1000) / 1000.0
            val lon = Math.round(vt.longitude * 1000) / 1000.0
            Toast.makeText(ctx, t("Đã lấy vị trí: $ten", "Location set: $ten"), Toast.LENGTH_SHORT).show()
            chon(ThanhPho(ten, lat, lon))
        }
    }

    val xinQuyen = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { kq ->
        if (kq.values.any { it }) dungViTri()
        else baoViTri = t(
            "Chưa có quyền vị trí. Bạn có thể cấp trong Cài đặt > Ứng dụng > CleanTxa > Quyền, hoặc chọn tỉnh / thành bên dưới.",
            "Location permission wasn't granted. You can allow it in Settings > Apps > CleanTxa > Permissions, or pick a province / city below.",
        )
    }

    val tinh = tinhChon
    val tinhKhop = remember(query, provinces) { XaPhuong.timTinh(provinces, query) }
    val xaKhop = remember(query, wards, tinh) {
        if (tinh == null) { if (query.isBlank()) emptyList() else XaPhuong.timXa(wards, query, 10) }
        else XaPhuong.locXa(XaPhuong.xaCuaTinh(wards, tinh), query)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (tinh == null) t("Chọn tỉnh / thành phố", "Choose province / city") else tinh.ten)
        },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (tinh == null) {
                    OutlinedButton(
                        onClick = {
                            if (ViTriHienTai.coQuyen(ctx)) dungViTri()
                            else xinQuyen.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
                        },
                        enabled = !dangLayViTri,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.MyLocation, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (dangLayViTri) t("Đang lấy vị trí…", "Getting your location…") else t("Dùng vị trí hiện tại", "Use my current location"))
                    }
                    if (baoViTri.isNotBlank()) {
                        Text(baoViTri, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it; worldResults = null },
                        label = { Text(t("Tìm tỉnh / thành, phường / xã…", "Search province, ward…")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (query.isNotBlank()) {
                        OutlinedButton(onClick = {
                            scope.launch {
                                worldResults = withContext(Dispatchers.IO) { XaPhuong.timTheGioi(query, NgonNgu.tiengViet) }
                            }
                        }) {
                            Text(t("Tìm thành phố nước ngoài", "Search worldwide cities"))
                        }
                    }
                    LazyColumn(Modifier.height(260.dp)) {
                        items(tinhKhop) { p ->
                            Text(
                                p.ten,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { tinhChon = p; query = "" }
                                    .padding(vertical = 10.dp),
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        if (query.isNotBlank()) {
                            items(xaKhop) { x ->
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { chon(ThanhPho(x.tenNgan, x.lat, x.lon)) }
                                        .padding(vertical = 8.dp),
                                ) {
                                    Text(x.ten, fontWeight = FontWeight.Medium)
                                    Text(x.tinh, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            worldResults?.let { wr ->
                                items(wr) { x ->
                                    Column(
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable { chon(ThanhPho(x.ten, x.lat, x.lon)) }
                                            .padding(vertical = 8.dp),
                                    ) {
                                        Text(x.ten, fontWeight = FontWeight.Medium)
                                        Text(x.tinh, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        t(
                            "Chọn phường / xã. Không muốn chọn thì chọn dòng cuối danh sách (tên ${tinh.tenNgan}).",
                            "Pick a ward. If you'd rather not, choose the last line of the list (${tinh.tenNgan}).",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text(t("Tìm phường / xã…", "Search ward…")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    LazyColumn(Modifier.height(300.dp)) {
                        items(xaKhop) { x ->
                            Text(
                                x.ten,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { chon(ThanhPho(x.tenNgan, x.lat, x.lon)) }
                                    .padding(vertical = 10.dp),
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        // Dòng cuối luôn có, kể cả khi đang lọc: chọn cả tỉnh / thành phố, không cần chọn phường / xã.
                        item {
                            Text(
                                t("${tinh.ten} (cả tỉnh / thành phố)", "${tinh.ten} (whole province / city)"),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { chon(ThanhPho(tinh.tenNgan, tinh.lat, tinh.lon)) }
                                    .padding(vertical = 12.dp),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t("Đóng", "Close")) } },
        dismissButton = if (tinh != null) {
            { TextButton(onClick = { tinhChon = null; query = "" }) { Text(t("Đổi tỉnh / thành", "Change province")) } }
        } else null,
    )
}

@Composable
private fun ChannelPickerDialog(vm: PhienLamViec) {
    var search by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { vm.moChonKenh = false },
        title = { Text(t("Chọn kênh VTVgo", "Pick VTVgo channels")) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text(t("Tìm kênh (VTV1, HTV7, Vĩnh Long…)", "Search channel…")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                val filtered = remember(search, vm.dsKenhGo) {
                    val q = search.trim().lowercase()
                    if (q.isEmpty()) vm.dsKenhGo
                    else vm.dsKenhGo.filter { it.ten.lowercase().contains(q) || it.nhom.lowercase().contains(q) }
                }
                LazyColumn(Modifier.height(300.dp)) {
                    items(filtered, key = { it.ma }) { k ->
                        val selected = vm.coKenh(k.ma)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { vm.batTatKenh(k.tenO, k.ma) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = selected, onCheckedChange = { vm.batTatKenh(k.tenO, k.ma) })
                            Column(Modifier.weight(1f)) {
                                Text(k.ten, fontWeight = FontWeight.Medium)
                                if (k.nhom.isNotBlank()) {
                                    Text(k.nhom, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { vm.moChonKenh = false }) { Text(t("Xong", "Done")) } },
    )
}

@Composable
private fun IptvDialog(vm: PhienLamViec, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Nhập danh sách IPTV (.m3u)", "Load IPTV playlist (.m3u)")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    t("Dán link .m3u hoặc .m3u8 (mở kênh bằng VLC, MX Player hoặc Kodi trên tivi).", "Paste an .m3u / .m3u8 URL (opens in VLC, MX Player or Kodi on TV)."),
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = vm.nguonIptv,
                    onValueChange = { vm.nguonIptv = it },
                    label = { Text("https://.../playlist.m3u") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                vm.dsIptv?.let { list ->
                    LazyColumn(Modifier.height(220.dp)) {
                        items(list) { k ->
                            val selected = vm.coO(k.url)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { vm.batTatIptv(k) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = selected, onCheckedChange = { vm.batTatIptv(k) })
                                Text(k.ten, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (vm.nguonIptv.isNotBlank()) vm.moIptv()
            }) { Text(t("Tải danh sách", "Load")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Đóng", "Close")) } },
    )
}

@Composable
private fun AdvancedOptionsDialog(vm: PhienLamViec) {
    val ctx = LocalContext.current
    val options = remember { NangCao.nap(ctx) }
    var search by remember { mutableStateOf("") }
    val filtered = remember(search, options) {
        val q = search.trim().lowercase()
        if (q.isEmpty()) options
        else options.filter { it.khoa.lowercase().contains(q) || it.tieuDe().lowercase().contains(q) }
    }

    AlertDialog(
        onDismissRequest = { vm.moNangCao = false },
        title = { Text(t("Tuỳ chọn nâng cao (${options.size})", "Advanced options (${options.size})")) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text(t("Tìm tuỳ chọn…", "Search option…")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.height(340.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filtered, key = { it.khoa }) { tc ->
                        val valStr = vm.nangCao[tc.khoa] ?: ""
                        Column(Modifier.fillMaxWidth()) {
                            Text(tc.tieuDe(), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                            Text(tc.moTaHien(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedTextField(
                                value = valStr,
                                onValueChange = { v ->
                                    if (v.isBlank()) vm.nangCao.remove(tc.khoa) else vm.nangCao[tc.khoa] = v
                                },
                                placeholder = { Text(tc.kieu) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { vm.moNangCao = false }) { Text(t("Xong", "Done")) } },
    )
}
