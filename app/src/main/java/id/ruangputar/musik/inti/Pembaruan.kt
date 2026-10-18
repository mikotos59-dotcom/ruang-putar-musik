package id.ruangputar.musik.inti

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import id.ruangputar.musik.App
import id.ruangputar.musik.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * Pembaruan aplikasi lewat GitHub Releases.
 *  - GitHub Actions membuat Release bertag "b<versionCode>" berisi APK.
 *  - Aplikasi mengecek: saat dibuka (paling sering sekali sehari), saat sering gagal, atau lewat tombol.
 *  - Ada versi baru -> tawarkan -> unduh -> buka jendela pasang bawaan Android.
 */
object Pembaruan {
    sealed class Status {
        data object Diam : Status()
        data object Mengecek : Status()
        data object Terbaru : Status()
        data class Tersedia(val nama: String, val kode: Int, val url: String, val catatan: String, val ukuran: Long) : Status()
        data class Mengunduh(val info: Tersedia, val persen: Int) : Status()
        data class Siap(val info: Tersedia, val file: File) : Status()
        data class Galat(val pesan: String) : Status()
    }

    private val _status = MutableStateFlow<Status>(Status.Diam)
    val status: StateFlow<Status> = _status

    /** true = jendela tawaran sedang ditampilkan (bukan hanya di Pengaturan). */
    val tawarkan = MutableStateFlow(false)

    /** true = tawaran muncul karena aplikasi sering gagal memutar/mencari. */
    val karenaMasalah = MutableStateFlow(false)

    private const val SEHARI = 24 * 3600_000L
    private const val SEJAM = 3600_000L
    private var tugas: Job? = null
    private val gagalBeruntun = AtomicInteger(0)

    private fun pref(ctx: Context) = ctx.getSharedPreferences("pembaruan", Context.MODE_PRIVATE)

    private fun folder(ctx: Context) = File(ctx.cacheDir, "pembaruan").apply { mkdirs() }

    /** Saat aplikasi dibuka: cek paling sering sekali sehari. Sisa file APK lama dibersihkan. */
    fun cekOtomatis(ctx: Context) {
        val p = pref(ctx)
        if (p.getInt("kode_terpasang", 0) != BuildConfig.VERSION_CODE) {
            folder(ctx).listFiles()?.forEach { it.delete() }
            p.edit().putInt("kode_terpasang", BuildConfig.VERSION_CODE).apply()
        }
        if (System.currentTimeMillis() - p.getLong("terakhir_cek", 0) < SEHARI) return
        cek(ctx, manual = false)
    }

    /** Dipanggil saat permintaan ke YouTube berhasil / gagal (bukan karena ditahan bot). */
    fun catatBerhasil() = gagalBeruntun.set(0)

    fun catatGagal(ctx: Context) {
        if (gagalBeruntun.incrementAndGet() < 3) return
        gagalBeruntun.set(0)
        val p = pref(ctx)
        if (System.currentTimeMillis() - p.getLong("terakhir_cek_masalah", 0) < SEJAM) return
        p.edit().putLong("terakhir_cek_masalah", System.currentTimeMillis()).apply()
        karenaMasalah.value = true
        cek(ctx, manual = false)
    }

    /** Cek Release terbaru di GitHub. manual = dari tombol (selalu tampilkan hasilnya). */
    fun cek(ctx: Context, manual: Boolean) {
        if (tugas?.isActive == true) return
        val app = ctx.applicationContext
        tugas = App.scope.launch(Dispatchers.IO) {
            val sebelum = _status.value
            if (sebelum is Status.Mengunduh) return@launch
            if (manual) _status.value = Status.Mengecek
            try {
                val info = ambilRilisTerbaru()
                pref(app).edit().putLong("terakhir_cek", System.currentTimeMillis()).apply()
                if (info != null && info.kode > BuildConfig.VERSION_CODE) {
                    val dilewati = pref(app).getInt("dilewati", 0)
                    _status.value = info
                    if (manual || karenaMasalah.value || info.kode != dilewati) tawarkan.value = true
                } else {
                    _status.value = if (manual) Status.Terbaru else Status.Diam
                    karenaMasalah.value = false
                }
            } catch (e: Exception) {
                _status.value = if (manual) Status.Galat("Gagal mengecek pembaruan: ${e.message ?: e.javaClass.simpleName}") else sebelum
            }
        }
    }

    /**
     * Rilis terbaru. Utama: file versi.json di Release (lewat github.com, tanpa API, tanpa batas).
     * Cadangan: API GitHub (dibatasi 60 cek/jam per jaringan — operator seluler sering berbagi satu IP).
     */
    private fun ambilRilisTerbaru(): Status.Tersedia? {
        val galatUtama = try {
            return lewatVersiJson()
        } catch (e: TidakAda) {
            null
        } catch (e: Exception) {
            e
        }
        return try {
            lewatApi()
        } catch (e: Exception) {
            throw galatUtama ?: e
        }
    }

    private class TidakAda : Exception()

    private fun minta(url: String, json: Boolean = false): String? {
        val b = okhttp3.Request.Builder().url(url).header("User-Agent", "RuangPutarMusik/${BuildConfig.VERSION_NAME}")
        if (json) b.header("Accept", "application/vnd.github+json")
        return Jaringan.api.newCall(b.build()).execute().use { r ->
            when {
                r.code == 404 -> null
                r.code == 403 || r.code == 429 ->
                    throw IllegalStateException("GitHub sedang membatasi pengecekan dari jaringan ini. Coba lagi nanti atau pakai Wi-Fi.")
                !r.isSuccessful -> throw IllegalStateException("HTTP ${r.code}")
                else -> r.body?.string()
            }
        }
    }

    private fun lewatVersiJson(): Status.Tersedia {
        val repo = BuildConfig.REPO_GITHUB
        val teks = minta("https://github.com/$repo/releases/latest/download/versi.json") ?: throw TidakAda()
        val o = JSONObject(teks)
        val kode = o.optInt("kode", -1).takeIf { it > 0 } ?: throw TidakAda()
        val file = o.optString("file").ifEmpty { throw TidakAda() }
        return Status.Tersedia(
            nama = o.optString("nama").ifEmpty { "build $kode" },
            kode = kode,
            url = "https://github.com/$repo/releases/download/b$kode/$file",
            catatan = o.optString("catatan"),
            ukuran = o.optLong("ukuran"),
        )
    }

    private fun lewatApi(): Status.Tersedia? {
        val teks = minta("https://api.github.com/repos/${BuildConfig.REPO_GITHUB}/releases/latest", json = true) ?: return null
        val o = JSONObject(teks)
        val kode = o.optString("tag_name").removePrefix("b").toIntOrNull() ?: return null
        val aset = o.optJSONArray("assets") ?: return null
        for (i in 0 until aset.length()) {
            val a = aset.getJSONObject(i)
            if (a.optString("name").endsWith(".apk")) {
                return Status.Tersedia(
                    nama = o.optString("name").ifEmpty { "build $kode" },
                    kode = kode,
                    url = a.optString("browser_download_url"),
                    catatan = o.optString("body"),
                    ukuran = a.optLong("size"),
                )
            }
        }
        return null
    }

    /** Tombol "Nanti": jangan tawarkan versi ini lagi secara otomatis (tombol cek tetap bisa). */
    fun nanti(ctx: Context) {
        (_status.value as? Status.Tersedia)?.let { pref(ctx).edit().putInt("dilewati", it.kode).apply() }
        tawarkan.value = false
        karenaMasalah.value = false
    }

    /** Unduh APK lalu buka jendela pasang Android. */
    fun perbarui(ctx: Context) {
        val app = ctx.applicationContext
        when (val s = _status.value) {
            is Status.Siap -> return pasang(app, s.file)
            is Status.Tersedia -> {
                tawarkan.value = true
                tugas = App.scope.launch(Dispatchers.IO) {
                    try {
                        val file = unduh(app, s)
                        _status.value = Status.Siap(s, file)
                        withContext(Dispatchers.Main) { pasang(app, file) }
                    } catch (e: Exception) {
                        _status.value = Status.Galat("Gagal mengunduh pembaruan: ${e.message ?: e.javaClass.simpleName}")
                    }
                }
            }
            else -> Unit
        }
    }

    private fun unduh(ctx: Context, info: Status.Tersedia): File {
        val tujuan = File(folder(ctx), "RuangPutarMusik-b${info.kode}.apk")
        if (tujuan.exists() && info.ukuran > 0 && tujuan.length() == info.ukuran) return tujuan
        val sementara = File(folder(ctx), "unduhan.tmp")
        _status.value = Status.Mengunduh(info, 0)
        val req = okhttp3.Request.Builder().url(info.url)
            .header("User-Agent", "RuangPutarMusik/${BuildConfig.VERSION_NAME}").build()
        Jaringan.api.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IllegalStateException("HTTP ${r.code}")
            val badan = r.body ?: throw IllegalStateException("Tidak ada data")
            val total = badan.contentLength().takeIf { it > 0 } ?: info.ukuran
            badan.byteStream().use { masuk ->
                sementara.outputStream().use { keluar ->
                    val buf = ByteArray(64 * 1024)
                    var dapat = 0L
                    while (true) {
                        val n = masuk.read(buf)
                        if (n < 0) break
                        keluar.write(buf, 0, n)
                        dapat += n
                        if (total > 0) _status.value = Status.Mengunduh(info, ((dapat * 100) / total).toInt().coerceIn(0, 100))
                    }
                }
            }
        }
        if (info.ukuran > 0 && sementara.length() != info.ukuran) {
            sementara.delete()
            throw IllegalStateException("Unduhan tidak lengkap")
        }
        tujuan.delete()
        sementara.renameTo(tujuan)
        return tujuan
    }

    /** Jendela pasang bawaan Android. Aplikasi otomatis tertutup saat diperbarui. */
    fun pasang(ctx: Context, file: File) {
        if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
            // sekali saja: izinkan "Instal aplikasi tidak dikenal" untuk Ruang Putar Musik
            perluIzin.value = true
            val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try { ctx.startActivity(i) } catch (e: ActivityNotFoundException) { }
            return
        }
        perluIzin.value = false
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.berkas", file)
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(i)
        } catch (e: ActivityNotFoundException) {
            _status.value = Status.Galat("HP ini tidak bisa membuka pemasang aplikasi.")
        }
    }

    /** true = menunggu pengguna mengizinkan "Instal aplikasi tidak dikenal". */
    val perluIzin = MutableStateFlow(false)

    /** Kembali dari layar izin: kalau sudah diizinkan, lanjut pasang. */
    fun lanjutkanBilaDiizinkan(ctx: Context) {
        if (!perluIzin.value) return
        val s = _status.value as? Status.Siap ?: return
        if (Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls()) pasang(ctx, s.file)
    }
}
