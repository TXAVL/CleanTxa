package com.txa.cleantxa.ui

import android.content.Context
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

/** Dòng ngăn giữa phần giới thiệu và phần toàn văn giấy phép trong file giấy phép. */
internal const val DAU_TACH_GIAY_PHEP = "@@LICENSE-TEXTS@@"

/** Tên file giấy phép theo ngôn ngữ app: giay-phep-vi.txt hoặc giay-phep-en.txt (trong assets). */
internal fun tenFileGiayPhep(tiengViet: Boolean) = if (tiengViet) "giay-phep-vi.txt" else "giay-phep-en.txt"

/** Tách nội dung file giấy phép thành (phần giới thiệu, phần toàn văn giấy phép) và điền số phiên bản. */
internal fun tachGiayPhep(raw: String, phienBan: String): Pair<String, String> {
    val noiDung = raw.replace("{{VERSION}}", phienBan)
    val i = noiDung.indexOf(DAU_TACH_GIAY_PHEP)
    return if (i < 0) noiDung.trim() to ""
    else noiDung.substring(0, i).trim() to noiDung.substring(i + DAU_TACH_GIAY_PHEP.length).trim()
}

private fun napGiayPhep(ctx: Context, tiengViet: Boolean): Pair<String, String> {
    val raw = runCatching {
        ctx.assets.open(tenFileGiayPhep(tiengViet)).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }.getOrDefault("")
    return tachGiayPhep(raw, BuildConfig.VERSION_NAME)
}

@Composable
fun LicensesScreen() {
    val ctx = LocalContext.current
    // Đọc NgonNgu.tiengViet trực tiếp để Compose vẽ lại và nạp lại đúng file ngay khi người dùng bấm đổi VI / EN.
    val isVi = NgonNgu.tiengViet
    val (gioiThieu, toanVan) = remember(isVi) { napGiayPhep(ctx, isVi) }

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
                gioiThieu.ifBlank { t("Không đọc được nội dung giấy phép.", "The license text could not be loaded.") },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (toanVan.isNotBlank()) {
            Text(
                toanVan,
                style = MaterialTheme.typography.bodySmall,
                // Chữ đều nét (monospace) làm mất dấu tiếng Việt ở nhiều máy: chỉ dùng cho bản tiếng Anh.
                fontFamily = if (isVi) FontFamily.Default else FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
