package id.ruangputar.musik.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import id.ruangputar.musik.inti.AntreanUnduh
import id.ruangputar.musik.inti.Lagu
import id.ruangputar.musik.inti.Playlist
import id.ruangputar.musik.inti.formatWaktu

// ====================================================================== bantuan
/** (sudah offline, jumlah lagu). Lagu file di HP dihitung offline. */
fun hitungOffline(p: Playlist, unduhan: Map<String, String>): Pair<Int, Int> =
    p.lagu.count { it.lokal || unduhan.containsKey(it.id) } to p.lagu.size

/** "✅ Offline penuh", "⬇ 12/50 offline", atau kosong (online). */
fun labelOffline(p: Playlist, unduhan: Map<String, String>): String {
    val (ada, total) = hitungOffline(p, unduhan)
    return when {
        total == 0 -> ""
        ada == total -> "✅ Offline penuh"
        ada > 0 -> "⬇ $ada/$total offline"
        else -> ""
    }
}

fun teksDurasiTotal(detik: Long): String = when {
    detik <= 0 -> ""
    detik >= 3600 -> "${detik / 3600} jam ${(detik % 3600) / 60} mnt"
    else -> "${(detik + 30) / 60} mnt"
}

/** Sampul playlist: 4 sampul lagu pertama (2×2), atau 1 sampul, atau ikon. */
@Composable
fun SampulPlaylist(p: Playlist, ukuran: Dp) {
    val sampul = p.lagu.map { it.sampul }.filter { it.isNotEmpty() }.distinct()
    if (sampul.size >= 4) {
        val kecil = ukuran / 2
        Column(Modifier.size(ukuran).clip(RoundedCornerShape(8.dp))) {
            Row {
                Sampul(sampul[0], kecil, RoundedCornerShape(0.dp))
                Sampul(sampul[1], kecil, RoundedCornerShape(0.dp))
            }
            Row {
                Sampul(sampul[2], kecil, RoundedCornerShape(0.dp))
                Sampul(sampul[3], kecil, RoundedCornerShape(0.dp))
            }
        }
    } else {
        Sampul(sampul.firstOrNull().orEmpty(), ukuran)
    }
}

// ====================================================================== daftar playlist (tab Pustaka)
@Composable
fun DaftarPlaylist(vm: AppVM) {
    val semua by vm.data.playlist.collectAsState()
    val unduhan by vm.data.unduhan.collectAsState()
    var buatBaru by remember { mutableStateOf(false) }
    var impor by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { buatBaru = true }) { Text("+ Playlist baru") }
                OutlinedButton(onClick = { impor = true }) { Text("Impor link YouTube") }
            }
        }
        if (semua.isEmpty()) {
            item {
                Kosong(
                    "Belum ada playlist. Buat playlist baru, lalu ketuk ⋮ di lagu mana saja → Tambah ke playlist.\n" +
                        "Bisa juga tempel / bagikan link playlist YouTube ke Ruang Putar.",
                )
            }
        }
        items(semua, key = { it.id }) { p ->
            Row(
                Modifier.fillMaxWidth().clickable { vm.bukaPlaylist(p.id) }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SampulPlaylist(p, 56.dp)
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text(p.nama, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val label = labelOffline(p, unduhan)
                    Text(
                        "${p.lagu.size} lagu" + (if (label.isNotEmpty()) " · $label" else "") + (if (p.sumber.isNotEmpty()) " · dari YouTube" else ""),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { vm.putar(p.lagu) }, enabled = p.lagu.isNotEmpty()) {
                    Icon(Icons.Filled.PlayArrow, "Putar")
                }
            }
        }
    }
    if (buatBaru) {
        DialogNama("Playlist baru", "", "Buat", tutup = { buatBaru = false }) { nama ->
            buatBaru = false
            vm.bukaPlaylist(vm.buatPlaylist(nama).id)
        }
    }
    if (impor) {
        DialogNama("Impor playlist YouTube", "", "Buka", label = "Link playlist YouTube", tutup = { impor = false }) { link ->
            impor = false
            vm.bukaTautan(link)
        }
    }
}

// ====================================================================== isi satu playlist
@Composable
fun LayarPlaylist(vm: AppVM, id: String) {
    val semua by vm.data.playlist.collectAsState()
    val unduhan by vm.data.unduhan.collectAsState()
    val p = semua.firstOrNull { it.id == id }
    if (p == null) {
        vm.tutupPlaylist()
        return
    }
    BackHandler { vm.tutupPlaylist() }
    val uri = LocalUriHandler.current
    var menu by remember { mutableStateOf(false) }
    var gantiNama by remember { mutableStateOf(false) }
    var tanyaHapus by remember { mutableStateOf(false) }
    var tanyaHapusUnduhan by remember { mutableStateOf(false) }
    var tanyaFile by remember { mutableStateOf<Lagu?>(null) }
    val (ada, total) = hitungOffline(p, unduhan)
    val label = labelOffline(p, unduhan)

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.tutupPlaylist() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") }
            SampulPlaylist(p, 72.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text("PLAYLIST", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(p.nama, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf("${p.lagu.size} lagu", teksDurasiTotal(p.totalDetik), label.ifEmpty { "Online" }).filter { it.isNotEmpty() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Menu playlist") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Ganti nama…") }, onClick = { menu = false; gantiNama = true })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("Urutkan: Judul A–Z") }, onClick = {
                        menu = false; vm.data.aturLaguPlaylist(p.id, p.lagu.sortedBy { it.judul.lowercase() })
                    })
                    DropdownMenuItem(text = { Text("Urutkan: Penyanyi A–Z") }, onClick = {
                        menu = false; vm.data.aturLaguPlaylist(p.id, p.lagu.sortedBy { it.artis.lowercase() })
                    })
                    DropdownMenuItem(text = { Text("Urutkan: Balik urutan") }, onClick = {
                        menu = false; vm.data.aturLaguPlaylist(p.id, p.lagu.reversed())
                    })
                    HorizontalDivider()
                    if (ada > 0 && p.lagu.any { !it.lokal && unduhan.containsKey(it.id) }) {
                        DropdownMenuItem(text = { Text("Hapus unduhan playlist ini…") }, onClick = { menu = false; tanyaHapusUnduhan = true })
                    }
                    if (p.sumber.isNotEmpty()) {
                        DropdownMenuItem(text = { Text("Buka playlist asli di YouTube") }, onClick = { menu = false; runCatching { uri.openUri(p.sumber) } })
                    }
                    DropdownMenuItem(text = { Text("Hapus playlist…") }, onClick = { menu = false; tanyaHapus = true })
                }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { vm.putar(p.lagu) }, enabled = p.lagu.isNotEmpty()) { Text("Putar") }
            OutlinedButton(onClick = { vm.putarAcak(p.lagu) }, enabled = p.lagu.isNotEmpty()) { Text("Acak") }
            val belum = total - ada
            when {
                total == 0 -> Unit
                belum == 0 -> Text("✅ Offline penuh", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                ada == 0 -> OutlinedButton(onClick = { vm.unduhPlaylist = p.id }) { Text("⬇ Unduh semua") }
                else -> OutlinedButton(onClick = { vm.unduhPlaylist = p.id }) { Text("⬇ Unduh yang belum ($belum)") }
            }
        }
        if (p.lagu.isEmpty()) {
            Kosong("Playlist masih kosong. Ketuk ⋮ di lagu mana saja (hasil cari, Riwayat, pemutar…) → Tambah ke playlist.")
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(p.lagu, key = { _, l -> l.kunci }) { i, l ->
                val aksiHapus: () -> Unit = {
                    if (vm.perluTanyaFile(p.id, l)) {
                        tanyaFile = l
                    } else {
                        vm.keluarkanDariPlaylist(p.id, l, hapusFile = false)
                    }
                }
                Row(Modifier.padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        BarisLagu(
                            l, vm, onKlik = { vm.putar(p.lagu, i) }, nomor = i + 1,
                            menuTambahan = listOf("Hapus dari playlist ini" to aksiHapus),
                        )
                    }
                    IconButton(onClick = { vm.data.geserDiPlaylist(p.id, i, i - 1) }, enabled = i > 0, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.KeyboardArrowUp, "Naikkan")
                    }
                    IconButton(onClick = { vm.data.geserDiPlaylist(p.id, i, i + 1) }, enabled = i < p.lagu.size - 1, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.KeyboardArrowDown, "Turunkan")
                    }
                }
            }
        }
    }

    if (gantiNama) {
        DialogNama("Ganti nama playlist", p.nama, "Simpan", tutup = { gantiNama = false }) { nama ->
            gantiNama = false
            vm.data.gantiNamaPlaylist(p.id, nama)
        }
    }
    if (tanyaHapus) {
        AlertDialog(
            onDismissRequest = { tanyaHapus = false },
            title = { Text("Hapus playlist?") },
            text = { Text("“${p.nama}” akan dihapus. Lagu-lagunya tidak hilang dari Favorit / Offline, dan file yang sudah diunduh tetap ada.") },
            confirmButton = { TextButton(onClick = { tanyaHapus = false; vm.hapusPlaylist(p.id) }) { Text("Hapus") } },
            dismissButton = { TextButton(onClick = { tanyaHapus = false }) { Text("Batal") } },
        )
    }
    if (tanyaHapusUnduhan) {
        AlertDialog(
            onDismissRequest = { tanyaHapusUnduhan = false },
            title = { Text("Hapus unduhan playlist ini?") },
            text = {
                Text(
                    "File lagu playlist ini dihapus dari HP untuk menghemat ruang. Playlist tetap ada dan kembali diputar online.\n\n" +
                        "Lagu yang juga ada di playlist offline lain tidak dihapus.",
                )
            },
            confirmButton = { TextButton(onClick = { tanyaHapusUnduhan = false; vm.hapusUnduhanPlaylist(p.id) }) { Text("Hapus file") } },
            dismissButton = { TextButton(onClick = { tanyaHapusUnduhan = false }) { Text("Batal") } },
        )
    }
    tanyaFile?.let { l ->
        val lain = vm.data.playlist.value.filter { q -> q.id != p.id && q.lagu.any { it.kunci == l.kunci } }.map { it.nama }
        AlertDialog(
            onDismissRequest = { tanyaFile = null },
            title = { Text("Hapus juga file unduhannya?") },
            text = {
                Text(
                    "“${l.judul}” dikeluarkan dari ${p.nama}." +
                        if (lain.isNotEmpty()) "\n\nLagu ini masih ada di ${lain.joinToString(", ")} — kalau file dihapus, di sana lagu ini diputar online." else "",
                )
            },
            confirmButton = { TextButton(onClick = { tanyaFile = null; vm.keluarkanDariPlaylist(p.id, l, hapusFile = true) }) { Text("Hapus file") } },
            dismissButton = { TextButton(onClick = { tanyaFile = null; vm.keluarkanDariPlaylist(p.id, l, hapusFile = false) }) { Text("Simpan file") } },
        )
    }
}

// ====================================================================== playlist YouTube (dari link)
@Composable
fun LayarPlaylistYt(vm: AppVM, h: HalamanPlaylistYt) {
    BackHandler { vm.tutupPlaylistYt() }
    val info = h.info
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.tutupPlaylistYt() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") }
            Sampul(info?.sampul.orEmpty(), 72.dp)
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text("PLAYLIST YOUTUBE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(info?.nama ?: "Membuka playlist…", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (info != null) {
                    Text(
                        listOf(info.pemilik, if (info.jumlah > 0) "${info.jumlah} lagu" else "").filter { it.isNotEmpty() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        h.galat?.let { Text(it, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.secondary) }
        if (info != null && h.lagu.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { vm.putarPlaylistYt(h) }) { Text("▶ Putar") }
                OutlinedButton(onClick = { vm.putarPlaylistYt(h, acak = true) }) { Text("Acak") }
                OutlinedButton(onClick = { vm.imporPlaylistYt(h) }, enabled = h.impor < 0) {
                    Text(if (h.impor >= 0) "Mengimpor… ${h.impor} lagu" else "⬇ Simpan ke Playlistku")
                }
            }
            Text(
                if (h.impor >= 0) "Mengambil semua lagu sedikit demi sedikit (maks. ${AppVM.BATAS_IMPOR}) supaya tidak dikira bot. Tunggu sebentar…"
                else "Putar = lagu diambil bertahap ± 100 sekaligus, tanpa diunduh. Simpan = jadi playlist milikmu sendiri (bisa diunduh untuk offline).",
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (h.impor >= 0) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
        }
        if (h.memuat && h.lagu.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(h.lagu, key = { _, l -> l.id }) { i, l ->
                Box(Modifier.padding(horizontal = 12.dp)) { BarisLagu(l, vm, onKlik = { vm.putarPlaylistYt(h, i) }, nomor = i + 1) }
            }
            if (h.adaBerikut) {
                item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        OutlinedButton(onClick = { vm.muatLagiPlaylistYt(h) }, enabled = !h.memuat) {
                            Text(if (h.memuat) "Memuat…" else "Muat lebih banyak")
                        }
                    }
                }
            }
        }
    }
}

// ====================================================================== jendela: tambah ke playlist (centang)
@Composable
fun DialogTambahKePlaylist(vm: AppVM) {
    val lagu = vm.tambahKePlaylist ?: return
    val semua by vm.data.playlist.collectAsState()
    val satu = lagu.size == 1
    // centang awal: playlist yang sudah berisi lagu ini (kalau satu lagu)
    val awal = remember(lagu) {
        semua.filter { p -> satu && p.lagu.any { it.kunci == lagu[0].kunci } }.map { it.id }.toSet()
    }
    val centang = remember(lagu) { mutableStateMapOf<String, Boolean>().apply { awal.forEach { put(it, true) } } }
    var namaBaru by remember(lagu) { mutableStateOf("") }
    var buat by remember(lagu) { mutableStateOf(false) }
    val tutup = { vm.tambahKePlaylist = null }

    AlertDialog(
        onDismissRequest = tutup,
        title = { Text(if (satu) "Tambah ke playlist" else "Tambah ${lagu.size} lagu ke playlist") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                if (satu) {
                    Text(lagu[0].judul, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                }
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(semua, key = { it.id }) { p ->
                        val dicentang = centang[p.id] == true
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable { centang[p.id] = !dicentang }.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(dicentang, { centang[p.id] = it })
                            Text((if (p.offline) "✅ " else "") + p.nama, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${p.lagu.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (semua.isEmpty()) {
                    Text("Belum ada playlist. Buat yang pertama di bawah.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                if (buat || semua.isEmpty()) {
                    OutlinedTextField(namaBaru, { namaBaru = it }, label = { Text("Nama playlist baru") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                } else {
                    TextButton(onClick = { buat = true }) { Text("+ Playlist baru…") }
                }
                if (semua.any { it.offline && centang[it.id] == true && it.id !in awal }) {
                    Text(
                        "✅ = playlist offline: lagu langsung diunduh (dengan jeda supaya YouTube tidak menganggap bot).",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                tutup()
                if (namaBaru.isNotBlank()) {
                    val p = vm.buatPlaylist(namaBaru)
                    centang[p.id] = true
                }
                vm.data.playlist.value.forEach { p ->
                    val kini = centang[p.id] == true
                    val dulu = p.id in awal
                    if (kini && !dulu) vm.masukkanKePlaylist(p.id, lagu)
                    if (!kini && dulu && satu) {
                        // keluar dari playlist; file unduhan disimpan (bisa dihapus dari halaman playlist)
                        vm.keluarkanDariPlaylist(p.id, lagu[0], hapusFile = false)
                        vm.kabar("Dikeluarkan dari ${p.nama}")
                    }
                }
            }) { Text("Selesai") }
        },
        dismissButton = { TextButton(onClick = tutup) { Text("Batal") } },
    )
}

// ====================================================================== jendela: unduh playlist
@Composable
fun DialogUnduhPlaylist(vm: AppVM) {
    val id = vm.unduhPlaylist ?: return
    val semua by vm.data.playlist.collectAsState()
    val unduhan by vm.data.unduhan.collectAsState()
    val pengaturan by vm.data.pengaturan.collectAsState()
    val p = semua.firstOrNull { it.id == id } ?: run { vm.unduhPlaylist = null; return }
    val belum = p.lagu.filter { !it.lokal && it.id.isNotEmpty() && !unduhan.containsKey(it.id) }
    val sudah = p.lagu.size - belum.size
    var pilihan by remember(id) { mutableIntStateOf(0) }          // 0 semua, 1 = 100 pertama, 2 = rentang
    var dari by remember(id) { mutableStateOf("1") }
    var sampai by remember(id) { mutableStateOf(minOf(50, p.lagu.size).toString()) }
    val daftar = when (pilihan) {
        1 -> belum.take(100)
        2 -> if (p.lagu.isEmpty()) emptyList() else {
            val a = (dari.toIntOrNull() ?: 1).coerceIn(1, maxOf(1, p.lagu.size))
            val b = (sampai.toIntOrNull() ?: p.lagu.size).coerceIn(a, maxOf(a, p.lagu.size))
            p.lagu.subList(a - 1, b).filter { l -> belum.any { it.id == l.id } }
        }
        else -> belum
    }
    val (ukuran, detik) = AntreanUnduh.perkiraan(daftar)
    val tutup = { vm.unduhPlaylist = null }

    AlertDialog(
        onDismissRequest = tutup,
        title = { Text("Unduh playlist “${p.nama}”") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text("Isi playlist: ${p.lagu.size} lagu")
                if (sudah > 0) Text("Sudah offline: $sudah lagu (dilewati)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Text("Unduh:", fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { pilihan = 0 }) {
                    RadioButton(pilihan == 0, { pilihan = 0 }); Text("Semua (${belum.size} lagu) — playlist jadi offline")
                }
                if (belum.size > 100) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { pilihan = 1 }) {
                        RadioButton(pilihan == 1, { pilihan = 1 }); Text("100 lagu pertama yang belum")
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(pilihan == 2, { pilihan = 2 }); Text("Lagu ke ")
                    OutlinedTextField(dari, { dari = it.filter(Char::isDigit).take(4); pilihan = 2 }, singleLine = true, modifier = Modifier.width(72.dp))
                    Text(" sampai ")
                    OutlinedTextField(sampai, { sampai = it.filter(Char::isDigit).take(4); pilihan = 2 }, singleLine = true, modifier = Modifier.width(72.dp))
                }
                Spacer(Modifier.height(10.dp))
                Text("Kecepatan:", fontWeight = FontWeight.SemiBold)
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("rendah" to "Rendah", "normal" to "Normal", "tanpa" to "Tanpa batas").forEach { (v, t) ->
                        Pilihan(t, pengaturan.kecepatan == v) { vm.data.ubahPengaturan { it.copy(kecepatan = v) } }
                    }
                }
                if (pengaturan.kecepatan == "tanpa") {
                    Text(
                        "⚠ Tanpa batas: paling cepat, tapi bisa membuat internet lag (game / video call) dan lebih cepat dicurigai YouTube.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text("Akan diunduh: ${daftar.size} lagu")
                        Text("Perkiraan ukuran: ${AntreanUnduh.teksUkuran(ukuran)}")
                        Text("Perkiraan waktu: ${AntreanUnduh.teksWaktu(detik)}")
                        Text(
                            "ℹ Ada jeda 3–8 detik antar lagu dan istirahat ± 2 menit setiap 20 lagu supaya YouTube tidak menganggap ini bot. " +
                                "Jeda ini normal, bukan error — dan sudah dihitung di perkiraan waktu. Musik tetap bisa diputar selama mengunduh.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = daftar.isNotEmpty(), onClick = {
                tutup()
                vm.mulaiUnduhPlaylist(p.id, daftar, semua = pilihan == 0)
            }) { Text("Mulai unduh") }
        },
        dismissButton = { TextButton(onClick = tutup) { Text("Batal") } },
    )
}

// ====================================================================== jendela: isi nama
@Composable
fun DialogNama(judul: String, awal: String, tombol: String, label: String = "Nama playlist", tutup: () -> Unit, simpan: (String) -> Unit) {
    var teks by remember { mutableStateOf(awal) }
    AlertDialog(
        onDismissRequest = tutup,
        title = { Text(judul) },
        text = {
            OutlinedTextField(teks, { teks = it }, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = { TextButton(enabled = teks.isNotBlank(), onClick = { simpan(teks.trim()) }) { Text(tombol) } },
        dismissButton = { TextButton(onClick = tutup) { Text("Batal") } },
    )
}

// ====================================================================== panel unduhan (di atas bar pemutar)
@Composable
fun PanelUnduhan(vm: AppVM) {
    val info by AntreanUnduh.info.collectAsState()
    val proses by id.ruangputar.musik.inti.Unduhan.proses.collectAsState()
    if (!info.tampil) return
    // jam berdetak untuk hitung mundur istirahat
    var kini by remember { mutableStateOf(System.currentTimeMillis()) }
    androidx.compose.runtime.LaunchedEffect(info.keadaan) {
        while (true) {
            kini = System.currentTimeMillis()
            kotlinx.coroutines.delay(500)
        }
    }
    val k = info.keadaan
    val selesaiSemua = !info.berjalan && k != AntreanUnduh.Keadaan.Tertunda
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
            Column(Modifier.fillMaxWidth()) {
                when {
                    k == AntreanUnduh.Keadaan.Tertunda -> {
                        Text("⬇ Ada ${info.antre.size} lagu yang belum selesai diunduh dari sebelumnya. Lanjutkan?", fontWeight = FontWeight.SemiBold)
                    }
                    selesaiSemua -> {
                        Text(
                            "✓ Unduhan selesai: ${info.selesai} lagu" + if (info.gagal.isNotEmpty()) " · ${info.gagal.size} gagal" else "",
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (info.gagal.isNotEmpty()) {
                            Text(
                                "Gagal: " + info.gagal.take(3).joinToString(", ") { it.lagu.judul } + if (info.gagal.size > 3) ", …" else "",
                                style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    else -> {
                        val nomor = (info.selesai + info.gagal.size + (if (info.kini != null) 1 else 0)).coerceAtMost(info.total)
                        val sisa = AntreanUnduh.perkiraan(info.antre.map { it.lagu }).second + when (k) {
                            is AntreanUnduh.Keadaan.Istirahat -> ((k.sampai - kini) / 1000).coerceAtLeast(0L)
                            else -> 0L
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "⬇ Mengunduh $nomor / ${info.total}" + if (info.grup.isNotEmpty()) " · ${info.grup}" else "",
                                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                            )
                            Text("  sisa ${AntreanUnduh.teksWaktu(sisa)}", style = MaterialTheme.typography.bodySmall)
                        }
                        LinearProgressIndicator(
                            progress = { if (info.total > 0) (info.selesai + info.gagal.size).toFloat() / info.total else 0f },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        )
                        val ket = when (k) {
                            is AntreanUnduh.Keadaan.Istirahat -> {
                                val dtk = ((k.sampai - kini) / 1000).coerceAtLeast(0)
                                "⏸ ${k.alasan} ${formatWaktu(dtk)} — supaya YouTube tidak menganggap bot. Ini normal, bukan error."
                            }
                            is AntreanUnduh.Keadaan.Ditahan -> {
                                val dtk = ((k.sampai - kini) / 1000).coerceAtLeast(0)
                                "⏸ YouTube sedang menahan permintaan. Lanjut otomatis dalam ${formatWaktu(dtk)}."
                            }
                            AntreanUnduh.Keadaan.Dijeda -> "⏸ Dijeda"
                            else -> info.kini?.let { item -> "Sekarang: ${item.lagu.judul} — ${item.lagu.artis}  ${proses[item.lagu.id] ?: 0}%" } ?: "Menyiapkan…"
                        }
                        Text(ket, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            when {
                k == AntreanUnduh.Keadaan.Tertunda -> {
                    Button(onClick = { AntreanUnduh.lanjutkan() }) { Text("Lanjutkan") }
                    TextButton(onClick = { AntreanUnduh.batalkan() }) { Text("Batalkan") }
                }
                selesaiSemua -> {
                    if (info.gagal.isNotEmpty()) OutlinedButton(onClick = { AntreanUnduh.cobaLagiGagal() }) { Text("Coba lagi ${info.gagal.size} lagu") }
                    TextButton(onClick = { AntreanUnduh.tutupRingkasan() }) { Text("Tutup") }
                }
                else -> {
                    if (k == AntreanUnduh.Keadaan.Dijeda) {
                        OutlinedButton(onClick = { AntreanUnduh.lanjutkan() }) { Text("Lanjutkan") }
                    } else {
                        OutlinedButton(onClick = { AntreanUnduh.jeda() }) { Text("Jeda") }
                    }
                    TextButton(onClick = { AntreanUnduh.batalkan() }) { Text("Batalkan") }
                }
            }
            }
        }
    }
}

