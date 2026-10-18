package id.ruangputar.musik.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import id.ruangputar.musik.inti.Unduhan
import id.ruangputar.musik.inti.formatWaktu

/** Bar kecil di atas menu bawah: lagu yang sedang diputar. */
@Composable
fun MiniPemutar(vm: AppVM) {
    val lagu = vm.laguKini ?: return
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().clickable { vm.pemutarTerbuka = true }) {
        Column {
            LinearProgressIndicator(
                progress = { if (vm.durasi > 0) (vm.posisi.toFloat() / vm.durasi).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth().height(2.dp),
            )
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Sampul(lagu.sampul, 44.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(lagu.judul, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text(
                        vm.galatPutar ?: lagu.artis, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TombolPutar(vm, besar = false)
                IconButton(onClick = { vm.berikutnya() }) { Icon(Ikon.Berikut, "Berikutnya") }
            }
        }
    }
}

@Composable
private fun TombolPutar(vm: AppVM, besar: Boolean) {
    val isi: @Composable () -> Unit = {
        when {
            vm.memuatLagu && !vm.main -> CircularProgressIndicator(Modifier.size(if (besar) 28.dp else 20.dp), strokeWidth = 2.dp)
            vm.main -> Icon(Ikon.Jeda, "Jeda", Modifier.size(if (besar) 36.dp else 24.dp))
            else -> Icon(Icons.Filled.PlayArrow, "Putar", Modifier.size(if (besar) 36.dp else 24.dp))
        }
    }
    if (besar) {
        FilledIconButton(onClick = { vm.putarJeda() }, modifier = Modifier.size(72.dp)) { isi() }
    } else {
        IconButton(onClick = { vm.putarJeda() }) { isi() }
    }
}

/** Pemutar layar penuh: sampul besar, geser waktu, kontrol, favorit, unduh, timer, antrean. */
@Composable
fun PemutarPenuh(vm: AppVM) {
    val lagu = vm.laguKini ?: return
    val ctx = LocalContext.current
    var lihatAntrean by remember { mutableStateOf(false) }
    BackHandler { if (lihatAntrean) lihatAntrean = false else vm.pemutarTerbuka = false }
    val favorit by vm.data.favorit.collectAsState()
    val unduhan by vm.data.unduhan.collectAsState()
    val proses by Unduhan.proses.collectAsState()
    val fav = favorit.any { it.kunci == lagu.kunci }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.pemutarTerbuka = false }) { Icon(Icons.Filled.KeyboardArrowDown, "Tutup") }
                Text(
                    if (lihatAntrean) "Antrean (${vm.antrean.size})" else "Sedang diputar",
                    Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleSmall,
                )
                IconButton(onClick = { lihatAntrean = !lihatAntrean }) {
                    Icon(Ikon.Antrean, "Antrean", tint = if (lihatAntrean) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                }
            }
            if (lihatAntrean) {
                DaftarAntrean(vm)
                return@Column
            }

            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 28.dp), contentAlignment = Alignment.Center) {
                val sisi = if (maxWidth < maxHeight) maxWidth else maxHeight
                Sampul(lagu.sampul, sisi, RoundedCornerShape(16.dp))
            }
            Column(Modifier.padding(horizontal = 28.dp)) {
                Spacer(Modifier.height(20.dp))
                Text(lagu.judul, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
                Text(
                    lagu.artis, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(enabled = lagu.channel.isNotEmpty()) { vm.bukaPenyanyi(lagu.artis, lagu.channel) },
                )
                vm.galatPutar?.let {
                    Text(it, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 6.dp))
                }

                var seret by remember { mutableStateOf<Float?>(null) }
                val durasi = vm.durasi.coerceAtLeast(1L).toFloat()
                Slider(
                    value = (seret ?: vm.posisi.toFloat()).coerceIn(0f, durasi),
                    onValueChange = { seret = it },
                    onValueChangeFinished = {
                        seret?.let { vm.geser(it.toLong()) }
                        seret = null
                    },
                    valueRange = 0f..durasi,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Row(Modifier.fillMaxWidth()) {
                    Text(formatWaktu(((seret ?: vm.posisi.toFloat()) / 1000).toLong()), style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.weight(1f))
                    Text(formatWaktu(vm.durasi / 1000), style = MaterialTheme.typography.labelSmall)
                }

                Row(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val aktif = MaterialTheme.colorScheme.primary
                    val biasa = MaterialTheme.colorScheme.onSurfaceVariant
                    IconButton(onClick = { vm.alihAcak() }) { Icon(Ikon.Acak, "Acak", tint = if (vm.acak) aktif else biasa) }
                    IconButton(onClick = { vm.sebelumnya() }, Modifier.size(56.dp)) { Icon(Ikon.Sebelum, "Sebelumnya", Modifier.size(32.dp)) }
                    TombolPutar(vm, besar = true)
                    IconButton(onClick = { vm.berikutnya() }, Modifier.size(56.dp)) { Icon(Ikon.Berikut, "Berikutnya", Modifier.size(32.dp)) }
                    IconButton(onClick = { vm.alihUlang() }) {
                        Icon(
                            if (vm.ulang == Player.REPEAT_MODE_ONE) Ikon.UlangSatu else Ikon.Ulang, "Ulang",
                            tint = if (vm.ulang == Player.REPEAT_MODE_OFF) biasa else aktif,
                        )
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { vm.alihFavorit(lagu) }) {
                        Icon(if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Favorit",
                            tint = if (fav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    }
                    if (!lagu.lokal) {
                        val sedang = proses[lagu.id]
                        IconButton(onClick = { vm.unduh(ctx, lagu) }, enabled = sedang == null) {
                            when {
                                sedang != null -> CircularProgressIndicator(progress = { sedang / 100f }, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                                unduhan.containsKey(lagu.id) -> Icon(Ikon.Terunduh, "Sudah diunduh", tint = MaterialTheme.colorScheme.primary)
                                else -> Icon(Ikon.Unduh, "Unduh")
                            }
                        }
                    }
                    TombolTimer(vm)
                }
            }
        }
    }
}

@Composable
private fun TombolTimer(vm: AppVM) {
    var menu by remember { mutableStateOf(false) }
    var sisa by remember { mutableStateOf("") }
    LaunchedEffect(vm.timerSampai) {
        while (vm.timerSampai > 0) {
            val m = ((vm.timerSampai - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0) + 1
            sisa = "$m mnt"
            kotlinx.coroutines.delay(15_000)
        }
        sisa = ""
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { menu = true }) {
            Icon(Ikon.Timer, "Timer tidur", tint = if (vm.timerSampai > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        }
        if (sisa.isNotEmpty()) Text(sisa, style = MaterialTheme.typography.labelSmall)
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            listOf(15, 30, 45, 60, 90).forEach { m ->
                DropdownMenuItem(text = { Text("Berhenti dalam $m menit") }, onClick = { menu = false; vm.setelTimer(m) })
            }
            if (vm.timerSampai > 0) {
                DropdownMenuItem(text = { Text("Matikan timer") }, onClick = { menu = false; vm.setelTimer(0) })
            }
        }
    }
}

@Composable
private fun DaftarAntrean(vm: AppVM) {
    val state = rememberLazyListState()
    LaunchedEffect(Unit) { if (vm.indeksKini > 0) state.scrollToItem(vm.indeksKini) }
    LazyColumn(Modifier.fillMaxSize(), state = state) {
        itemsIndexed(vm.antrean) { i, l ->
            val aktif = i == vm.indeksKini
            Row(
                Modifier.fillMaxWidth()
                    .background(if (aktif) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.background)
                    .clickable { vm.putarDiAntrean(i) }
                    .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Sampul(l.sampul, 40.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(l.judul, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold,
                        color = if (aktif) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    Text(l.artis, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { vm.geserDiAntrean(i, i - 1) }, enabled = i > 0, colors = IconButtonDefaults.iconButtonColors()) {
                    Icon(Icons.Filled.KeyboardArrowUp, "Naikkan")
                }
                IconButton(onClick = { vm.geserDiAntrean(i, i + 1) }, enabled = i < vm.antrean.size - 1) {
                    Icon(Icons.Filled.KeyboardArrowDown, "Turunkan")
                }
                IconButton(onClick = { vm.hapusDiAntrean(i) }) { Icon(Icons.Filled.Clear, "Hapus dari antrean") }
            }
        }
    }
}
