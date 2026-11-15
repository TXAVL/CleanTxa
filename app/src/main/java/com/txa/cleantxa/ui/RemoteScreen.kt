package com.txa.cleantxa.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.txa.cleantxa.dulieu.tenDep
import com.txa.cleantxa.t

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RemoteScreen(vm: PhienLamViec) {
    val apkPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) vm.caiApkLenTv(uri)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (vm.remoteBao.isNotBlank()) {
            Text(vm.remoteBao, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        }

        TxaCard(highlighted = true) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    FilledTonalIconButton(onClick = { vm.bamPhim(26) }) {
                        Icon(Icons.Default.PowerSettingsNew, contentDescription = t("Nguồn", "Power"), tint = MaterialTheme.colorScheme.error)
                    }
                    FilledTonalIconButton(onClick = { vm.bamPhim(164) }) {
                        Icon(Icons.AutoMirrored.Filled.VolumeOff, contentDescription = t("Tắt tiếng", "Mute"))
                    }
                }

                FilledTonalIconButton(onClick = { vm.bamPhim(19) }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Up", modifier = Modifier.size(32.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(onClick = { vm.bamPhim(21) }, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Left", modifier = Modifier.size(32.dp))
                    }
                    Button(onClick = { vm.bamPhim(23) }, shape = CircleShape, modifier = Modifier.size(68.dp)) {
                        Text("OK", fontWeight = FontWeight.Bold)
                    }
                    FilledTonalIconButton(onClick = { vm.bamPhim(22) }, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Right", modifier = Modifier.size(32.dp))
                    }
                }
                FilledTonalIconButton(onClick = { vm.bamPhim(20) }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Down", modifier = Modifier.size(32.dp))
                }

                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    FilledTonalButton(onClick = { vm.bamPhim(4) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(t("Quay lại", "Back"))
                    }
                    FilledTonalButton(onClick = { vm.bamPhim(3) }) {
                        Icon(Icons.Default.Home, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Home")
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    OutlinedButton(onClick = { vm.bamPhim(25) }) {
                        Icon(Icons.AutoMirrored.Filled.VolumeDown, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(t("Giảm", "Vol -"))
                    }
                    OutlinedButton(onClick = { vm.bamPhim(24) }) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(t("Tăng", "Vol +"))
                    }
                }
            }
        }

        TxaCard(
            title = t("Nhập chữ lên tivi", "Type text to TV"),
            subtitle = if (vm.goCoDau) t("Hỗ trợ gõ tiếng Việt có dấu.", "Vietnamese accents enabled.") else t("Gõ không dấu (bật Dịch vụ phím Home để gõ có dấu).", "ASCII input (enable Home key service for Vietnamese accents)."),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = vm.chuGo,
                    onValueChange = { vm.chuGo = it },
                    placeholder = { Text(t("Nhập nội dung cần tìm…", "Type text…")) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = { vm.guiChu() }, enabled = vm.chuGo.isNotBlank()) {
                    Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(18.dp))
                }
            }
            if (!vm.goCoDau) {
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = { vm.batGoCoDau() }) {
                    Text(t("Bật hỗ trợ gõ có dấu", "Enable Vietnamese accent input"))
                }
            }
        }

        TxaCard(
            title = t("Hẹn giờ tắt tivi", "Sleep timer"),
            subtitle = t("Tivi tự đếm và tắt đúng giờ ngay cả khi đóng app.", "TV counts down even after you close this app."),
            icon = Icons.Default.Timer,
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(15, 30, 60, 90, 120).forEach { mins ->
                    AssistChip(onClick = { vm.henTat(mins) }, label = { Text("${mins}m") })
                }
                if (vm.henTatLuc != null) {
                    AssistChip(onClick = { vm.henTat(0) }, label = { Text(t("Huỷ hẹn", "Cancel")) })
                }
            }
        }

        TxaCard(
            title = t("Ứng dụng & Cài APK", "Apps & Sideload APK"),
            icon = Icons.Default.Apps,
        ) {
            OutlinedButton(
                onClick = { apkPicker.launch(arrayOf("application/vnd.android.package-archive", "*/*")) },
                enabled = !vm.dangBan,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.InstallMobile, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(t("Cài file .APK từ điện thoại sang tivi", "Install .APK from phone to TV"))
            }
            if (vm.appTuCai.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    vm.appTuCai.sortedBy { tenDep(it).lowercase() }.forEach { pkg ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(tenDep(pkg), fontWeight = FontWeight.Medium)
                                Text(pkg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { vm.moAppTv(pkg) }) {
                                Icon(Icons.Default.OpenInNew, contentDescription = t("Mở", "Open"))
                            }
                            IconButton(onClick = { vm.hoiGo = pkg }) {
                                Icon(Icons.Default.Delete, contentDescription = t("Gỡ", "Uninstall"), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    vm.hoiGo?.let { pkg ->
        AlertDialog(
            onDismissRequest = { vm.hoiGo = null },
            title = { Text(tenDep(pkg)) },
            text = { Text(t("Gỡ cài đặt $pkg khỏi tivi?", "Uninstall $pkg from TV?")) },
            confirmButton = {
                Button(onClick = { vm.hoiGo = null; vm.goAppTv(pkg) }) {
                    Text(t("Gỡ cài đặt", "Uninstall"))
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.hoiGo = null }) { Text(t("Huỷ", "Cancel")) }
            },
        )
    }
}

