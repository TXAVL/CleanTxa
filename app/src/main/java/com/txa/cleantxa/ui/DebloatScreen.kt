package com.txa.cleantxa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.txa.cleantxa.dulieu.KeHoach
import com.txa.cleantxa.dulieu.LoaiMuc
import com.txa.cleantxa.dulieu.MucGoi
import com.txa.cleantxa.dulieu.tenDep
import com.txa.cleantxa.t

@Composable
fun DebloatScreen(vm: PhienLamViec) {
    var search by remember { mutableStateOf("") }
    var showKeepWarning by remember { mutableStateOf(false) }

    val selectedCount = vm.muc.count { vm.chon[it.pkg] == true }
    val filtered = remember(search, vm.muc) {
        val q = search.trim().lowercase()
        if (q.isEmpty()) vm.muc
        else vm.muc.filter {
            it.pkg.lowercase().contains(q) ||
                tenDep(it.pkg).lowercase().contains(q) ||
                it.ghiChu.lowercase().contains(q)
        }
    }
    val grouped = remember(filtered) { filtered.groupBy { it.loai } }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("$selectedCount", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                        Text(
                            t("gói sẽ tắt / ${vm.muc.size}", "packages to disable / ${vm.muc.size}"),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                    LinearProgressIndicator(
                        progress = { if (vm.muc.isEmpty()) 0f else selectedCount.toFloat() / vm.muc.size },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        t(
                            "Đã tích sẵn gói an toàn. App có biểu tượng thì hỏi chủ nhà. Gói tivi cần (HDMI, remote, bàn phím, CH Play) app tự giữ, không hiện ở đây.",
                            "Safe packages are pre-ticked. For apps with an icon, ask the household. Packages the TV needs (HDMI, remote, keyboard, Play Store) are kept automatically and not listed here.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { vm.muc.forEach { vm.chon[it.pkg] = it.chonSan } }) {
                            Text(t("Mặc định", "Defaults"))
                        }
                        TextButton(onClick = { vm.muc.forEach { vm.chon[it.pkg] = false } }) {
                            Text(t("Bỏ chọn hết", "Clear all"))
                        }
                    }
                }
            }
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                placeholder = { Text(t("Tìm tên app hoặc tên gói…", "Search app or package name…")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LoaiMuc.entries.forEach { loai ->
                val list = grouped[loai] ?: return@forEach
                item(key = "hdr_${loai.name}") {
                    val tatCa = list.all { vm.chon[it.pkg] == true }
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(loai.tieuDe, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(loai.moTa, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { list.forEach { vm.chon[it.pkg] = !tatCa } }) {
                            Text(if (tatCa) t("Bỏ hết", "Clear") else t("Chọn hết", "Select all"), fontWeight = FontWeight.Bold)
                        }
                    }
                }
                items(list, key = { it.pkg }) { m ->
                    PackageRow(
                        m = m,
                        checked = vm.chon[m.pkg] == true,
                        onToggle = { vm.chon[m.pkg] = it },
                    )
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }

        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val risky = vm.muc.filter { vm.chon[it.pkg] == true && KeHoach.nenGiu(it) }
                        if (risky.isNotEmpty()) showKeepWarning = true
                        else if (selectedCount > 0) vm.batDauTat() else vm.boQuaDon()
                    },
                    enabled = !vm.dangBan,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.CleaningServices, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (selectedCount > 0) t("Sao lưu & dọn $selectedCount gói", "Back up & clean $selectedCount packages")
                        else t("Không tắt gói nào, sang Home sạch", "Skip disabling, go to the clean Home")
                    )
                }
                if (selectedCount > 0) {
                    OutlinedButton(onClick = { vm.boQuaDon() }, enabled = !vm.dangBan, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Home, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(t("Bỏ qua, chỉ đổi giao diện", "Skip, only change the look"))
                    }
                }
                CommandPreviewButton { vm.lenhDon() }
            }
        }
    }

    if (showKeepWarning) {
        val risky = vm.muc.filter { vm.chon[it.pkg] == true && KeHoach.nenGiu(it) }
        AlertDialog(
            onDismissRequest = { showKeepWarning = false },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(t("Cẩn thận gói hay được giữ lại", "Warning: commonly kept packages")) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        t(
                            "Các gói sau được nhiều người dùng giữ lại để tránh mất tính năng của tivi. Bạn vẫn muốn tắt chứ?",
                            "These packages are usually kept by TV owners. Are you sure you want to disable them?"
                        )
                    )
                    risky.forEach { m ->
                        Text("• ${tenDep(m.pkg)} (${m.pkg}) — ${t("giữ", "kept")} ${m.soMauGiu}, ${t("tắt", "off")} ${m.soMauTat}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showKeepWarning = false; vm.batDauTat() }) {
                    Text(t("Vẫn tắt", "Disable anyway"))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    risky.forEach { vm.chon[it.pkg] = false }
                    showKeepWarning = false
                }) {
                    Text(t("Bỏ tích các gói này", "Uncheck these"))
                }
            },
        )
    }
}

@Composable
private fun PackageRow(m: MucGoi, checked: Boolean, onToggle: (Boolean) -> Unit) {
    val keepWarn = KeHoach.nenGiu(m)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (checked) 0.85f else 0.45f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!checked) },
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                Text(tenDep(m.pkg).take(1).uppercase(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(tenDep(m.pkg), fontWeight = FontWeight.SemiBold)
                Text(m.pkg, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (m.ghiChu.isNotBlank()) {
                    Text(m.ghiChu, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                }
                if (m.soMauTat > 0 || m.soMauGiu > 0) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (m.soMauTat > 0) NhanChip(t("tắt trên ${m.soMauTat} mẫu", "off on ${m.soMauTat} models"), canhBao = false)
                        if (m.soMauGiu > 0) {
                            NhanChip((if (keepWarn) "⚠ " else "") + t("${m.soMauGiu} mẫu nên giữ", "${m.soMauGiu} models keep it"), canhBao = keepWarn)
                        }
                    }
                }
            }
            Checkbox(checked = checked, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun NhanChip(chu: String, canhBao: Boolean) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (canhBao) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surface,
    ) {
        Text(
            chu,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (canhBao) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun ExecutingScreen(vm: PhienLamViec) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TxaCard(
            title = if (vm.dangBan) t("Đang thực hiện trên tivi…", "Working on TV…") else t("Đã dừng", "Stopped"),
            icon = Icons.Default.CheckCircle,
        ) {
            val scroll = rememberScrollState()
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .height(340.dp)
                    .verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                vm.nhatKy.forEach { line ->
                    Text(line, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
            }
        }
        if (!vm.dangBan) {
            Button(onClick = { vm.man = Man.CHON_GOI }, modifier = Modifier.fillMaxWidth()) {
                Text(t("Quay lại chọn gói", "Back to package list"))
            }
        }
    }
}

