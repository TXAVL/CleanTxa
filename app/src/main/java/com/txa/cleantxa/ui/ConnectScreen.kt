package com.txa.cleantxa.ui

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
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.txa.cleantxa.ManHinh
import com.txa.cleantxa.t

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConnectScreen(vm: PhienLamViec) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (ManHinh.laTv) {
            TxaCard(
                title = t("Kết nối ngay trên tivi này", "Connect on this TV"),
                subtitle = t("CleanTxa sẽ kết nối vào chính tivi đang chạy app (127.0.0.1:5555).", "CleanTxa will connect to this TV locally (127.0.0.1:5555)."),
                icon = Icons.Default.Tv,
                highlighted = true,
            ) {
                Button(
                    onClick = { vm.ketNoiTiviNay() },
                    enabled = !vm.dangBan,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(t("Kết nối tivi này", "Connect this TV"))
                }
            }
        }

        TxaCard(
            title = t("Quét tìm tivi trên Wi-Fi", "Scan Wi-Fi for TV"),
            subtitle = t("Điện thoại và tivi phải dùng chung một mạng Wi-Fi.", "Phone and TV must be on the same Wi-Fi network."),
            icon = Icons.Default.Wifi,
        ) {
            Button(
                onClick = { vm.quetMang() },
                enabled = !vm.dangQuet && !vm.dangBan,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (vm.dangQuet) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text(t("Đang quét mạng…", "Scanning network…"))
                } else {
                    Icon(Icons.Default.Radar, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(t("Quét mạng LAN", "Scan local network"))
                }
            }
            if (vm.timThay.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(t("Tìm thấy:", "Found:"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    vm.timThay.forEach { addr ->
                        AssistChip(
                            onClick = {
                                val parts = addr.split(':')
                                vm.ip = parts[0]
                                vm.cong = parts.getOrElse(1) { "5555" }
                                vm.ketNoi()
                            },
                            label = { Text(addr) },
                        )
                    }
                }
            }
        }

        TxaCard(
            title = t("Nhập IP thủ công", "Enter IP manually"),
            subtitle = t("Mặc định cổng ADB là 5555. Nếu dùng Gỡ lỗi không dây, nhập cổng hiện trên tivi.", "Default ADB port is 5555. For Wireless debugging, use the port shown on the TV."),
            icon = Icons.Default.Tv,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = vm.ip,
                    onValueChange = { vm.ip = it },
                    label = { Text(t("Địa chỉ IP", "IP address")) },
                    placeholder = { Text("192.168.1.50") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.weight(2f),
                )
                OutlinedTextField(
                    value = vm.cong,
                    onValueChange = { vm.cong = it },
                    label = { Text(t("Cổng", "Port")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { vm.ketNoi() },
                enabled = vm.ip.isNotBlank() && !vm.dangBan,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(t("Kết nối", "Connect"))
            }
        }

        ExpandableSection(
            title = t("Ghép đôi bằng mã 6 số (Google TV mới)", "Pair with 6-digit code (Newer Google TV)"),
            icon = Icons.Default.Key,
        ) {
            Text(
                t(
                    "Trên tivi: Cài đặt → Hệ thống → Tùy chọn nhà phát triển → Gỡ lỗi qua Wi-Fi → Ghép nối thiết bị bằng mã ghép nối.",
                    "On TV: Settings → System → Developer options → Wireless debugging → Pair device with pairing code."
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = vm.ghepIpCong,
                onValueChange = { vm.ghepIpCong = it },
                label = { Text(t("IP:cổng ghép đôi", "Pairing IP:port")) },
                placeholder = { Text("192.168.1.50:37123") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = vm.ghepMa,
                onValueChange = { vm.ghepMa = it },
                label = { Text(t("Mã ghép đôi 6 số", "6-digit pairing code")) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { vm.ghepDoi() },
                enabled = !vm.dangBan && vm.ghepIpCong.isNotBlank() && vm.ghepMa.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(t("Ghép đôi & Kết nối", "Pair & Connect"))
            }
        }

        TextButton(onClick = { vm.moHuongDan() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Icon(Icons.AutoMirrored.Filled.HelpOutline, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(t("Chưa bật được Gỡ lỗi? Xem hướng dẫn 7 bước", "Need help? Open 7-step guide"))
        }
    }
}
