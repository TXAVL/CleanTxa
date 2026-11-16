package com.txa.cleantxa.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.txa.cleantxa.LogEntry
import com.txa.cleantxa.LogLevel
import com.txa.cleantxa.TxaLogger
import com.txa.cleantxa.t
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogsScreen(vm: PhienLamViec) {
    val ctx = LocalContext.current
    var selectedLevel by remember { mutableStateOf<LogLevel?>(null) }
    var query by remember { mutableStateOf("") }
    var selectedEntry by remember { mutableStateOf<LogEntry?>(null) }
    val isTv = com.txa.cleantxa.ManHinh.laTv
    val allLogs = TxaLogger.entries.toList().asReversed()
    val tvSummary = vm.tvSummaryForLogs()
    val compactConfig = remember(allLogs.size, tvSummary) {
        TxaLogger.compactConfigPairs(ctx, tvSummary)
    }
    val filtered = remember(allLogs.size, selectedLevel, query) {
        allLogs.filter { e ->
            (selectedLevel == null || e.level == selectedLevel) &&
                (query.isBlank() || e.tag.contains(query, true) || e.message.contains(query, true))
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TxaCard(highlighted = true) {
            Text(
                t("Nhật ký & Cấu hình thiết bị", "Logs & Device Configuration"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                compactConfig.forEach { (k, v) ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            "$k:",
                            modifier = Modifier.width(86.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            v,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                t(
                    "Bấm «Copy tất cả» để sao chép đầy đủ cấu hình thiết bị & logs.",
                    "Tap «Copy all» to copy full device configuration & logs."
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
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
                ) {
                    Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(t("Copy tất cả", "Copy all"))
                }
                OutlinedButton(
                    onClick = {
                        val text = TxaLogger.fullReport(ctx, vm.tvSummaryForLogs())
                        val send = Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(Intent.EXTRA_SUBJECT, "CleanTxa Logs")
                            .putExtra(Intent.EXTRA_TEXT, text)
                        ctx.startActivity(
                            Intent.createChooser(send, t("Chia sẻ logs", "Share logs"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                ) {
                    Icon(Icons.Default.Share, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(t("Chia sẻ", "Share"))
                }
                OutlinedButton(onClick = { vm.moManLoiQr() }) {
                    Icon(Icons.Default.QrCode2, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(t("Web QR (2311)", "Web QR (2311)"))
                }
                OutlinedButton(onClick = { TxaLogger.clear(ctx) }) {
                    Icon(Icons.Default.DeleteSweep, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(t("Xoá", "Clear"))
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = selectedLevel == null,
                onClick = { selectedLevel = null },
                label = { Text(t("Tất cả (${allLogs.size})", "All (${allLogs.size})")) },
            )
            LogLevel.entries.forEach { lv ->
                FilterChip(
                    selected = selectedLevel == lv,
                    onClick = { selectedLevel = if (selectedLevel == lv) null else lv },
                    label = { Text(lv.name) },
                )
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(t("Tìm theo tag hoặc nội dung…", "Filter by tag or message…")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        )

        if (filtered.isEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    t("Chưa có dòng log nào phù hợp.", "No matching log entries."),
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(filtered) { item ->
                    val badgeColor = when (item.level) {
                        LogLevel.ERROR -> Color(0xFFEF5350)
                        LogLevel.WARN -> Color(0xFFFFB74D)
                        LogLevel.INFO -> Color(0xFF2EC4B6)
                    }
                    val firstLine = item.message.lineSequence().firstOrNull() ?: item.message
                    val hasMore = item.message.contains('\n') || !item.stackTrace.isNullOrBlank()
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                            .clickable { selectedEntry = item }
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                item.level.name,
                                color = badgeColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                            )
                            Text(
                                formatLogTime(item.time),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                item.tag,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Text(
                            firstLine,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (hasMore) {
                            Text(
                                t("Bấm để xem chi tiết…", "Tap to view details…"),
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }

    selectedEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { selectedEntry = null },
            title = {
                Text("${entry.level.name} · ${entry.tag}")
            },
            text = {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        formatLogFullTime(entry.time),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SelectionContainer {
                        Text(
                            entry.message,
                            fontSize = 12.5.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    if (!entry.stackTrace.isNullOrBlank()) {
                        SelectionContainer {
                            Text(
                                entry.stackTrace,
                                fontSize = 11.5.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFFFFCDD2),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val ok = TxaLogger.copyToClipboard(ctx, entry.formattedLine())
                        Toast.makeText(
                            ctx,
                            if (ok) t("Đã copy dòng log", "Copied log entry")
                            else t("Không thể copy", "Copy failed"),
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                ) {
                    Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copy")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedEntry = null }) {
                    Text(t("Đóng", "Close"))
                }
            },
        )
    }
}

private fun formatLogTime(ms: Long): String =
    SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(ms))

private fun formatLogFullTime(ms: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(ms))

