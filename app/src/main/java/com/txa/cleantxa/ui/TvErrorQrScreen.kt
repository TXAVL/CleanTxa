package com.txa.cleantxa.ui

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.txa.cleantxa.LogWebServer
import com.txa.cleantxa.TxaLogger
import com.txa.cleantxa.t

@Composable
fun TvErrorQrScreen(vm: PhienLamViec) {
    val ctx = LocalContext.current
    var serverOk by remember { mutableStateOf(false) }
    val url = remember(serverOk) { LogWebServer.urlForDevice() }
    val qrBitmap = remember(url) { LogWebServer.generateQrBitmap(url, 520) }
    val pendingError = remember { TxaLogger.pendingTvErrorSummary(ctx) }

    DisposableEffect(Unit) {
        serverOk = LogWebServer.start(ctx) { vm.tvSummaryForLogs() }
        onDispose {
            LogWebServer.stop()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TxaCard(highlighted = true) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Default.QrCode2, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        t("Xem file logs trực tiếp qua Web (Cổng 2311)", "Read Logs Directly in Browser (Port 2311)"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (serverOk) {
                            t("Dùng điện thoại hoặc máy tính cùng Wi-Fi quét mã QR bên dưới để mở trình duyệt đọc file logs.", "Use your phone or another device on the same Wi-Fi to scan the QR code and read the log file in a browser.")
                        } else {
                            t("Không mở được cổng 2311 cục bộ. Bạn vẫn có thể copy logs trực tiếp.", "Could not open local port 2311. You can still copy the logs directly.")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (pendingError.isNotBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        t("Đã tự lưu sự cố vừa xảy ra trên TV", "Recorded recent TV error"),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Text(
                        pendingError,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }

        if (qrBitmap != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White)
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = url,
                    modifier = Modifier.size(240.dp),
                )
            }
        }

        Text(
            url,
            style = MaterialTheme.typography.titleMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = {
                    val text = TxaLogger.fullReport(ctx, vm.tvSummaryForLogs())
                    val ok = TxaLogger.copyToClipboard(ctx, text)
                    Toast.makeText(
                        ctx,
                        if (ok) t("Đã copy logs + cấu hình thiết bị", "Copied logs + device config")
                        else t("Không thể copy", "Copy failed"),
                        Toast.LENGTH_SHORT,
                    ).show()
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(t("Copy logs", "Copy logs"))
            }
            Button(
                onClick = {
                    TxaLogger.clearPendingTvError(ctx)
                    vm.man = Man.CHINH
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.Home, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(t("Về trang chính", "Back Home"))
            }
        }
    }
}

