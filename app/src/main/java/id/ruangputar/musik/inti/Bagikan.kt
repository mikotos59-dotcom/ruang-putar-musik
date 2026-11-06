package id.ruangputar.musik.inti

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import id.ruangputar.musik.BuildConfig
import java.io.File

/** Membagikan aplikasi ini ke orang lain lewat menu "Bagikan" Android (WhatsApp, Telegram, Drive, dll). */
object Bagikan {
    val tautanUnduh get() = "https://github.com/${BuildConfig.REPO_GITHUB}/releases/latest"
    const val TAUTAN_DISCORD = "https://discord.gg/VRqw9EGUpb"

    private fun pesan() =
        "Coba Ruang Putar Musik v${BuildConfig.VERSION_NAME} — pemutar musik ringan buatan @SuS_X_x.\n" +
        "Unduh versi terbaru: $tautanUnduh\nDiscord: $TAUTAN_DISCORD"

    /** Salin APK yang sedang terpasang ke cache lalu buka menu bagikan. Berjalan di thread latar. */
    fun siapkanApk(ctx: Context): File {
        val asal = File(ctx.applicationInfo.sourceDir)
        val folder = File(ctx.cacheDir, "bagikan").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val tujuan = File(folder, "RuangPutarMusik-v${BuildConfig.VERSION_NAME}.apk")
        asal.copyTo(tujuan, overwrite = true)
        return tujuan
    }

    fun kirimApk(ctx: Context, berkas: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.berkas", berkas)
        val niat = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, pesan())
            clipData = ClipData.newRawUri(berkas.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(niat, "Bagikan APK Ruang Putar Musik").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun kirimTautan(ctx: Context) {
        val niat = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, pesan())
        }
        ctx.startActivity(Intent.createChooser(niat, "Bagikan tautan Ruang Putar Musik").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
