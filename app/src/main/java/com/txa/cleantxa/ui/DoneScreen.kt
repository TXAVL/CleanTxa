package com.txa.cleantxa.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.txa.cleantxa.AnhTivi
import com.txa.cleantxa.t

@Composable
fun DoneScreen(vm: PhienLamViec) {
    val ctx = LocalContext.current
    var savedMsg by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TxaCard(
            title = t("Đã hoàn tất!", "All done!"),
            subtitle = vm.ketQua?.let { kq ->
                t(
                    "Đã tắt ${kq.daTat.size} gói, giữ lại ${kq.boQua.size} gói.",
                    "Disabled ${kq.daTat.size} packages, skipped ${kq.boQua.size}."
                )
            } ?: t("Tivi của bạn đã được thiết lập xong.", "Your TV setup is complete."),
            icon = Icons.Default.CheckCircle,
            highlighted = true,
        ) {
            if (vm.phimHomeOk == true) {
                Text(t("Nút Home đã chuyển về Home sạch.", "Home button now opens Clean Home."), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
            }
            if (vm.daTuTatAdb) {
                Text(
                    t("ADB đã tự tắt để bảo mật.", "ADB was automatically turned off for security."),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        TxaCard(
            title = t("Kiểm tra & Tiện ích", "Verify & Utilities"),
            icon = Icons.Default.Screenshot,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { vm.chupManHinh() }, enabled = !vm.dangBan, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Screenshot, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(t("Xem màn tivi", "TV screen"))
                    }
                    OutlinedButton(onClick = { vm.khoiDongLaiTv() }, enabled = !vm.dangBan, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.RestartAlt, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(t("Khởi động lại", "Reboot TV"))
                    }
                }
                if (vm.daCoHomeSach) {
                    OutlinedButton(onClick = { vm.man = Man.HOME_SACH }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Home, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(t("Chỉnh lại Home sạch", "Customize Clean Home"))
                    }
                }
                OutlinedButton(onClick = { vm.xuatBaoCaoChanDoan() }, enabled = !vm.dangBan, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Share, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(t("Xuất báo cáo chẩn đoán", "Export diagnostic report"))
                }
            }

            vm.anhTv?.let { bmp ->
                Spacer(Modifier.height(12.dp))
                Image(
                    bmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(10.dp)),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (AnhTivi.luuDuoc) {
                        OutlinedButton(onClick = {
                            savedMsg = if (AnhTivi.luu(ctx, bmp)) t("Đã lưu vào Pictures/CleanTxa.", "Saved to Pictures/CleanTxa.")
                            else t("Không lưu được ảnh.", "Failed to save image.")
                        }) {
                            Icon(Icons.Default.Save, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(t("Lưu ảnh", "Save"))
                        }
                    }
                    OutlinedButton(onClick = { AnhTivi.chiaSe(ctx, bmp) }) {
                        Icon(Icons.Default.Share, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(t("Chia sẻ", "Share"))
                    }
                }
                if (savedMsg.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(savedMsg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        TxaCard(
            title = t("Đóng kết nối an toàn", "Close connection safely"),
            subtitle = t("Khuyên tắt gỡ lỗi USB/Wi-Fi khi đã dọn xong để tivi an toàn hơn.", "Turning off ADB when finished keeps your TV secure."),
            icon = Icons.Default.Shield,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { vm.tatAdbVaKetThuc() },
                    enabled = !vm.dangBan,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.PowerSettingsNew, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(t("Tắt ADB & Về màn chính", "Turn off ADB & Finish"))
                }
                OutlinedButton(
                    onClick = { vm.chonGiuAdb { vm.veManChinh() } },
                    enabled = !vm.dangBan,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(t("Giữ ADB bật để lần sau chỉnh tiếp", "Keep ADB on for later"))
                }
            }
        }
    }
}

