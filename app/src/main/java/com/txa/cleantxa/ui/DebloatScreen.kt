package com.txa.cleantxa.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
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
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                placeholder = { Text(t("Tìm tên app hoặc tên gói…", "Search app or package name…")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    t("Đã chọn $selectedCount / ${vm.muc.size} gói", "Selected $selectedCount / ${vm.muc.size}"),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
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

        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LoaiMuc.entries.forEach { loai ->
                val list = grouped[loai] ?: return@forEach
                item(key = "hdr_${loai.name}") {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(loai.tieuDe, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text(loai.moTa, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                CommandPreviewButton { vm.lenhDon() }
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
                        if (selectedCount > 0) t("Tắt $selectedCount gói đã chọn", "Disable $selectedCount selected")
                        else t("Không tắt gói nào — Sang Home sạch", "Skip disabling — Go to Clean Home")
                    )
                }
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
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (checked) 0.85f else 0.45f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!checked) },
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Checkbox(checked = checked, onCheckedChange = onToggle)
            Column(Modifier.weight(1f)) {
                Text(tenDep(m.pkg), fontWeight = FontWeight.SemiBold)
                Text(m.pkg, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (m.ghiChu.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(m.ghiChu, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                }
                if (m.soMauTat > 0 || m.soMauGiu > 0) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        t("${m.soMauTat} mẫu tắt · ${m.soMauGiu} mẫu giữ", "${m.soMauTat} disabled · ${m.soMauGiu} kept") +
                            (if (keepWarn) t(" (Nên giữ)", " (Recommended to keep)") else ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (keepWarn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
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

