package id.ruangputar.musik.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import id.ruangputar.musik.BuildConfig
import id.ruangputar.musik.inti.Pembaruan
import id.ruangputar.musik.inti.Pembaruan.Status

private fun mb(b: Long) = if (b > 0) " · %.1f MB".format(b / 1048576.0) else ""

/** Jendela "Pembaruan tersedia" — muncul otomatis atau dari tombol di Pengaturan. */
@Composable
fun DialogPembaruan() {
    val ctx = LocalContext.current
    val tampil by Pembaruan.tawarkan.collectAsState()
    val s by Pembaruan.status.collectAsState()
    val masalah by Pembaruan.karenaMasalah.collectAsState()
    val izin by Pembaruan.perluIzin.collectAsState()
    if (!tampil) return
    val tutup = { Pembaruan.nanti(ctx) }
    when (val st = s) {
        is Status.Tersedia -> AlertDialog(
            onDismissRequest = tutup,
            title = { Text("Pembaruan tersedia") },
            text = {
                Column {
                    if (masalah) {
                        Text(
                            "Lagu sering gagal diputar atau dicari. Versi baru ini mungkin sudah memperbaikinya.",
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    Text(st.nama + mb(st.ukuran))
                    Text("Terpasang: v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (st.catatan.isNotBlank()) {
                        Text(st.catatan, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            },
            confirmButton = { Button(onClick = { Pembaruan.perbarui(ctx) }) { Text("Perbarui") } },
            dismissButton = { TextButton(onClick = tutup) { Text("Nanti") } },
        )
        is Status.Mengunduh -> AlertDialog(
            onDismissRequest = { },
            title = { Text("Mengunduh pembaruan…") },
            text = {
                Column {
                    LinearProgressIndicator(progress = { st.persen / 100f }, modifier = Modifier.fillMaxWidth())
                    Text("${st.persen}%" + mb(st.info.ukuran), Modifier.padding(top = 8.dp))
                    Text("Musik tetap bisa diputar selama mengunduh.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = { Pembaruan.tawarkan.value = false }) { Text("Sembunyikan") } },
        )
        is Status.Siap -> AlertDialog(
            onDismissRequest = { Pembaruan.tawarkan.value = false },
            title = { Text("Siap dipasang") },
            text = {
                Text(
                    if (izin) "Izinkan \"Instal aplikasi tidak dikenal\" untuk Ruang Putar Musik (cukup sekali), lalu kembali ke sini."
                    else "Tekan Pasang, lalu pilih Perbarui di jendela Android. Aplikasi akan tertutup sebentar; data tetap aman.",
                )
            },
            confirmButton = { Button(onClick = { Pembaruan.perbarui(ctx) }) { Text("Pasang") } },
            dismissButton = { TextButton(onClick = { Pembaruan.tawarkan.value = false }) { Text("Nanti") } },
        )
        is Status.Galat -> AlertDialog(
            onDismissRequest = { Pembaruan.tawarkan.value = false },
            title = { Text("Pembaruan gagal") },
            text = { Text(st.pesan) },
            confirmButton = { TextButton(onClick = { Pembaruan.tawarkan.value = false }) { Text("Tutup") } },
        )
        else -> Unit
    }
}

/** Bagian "Pembaruan" di Pengaturan. */
@Composable
fun BagianPembaruan() {
    val ctx = LocalContext.current
    val s by Pembaruan.status.collectAsState()
    Text("Versi terpasang: v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})")
    Text("Mesin YouTube: NewPipeExtractor ${BuildConfig.VERSI_NEWPIPE}",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        when (val st = s) {
            is Status.Tersedia, is Status.Siap -> Button(onClick = { Pembaruan.perbarui(ctx) }) { Text("Perbarui sekarang") }
            is Status.Mengunduh -> OutlinedButton(onClick = { Pembaruan.tawarkan.value = true }) { Text("Mengunduh ${st.persen}%") }
            else -> OutlinedButton(onClick = { Pembaruan.cek(ctx, manual = true) }, enabled = st !is Status.Mengecek) {
                Text("Cek pembaruan")
            }
        }
        Spacer(Modifier.width(12.dp))
        val ket = when (val st = s) {
            Status.Mengecek -> "Mengecek…"
            Status.Terbaru -> "Sudah versi terbaru ✓"
            is Status.Tersedia -> st.nama
            is Status.Siap -> "Sudah diunduh"
            is Status.Galat -> st.pesan
            else -> ""
        }
        Text(ket, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Text(
        "Dicek otomatis sekali sehari, dan langsung dicek bila lagu sering gagal diputar.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
}
