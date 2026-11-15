package com.txa.cleantxa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.txa.cleantxa.NgonNgu
import com.txa.cleantxa.dulieu.HUONG_DAN
import com.txa.cleantxa.t

@Composable
fun GuideScreen(vm: PhienLamViec) {
    val langDir = if (NgonNgu.tiengViet) "vi" else "en"
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            t(
                "Làm theo 7 bước dưới đây trên tivi để bật Gỡ lỗi không dây, sau đó quay lại màn Kết nối.",
                "Follow these 7 steps on your TV to enable debugging, then go back to Connect."
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HUONG_DAN.forEachIndexed { idx, step ->
            TxaCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            "${idx + 1}",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        if (NgonNgu.tiengViet) step.tieuDeVi else step.tieuDeEn,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(12.dp)),
                ) {
                    AssetImage(
                        assetPath = "huongdan/$langDir/${step.anh}.webp",
                        contentDescription = if (NgonNgu.tiengViet) step.tieuDeVi else step.tieuDeEn,
                        modifier = Modifier.fillMaxSize(),
                    )
                    // Subtle repeating diagonal watermark so screenshots keep CleanTxa attribution without obscuring TV menus
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .rotate(-18f)
                            .alpha(0.16f),
                        verticalArrangement = Arrangement.SpaceEvenly,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        repeat(2) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                            ) {
                                repeat(2) {
                                    Text(
                                        "CleanTxa",
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 18.sp,
                                        letterSpacing = 1.5.sp,
                                    )
                                }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xB80B1118))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        CleanTxaLogo(size = 14.dp)
                        Text(
                            "CleanTxa",
                            color = Color(0xFF2EC4B6),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    if (NgonNgu.tiengViet) step.chuVi else step.chuEn,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        Button(
            onClick = { vm.man = Man.KET_NOI },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(t("Đã bật xong — Đi kết nối", "Ready — Go to Connect"))
        }
    }
}
