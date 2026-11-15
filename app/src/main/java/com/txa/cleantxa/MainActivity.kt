package com.txa.cleantxa

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.txa.cleantxa.ui.CleanTxaApp
import com.txa.cleantxa.ui.CleanTxaTheme
import com.txa.cleantxa.ui.PhienLamViec
import com.txa.cleantxa.ui.SplashScreen

class MainActivity : ComponentActivity() {
    private val xinThongBao = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { java.security.Security.insertProviderAt(org.conscrypt.Conscrypt.newProvider(), 1) }
        NgonNgu.nap(this)
        ManHinh.doc(this)
        TxaLogger.init(this)
        CapNhat.donOtaCuNeuDoiBan(this)
        ThongBaoCapNhat.hen(this)
        docYDinh(intent)
        if (!ManHinh.laTv && Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            val p = getSharedPreferences("ota", MODE_PRIVATE)
            if (!p.getBoolean("da_xin_tb", false)) {
                p.edit().putBoolean("da_xin_tb", true).apply()
                xinThongBao.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        enableEdgeToEdge()
        setContent {
            CleanTxaTheme {
                RootApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        docYDinh(intent)
    }

    private fun docYDinh(i: Intent?) {
        if (i?.getBooleanExtra(ThongBaoCapNhat.MO_CAP_NHAT, false) == true) {
            CapNhat.moTuThongBao = true
            i.removeExtra(ThongBaoCapNhat.MO_CAP_NHAT)
        }
    }
}

@Composable
private fun RootApp(vm: PhienLamViec = viewModel()) {
    var showSplash by rememberSaveable { mutableStateOf(true) }
    if (showSplash) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            SplashScreen(onDone = { showSplash = false })
        }
    } else {
        CleanTxaApp(vm)
    }
}
