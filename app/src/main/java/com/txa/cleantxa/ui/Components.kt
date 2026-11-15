package com.txa.cleantxa.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.txa.cleantxa.NgonNgu
import com.txa.cleantxa.dulieu.tenDep
import com.txa.cleantxa.t

@Composable
fun CleanTxaApp(vm: PhienLamViec) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    vm.huyTuTatAdb()
                    vm.kiemKetNoiKhiVe()
                }
                Lifecycle.Event.ON_STOP -> {
                    if (vm.thongTin != null && !vm.giuAdb) vm.henTuTatAdb()
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    BackHandler(enabled = vm.man != Man.CHINH) {
        navigateBack(vm)
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CleanTxaTopBar(vm)
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            stepIndex(vm.man)?.let { step ->
                StepProgressBar(currentStep = step)
            }
            StatusBanner(vm)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (vm.man) {
                    Man.CHINH -> HomeScreen(vm)
                    Man.KET_NOI -> ConnectScreen(vm)
                    Man.THONG_TIN -> TvInfoScreen(vm)
                    Man.CHON_GOI -> DebloatScreen(vm)
                    Man.DANG_TAT -> ExecutingScreen(vm)
                    Man.HOME_SACH -> CleanHomeScreen(vm)
                    Man.XONG -> DoneScreen(vm)
                    Man.KHOI_PHUC -> RestoreScreen(vm)
                    Man.HUONG_DAN -> GuideScreen(vm)
                    Man.GIAY_PHEP -> LicensesScreen()
                    Man.KHOA_TRE -> KidsLockScreen(vm)
                    Man.REMOTE -> RemoteScreen(vm)
                }
            }
        }
    }

    StorageManagerDialog(vm)

    if (vm.hoiCapNhat) {
        vm.banMoi?.let { b ->
            AlertDialog(
                onDismissRequest = { vm.hoiCapNhat = false },
                title = {
                    Text(t("Đã có CleanTxa ${b.versionName}", "CleanTxa ${b.versionName} is out"))
                },
                text = {
                    Text(
                        b.ghiChu.ifBlank {
                            t("Bạn có muốn tải và cài đặt bản cập nhật mới ngay không?", "Download and install the new update now?")
                        }
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            vm.hoiCapNhat = false
                            vm.capNhatApp()
                        },
                        enabled = !vm.dangCapNhat,
                    ) {
                        Text(if (vm.dangCapNhat) t("Đang tải…", "Downloading…") else t("Cập nhật", "Update"))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { vm.hoiCapNhat = false }) {
                        Text(t("Để sau", "Later"))
                    }
                },
            )
        }
    }
}

fun navigateBack(vm: PhienLamViec) {
    when (vm.man) {
        Man.CHINH -> Unit
        Man.HUONG_DAN, Man.GIAY_PHEP -> vm.man = vm.manTruoc
        Man.KET_NOI -> vm.veManChinh()
        Man.THONG_TIN -> vm.veManChinh()
        Man.CHON_GOI, Man.KHOI_PHUC, Man.KHOA_TRE, Man.REMOTE -> vm.man = Man.THONG_TIN
        Man.HOME_SACH -> vm.man = if (vm.coDauCleanTv) Man.THONG_TIN else Man.CHON_GOI
        Man.DANG_TAT -> if (!vm.dangBan) vm.man = Man.CHON_GOI
        Man.XONG -> vm.veManChinh()
    }
}

private fun stepIndex(m: Man): Int? = when (m) {
    Man.KET_NOI -> 1
    Man.THONG_TIN -> 2
    Man.CHON_GOI, Man.DANG_TAT -> 3
    Man.HOME_SACH -> 4
    Man.XONG -> 5
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CleanTxaTopBar(vm: PhienLamViec) {
    val ctx = LocalContext.current
    val title = when (vm.man) {
        Man.CHINH -> "CleanTxa"
        Man.KET_NOI -> t("Kết nối tivi", "Connect TV")
        Man.THONG_TIN -> vm.thongTin?.tenHienThi?.ifBlank { t("Thông tin tivi", "TV Info") } ?: t("Thông tin tivi", "TV Info")
        Man.CHON_GOI -> t("Chọn gói cần tắt", "Select packages")
        Man.DANG_TAT -> t("Đang dọn tivi", "Cleaning TV")
        Man.HOME_SACH -> t("Home sạch", "Clean Home")
        Man.XONG -> t("Hoàn tất", "Done")
        Man.KHOI_PHUC -> t("Khôi phục", "Restore")
        Man.HUONG_DAN -> t("Hướng dẫn kết nối", "Setup guide")
        Man.GIAY_PHEP -> t("Giấy phép nguồn mở", "Open-source licenses")
        Man.KHOA_TRE -> t("Khoá trẻ em", "Kids lock")
        Man.REMOTE -> t("Remote tivi", "TV Remote")
    }

    CenterAlignedTopAppBar(
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
        ),
        navigationIcon = {
            if (vm.man != Man.CHINH) {
                IconButton(onClick = { navigateBack(vm) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = t("Quay lại", "Back"))
                }
            } else {
                Box(Modifier.padding(start = 12.dp)) {
                    CleanTxaLogo(size = 32.dp)
                }
            }
        },
        title = {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        },
        actions = {
            if (vm.man != Man.HUONG_DAN) {
                IconButton(onClick = { vm.moHuongDan() }) {
                    Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = t("Hướng dẫn", "Guide"))
                }
            }
            TextButton(onClick = { NgonNgu.doi(ctx) }) {
                Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (NgonNgu.tiengViet) "VI" else "EN", fontWeight = FontWeight.Bold)
            }
        },
    )
}

@Composable
private fun StepProgressBar(currentStep: Int) {
    val labels = listOf(
        t("Kết nối", "Connect"),
        t("Tivi", "TV"),
        t("Dọn", "Clean"),
        t("Home", "Home"),
        t("Xong", "Done"),
    )
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { idx, label ->
            val step = idx + 1
            val active = step == currentStep
            val done = step < currentStep
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                active -> MaterialTheme.colorScheme.primary
                                done -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun StatusBanner(vm: PhienLamViec) {
    Column(Modifier.fillMaxWidth()) {
        if (vm.dangBan) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            if (vm.thongBao.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        vm.thongBao,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
        if (vm.loi.isNotBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            vm.loi,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        IconButton(onClick = { vm.loi = "" }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(16.dp))
                        }
                    }
                    if (vm.canDonBoNho) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { vm.donBoNhoDem() }) {
                                Icon(Icons.Default.CleaningServices, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(t("Xoá bộ nhớ đệm", "Clear cache"))
                            }
                            OutlinedButton(onClick = { vm.moAppChiemCho() }) {
                                Icon(Icons.Default.Storage, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(t("Xem app chiếm chỗ", "App storage"))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TxaCard(
    title: String? = null,
    subtitle: String? = null,
    icon: ImageVector? = null,
    highlighted: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (title != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (icon != null) {
                        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        if (!subtitle.isNullOrBlank()) {
                            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            content()
        }
    }
}

@Composable
fun ExpandableSection(
    title: String,
    icon: ImageVector,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
            }
            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(top = 12.dp)) {
                    content()
                }
            }
        }
    }
}

@Composable
fun ToggleRow(
    title: String,
    subtitle: String = "",
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun CommandPreviewButton(commandsProvider: () -> List<String>) {
    var show by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { show = true }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.Terminal, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(t("Xem trước lệnh ADB", "Preview ADB commands"))
    }
    if (show) {
        val cmds = remember { commandsProvider() }
        AlertDialog(
            onDismissRequest = { show = false },
            title = { Text(t("Lệnh sẽ chạy trên tivi", "Commands to run on TV")) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        if (cmds.isEmpty()) t("Không có lệnh nào.", "No commands.") else cmds.joinToString("\n"),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { show = false }) { Text(t("Đóng", "Close")) }
            },
        )
    }
}

@Composable
fun StorageManagerDialog(vm: PhienLamViec) {
    val list = vm.appChiemCho ?: return
    AlertDialog(
        onDismissRequest = { vm.appChiemCho = null },
        title = { Text(t("Ứng dụng chiếm dung lượng", "Storage-heavy apps")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (vm.thongBaoDon.isNotBlank()) {
                    Text(vm.thongBaoDon, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }
                if (list.isEmpty()) {
                    Text(t("Không đọc được danh sách app chiếm chỗ.", "Could not read app storage stats."))
                }
                list.forEach { app ->
                    val totalMb = app.tong / (1024 * 1024)
                    val dataMb = app.duLieu / (1024 * 1024)
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text(tenDep(app.pkg), fontWeight = FontWeight.SemiBold)
                            Text("${app.pkg} · ${totalMb} MB (${t("dữ liệu", "data")} ${dataMb} MB)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (app.coCapNhat) {
                                    OutlinedButton(onClick = { vm.hoiDonApp = app.pkg to "go" }) {
                                        Text(t("Gỡ cập nhật", "Uninstall updates"))
                                    }
                                }
                                OutlinedButton(onClick = { vm.hoiDonApp = app.pkg to "xoa" }) {
                                    Text(t("Xoá dữ liệu", "Clear data"))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.appChiemCho = null }) { Text(t("Đóng", "Close")) }
        },
    )

    vm.hoiDonApp?.let { (pkg, action) ->
        AlertDialog(
            onDismissRequest = { vm.hoiDonApp = null },
            title = { Text(tenDep(pkg)) },
            text = {
                Text(
                    if (action == "go") t("Gỡ các bản cập nhật của $pkg để trống bộ nhớ?", "Uninstall updates for $pkg to free space?")
                    else t("Xoá toàn bộ dữ liệu và đăng nhập của $pkg?", "Clear all data and sign-ins for $pkg?")
                )
            },
            confirmButton = {
                Button(onClick = { vm.hoiDonApp = null; vm.donApp(pkg, action) }) {
                    Text(t("Xác nhận", "Confirm"))
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.hoiDonApp = null }) { Text(t("Huỷ", "Cancel")) }
            },
        )
    }
}

@Composable
fun AssetImage(assetPath: String, contentDescription: String? = null, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val bmp = remember(assetPath) {
        runCatching { ctx.assets.open(assetPath).use { BitmapFactory.decodeStream(it) } }.getOrNull()
    }
    if (bmp != null) {
        Image(bmp.asImageBitmap(), contentDescription = contentDescription, modifier = modifier)
    }
}

