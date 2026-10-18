package id.ruangputar.musik.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import id.ruangputar.musik.App
import id.ruangputar.musik.BuildConfig
import id.ruangputar.musik.inti.Lagu
import id.ruangputar.musik.inti.Penyanyi
import id.ruangputar.musik.inti.Rak
import id.ruangputar.musik.inti.Unduhan
import id.ruangputar.musik.inti.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

// ====================================================================== Beranda
@Composable
fun LayarBeranda(vm: AppVM) {
    val rak by vm.data.rak.collectAsState()
    val riwayat by vm.data.riwayat.collectAsState()
    val pengaturan by vm.data.pengaturan.collectAsState()
    val ditahan by YouTube.ditahanSampai.collectAsState()
    val sering = remember(riwayat) { vm.data.seringDiputar() }
    var dialogRak by remember { mutableStateOf<Rak?>(null) }
    var tambahRak by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item { KepalaBeranda(pengaturan.tema == "batik") }
        if (ditahan > System.currentTimeMillis()) item { BannerDitahan() }
        if (sering.isNotEmpty()) {
            item {
                JudulRak("Sering kamu putar", null, null)
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(sering) { i, l -> KartuLagu(l) { vm.putar(sering, i) } }
                }
            }
        }
        items(rak, key = { it.id }) { r ->
            LaunchedEffect(r.id) { vm.muatRak(r) }
            RakBeranda(r, vm, onUbah = { dialogRak = r })
        }
        item {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 12.dp)) {
                TextButton(onClick = { tambahRak = true }) { Text("+ Tambah rak") }
                TextButton(onClick = { vm.data.kembalikanRakBawaan() }) { Text("Kembalikan rak bawaan") }
            }
        }
    }

    if (tambahRak) {
        DialogRak(null, onBatal = { tambahRak = false }) { nama, kueri, maks, min ->
            vm.data.tambahRak(nama, kueri, maks, minDetik = min)
            tambahRak = false
        }
    }
    dialogRak?.let { r ->
        DialogRak(r, onBatal = { dialogRak = null }) { nama, kueri, maks, min ->
            val baru = r.copy(nama = nama, kueri = kueri, maksMenit = maks, minDetik = min)
            vm.data.ubahRak(baru)
            vm.muatRak(baru, paksa = true)
            dialogRak = null
        }
    }
}

@Composable
private fun KepalaBeranda(batik: Boolean) {
    val jam = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val sapa = when (jam) {
        in 4..10 -> "pagi"
        in 11..14 -> "siang"
        in 15..17 -> "sore"
        else -> "malam"
    }
    Box(Modifier.fillMaxWidth().height(if (batik) 132.dp else 96.dp)) {
        if (batik) {
            MotifKawung(Modifier.fillMaxSize(), MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f))
            Box(
                Modifier.fillMaxSize().background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(androidx.compose.ui.graphics.Color.Transparent, MaterialTheme.colorScheme.background),
                    ),
                ),
            )
        }
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Text("Selamat $sapa", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Ruang Putar Musik", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun BannerDitahan() {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.secondary)
            Text(
                "YouTube sementara menahan permintaan dari internetmu (dikira bot). Lagu di cache & Offline tetap bisa " +
                    "diputar. Biasanya pulih sendiri; matikan-nyalakan data / mode pesawat untuk IP baru.",
                Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun JudulRak(judul: String, onKlik: (() -> Unit)?, ekor: (@Composable () -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            judul, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f).let { if (onKlik != null) it.clickable(onClick = onKlik) else it },
        )
        ekor?.invoke()
    }
}

@Composable
private fun RakBeranda(r: Rak, vm: AppVM, onUbah: () -> Unit) {
    val isi = vm.isiRak[r.id]
    val pesan = vm.pesanRak[r.id]
    var menu by remember { mutableStateOf(false) }
    val pengaturan by vm.data.pengaturan.collectAsState()
    JudulRak(r.nama + "  ›", { vm.lihatSemuaRak(r) }) {
        val label = labelDurasi(r.minBerlaku(pengaturan), r.maksMenit)
        if (label.isNotEmpty()) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { vm.muatRak(r, paksa = true) }) { Icon(Icons.Filled.Refresh, "Muat ulang") }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Menu rak") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Ubah…") }, onClick = { menu = false; onUbah() })
                DropdownMenuItem(text = { Text("Naikkan") }, onClick = { menu = false; vm.data.geserRak(r.id, -1) })
                DropdownMenuItem(text = { Text("Turunkan") }, onClick = { menu = false; vm.data.geserRak(r.id, 1) })
                DropdownMenuItem(text = { Text("Hapus rak") }, onClick = { menu = false; vm.data.hapusRak(r.id) })
            }
        }
    }
    when {
        !isi.isNullOrEmpty() -> LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(isi) { i, l -> KartuLagu(l) { vm.putar(isi, i) } }
        }
        pesan != null -> Text(pesan, Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        else -> Text("Tidak ada lagu.", Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DialogRak(lama: Rak?, onBatal: () -> Unit, onSimpan: (String, String, Int, Int) -> Unit) {
    var nama by remember { mutableStateOf(lama?.nama ?: "") }
    var kueri by remember { mutableStateOf(lama?.kueri ?: "") }
    var maks by remember { mutableIntStateOf(lama?.maksMenit ?: 5) }
    var min by remember { mutableIntStateOf(lama?.minDetik ?: -1) }
    AlertDialog(
        onDismissRequest = onBatal,
        title = { Text(if (lama == null) "Tambah rak" else "Ubah rak") },
        text = {
            Column {
                OutlinedTextField(nama, { nama = it }, label = { Text("Nama rak") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(kueri, { kueri = it }, label = { Text("Kata kunci pencarian") }, singleLine = true)
                Spacer(Modifier.height(12.dp))
                Text("Durasi minimal", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PILIHAN_MIN_RAK.forEach { (d, t) -> Pilihan(t, min == d) { min = d } }
                }
                Text("Durasi maksimal", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to "Semua", 5 to "< 5 mnt", 10 to "< 10 mnt", 20 to "< 20 mnt", 60 to "< 1 jam").forEach { (m, t) ->
                        Pilihan(t, maks == m) { maks = m }
                    }
                }
                Text(
                    "“< 5 mnt” = lagu satuan, tanpa kompilasi / full album.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = kueri.isNotBlank(), onClick = { onSimpan(nama.ifBlank { kueri.take(30) }, kueri.trim(), maks, min) }) {
                Text("Simpan")
            }
        },
        dismissButton = { TextButton(onClick = onBatal) { Text("Batal") } },
    )
}

// ====================================================================== Cari
@Composable
fun LayarCari(vm: AppVM) {
    val fokus = LocalFocusManager.current
    val pengaturan by vm.data.pengaturan.collectAsState()
    val h = vm.hasilCari
    val daftarState = rememberLazyListState()
    LaunchedEffect(h, h?.indeks) { daftarState.scrollToItem(0) }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = vm.kueri,
            onValueChange = { vm.kueri = it },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            placeholder = { Text("Judul lagu, penyanyi, atau link YouTube") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (vm.kueri.isNotEmpty()) IconButton(onClick = { vm.kueri = "" }) { Icon(Icons.Filled.Clear, "Hapus") }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                fokus.clearFocus()
                vm.cari(vm.kueri)
            }),
        )
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pilihan("Lagu", pengaturan.filterCari == "lagu") { vm.ubahFilterCari("lagu") }
            Pilihan("Semua video", pengaturan.filterCari == "video") { vm.ubahFilterCari("video") }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Min.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PILIHAN_MIN.forEach { (d, t) -> Pilihan(t, pengaturan.minDetik == d) { vm.ubahMinDurasi(d) } }
        }
        if (h == null) {
            Kosong("Ketik judul lagu atau nama penyanyi, lalu tekan cari. Bisa juga tempel link YouTube.")
            return@Column
        }
        Text(
            vm.judulCari, Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp),
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        h.galat?.let { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.secondary) }
        if (h.memuat && h.kini.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        val daftar = h.kini
        LazyColumn(Modifier.weight(1f), state = daftarState) {
            if (daftar.isNotEmpty()) {
                item {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.putar(daftar) }) { Text("Putar semua") }
                        OutlinedButton(onClick = { daftar.forEach { vm.antre(it) } }) { Text("Tambah ke antrean") }
                    }
                }
            } else if (!h.memuat && h.galat == null) {
                item { Kosong("Tidak ada hasil.") }
            }
            itemsIndexed(daftar, key = { _, l -> l.id }) { i, l -> BarisLagu(l, vm, onKlik = { vm.putar(daftar, i) }) }
            item { TombolHalaman(h) }
        }
    }
}

// ====================================================================== Pustaka
@Composable
fun LayarPustaka(vm: AppVM) {
    var sub by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Text(
            "Pustaka", Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("Favorit", "Riwayat", "Offline", "Penyanyi").forEachIndexed { i, t -> Pilihan(t, sub == i) { sub = i } }
        }
        when (sub) {
            0 -> {
                val favorit by vm.data.favorit.collectAsState()
                DaftarLagu(vm, favorit, "Belum ada favorit. Ketuk ⋮ di sebuah lagu, lalu ♥ Favoritkan.")
            }
            1 -> {
                val riwayat by vm.data.riwayat.collectAsState()
                DaftarLagu(vm, riwayat, "Riwayat kosong.", ekstra = {
                    TextButton(onClick = { vm.data.hapusRiwayat() }) { Text("Hapus riwayat") }
                })
            }
            2 -> HalamanOffline(vm)
            else -> HalamanDaftarPenyanyi(vm)
        }
    }
}

@Composable
private fun DaftarLagu(vm: AppVM, daftar: List<Lagu>, kosong: String, ekstra: (@Composable () -> Unit)? = null) {
    LazyColumn(Modifier.fillMaxSize()) {
        if (daftar.isEmpty()) {
            item { Kosong(kosong) }
        } else {
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { vm.putar(daftar) }) { Text("Putar semua") }
                    OutlinedButton(onClick = { vm.putarAcak(daftar) }) { Text("Acak") }
                    ekstra?.invoke()
                }
            }
            itemsIndexed(daftar, key = { _, l -> l.kunci }) { i, l -> BarisLagu(l, vm, onKlik = { vm.putar(daftar, i) }) }
        }
    }
}

@Composable
private fun HalamanOffline(vm: AppVM) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val unduhan by vm.data.unduhan.collectAsState()
    var daftar by remember { mutableStateOf<List<Lagu>?>(null) }
    val izinNama = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    var punyaIzin by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, izinNama) == PackageManager.PERMISSION_GRANTED)
    }
    val minta = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { punyaIzin = it }
    fun muat() {
        scope.launch { daftar = withContext(Dispatchers.IO) { Unduhan.daftarOffline(ctx) } }
    }
    LaunchedEffect(punyaIzin, unduhan.size) { muat() }

    Column(Modifier.fillMaxSize()) {
        Text(
            "Lagu di folder Music/${Unduhan.FOLDER} — diputar tanpa internet.",
            Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!punyaIzin) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Izinkan membaca musik supaya semua file di folder itu tampil.", Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { minta.launch(izinNama) }) { Text("Izinkan") }
            }
        }
        val d = daftar
        when {
            d == null -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> DaftarLagu(vm, d, "Belum ada lagu offline. Ketuk ⋮ di sebuah lagu, lalu Unduh ke Offline.")
        }
    }
}

@Composable
private fun HalamanDaftarPenyanyi(vm: AppVM) {
    val penyanyi by vm.data.penyanyi.collectAsState()
    LazyColumn(Modifier.fillMaxSize()) {
        if (penyanyi.isEmpty()) {
            item { Kosong("Belum mengikuti penyanyi. Ketuk ⋮ di sebuah lagu, lalu Ikuti.") }
        }
        items(penyanyi, key = { it.url }) { p ->
            var menu by remember { mutableStateOf(false) }
            Row(
                Modifier.fillMaxWidth().clickable { vm.bukaPenyanyi(p.nama, p.url) }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) { KepalaPenyanyi(p.nama) }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Menu") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Berhenti mengikuti") }, onClick = { menu = false; vm.alihIkuti(p.nama, p.url) })
                    }
                }
            }
        }
    }
}

// ====================================================================== Penyanyi
@Composable
fun LayarPenyanyi(vm: AppVM, p: Penyanyi, h: Halaman) {
    BackHandler { vm.tutupPenyanyi() }
    val diikuti by vm.data.penyanyi.collectAsState()
    val ikut = diikuti.any { it.url == p.url }
    Column(Modifier.fillMaxSize()) {
        val semuaRak by vm.data.rak.collectAsState()
        val adaRak = semuaRak.any { it.channel == p.url }
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.tutupPenyanyi() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") }
            Box(Modifier.weight(1f)) { KepalaPenyanyi(p.nama.ifEmpty { "Penyanyi" }) }
        }
        Row(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (ikut) {
                OutlinedButton(onClick = { vm.alihIkuti(p.nama, p.url) }) { Text("✓ Diikuti") }
            } else {
                Button(onClick = { vm.alihIkuti(p.nama, p.url) }) { Text("Ikuti") }
            }
            if (adaRak) {
                OutlinedButton(onClick = { vm.alihRakPenyanyi(p) }) { Text("✓ Rak di Beranda") }
            } else {
                OutlinedButton(onClick = { vm.alihRakPenyanyi(p) }) { Text("+ Rak Beranda") }
            }
        }
        h.galat?.let { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.secondary) }
        if (h.memuat && h.kini.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        val daftar = h.kini
        LazyColumn(Modifier.weight(1f)) {
            if (daftar.isNotEmpty()) {
                item {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.putar(daftar) }) { Text("Putar semua") }
                        OutlinedButton(onClick = { vm.putarAcak(daftar) }) { Text("Acak") }
                    }
                }
            }
            itemsIndexed(daftar, key = { _, l -> l.id }) { i, l -> BarisLagu(l, vm, onKlik = { vm.putar(daftar, i) }) }
            item { TombolHalaman(h) }
        }
    }
}

// ====================================================================== Pengaturan
@OptIn(UnstableApi::class)
@Composable
fun LayarPengaturan(vm: AppVM) {
    val p by vm.data.pengaturan.collectAsState()
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Pengaturan", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        Bagian("Tampilan")
        BarisPilihan("Tema", listOf("batik" to "Batik", "gelap" to "Gelap", "terang" to "Terang", "otomatis" to "Ikut sistem"), p.tema) { v ->
            vm.data.ubahPengaturan { it.copy(tema = v) }
        }

        Bagian("Pencarian & Beranda")
        BarisPilihan(
            "Durasi minimal lagu", PILIHAN_MIN.map { (d, t) -> d.toString() to t }, p.minDetik.toString(),
            "Lagu yang lebih pendek disembunyikan dari hasil cari & rak (mis. potongan / klip pendek). Tiap rak bisa diatur sendiri.",
        ) { v -> vm.ubahMinDurasi(v.toInt()) }

        Bagian("Pemutaran")
        BarisPilihan("Kualitas suara", listOf("tinggi" to "Tinggi", "hemat" to "Hemat kuota"), p.kualitas,
            "Hemat kuota: ± 50–70 kbps, cukup jernih untuk HP & headset.") { v ->
            vm.data.ubahPengaturan { it.copy(kualitas = v) }
        }
        BarisPilihan(
            "Kecepatan unduh", listOf("rendah" to "Rendah ± 1 MB/s", "normal" to "Normal ± 3 MB/s", "tanpa" to "Tanpa batas"),
            p.kecepatan, "Berlaku untuk semua unduhan sekaligus (putar & Offline). Rendah = tidak mengganggu game.",
        ) { v -> vm.data.ubahPengaturan { it.copy(kecepatan = v) } }

        Bagian("Cache")
        BarisPilihan(
            "Batas cache", listOf("256" to "256 MB", "512" to "512 MB", "1024" to "1 GB", "2048" to "2 GB"), p.cacheMb.toString(),
            "Lagu yang pernah diputar disimpan di sini dan bisa diulang tanpa internet. Batas baru berlaku setelah aplikasi dibuka ulang.",
        ) { v -> vm.data.ubahPengaturan { it.copy(cacheMb = v.toInt()) } }
        OutlinedButton(onClick = {
            scope.launch(Dispatchers.IO) {
                val c = App.aku.cache
                c.keys.toList().forEach { k -> runCatching { c.removeResource(k) } }
                withContext(Dispatchers.Main) { vm.kabar("Cache dikosongkan") }
            }
        }) { Text("Kosongkan cache") }

        Bagian("Beranda")
        OutlinedButton(onClick = { vm.data.kembalikanRakBawaan(); vm.kabar("Rak Beranda dikembalikan ke bawaan") }) {
            Text("Kembalikan rak bawaan")
        }

        Bagian("Pembaruan")
        BagianPembaruan()

        Bagian("Tentang")
        Text("Ruang Putar Musik v${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.SemiBold)
        Text("Dibuat oleh @SuS_X_x", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "Untuk pemakaian pribadi. Mesin pengambil lagu: NewPipeExtractor (GPL-3.0).",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Bagian(judul: String) {
    Spacer(Modifier.height(20.dp))
    Text(judul.uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
    HorizontalDivider(Modifier.padding(top = 4.dp, bottom = 8.dp))
}

@Composable
private fun BarisPilihan(judul: String, pilihan: List<Pair<String, String>>, nilai: String, ket: String = "", ubah: (String) -> Unit) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(judul, fontWeight = FontWeight.SemiBold)
        if (ket.isNotEmpty()) {
            Text(ket, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            pilihan.forEach { (v, t) -> Pilihan(t, v == nilai) { ubah(v) } }
        }
    }
}

@Composable
fun Bulat(isi: @Composable () -> Unit) {
    Box(Modifier.size(40.dp).clip(MaterialTheme.shapes.extraLarge).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center) { isi() }
}


// ---------------------------------------------------------------------- durasi minimal
private val PILIHAN_MIN = listOf(0 to "Semua", 30 to "30 dtk", 60 to "1 mnt", 90 to "1,5 mnt", 120 to "2 mnt")
private val PILIHAN_MIN_RAK = listOf(-1 to "Ikut pengaturan") + PILIHAN_MIN

/** "1–5 mnt", "≥ 1 mnt", "≤ 5 mnt", atau kosong. */
private fun labelDurasi(minDetik: Int, maksMenit: Int): String {
    val min = when {
        minDetik <= 0 -> ""
        minDetik % 60 == 0 -> "${minDetik / 60}"
        minDetik < 60 -> "${minDetik} dtk"
        else -> "%.1f".format(minDetik / 60.0).replace('.', ',')
    }
    return when {
        min.isNotEmpty() && maksMenit > 0 -> "$min–$maksMenit mnt"
        min.isNotEmpty() -> if (min.endsWith("dtk")) "≥ $min" else "≥ $min mnt"
        maksMenit > 0 -> "≤ $maksMenit mnt"
        else -> ""
    }
}
