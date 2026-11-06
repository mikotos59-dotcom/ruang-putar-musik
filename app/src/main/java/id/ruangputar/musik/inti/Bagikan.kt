package id.ruangputar.musik.inti

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import id.ruangputar.musik.BuildConfig
import id.ruangputar.musik.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Bagikan Ruang Putar Musik ke teman lewat menu bagikan Android (WhatsApp, Telegram, Quick Share, dll.).
 *  - kirimApk: salin APK aplikasi ini (yang sedang terpasang) dengan nama rapi, lalu buka menu bagikan.
 *  - kirimLink: teks + link rilis GitHub (cadangan kalau aplikasi tujuan menolak file .apk).
 */
object Bagikan {
    private const val HARI = 24L * 60 * 60 * 1000
    val linkRilis get() = "https://github.com/${BuildConfig.REPO_GITHUB}/releases/latest"
    private val namaFile get() = "RuangPutarMusik-${BuildConfig.VERSION_NAME}.apk"

    private fun teks() = "Coba Ruang Putar Musik, aplikasi putar musik ringan & gratis.\n" +
        "Unduh versi terbaru: $linkRilis"

    /** Ukuran APK dalam byte (0 kalau tidak terbaca). */
    fun ukuranApk(ctx: Context): Long = runCatching { File(ctx.applicationInfo.sourceDir).length() }.getOrDefault(0L)

    suspend fun kirimApk(ctx: Context) {
        val tujuan = withContext(Dispatchers.IO) {
            val dir = File(ctx.cacheDir, "bagikan").apply { mkdirs() }
            dir.listFiles()?.forEach { if (it.name != namaFile) it.delete() } // buang salinan versi lama
            val f = File(dir, namaFile)
            val asal = File(ctx.applicationInfo.sourceDir)
            if (!f.exists() || f.length() != asal.length()) asal.copyTo(f, overwrite = true)
            f
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.berkas", tujuan)
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, teks())
            putExtra(Intent.EXTRA_SUBJECT, "Ruang Putar Musik")
            clipData = ClipData.newRawUri(namaFile, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(i, "Kirim aplikasi lewat…").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun kirimLink(ctx: Context) {
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, teks())
        }
        // Ruang Putar sendiri juga menerima teks (link YouTube) — jangan tampil di daftar
        val c = Intent.createChooser(i, "Kirim link lewat…").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(ctx, MainActivity::class.java)))
        ctx.startActivity(c)
    }

    /** Kartu pengingat di Beranda: muncul 3 hari setelah dipasang, lalu sesuai jadwal tunda. */
    fun kartuMuncul(ctx: Context, p: Pengaturan): Boolean {
        if (p.kartuBagiMati) return false
        val pasang = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).firstInstallTime }.getOrDefault(0L)
        val now = System.currentTimeMillis()
        return now >= maxOf(p.kartuBagiSampai, pasang + 3 * HARI)
    }

    fun tunda(hari: Int) = System.currentTimeMillis() + hari * HARI
}
