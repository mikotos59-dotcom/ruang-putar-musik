package id.ruangputar.musik.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import id.ruangputar.musik.inti.Jaringan
import id.ruangputar.musik.inti.Lagu
import id.ruangputar.musik.inti.Unduhan
import id.ruangputar.musik.inti.formatWaktu
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Pemuat gambar kecil (tanpa pustaka tambahan): unduh, perkecil, simpan di memori. */
object PemuatGambar {
    private val simpanan = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    private val ytimg = Regex("""^https?://i\d?\.ytimg\.com/vi(?:_webp)?/([\w-]{11})/""")
    private val ukuranGoogle = Regex("""=(w\d+-h\d+|s\d+)[^/]*$""")

    /**
     * Versi gambar yang lebih tajam. Sampul YouTube: besar = 1280×720, kecil (daftar) = 320×180;
     * keduanya tanpa garis hitam. Sampul YouTube Music: 544×544.
     */
    private fun calon(url: String, besar: Boolean): List<String> {
        ytimg.find(url)?.let { m ->
            val id = m.groupValues[1]
            val pilih = if (besar) "https://i.ytimg.com/vi/$id/hq720.jpg" else "https://i.ytimg.com/vi/$id/mqdefault.jpg"
            return listOf(pilih, url).distinct()
        }
        if ("googleusercontent.com" in url || "ggpht.com" in url) {
            val ukuran = if (besar) "=w544-h544-l90-rj" else "=w226-h226-l90-rj"
            return listOf(ukuranGoogle.replace(url, ukuran), url).distinct()
        }
        return listOf(url)
    }

    private fun unduh(url: String): ByteArray? {
        if (url.startsWith("file:")) return java.io.File(android.net.Uri.parse(url).path ?: return null).takeIf { it.exists() }?.readBytes()
        val req = okhttp3.Request.Builder().url(url).header("User-Agent", Jaringan.AGEN).build()
        return Jaringan.api.newCall(req).execute().use { r -> if (r.isSuccessful) r.body?.bytes() else null }
    }

    /** Gambar versi paling tajam yang tersedia (dipakai saat menyimpan sampul lagu offline). */
    fun unduhTerbaik(url: String, besar: Boolean): ByteArray? =
        calon(url, besar).firstNotNullOfOrNull { runCatching { unduh(it) }.getOrNull() }

    suspend fun ambil(url: String, targetPx: Int): ImageBitmap? {
        if (url.isEmpty()) return null
        val besar = targetPx > 400
        val kunci = "$url|${if (besar) "b" else "k"}"
        simpanan.get(kunci)?.let { return it.asImageBitmap() }
        return withContext(Dispatchers.IO) {
            try {
                val bytes = unduhTerbaik(url, besar) ?: return@withContext null
                val batas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, batas)
                var skala = 1
                while (batas.outWidth / (skala * 2) >= targetPx && batas.outHeight / (skala * 2) >= targetPx) skala *= 2
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = skala })
                    ?: return@withContext null
                simpanan.put(kunci, bmp)
                bmp.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }
}

@Composable
fun Sampul(url: String, ukuran: Dp, bentuk: Shape = RoundedCornerShape(8.dp), modifier: Modifier = Modifier) {
    val px = with(LocalDensity.current) { ukuran.roundToPx() }
    val gambar by produceState<ImageBitmap?>(null, url, px) { value = PemuatGambar.ambil(url, px) }
    Box(
        modifier.size(ukuran).clip(bentuk).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val g = gambar
        if (g != null) {
            Image(g, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.High)
        } else {
            Icon(Ikon.Nada, null, Modifier.size(ukuran / 3), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Satu baris lagu: sampul, judul, artis · durasi, tombol menu (⋮). */
@Composable
fun BarisLagu(
    lagu: Lagu,
    vm: AppVM,
    onKlik: () -> Unit,
    aktif: Boolean = false,
    nomor: Int? = null,
    menuTambahan: List<Pair<String, () -> Unit>> = emptyList(),
) {
    val ctx = LocalContext.current
    val proses by Unduhan.proses.collectAsState()
    val unduhan by vm.data.unduhan.collectAsState()
    val favorit by vm.data.favorit.collectAsState()
    var menu by remember { mutableStateOf(false) }
    val warnaAktif = MaterialTheme.colorScheme.primary
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onKlik).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (nomor != null) {
            Text(
                "$nomor", Modifier.width(28.dp), style = MaterialTheme.typography.labelMedium,
                color = if (aktif) warnaAktif else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Sampul(lagu.sampul, 48.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                lagu.judul, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold, color = if (aktif) warnaAktif else MaterialTheme.colorScheme.onSurface,
            )
            val ket = buildList {
                if (lagu.artis.isNotEmpty()) add(lagu.artis)
                if (lagu.durasi > 0) add(formatWaktu(lagu.durasi))
                if (lagu.lokal || unduhan.containsKey(lagu.id)) add("Offline")
            }.joinToString(" · ")
            Text(ket, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            proses[lagu.id]?.let { p ->
                LinearProgressIndicator(progress = { p / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            }
        }
        if (favorit.any { it.kunci == lagu.kunci }) {
            Icon(Icons.Filled.Favorite, null, Modifier.size(16.dp), tint = warnaAktif)
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Menu") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                @Composable
                fun item(teks: String, aksi: () -> Unit) = DropdownMenuItem(text = { Text(teks) }, onClick = { menu = false; aksi() })
                item("Putar setelah ini") { vm.putarBerikutnya(lagu) }
                item("Tambah ke antrean") { vm.antre(lagu) }
                item(if (favorit.any { it.kunci == lagu.kunci }) "Hapus dari Favorit" else "♥ Favoritkan") { vm.alihFavorit(lagu) }
                if (!lagu.lokal) {
                    if (!unduhan.containsKey(lagu.id)) item("Unduh ke Offline") { vm.unduh(ctx, lagu) }
                    if (lagu.channel.isNotEmpty()) {
                        item("Lagu lain dari ${lagu.artis.ifEmpty { "penyanyi ini" }}") { vm.bukaPenyanyi(lagu.artis, lagu.channel) }
                        item(if (vm.data.mengikuti(lagu.channel)) "Berhenti mengikuti" else "Ikuti ${lagu.artis}") {
                            vm.alihIkuti(lagu.artis, lagu.channel)
                        }
                    }
                }
                menuTambahan.forEach { (t, a) -> item(t, a) }
            }
        }
    }
}

/** Kartu di rak Beranda. */
@Composable
fun KartuLagu(lagu: Lagu, onKlik: () -> Unit) {
    Column(Modifier.width(140.dp).clickable(onClick = onKlik)) {
        Sampul(lagu.sampul, 140.dp, RoundedCornerShape(10.dp))
        Text(
            lagu.judul, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            lagu.artis, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun Pilihan(teks: String, dipilih: Boolean, onKlik: () -> Unit) {
    Surface(
        onClick = onKlik,
        shape = RoundedCornerShape(50),
        color = if (dipilih) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (dipilih) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
    ) {
        Text(teks, Modifier.padding(horizontal = 14.dp, vertical = 7.dp), style = MaterialTheme.typography.labelLarge)
    }
}

/** Tombol ◀ Sebelumnya · Halaman n · Berikutnya ▶. */
@Composable
fun TombolHalaman(h: Halaman) {
    if (h.daftar.isEmpty() && !h.memuat) return
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(onClick = { h.mundur() }, enabled = h.bisaMundur) { Text("◀ Sebelumnya") }
        Text(
            if (h.memuat) "Memuat…" else "Halaman ${h.indeks + 1}",
            Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.labelMedium,
        )
        OutlinedButton(onClick = { h.maju() }, enabled = h.bisaMaju) { Text("Berikutnya ▶") }
    }
}

@Composable
fun Kosong(teks: String) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(teks, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun KepalaPenyanyi(nama: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Person, null) }
        Spacer(Modifier.width(12.dp))
        Text(nama, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}
