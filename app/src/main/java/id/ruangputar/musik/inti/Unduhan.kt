package id.ruangputar.musik.inti

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import id.ruangputar.musik.App
import id.ruangputar.musik.ui.PemuatGambar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import kotlin.coroutines.resume

/**
 * Unduh lagu ke folder Music/RuangPutar sebagai suara asli YouTube (M4A bila ada) —
 * tanpa ubah format, jadi tidak butuh ffmpeg dan aplikasinya tetap ringan.
 */
object Unduhan {
    const val FOLDER = "RuangPutar"

    private val _proses = MutableStateFlow<Map<String, Int>>(emptyMap())   // id -> persen

    /** Lagu yang sedang diunduh dan persennya. */
    val proses: StateFlow<Map<String, Int>> = _proses

    fun unduh(ctx: Context, lagu: Lagu, selesai: (String) -> Unit) {
        if (lagu.lokal || lagu.id in _proses.value || App.data.unduhan.value.containsKey(lagu.id)) return
        _proses.value = _proses.value + (lagu.id to 0)
        App.scope.launch(Dispatchers.IO) {
            val pesan = try {
                val uri = jalankan(ctx.applicationContext, lagu)
                simpanSampul(ctx.applicationContext, lagu)
                App.data.catatUnduhan(lagu.id, uri)
                "✓ “${lagu.judul}” tersimpan di Music/$FOLDER"
            } catch (e: Exception) {
                if (YouTube.adalahGalatBot(e)) "YouTube sedang menahan permintaan. Coba unduh lagi nanti."
                else "Gagal mengunduh “${lagu.judul}”: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                _proses.value = _proses.value - lagu.id
            }
            withContext(Dispatchers.Main) { selesai(pesan) }
        }
    }

    /** Ditolak server (mis. HTTP 403): coba stream lain. */
    private class Ditolak(kode: Int) : Exception("HTTP $kode")

    /**
     * Coba setiap pilihan stream bergantian. Kalau semuanya ditolak (alamat kedaluwarsa / dibatasi),
     * ambil alamat baru sekali lagi lalu ulangi.
     */
    private suspend fun jalankan(ctx: Context, lagu: Lagu): String {
        var galat: Exception? = null
        repeat(2) { putaran ->
            if (putaran > 0) {
                YouTube.lupakan(lagu.id)
                kotlinx.coroutines.delay(1500)
            }
            for (suara in YouTube.suaraUnduh(lagu.id).take(4)) {
                try {
                    return tulis(ctx, lagu, suara)
                } catch (e: Ditolak) {
                    galat = e
                    _proses.value = _proses.value + (lagu.id to 0)
                }
            }
        }
        throw galat ?: IllegalStateException("Tidak ada stream yang bisa diunduh")
    }

    private suspend fun tulis(ctx: Context, lagu: Lagu, suara: Suara): String {
        val nama = aman("${lagu.artis.ifBlank { "Tanpa artis" }} - ${lagu.judul}") + ".${suara.ext}"
        return if (Build.VERSION.SDK_INT >= 29) {
            val nilai = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, nama)
                put(MediaStore.Audio.Media.MIME_TYPE, suara.mime)
                put(MediaStore.Audio.Media.TITLE, lagu.judul)
                put(MediaStore.Audio.Media.ARTIST, lagu.artis)
                put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/$FOLDER")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val r = ctx.contentResolver
            val uri = r.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), nilai)
                ?: throw IllegalStateException("Tidak bisa membuat file di folder Musik")
            try {
                r.openOutputStream(uri)?.use { tarik(suara.url, lagu.id, it) }
                    ?: throw IllegalStateException("Tidak bisa menulis file")
                r.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
            } catch (e: Exception) {
                r.delete(uri, null, null)
                throw e
            }
            uri.toString()
        } else {
            @Suppress("DEPRECATION")
            val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), FOLDER)
            folder.mkdirs()
            val file = File(folder, nama)
            try {
                FileOutputStream(file).use { tarik(suara.url, lagu.id, it) }
            } catch (e: Exception) {
                file.delete()
                throw e
            }
            pindai(ctx, file, suara.mime) ?: Uri.fromFile(file).toString()
        }
    }

    /** Diunduh per potongan 1 MB (Range) — server YouTube memperlambat unduhan satu potongan besar. */
    /**
     * Diunduh per potongan 1 MB — server YouTube memperlambat unduhan satu potongan besar.
     * Potongan diminta lewat header Range; kalau ditolak (403), dicoba lagi dengan cara aplikasi
     * NewPipe (&range=awal-akhir di alamat).
     */
    private fun tarik(url: String, id: String, keluar: OutputStream) {
        val potong = 1L shl 20
        var awal = 0L
        var total = Regex("[?&]clen=(\\d+)").find(url)?.groupValues?.get(1)?.toLongOrNull() ?: -1L
        var lewatAlamat = false
        while (total < 0 || awal < total) {
            val akhir = if (total > 0) minOf(awal + potong, total) - 1 else awal + potong - 1
            var n = -1L
            for (coba in 0..1) {
                val b = okhttp3.Request.Builder().header("User-Agent", Jaringan.AGEN)
                if (lewatAlamat) b.url("$url&range=$awal-$akhir") else b.url(url).header("Range", "bytes=$awal-$akhir")
                val hasil = Jaringan.media.newCall(b.build()).execute().use { r ->
                    if (r.code == 403 || r.code == 410 || r.code == 429) return@use -r.code.toLong()
                    if (r.code !in 200..299) throw IllegalStateException("HTTP ${r.code}")
                    if (total < 0) {
                        total = r.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull()
                            ?: if (r.code == 200 && !lewatAlamat) r.body?.contentLength() ?: -1L else -1L
                        if (total <= 0) throw IllegalStateException("Ukuran tidak diketahui")
                    }
                    val m = r.body?.byteStream()?.use { it.copyTo(keluar) } ?: 0L
                    if (r.code == 200 && !lewatAlamat && m >= total) awal = total - m   // server mengirim semuanya sekaligus
                    m
                }
                if (hasil >= 0) { n = hasil; break }
                if (coba == 1 || lewatAlamat) throw Ditolak((-hasil).toInt())
                lewatAlamat = true
            }
            if (n <= 0) throw IllegalStateException("Unduhan terputus")
            awal += n
            _proses.value = _proses.value + (id to ((awal * 100) / total.coerceAtLeast(1)).toInt().coerceIn(0, 100))
        }
    }

    private suspend fun pindai(ctx: Context, file: File, mime: String): String? = suspendCancellableCoroutine { k ->
        MediaScannerConnection.scanFile(ctx, arrayOf(file.absolutePath), arrayOf(mime)) { _, uri ->
            if (k.isActive) k.resume(uri?.toString())
        }
    }

    /** Sampul disimpan di memori aplikasi supaya tetap tampil walau tanpa internet. */
    private fun fileSampul(ctx: Context, id: String) = File(File(ctx.filesDir, "sampul"), "$id.jpg")

    private fun simpanSampul(ctx: Context, lagu: Lagu) {
        if (lagu.sampul.isEmpty()) return
        try {
            val bytes = PemuatGambar.unduhTerbaik(lagu.sampul, besar = true) ?: return
            val f = fileSampul(ctx, lagu.id)
            f.parentFile?.mkdirs()
            f.writeBytes(bytes)
        } catch (e: Exception) {
            // sampul gagal: lagu tetap tersimpan
        }
    }

    /** Sampul untuk lagu offline: file yang disimpan, atau (unduhan lama) gambar dari YouTube. */
    private fun sampulOffline(ctx: Context, id: String): String {
        if (id.isEmpty()) return ""
        val f = fileSampul(ctx, id)
        return if (f.exists()) Uri.fromFile(f).toString() else "https://i.ytimg.com/vi/$id/mqdefault.jpg"
    }

    private fun aman(nama: String) = nama.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(120)

    /** Isi halaman Offline: semua file musik di Music/RuangPutar. */
    fun daftarOffline(ctx: Context): List<Lagu> {
        val hasil = ArrayList<Lagu>()
        // nomor file MediaStore -> ID video (bentuk alamat bisa beda: external / external_primary)
        val keId = App.data.unduhan.value.entries.associate { (id, u) -> Uri.parse(u).lastPathSegment to id }
        val koleksi = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val kolom = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DURATION,
        )
        val (pilih, arg) = if (Build.VERSION.SDK_INT >= 29) {
            "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?" to arrayOf("${Environment.DIRECTORY_MUSIC}/$FOLDER%")
        } else {
            @Suppress("DEPRECATION")
            "${MediaStore.Audio.Media.DATA} LIKE ?" to arrayOf("%/${Environment.DIRECTORY_MUSIC}/$FOLDER/%")
        }
        try {
            ctx.contentResolver.query(koleksi, kolom, pilih, arg, "${MediaStore.Audio.Media.DATE_ADDED} DESC")?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val iJudul = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val iArtis = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val iDurasi = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                while (c.moveToNext()) {
                    val nomor = c.getLong(iId)
                    val uri = ContentUris.withAppendedId(koleksi, nomor)
                    val artis = c.getString(iArtis)?.takeIf { it != MediaStore.UNKNOWN_STRING } ?: ""
                    val idVideo = keId[nomor.toString()] ?: ""
                    hasil.add(
                        Lagu(
                            id = idVideo, judul = c.getString(iJudul) ?: "Tanpa judul", artis = artis,
                            durasi = c.getLong(iDurasi) / 1000, sampul = sampulOffline(ctx, idVideo), uri = uri.toString(),
                        ),
                    )
                }
            }
        } catch (e: SecurityException) {
            // izin membaca musik belum diberikan
        }
        return hasil
    }
}
