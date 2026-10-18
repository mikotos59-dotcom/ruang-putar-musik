package id.ruangputar.musik

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import id.ruangputar.musik.inti.Pembaruan
import id.ruangputar.musik.ui.AppVM
import id.ruangputar.musik.ui.DialogPembaruan
import id.ruangputar.musik.ui.Ikon
import id.ruangputar.musik.ui.LayarBeranda
import id.ruangputar.musik.ui.LayarCari
import id.ruangputar.musik.ui.LayarPengaturan
import id.ruangputar.musik.ui.LayarPenyanyi
import id.ruangputar.musik.ui.LayarPustaka
import id.ruangputar.musik.ui.MiniPemutar
import id.ruangputar.musik.ui.PemutarPenuh
import id.ruangputar.musik.ui.Tab
import id.ruangputar.musik.ui.TemaRuangPutar

class MainActivity : ComponentActivity() {
    private val vm: AppVM by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val p by vm.data.pengaturan.collectAsState()
            TemaRuangPutar(p.tema) { Aplikasi(vm) }
        }
        tangani(intent)
    }

    override fun onStart() {
        super.onStart()
        vm.sambung(this)
        Pembaruan.cekOtomatis(this)
    }

    override fun onResume() {
        super.onResume()
        Pembaruan.lanjutkanBilaDiizinkan(this)   // kembali dari layar izin "Instal aplikasi tidak dikenal"
    }

    override fun onStop() {
        vm.lepas()
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        tangani(intent)
    }

    /** Link YouTube yang dibagikan dari aplikasi lain (YouTube, browser, WhatsApp, …). */
    private fun tangani(i: Intent?) {
        if (i?.action == Intent.ACTION_SEND) {
            i.getStringExtra(Intent.EXTRA_TEXT)?.let { vm.bukaTautan(it) }
        }
    }
}

@Composable
private fun Aplikasi(vm: AppVM) {
    val ctx = LocalContext.current
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        vm.pesan.collect { snack.showSnackbar(it, duration = SnackbarDuration.Short) }
    }
    // Android 7–9: izin menyimpan unduhan ke folder Musik
    val minta = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT < 29 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            minta.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Scaffold(
            snackbarHost = { SnackbarHost(snack) },
            bottomBar = {
                Column {
                    MiniPemutar(vm)
                    NavigationBar {
                        fun pilih(t: Tab) {
                            vm.tutupPenyanyi()
                            vm.tab = t
                        }
                        val terpilih = if (vm.penyanyiDibuka != null) null else vm.tab
                        NavigationBarItem(terpilih == Tab.BERANDA, { pilih(Tab.BERANDA) },
                            icon = { Icon(Icons.Filled.Home, null) }, label = { Text("Beranda") })
                        NavigationBarItem(terpilih == Tab.CARI, { pilih(Tab.CARI) },
                            icon = { Icon(Icons.Filled.Search, null) }, label = { Text("Cari") })
                        NavigationBarItem(terpilih == Tab.PUSTAKA, { pilih(Tab.PUSTAKA) },
                            icon = { Icon(Ikon.Pustaka, null) }, label = { Text("Pustaka") })
                        NavigationBarItem(terpilih == Tab.PENGATURAN, { pilih(Tab.PENGATURAN) },
                            icon = { Icon(Icons.Filled.Settings, null) }, label = { Text("Pengaturan") })
                    }
                }
            },
        ) { pad ->
            Box(Modifier.padding(pad)) {
                val p = vm.penyanyiDibuka
                val h = vm.halamanPenyanyi
                if (p != null && h != null) {
                    LayarPenyanyi(vm, p, h)
                } else {
                    when (vm.tab) {
                        Tab.BERANDA -> LayarBeranda(vm)
                        Tab.CARI -> LayarCari(vm)
                        Tab.PUSTAKA -> LayarPustaka(vm)
                        Tab.PENGATURAN -> LayarPengaturan(vm)
                    }
                }
            }
        }
        if (vm.pemutarTerbuka && vm.laguKini != null) PemutarPenuh(vm)
        DialogPembaruan()
    }
}
