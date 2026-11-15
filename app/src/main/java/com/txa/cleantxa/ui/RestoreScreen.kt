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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AppSettingsAlt
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import com.txa.cleantxa.dulieu.tenDep
import com.txa.cleantxa.t

@Composable
fun RestoreScreen(vm: PhienLamViec) {
    val sl = vm.saoLuu
    var goLauncher by remember { mutableStateOf(true) }
    var batTatCa by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (sl != null) {
            TxaCard(
                title = t("Khôi phục từ bản sao lưu", "Restore from backup"),
                subtitle = t(
                    "${sl.ten} · Đã tắt ${sl.daTatBoiApp.size} gói bởi CleanTxa",
                    "${sl.ten} · ${sl.daTatBoiApp.size} packages disabled by CleanTxa"
                ),
                icon = Icons.Default.Restore,
                highlighted = true,
            ) {
                ToggleRow(
                    title = t("Gỡ Home sạch & trả Home gốc", "Uninstall Clean Home & restore stock Home"),
                    checked = goLauncher,
                    onCheckedChange = { goLauncher = it },
                )
                ToggleRow(
                    title = t("Bật lại toàn bộ gói đang tắt trên tivi", "Re-enable ALL disabled packages on TV"),
                    subtitle = t("Dùng nếu bạn đã đổi điện thoại hoặc thiếu gói trong sao lưu.", "Useful if packages were disabled outside this backup."),
                    checked = batTatCa,
                    onCheckedChange = { batTatCa = it },
                )
                Spacer(Modifier.height(10.dp))
                CommandPreviewButton { com.txa.cleantxa.dulieu.KichBanKhoiPhuc.tao(sl, com.txa.cleantxa.NgonNgu.tiengViet).lines() }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { vm.chayKhoiPhuc(goLauncher, batTatCa) },
                    enabled = !vm.dangBan,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Restore, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(t("Khôi phục ngay", "Restore now"))
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { vm.xuatKichBanTv() },
                    enabled = !vm.dangBan,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Save, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(t("Lưu kịch bản khôi phục (.bat / .sh) vào Download", "Save restore script (.bat / .sh) to Download"))
                }
            }
        } else {
            TxaCard(
                title = t("Khôi phục không cần bản sao lưu", "Restore without a backup"),
                subtitle = t("Đọc trực tiếp danh sách gói đang tắt từ tivi.", "Reads disabled packages directly from the TV."),
                icon = Icons.Default.Restore,
            ) {
                ToggleRow(
                    title = t("Gỡ Home sạch & trả Home gốc", "Uninstall Clean Home & restore stock Home"),
                    checked = goLauncher,
                    onCheckedChange = { goLauncher = it },
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { vm.docGoiTatKhongSaoLuu() },
                        enabled = !vm.dangBan,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(t("Đọc gói đang tắt", "Scan disabled"))
                    }
                    Button(
                        onClick = { vm.chayKhoiPhucKhongSaoLuu(goLauncher) },
                        enabled = !vm.dangBan,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(t("Khôi phục hết", "Restore all"))
                    }
                }
                vm.goiTatKhongSaoLuu?.let { list ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        t("Tìm thấy ${list.size} gói đang tắt.", "Found ${list.size} disabled packages."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        TxaCard(
            title = t("Bật lại từng app", "Re-enable individual apps"),
            subtitle = t("Đọc thẳng từ tivi — bật lại từng gói nếu lỡ tắt nhầm.", "Reads straight from TV — re-enable specific packages."),
            icon = Icons.Default.AppSettingsAlt,
        ) {
            OutlinedButton(
                onClick = { vm.docDsTat() },
                enabled = !vm.dangBan,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(t("Đọc danh sách app đang tắt", "Load disabled apps from TV"))
            }
            if (vm.batLaiBao.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(vm.batLaiBao, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }
            vm.dsTatTv?.let { list ->
                Spacer(Modifier.height(10.dp))
                if (list.isEmpty()) {
                    Text(t("Không có gói nào đang bị tắt.", "No disabled packages found."), style = MaterialTheme.typography.bodySmall)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        list.forEach { pkg ->
                            val checked = vm.chonBatLai[pkg] == true
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { vm.chonBatLai[pkg] = !checked }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = checked, onCheckedChange = { vm.chonBatLai[pkg] = it })
                                Column(Modifier.weight(1f)) {
                                    Text(tenDep(pkg), fontWeight = FontWeight.Medium)
                                    Text(pkg, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Button(
                            onClick = { vm.batLaiDaChon() },
                            enabled = !vm.dangBan && vm.chonBatLai.values.any { it },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(t("Bật lại các app đã chọn", "Re-enable selected apps"))
                        }
                    }
                }
            }
        }
    }
}

