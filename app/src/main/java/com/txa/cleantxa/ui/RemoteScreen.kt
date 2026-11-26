package com.txa.cleantxa.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsInputHdmi
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.txa.cleantxa.NgonNgu
import com.txa.cleantxa.dulieu.tenDep
import com.txa.cleantxa.t
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

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
    const val SEARCH = 84
    const val DEL = 67
    const val PLAY_PAUSE = 85
    const val MUTE = 164
    const val SETTINGS = 176
    const val TV_INPUT = 178
    const val APP_SWITCH = 187
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RemoteScreen(vm: PhienLamViec) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var cheDoBanRe by remember { mutableStateOf(false) }

    val apkPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) vm.caiApkLenTv(uri)
    }
    val photoBeamPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) vm.chieuAnhLenTv(uri)
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

    fun docClipboardDienThoai(): String {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return ""
        return cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty()
    }

    fun chepVaoClipboardDienThoai(txt: String) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        cm.setPrimaryClip(ClipData.newPlainText("CleanTxa", txt))
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (vm.dangLienKetNgam) {
                val activeAppLabel = vm.appDangMoTrenTv
                    .takeIf { it.isNotBlank() && it != "android" }
                    ?.let { " · ${tenDep(it)}" }
                    .orEmpty()
                Text(
                    t("🔗 Liên kết ngầm$activeAppLabel", "🔗 Wi-Fi Link$activeAppLabel"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            } else {
                Spacer(Modifier.width(4.dp))
            }
            vm.thongTin?.tenHienThi?.takeIf { it.isNotBlank() }?.let { ten ->
                Text(
                    ten,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (vm.remoteBao.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    vm.remoteBao,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }

        // ---- Remote: hàng trên, vòng điều hướng / bàn rê cử chỉ, hàng dưới ----
        TxaCard(highlighted = true) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    NutTron(Icons.Default.PowerSettingsNew, t("Nguồn", "Power"), { vm.bamPhim(Phim.POWER) }, nguy = true)
                    NutTron(Icons.Default.Home, "Home", { vm.bamPhim(Phim.HOME) })
                    NutTron(Icons.AutoMirrored.Filled.ArrowBack, t("Quay lại", "Back"), { vm.bamPhim(Phim.BACK) })
                    NutTron(Icons.Default.Menu, "Menu", { vm.bamPhim(Phim.MENU) })
                }

                // Nút chuyển giữa Cụm D-Pad cổ điển và Bàn rê cảm ứng thông minh (Touchpad)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !cheDoBanRe,
                        onClick = { cheDoBanRe = false },
                        leadingIcon = { Icon(Icons.Default.TouchApp, null, Modifier.size(16.dp)) },
                        label = { Text(t("Phím D-Pad", "D-Pad")) },
                    )
                    FilterChip(
                        selected = cheDoBanRe,
                        onClick = { cheDoBanRe = true },
                        leadingIcon = { Icon(Icons.Default.Swipe, null, Modifier.size(16.dp)) },
                        label = { Text(t("Bàn rê cử chỉ", "Swipe Pad")) },
                    )
                }

                if (!cheDoBanRe) {
                    Box(
                        Modifier
                            .size(252.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        IconButton(
                            onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); vm.bamPhim(Phim.UP) },
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp).size(60.dp),
                        ) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = t("Lên", "Up"), modifier = Modifier.size(36.dp))
                        }
                        IconButton(
                            onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); vm.bamPhim(Phim.DOWN) },
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp).size(60.dp),
                        ) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = t("Xuống", "Down"), modifier = Modifier.size(36.dp))
                        }
                        IconButton(
                            onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); vm.bamPhim(Phim.LEFT) },
                            modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp).size(60.dp),
                        ) {
                            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = t("Trái", "Left"), modifier = Modifier.size(36.dp))
                        }
                        IconButton(
                            onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); vm.bamPhim(Phim.RIGHT) },
                            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp).size(60.dp),
                        ) {
                            Icon(Icons.Default.KeyboardArrowRight, contentDescription = t("Phải", "Right"), modifier = Modifier.size(36.dp))
                        }
                        Button(
                            onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.bamPhim(Phim.OK) },
                            shape = CircleShape,
                            modifier = Modifier.size(96.dp),
                            contentPadding = PaddingValues(0.dp),
                        ) { Text("OK", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge) }
                    }
                } else {
                    TouchpadArea(
                        onKey = { key ->
                            haptic.performHapticFeedback(
                                if (key == Phim.OK || key == Phim.HOME) HapticFeedbackType.LongPress
                                else HapticFeedbackType.TextHandleMove
                            )
                            vm.bamPhim(key)
                        }
                    )
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    NutTron(Icons.AutoMirrored.Filled.VolumeDown, t("Giảm âm lượng", "Volume down"), { vm.bamPhim(Phim.VOL_DOWN) })
                    NutTron(Icons.AutoMirrored.Filled.VolumeOff, t("Tắt tiếng", "Mute"), { vm.bamPhim(Phim.MUTE) })
                    NutTron(Icons.AutoMirrored.Filled.VolumeUp, t("Tăng âm lượng", "Volume up"), { vm.bamPhim(Phim.VOL_UP) })
                    NutTron(Icons.Default.PlayArrow, t("Phát / Dừng", "Play / Pause"), { vm.bamPhim(Phim.PLAY_PAUSE) })
                }

                // Thanh kéo âm lượng TV chính xác (độc quyền khi nối qua CleanTxa Link)
                if (vm.dangLienKetNgam && vm.amLuongTv != null) {
                    val (curVol, maxVol) = vm.amLuongTv!!
                    Column(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                t("Âm lượng trực tiếp", "Direct TV Volume"),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "$curVol / $maxVol",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Slider(
                            value = curVol.toFloat(),
                            onValueChange = { vm.datAmLuongTv(it.roundToInt()) },
                            valueRange = 0f..maxVol.coerceAtLeast(1).toFloat(),
                        )
                    }
                }

                // Hàng phím tắt nhanh cho tivi: Nguồn vào HDMI, Đa nhiệm, Tìm kiếm, Cài đặt TV, Chụp màn hình
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AssistChip(
                        onClick = { vm.bamPhim(Phim.TV_INPUT) },
                        leadingIcon = { Icon(Icons.Default.SettingsInputHdmi, null, Modifier.size(16.dp)) },
                        label = { Text(t("Nguồn HDMI", "HDMI Input")) },
                    )
                    AssistChip(
                        onClick = { vm.bamPhim(Phim.APP_SWITCH) },
                        leadingIcon = { Icon(Icons.Default.Apps, null, Modifier.size(16.dp)) },
                        label = { Text(t("Đa nhiệm", "Recents")) },
                    )
                    AssistChip(
                        onClick = { vm.bamPhim(Phim.SEARCH) },
                        leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(16.dp)) },
                        label = { Text(t("Tìm kiếm", "Search")) },
                    )
                    AssistChip(
                        onClick = { vm.bamPhim(Phim.SETTINGS) },
                        leadingIcon = { Icon(Icons.Default.Settings, null, Modifier.size(16.dp)) },
                        label = { Text(t("Cài đặt TV", "TV Settings")) },
                    )
                    AssistChip(
                        onClick = { vm.chupManHinh() },
                        leadingIcon = { Icon(Icons.Default.Screenshot, null, Modifier.size(16.dp)) },
                        label = { Text(t("Chụp màn TV", "Screenshot")) },
                    )
                }
            }
        }

        // ---- ĐẶC QUYỀN ĐỘC QUYỀN CLEANTXA LINK (Chỉ xuất hiện khi có Liên kết ngầm) ----
        if (vm.dangLienKetNgam) {
            TxaCard(
                title = t("Đặc quyền Liên kết ngầm (Độc quyền)", "CleanTxa Link Exclusives"),
                subtitle = t(
                    "Những tính năng chỉ hoạt động nhờ cầu nối chạy ngầm trên TV — không cần bật ADB, không cần cấu hình Cast.",
                    "Exclusive capabilities powered by the background bridge on your TV — zero ADB, zero Cast setup.",
                ),
                icon = Icons.Default.AutoAwesome,
                highlighted = true,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // 1. Bắn nhanh Video / Link Web lên TV
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.OpenInNew, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Text(
                                t("1. Bắn nhanh Link / Video YouTube lên TV", "1. Beam Link / YouTube Video to TV"),
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Text(
                            t(
                                "Mẹo nhanh: Ở YouTube, Chrome, Facebook… trên điện thoại, bấm Chia sẻ → chọn CleanTxa là video tự mở ngay trên TV!",
                                "Tip: In YouTube, Chrome, or any app on your phone, tap Share → CleanTxa to open it straight on your TV!",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = vm.linkCanBan,
                            onValueChange = { vm.linkCanBan = it },
                            placeholder = { Text(t("Dán link YouTube, web, VTVgo…", "Paste YouTube, web, or stream URL…")) },
                            singleLine = true,
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        val clip = docClipboardDienThoai()
                                        if (clip.isNotBlank()) vm.linkCanBan = clip
                                    }
                                ) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = t("Dán", "Paste"))
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = { vm.banLinkLenTv() },
                            enabled = vm.linkCanBan.isNotBlank() && !vm.dangBan,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Link, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(t("Mở ngay trên màn hình TV", "Open right on TV"))
                        }
                    }

                    HorizontalDivider()

                    // 2. Chiếu Ảnh điện thoại toàn màn hình lên TV 1 chạm
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Image, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Text(
                                t("2. Trình chiếu Ảnh lên TV tức thì", "2. Instant Full-Screen Photo Beam"),
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { photoBeamPicker.launch("image/*") },
                                enabled = !vm.dangBan,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Default.Image, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(t("Chọn ảnh chiếu lên TV", "Pick photo to beam"))
                            }
                            if (vm.dangChieuAnhTv) {
                                Button(onClick = { vm.dongAnhTrenTv() }) {
                                    Icon(Icons.Default.Close, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(t("Đóng ảnh", "Close"))
                                }
                            }
                        }
                    }

                    HorizontalDivider()

                    // 3. Tin nhắn nổi trên màn hình TV + Đọc phát loa bằng giọng nói (TTS)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Campaign, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Text(
                                t("3. Lời nhắn nổi & Phát giọng nói trên TV", "3. Live TV Banner & Voice Announcer"),
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            listOf(
                                t("🍽️ Đến giờ ăn cơm rồi!", "🍽️ Dinner is ready!"),
                                t("🛏️ Khuya rồi, tắt TV đi ngủ nhé!", "🛏️ Time for bed, turn off the TV!"),
                                t("📚 Tạm dừng xem, vào học bài thôi!", "📚 Study time, pause the TV!"),
                            ).forEach { mau ->
                                AssistChip(
                                    onClick = { vm.guiThongBaoLenTv(mau, vm.docToTinNhanTv) },
                                    label = { Text(mau, style = MaterialTheme.typography.labelSmall) },
                                )
                            }
                        }
                        OutlinedTextField(
                            value = vm.tinNhanTv,
                            onValueChange = { vm.tinNhanTv = it },
                            placeholder = { Text(t("Nhập lời nhắn hiện đè lên màn TV…", "Message to display on TV…")) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                t("Đọc to bằng giọng nói qua loa TV", "Speak message aloud on TV speakers"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = vm.docToTinNhanTv,
                                onCheckedChange = { vm.docToTinNhanTv = it },
                            )
                        }
                        OutlinedButton(
                            onClick = { vm.guiThongBaoLenTv() },
                            enabled = vm.tinNhanTv.isNotBlank() && !vm.dangBan,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(t("Hiện lời nhắn lên TV", "Send live banner to TV"))
                        }
                    }

                    HorizontalDivider()

                    // 4. Đồng bộ Clipboard 2 chiều Điện thoại ↔ Tivi
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.ContentCopy, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Text(
                                t("4. Đồng bộ Clipboard 2 chiều", "4. Two-Way Clipboard Sync"),
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { vm.dayClipboardLenTv(docClipboardDienThoai(), danLuon = true) },
                                enabled = !vm.dangBan,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Default.ContentPaste, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(t("Dán Clipboard → TV", "Paste Phone → TV"))
                            }
                            OutlinedButton(
                                onClick = {
                                    vm.layClipboardTuTv { text ->
                                        chepVaoClipboardDienThoai(text)
                                    }
                                },
                                enabled = !vm.dangBan,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(t("Lấy chữ TV → Máy", "Copy TV → Phone"))
                            }
                        }
                    }

                    HorizontalDivider()

                    // 5. Bộ lọc Dịu mắt ban đêm trên TV (0% – 75%)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.BrightnessLow, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Text(
                                    t("5. Chế độ Dịu mắt xem đêm", "5. Night Eye-Care Dimmer"),
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            Text(
                                if (vm.doToiTv == 0) t("Tắt", "Off") else "-${vm.doToiTv}%",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        Slider(
                            value = vm.doToiTv.toFloat(),
                            onValueChange = { vm.datDoToiManHinhTv(it.roundToInt()) },
                            valueRange = 0f..75f,
                        )
                    }
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
        var locApp by remember { mutableStateOf("") }
        val dsAppLoc = remember(locApp, dsApp) {
            val q = locApp.trim().lowercase()
            if (q.isEmpty()) dsApp else dsApp.filter { tenDep(it).lowercase().contains(q) || it.lowercase().contains(q) }
        }
        TxaCard(title = t("App trên tivi (${dsApp.size})", "Apps on the TV (${dsApp.size})"), icon = Icons.Default.Apps) {
            OutlinedButton(
                onClick = { apkPicker.launch(arrayOf("application/vnd.android.package-archive", "*/*")) },
                enabled = !vm.dangBan,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.InstallMobile, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(t("Cài file APK từ điện thoại", "Install an APK from this phone"))
            }
            if (dsApp.size > 5) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = locApp,
                    onValueChange = { locApp = it },
                    placeholder = { Text(t("Lọc nhanh app trên tivi…", "Filter TV apps…")) },
                    leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(18.dp)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (dsAppLoc.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                dsAppLoc.forEach { pkg ->
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

    // Hộp xem & chia sẻ ảnh chụp màn hình TV (hoạt động cả khi đã tắt ADB qua CleanTxa Link!)
    vm.anhTv?.let { bmp ->
        AlertDialog(
            onDismissRequest = { vm.anhTv = null },
            title = { Text(t("Ảnh chụp màn hình TV", "TV Screenshot")) },
            text = {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp)),
                )
            },
            confirmButton = {
                Button(onClick = { vm.chiaSeAnhChupTv() }) {
                    Icon(Icons.Default.Share, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(t("Chia sẻ / Lưu", "Share / Save"))
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.anhTv = null }) {
                    Text(t("Đóng", "Close"))
                }
            },
        )
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

/**
 * Bàn rê cảm ứng thông minh kiểu điều khiển cao cấp:
 *  - Vuốt 4 hướng: Lên / Xuống / Trái / Phải (vuốt dài sẽ tự lặp từng bước mượt mà)
 *  - Chạm 1 lần: OK
 *  - Chạm đúp: Quay lại (Back)
 *  - Nhấn giữ: Về màn hình chính (Home)
 */
@Composable
private fun TouchpadArea(onKey: (Int) -> Unit) {
    var accX by remember { mutableFloatStateOf(0f) }
    var accY by remember { mutableFloatStateOf(0f) }
    val stepThresholdPx = 95f

    Box(
        Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f))
            .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), RoundedCornerShape(28.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onKey(Phim.OK) },
                    onDoubleTap = { onKey(Phim.BACK) },
                    onLongPress = { onKey(Phim.HOME) },
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { accX = 0f; accY = 0f },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        accX += dragAmount.x
                        accY += dragAmount.y
                        if (abs(accX) >= abs(accY) && abs(accX) >= stepThresholdPx) {
                            onKey(if (accX > 0) Phim.RIGHT else Phim.LEFT)
                            accX = 0f
                            accY = 0f
                        } else if (abs(accY) > abs(accX) && abs(accY) >= stepThresholdPx) {
                            onKey(if (accY > 0) Phim.DOWN else Phim.UP)
                            accX = 0f
                            accY = 0f
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(
                Icons.Default.Swipe,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(34.dp),
            )
            Text(
                t("Vuốt để di chuyển · Chạm = OK", "Swipe to navigate · Tap = OK"),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                t("Chạm đúp = Quay lại · Nhấn giữ = Home", "Double-tap = Back · Long-press = Home"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
