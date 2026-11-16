package com.txa.cleantxa.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.txa.cleantxa.NgonNgu
import com.txa.cleantxa.dulieu.tenDep
import com.txa.cleantxa.t
import java.util.Locale

/** Mã phím Android dùng trên remote (KeyEvent.KEYCODE_*). */
private object Phim {
    const val HOME = 3
    const val BACK = 4
    const val UP = 19
    const val DOWN = 20
    const val LEFT = 21
    const val RIGHT = 22
    const val OK = 23
    const val VOL_UP = 24
    const val VOL_DOWN = 25
    const val POWER = 26
    const val MENU = 82
    const val DEL = 67
    const val PLAY_PAUSE = 85
    const val MUTE = 164
}

/** Nút tròn của remote. */
@Composable
private fun NutTron(icon: ImageVector, mota: String, onClick: () -> Unit, nguy: Boolean = false) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(56.dp),
        colors = if (nguy) IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ) else IconButtonDefaults.filledTonalIconButtonColors(),
    ) { Icon(icon, contentDescription = mota) }
}

@Composable
fun RemoteScreen(vm: PhienLamViec) {
    val ctx = LocalContext.current
    val apkPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) vm.caiApkLenTv(uri)
    }
    // Nói thay vì gõ: dùng bộ nhận dạng giọng nói của điện thoại, kết quả điền vào ô chữ.
    val noi = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val chu = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (r.resultCode == Activity.RESULT_OK && !chu.isNullOrBlank()) vm.chuGo = chu
    }
    fun batMic() {
        val y = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (NgonNgu.tiengViet) "vi-VN" else Locale.getDefault().toLanguageTag())
        runCatching { noi.launch(y) }.onFailure {
            Toast.makeText(ctx, t("Điện thoại chưa có nhận dạng giọng nói.", "This phone has no speech recognition."), Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        vm.thongTin?.tenHienThi?.takeIf { it.isNotBlank() }?.let { ten ->
            Text(
                ten,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End,
                maxLines = 1,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (vm.remoteBao.isNotBlank()) {
            Text(vm.remoteBao, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        }

        // ---- Remote: hàng trên, vòng điều hướng, hàng dưới ----
        TxaCard(highlighted = true) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    NutTron(Icons.Default.PowerSettingsNew, t("Nguồn", "Power"), { vm.bamPhim(Phim.POWER) }, nguy = true)
                    NutTron(Icons.Default.Home, "Home", { vm.bamPhim(Phim.HOME) })
                    NutTron(Icons.AutoMirrored.Filled.ArrowBack, t("Quay lại", "Back"), { vm.bamPhim(Phim.BACK) })
                    NutTron(Icons.Default.Menu, "Menu", { vm.bamPhim(Phim.MENU) })
                }

                Box(
                    Modifier
                        .size(252.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(onClick = { vm.bamPhim(Phim.UP) }, modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp).size(60.dp)) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = t("Lên", "Up"), modifier = Modifier.size(36.dp))
                    }
                    IconButton(onClick = { vm.bamPhim(Phim.DOWN) }, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp).size(60.dp)) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = t("Xuống", "Down"), modifier = Modifier.size(36.dp))
                    }
                    IconButton(onClick = { vm.bamPhim(Phim.LEFT) }, modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp).size(60.dp)) {
                        Icon(Icons.Default.KeyboardArrowLeft, contentDescription = t("Trái", "Left"), modifier = Modifier.size(36.dp))
                    }
                    IconButton(onClick = { vm.bamPhim(Phim.RIGHT) }, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp).size(60.dp)) {
                        Icon(Icons.Default.KeyboardArrowRight, contentDescription = t("Phải", "Right"), modifier = Modifier.size(36.dp))
                    }
                    Button(
                        onClick = { vm.bamPhim(Phim.OK) },
                        shape = CircleShape,
                        modifier = Modifier.size(96.dp),
                        contentPadding = PaddingValues(0.dp),
                    ) { Text("OK", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge) }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    NutTron(Icons.AutoMirrored.Filled.VolumeDown, t("Giảm âm lượng", "Volume down"), { vm.bamPhim(Phim.VOL_DOWN) })
                    NutTron(Icons.AutoMirrored.Filled.VolumeOff, t("Tắt tiếng", "Mute"), { vm.bamPhim(Phim.MUTE) })
                    NutTron(Icons.AutoMirrored.Filled.VolumeUp, t("Tăng âm lượng", "Volume up"), { vm.bamPhim(Phim.VOL_UP) })
                    NutTron(Icons.Default.PlayArrow, t("Phát / Dừng", "Play / Pause"), { vm.bamPhim(Phim.PLAY_PAUSE) })
                }
            }
        }

        // ---- Gõ chữ lên tivi ----
        TxaCard(
            title = t("Gõ chữ lên tivi", "Type on the TV"),
            subtitle = t(
                "Chọn ô chữ trên tivi trước (ô tìm kiếm của YouTube, mật khẩu Wi-Fi…), rồi gõ hoặc nói ở đây.",
                "Pick a text field on the TV first (YouTube search, Wi-Fi password…), then type or speak here.",
            ),
            icon = Icons.Default.Keyboard,
        ) {
            OutlinedTextField(
                value = vm.chuGo,
                onValueChange = { vm.chuGo = it },
                placeholder = { Text(t("Chữ cần gõ", "Text to type")) },
                trailingIcon = {
                    IconButton(onClick = { batMic() }) {
                        Icon(Icons.Default.Mic, contentDescription = t("Nói để nhập", "Speak to type"), tint = MaterialTheme.colorScheme.primary)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { vm.guiChu() }, enabled = vm.chuGo.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(t("Gửi lên tivi", "Send to TV"))
            }
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = { vm.guiChu(xuongDong = true) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Filled.KeyboardReturn, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(t("Gửi rồi bấm Enter (tìm luôn)", "Send, then press Enter (search now)"))
            }
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = { vm.bamPhim(Phim.DEL) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Filled.Backspace, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(t("Xoá lùi một chữ trên tivi", "Delete one character on the TV"))
            }
            Spacer(Modifier.height(4.dp))
            Text(
                if (vm.goCoDau) t("Đang hỗ trợ gõ tiếng Việt có dấu.", "Vietnamese accents are supported.")
                else t("Hiện chỉ gõ được không dấu.", "Only plain letters can be typed right now."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!vm.goCoDau) {
                TextButton(onClick = { vm.batGoCoDau() }) {
                    Text(t("Bật hỗ trợ gõ có dấu", "Enable Vietnamese accent input"))
                }
            }
        }

        // ---- Hẹn giờ tắt ----
        TxaCard(
            title = t("Hẹn giờ tắt tivi", "Sleep timer"),
            subtitle = t("Tivi tự đếm giờ, đóng app điện thoại vẫn tắt đúng hẹn.", "The TV counts down on its own, so it still switches off if you close this app."),
            icon = Icons.Default.Bedtime,
        ) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 30, 45, 60, 90, 120).forEach { phut ->
                    AssistChip(onClick = { vm.henTat(phut) }, label = { Text(t("$phut phút", "$phut min")) })
                }
                if (vm.henTatLuc != null) {
                    AssistChip(onClick = { vm.henTat(0) }, label = { Text(t("Huỷ hẹn", "Cancel timer")) })
                }
            }
        }

        // ---- App trên tivi ----
        val dsApp = vm.appTrenTv.ifEmpty { vm.appTuCai.sortedBy { tenDep(it).lowercase() } }
        TxaCard(title = t("App trên tivi", "Apps on the TV"), icon = Icons.Default.Apps) {
            OutlinedButton(
                onClick = { apkPicker.launch(arrayOf("application/vnd.android.package-archive", "*/*")) },
                enabled = !vm.dangBan,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.InstallMobile, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(t("Cài file APK từ điện thoại", "Install an APK from this phone"))
            }
            if (dsApp.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                dsApp.forEach { pkg ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(tenDep(pkg), style = MaterialTheme.typography.bodyLarge)
                            Text(pkg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        TextButton(onClick = { vm.moAppTv(pkg) }) { Text(t("Mở", "Open"), fontWeight = FontWeight.Bold) }
                        if (pkg in vm.appTuCai) {
                            TextButton(onClick = { vm.hoiGo = pkg }) {
                                Text(t("Gỡ", "Remove"), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    t(
                        "Chỉ gỡ được app bạn tự cài; app hệ thống thì dùng phần Dọn của CleanTxa (tắt được, bật lại được).",
                        "Only apps you installed can be removed; for system apps use CleanTxa's clean-up (they can be turned off and back on).",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    vm.hoiGo?.let { pkg ->
        AlertDialog(
            onDismissRequest = { vm.hoiGo = null },
            title = { Text(tenDep(pkg)) },
            text = { Text(t("Gỡ cài đặt $pkg khỏi tivi?", "Uninstall $pkg from the TV?")) },
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
