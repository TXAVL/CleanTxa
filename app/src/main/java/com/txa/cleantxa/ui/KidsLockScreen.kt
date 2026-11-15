package com.txa.cleantxa.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.txa.cleantxa.dulieu.tenDep
import com.txa.cleantxa.t

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KidsLockScreen(vm: PhienLamViec) {
    val dayNames = listOf(
        1 to t("T2", "Mon"),
        2 to t("T3", "Tue"),
        3 to t("T4", "Wed"),
        4 to t("T5", "Thu"),
        5 to t("T6", "Fri"),
        6 to t("T7", "Sat"),
        7 to t("CN", "Sun"),
    )

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TxaCard(
            title = t("Khoá trẻ em", "Kids lock"),
            subtitle = t("Hẹn giờ khoá tivi theo khung giờ trong tuần, mở tạm 60 phút bằng mã PIN 4 số.", "Schedule TV lock windows by weekday, unlock for 60 minutes with a 4-digit PIN."),
            icon = Icons.Default.ChildCare,
            highlighted = true,
        ) {
            ToggleRow(
                title = t("Bật Khoá trẻ em", "Enable Kids lock"),
                checked = vm.khoaBat,
                onCheckedChange = { vm.khoaBat = it },
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = vm.khoaPin,
                onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) vm.khoaPin = it },
                label = { Text(t("Mã PIN 4 số", "4-digit PIN")) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            if (vm.khoaTrenTv != null) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { vm.moTamKhoaTre(60) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.LockOpen, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(t("Mở 60 phút", "Unlock 60m"))
                    }
                    OutlinedButton(onClick = { vm.tatKhoaTre() }, modifier = Modifier.weight(1f)) {
                        Text(t("Tắt khoá", "Turn off"))
                    }
                }
            }
        }

        TxaCard(title = t("Khung giờ khoá", "Lock schedules")) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                vm.khoaKhung.forEachIndexed { idx, k ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = k.tu,
                                onValueChange = { vm.khoaKhung[idx] = k.copy(tu = it) },
                                label = { Text(t("Từ", "From")) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = k.den,
                                onValueChange = { vm.khoaKhung[idx] = k.copy(den = it) },
                                label = { Text(t("Đến", "To")) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { vm.khoaKhung.removeAt(idx) }) {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            dayNames.forEach { (d, label) ->
                                val selected = d in k.ngay
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        val next = if (selected) k.ngay - d else k.ngay + d
                                        vm.khoaKhung[idx] = k.copy(ngay = next)
                                    },
                                    label = { Text(label) },
                                )
                            }
                        }
                    }
                }
                OutlinedButton(
                    onClick = { vm.khoaKhung.add(PhienLamViec.KhungKhoa("21:00", "06:30", setOf(1, 2, 3, 4, 5, 6, 7))) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(t("Thêm khung giờ", "Add schedule"))
                }
            }
        }

        if (vm.khoaAppCo.isNotEmpty()) {
            ExpandableSection(
                title = t("Khoá riêng từng app (bỏ trống = khoá cả tivi)", "Lock specific apps (empty = lock entire TV)"),
                icon = Icons.Default.ChildCare,
            ) {
                vm.khoaAppCo.forEach { pkg ->
                    val checked = pkg in vm.khoaApps
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (checked) vm.khoaApps.remove(pkg) else vm.khoaApps.add(pkg)
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = { on ->
                            if (on && !checked) vm.khoaApps.add(pkg) else if (!on) vm.khoaApps.remove(pkg)
                        })
                        Text(tenDep(pkg), fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        Button(
            onClick = { vm.luuKhoaTre() },
            enabled = !vm.dangBan && (vm.khoaPin.length == 4 || vm.khoaTrenTv != null),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(t("Lưu & Áp dụng lên tivi", "Save & Apply to TV"))
        }
    }
}

