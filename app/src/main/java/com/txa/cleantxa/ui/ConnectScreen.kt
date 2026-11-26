package com.txa.cleantxa.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Phonelink
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
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
        TvConnectHero(
            scanning = vm.dangQuet,
            connecting = vm.dangBan,
            foundCount = vm.timThay.size,
            statusMessage = when {
                vm.dangQuet -> t("Đang quét tìm TV trên mạng Wi-Fi…", "Scanning Wi-Fi for TVs…")
                vm.dangBan -> vm.thongBao.ifBlank { t("Đang kết nối tới TV…", "Connecting to TV…") }
                vm.timThay.isNotEmpty() -> t(
                    "Tìm thấy ${vm.timThay.size} thiết bị — chạm IP bên dưới để kết nối",
                    "Found ${vm.timThay.size} device(s) — tap an IP below to connect"
                )
                else -> t("Sẵn sàng kết nối TV qua Wi-Fi", "Ready to connect TV over Wi-Fi")
            },
        )

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

        if (!ManHinh.laTv && vm.dsTvLink.isNotEmpty()) {
            TxaCard(
                title = t("Tivi có TxaLink (Không cần ADB)", "TVs with TxaLink (No ADB needed)"),
                subtitle = t(
                    "Kết nối thẳng qua Wi-Fi với app CleanTxa đang chạy ngầm trên tivi — dùng Remote, cài APK và đổi giao diện Home sạch kể cả khi đã tắt ADB.",
                    "Connect directly over Wi-Fi to CleanTxa running in the background on your TV — works even with ADB turned off.",
                ),
                icon = Icons.Default.Phonelink,
                highlighted = true,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    vm.dsTvLink.forEach { d ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(d.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${d.ip} · Android ${d.androidVer}" + if (d.isTrusted) t(" · Đã tin cậy", " · Trusted") else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Button(
                                onClick = { vm.chonTvLink(d, chuyenMan = true) },
                                enabled = !vm.dangBan,
                            ) {
                                Icon(Icons.Default.Link, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (d.isTrusted) t("Kết nối ngầm", "Link") else t("Ghép tin cậy", "Trust & Link"))
                            }
                        }
                    }
                }
            }
        }

        vm.maGhepLinkDangCho?.let { pin ->
            AlertDialog(
                onDismissRequest = {},
                title = { Text(t("Mã xác nhận liên kết: $pin", "Link verification code: $pin")) },
                text = {
                    Text(
                        t(
                            "Nhìn lên màn hình tivi và bấm \"Tin tưởng & Cho phép\" trên hộp thoại có mã $pin.\n\n(Chỉ cần xác nhận một lần duy nhất — từ lần sau hai máy sẽ tự kết nối ngầm khi chung mạng Wi-Fi).",
                            "Look at your TV screen and select \"Trust & Allow\" on the prompt showing code $pin.\n\n(One-time setup — next time both devices will auto-connect silently over Wi-Fi)."
                        )
                    )
                },
                confirmButton = {},
            )
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
            AnimatedVisibility(
                visible = vm.timThay.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
            ) {
                Column {
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
            LaunchedEffect(Unit) {
                if (vm.ghepIpCong.isBlank() && !vm.dangTimGhep) {
                    vm.tuTimGhepDoi()
                }
            }
            Text(
                t(
                    "Trên tivi: Cài đặt → Hệ thống → Tùy chọn nhà phát triển → Gỡ lỗi qua Wi-Fi → Ghép nối thiết bị bằng mã ghép nối.",
                    "On TV: Settings → System → Developer options → Wireless debugging → Pair device with pairing code."
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { vm.tuTimGhepDoi() },
                enabled = !vm.dangTimGhep && !vm.dangBan,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (vm.dangTimGhep) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(t("Đang tự tìm IP & cổng ghép đôi…", "Auto-detecting pairing IP & port…"))
                } else {
                    Icon(Icons.Default.Radar, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(t("Tự tìm IP & cổng ghép đôi", "Auto-detect pairing IP & port"))
                }
            }
            AnimatedVisibility(
                visible = vm.dsGhepTimThay.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
            ) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        vm.dsGhepTimThay.forEach { addr ->
                            AssistChip(
                                onClick = { vm.ghepIpCong = addr },
                                label = { Text(addr) },
                            )
                        }
                    }
                }
            }
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

@Composable
private fun TvConnectHero(
    scanning: Boolean,
    connecting: Boolean,
    foundCount: Int,
    statusMessage: String,
) {
    val active = scanning || connecting
    val accent = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val surface = MaterialTheme.colorScheme.surfaceVariant

    val infinite = rememberInfiniteTransition(label = "tv_connect_hero")
    val pulse by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (active) 1300 else 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse",
    )
    val glow by infinite.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (active) 700 else 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow",
    )

    Surface(
        color = surface.copy(alpha = 0.55f),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(126.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f

                    // Expanding radar / Wi-Fi rings behind TV
                    val ringCount = if (active) 3 else 2
                    for (i in 0 until ringCount) {
                        val phase = (pulse + i / ringCount.toFloat()) % 1f
                        val radius = 34.dp.toPx() + phase * 48.dp.toPx()
                        val alpha = (1f - phase) * (if (active) 0.42f else 0.18f)
                        drawCircle(
                            color = accent.copy(alpha = alpha),
                            radius = radius,
                            center = Offset(cx, cy - 4.dp.toPx()),
                            style = Stroke(width = 2.dp.toPx()),
                        )
                    }

                    // TV frame
                    val tvW = 132.dp.toPx()
                    val tvH = 80.dp.toPx()
                    val tvLeft = cx - tvW / 2f
                    val tvTop = cy - tvH / 2f - 6.dp.toPx()

                    drawRoundRect(
                        color = Color(0xFF15222E),
                        topLeft = Offset(tvLeft, tvTop),
                        size = Size(tvW, tvH),
                        cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                    )
                    drawRoundRect(
                        color = accent.copy(alpha = if (active || foundCount > 0) 0.75f else 0.4f),
                        topLeft = Offset(tvLeft, tvTop),
                        size = Size(tvW, tvH),
                        cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                        style = Stroke(width = 2.dp.toPx()),
                    )

                    val pad = 6.dp.toPx()
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                accent.copy(alpha = 0.24f * glow),
                                secondary.copy(alpha = 0.18f * glow),
                                Color(0xFF0A141D),
                            ),
                            start = Offset(tvLeft + pad, tvTop + pad),
                            end = Offset(tvLeft + tvW - pad, tvTop + tvH - pad),
                        ),
                        topLeft = Offset(tvLeft + pad, tvTop + pad),
                        size = Size(tvW - pad * 2, tvH - pad * 2),
                        cornerRadius = CornerRadius(7.dp.toPx(), 7.dp.toPx()),
                    )

                    val arcCenter = Offset(cx, tvTop + tvH * 0.62f)
                    for (j in 0..2) {
                        val arcRadius = (12 + j * 11).dp.toPx()
                        val arcAlpha = if (active) {
                            val wave = ((pulse * 3f - j * 0.4f) % 1f + 1f) % 1f
                            0.35f + 0.65f * (1f - wave)
                        } else {
                            0.55f - j * 0.12f
                        }
                        drawArc(
                            color = accent.copy(alpha = arcAlpha),
                            startAngle = -135f,
                            sweepAngle = 90f,
                            useCenter = false,
                            topLeft = Offset(arcCenter.x - arcRadius, arcCenter.y - arcRadius),
                            size = Size(arcRadius * 2, arcRadius * 2),
                            style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round),
                        )
                    }
                    drawCircle(
                        color = if (foundCount > 0) Color(0xFF4ADE80) else accent,
                        radius = 3.5.dp.toPx() * (0.9f + 0.2f * glow),
                        center = arcCenter,
                    )

                    val standTop = tvTop + tvH
                    drawLine(
                        color = Color(0xFF283A4A),
                        start = Offset(cx, standTop),
                        end = Offset(cx, standTop + 10.dp.toPx()),
                        strokeWidth = 6.dp.toPx(),
                    )
                    drawLine(
                        color = accent.copy(alpha = 0.55f),
                        start = Offset(cx - 26.dp.toPx(), standTop + 11.dp.toPx()),
                        end = Offset(cx + 26.dp.toPx(), standTop + 11.dp.toPx()),
                        strokeWidth = 3.5.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }
            Text(
                text = statusMessage,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
