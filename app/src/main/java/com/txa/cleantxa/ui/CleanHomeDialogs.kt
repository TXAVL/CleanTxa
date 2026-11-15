package com.txa.cleantxa.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

@Composable
private fun WeatherLocationDialog(vm: PhienLamViec, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var wards by remember { mutableStateOf<List<XaPhuong.Xa>>(emptyList()) }
    var worldResults by remember { mutableStateOf<List<XaPhuong.Xa>?>(null) }

    LaunchedEffect(Unit) {
        wards = withContext(Dispatchers.IO) { XaPhuong.nap(ctx) }
    }

    val matchedWards = remember(query, wards) {
        if (query.isBlank()) emptyList() else XaPhuong.timXa(wards, query, 10)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Chọn địa điểm thời tiết", "Choose weather location")) },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it; worldResults = null },
                    label = { Text(t("Tìm phường/xã hoặc thành phố…", "Search ward or city…")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (query.isNotBlank()) {
                    OutlinedButton(onClick = {
                        scope.launch {
                            worldResults = withContext(Dispatchers.IO) {
                                XaPhuong.timTheGioi(query, NgonNgu.tiengViet)
                            }
                        }
                    }) {
                        Text(t("Tìm thành phố nước ngoài", "Search worldwide cities"))
                    }
                }
                LazyColumn(Modifier.height(260.dp)) {
                    if (query.isBlank()) {
                        items(THANH_PHO) { tp ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { vm.thanhPho = tp; onDismiss() }
                                    .padding(vertical = 8.dp)
                            ) {
                                Text(tp.ten, fontWeight = FontWeight.Medium)
                            }
                        }
                    } else {
                        items(matchedWards) { x ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        vm.thanhPho = ThanhPho(x.tenNgan, x.lat, x.lon)
                                        onDismiss()
                                    }
                                    .padding(vertical = 8.dp)
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
                                        .clickable {
                                            vm.thanhPho = ThanhPho(x.ten, x.lat, x.lon)
                                            onDismiss()
                                        }
                                        .padding(vertical = 8.dp)
                                ) {
                                    Text(x.ten, fontWeight = FontWeight.Medium)
                                    Text(x.tinh, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t("Đóng", "Close")) } },
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
