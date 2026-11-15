package com.txa.cleantxa.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.txa.cleantxa.BuildConfig
import com.txa.cleantxa.NgonNgu
import com.txa.cleantxa.t

@Composable
fun LicensesScreen() {
    val ctx = LocalContext.current
    // Đọc NgonNgu.tiengViet trực tiếp để Compose tự vẽ lại ngay khi người dùng bấm đổi VI / EN
    val isVi = NgonNgu.tiengViet
    val licenseBody = remember {
        runCatching { ctx.assets.open("giay-phep.txt").bufferedReader().readText() }.getOrDefault("")
    }

    val headerText = if (isVi) {
        """
        CleanTxa ${BuildConfig.VERSION_NAME} — TXAVL (https://github.com/TXAVL/CleanTxa)
        Phần mềm miễn phí, cung cấp nguyên trạng, không kèm bảo hành.

        QUYỀN RIÊNG TƯ & HOẠT ĐỘNG CỤC BỘ
        • CleanTxa hoạt động cục bộ qua Wi-Fi ADB giữa điện thoại và tivi của bạn.
        • Không thu thập tài khoản, không gửi dữ liệu người dùng hay danh sách ứng dụng lên máy chủ bên thứ ba.
        • Kết nối mạng chỉ dùng khi: kiểm tra phiên bản mới trên GitHub Releases, tải thời tiết (Open-Meteo), hoặc bạn chủ động dùng máy chủ logs cục bộ cổng 2311 trong mạng Wi-Fi nhà bạn.

        THÀNH PHẦN MÃ NGUỒN MỞ SỬ DỤNG
        1. LibADB Android — Muntashir Al-Islam (Apache License 2.0)
           https://github.com/MuntashirAkon/libadb-android
        2. Conscrypt — Google (Apache License 2.0)
           https://github.com/google/conscrypt
        3. AndroidX, Jetpack Compose, Kotlin — Google / JetBrains (Apache License 2.0)
        4. Bouncy Castle — Legion of the Bouncy Castle Inc. (MIT License)
           https://www.bouncycastle.org
        5. ZXing Core — ZXing Authors (Apache License 2.0)
           https://github.com/zxing/zxing
        6. Dữ liệu thời tiết: Open-Meteo (CC BY 4.0) · Địa danh: © OpenStreetMap contributors (ODbL 1.0)
        7. DNS chặn quảng cáo (tuỳ chọn): AdGuard DNS — https://adguard-dns.io

        Tên các hãng tivi chỉ dùng để mô tả khả năng tương thích; CleanTxa không liên kết với các hãng đó.
        """.trimIndent()
    } else {
        """
        CleanTxa ${BuildConfig.VERSION_NAME} — TXAVL (https://github.com/TXAVL/CleanTxa)
        Free software, provided "as is", without warranty.

        PRIVACY & LOCAL-FIRST OPERATION
        • CleanTxa runs locally over Wi-Fi ADB between your phone and your TV.
        • No account is required, and no user telemetry or installed app lists are sent to third-party servers.
        • Network access is only used to check GitHub Releases for app updates, fetch optional weather data (Open-Meteo), or serve the local log viewer on port 2311 over your own Wi-Fi.

        OPEN-SOURCE LIBRARIES USED
        1. LibADB Android — Muntashir Al-Islam (Apache License 2.0)
           https://github.com/MuntashirAkon/libadb-android
        2. Conscrypt — Google (Apache License 2.0)
           https://github.com/google/conscrypt
        3. AndroidX, Jetpack Compose, Kotlin — Google / JetBrains (Apache License 2.0)
        4. Bouncy Castle — Legion of the Bouncy Castle Inc. (MIT License)
           https://www.bouncycastle.org
        5. ZXing Core — ZXing Authors (Apache License 2.0)
           https://github.com/zxing/zxing
        6. Weather data: Open-Meteo (CC BY 4.0) · Place coordinates: © OpenStreetMap contributors (ODbL 1.0)
        7. Optional ad-blocking DNS: AdGuard DNS — https://adguard-dns.io

        TV brand names are only used to describe compatibility; CleanTxa is not affiliated with them.
        """.trimIndent()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TxaCard(highlighted = true) {
            Text(
                t("Thông tin & Giấy phép CleanTxa", "CleanTxa Info & Licenses"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                headerText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            licenseBody,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
