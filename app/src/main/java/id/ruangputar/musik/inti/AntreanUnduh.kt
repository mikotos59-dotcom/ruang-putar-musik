package id.ruangputar.musik.inti

import id.ruangputar.musik.App
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.random.Random

/**
 * Pengelola unduhan: semua unduhan (satu lagu maupun satu playlist) lewat antrean ini.
 *  - Satu per satu, dengan jeda acak 3–8 detik antar lagu dan istirahat ± 2 menit setiap 20 lagu,
 *    supaya YouTube tidak menganggap ini bot.
 *  - YouTube menahan permintaan -> berhenti 25 menit, lalu lanjut sendiri.
 *  - Lagu yang sedang mulai diputar didahulukan (unduhan menunggu sebentar).
 *  - Lagu gagal tidak menghentikan antrean; bisa dicoba lagi di akhir.
 *  - Antrean disimpan: kalau aplikasi ditutup / dihentikan Android di tengah jalan, ditawarkan untuk dilanjutkan.
 */
object AntreanUnduh {
    data class Item(val lagu: Lagu, val grup: String = "", val pesan: String = "")

    sealed class Keadaan {
        data object Diam : Keadaan()
        data object Mengunduh : Keadaan()
        /** Jeda antar lagu / istirahat rutin. */
        data class Istirahat(val sampai: Long, val alasan: String) : Keadaan()
        /** YouTube sedang menahan permintaan (dikira bot). */
        data class Ditahan(val sampai: Long) : Keadaan()
        /** Dijeda oleh pengguna. */
        data object Dijeda : Keadaan()
        /** Sisa antrean dari sesi sebelumnya, menunggu keputusan pengguna. */
        data object Tertunda : Keadaan()
    }

    data class Info(
        val antre: List<Item> = emptyList(),
        val kini: Item? = null,
        val selesai: Int = 0,
        val gagal: List<Item> = emptyList(),
        val keadaan: Keadaan = Keadaan.Diam,
        val total: Int = 0,
    ) {
        /** Panel unduhan perlu ditampilkan? */
        val tampil: Boolean get() = antre.isNotEmpty() || kini != null || selesai > 0 || gagal.isNotEmpty() || keadaan == Keadaan.Tertunda
        val berjalan: Boolean get() = antre.isNotEmpty() || kini != null
        /** Nama grup (playlist) yang sedang diunduh, kalau semuanya dari satu playlist. */
        val grup: String
            get() = (listOfNotNull(kini) + antre).map { it.grup }.distinct().singleOrNull().orEmpty()
    }

    private val _info = MutableStateFlow(Info())
    val info: StateFlow<Info> = _info

    /** Diisi tampilan: pemutar sedang memuat lagu -> unduhan mengalah sebentar. */
    @Volatile var pemutarMemuat = false

    /** Pesan singkat ke pengguna (diisi oleh tampilan). */
    var kabar: (String) -> Unit = {}

    private var tugas: Job? = null
    private val file get() = File(App.aku.filesDir, "antrean-unduh.json")
    private const val ISTIRAHAT_SETIAP = 20
    private const val LAMA_ISTIRAHAT = 120_000L
    private const val LAMA_DITAHAN = 25 * 60_000L

    @Synchronized
    private fun ubah(f: (Info) -> Info) {
        _info.value = f(_info.value)
    }

    // ------------------------------------------------------------------ mulai aplikasi
    /** Dipanggil saat aplikasi dibuka: sisa antrean sesi lalu -> ditawarkan untuk dilanjutkan. */
    fun muat() {
        val sisa = runCatching {
            val a = JSONArray(file.readText(Charsets.UTF_8))
            (0 until a.length()).mapNotNull { i ->
                a.optJSONObject(i)?.let { o -> Item(Lagu.dariJson(o.optJSONObject("lagu") ?: JSONObject()), o.optString("grup")) }
            }.filter { it.lagu.id.isNotEmpty() && !Unduhan.sudahDiunduh(it.lagu.id) }
        }.getOrDefault(emptyList())
        if (sisa.isNotEmpty()) ubah { Info(antre = sisa, total = sisa.size, keadaan = Keadaan.Tertunda) }
    }

    private fun simpan() {
        val s = _info.value
        val semua = listOfNotNull(s.kini) + s.antre
        runCatching {
            if (semua.isEmpty()) {
                file.delete()
            } else {
                file.writeText(JSONArray(semua.map { JSONObject().put("lagu", it.lagu.keJson()).put("grup", it.grup) }).toString(), Charsets.UTF_8)
            }
        }
    }

    // ------------------------------------------------------------------ perintah
    /** Tambah lagu ke antrean. Yang sudah diunduh / sudah diantre dilewati. Mengembalikan jumlah yang ditambahkan. */
    fun tambah(lagu: List<Lagu>, grup: String = ""): Int {
        val s = _info.value
        val sudah = (listOfNotNull(s.kini) + s.antre).map { it.lagu.id }.toSet()
        val baru = lagu.filter { !it.lokal && it.id.isNotEmpty() && it.id !in sudah && !Unduhan.sudahDiunduh(it.id) }.distinctBy { it.id }
        if (baru.isEmpty()) return 0
        ubah { i ->
            val mulaiBaru = !i.berjalan && i.keadaan != Keadaan.Tertunda
            i.copy(
                antre = i.antre + baru.map { Item(it, grup) },
                total = (if (mulaiBaru) 0 else i.total) + baru.size,
                selesai = if (mulaiBaru) 0 else i.selesai,
                gagal = if (mulaiBaru) emptyList() else i.gagal,
                keadaan = if (i.keadaan == Keadaan.Tertunda) Keadaan.Diam else i.keadaan,
            )
        }
        simpan()
        jalankan()
        return baru.size
    }

    fun jeda() {
        ubah { it.copy(keadaan = Keadaan.Dijeda) }
    }

    fun lanjutkan() {
        ubah { it.copy(keadaan = Keadaan.Diam) }
        jalankan()
    }

    /** Kosongkan antrean. Lagu yang sedang diunduh dibiarkan selesai. */
    fun batalkan() {
        ubah { it.copy(antre = emptyList(), gagal = emptyList(), keadaan = if (it.kini != null) it.keadaan else Keadaan.Diam, total = it.selesai + (if (it.kini != null) 1 else 0)) }
        simpan()
        if (_info.value.kini == null) tutupRingkasan()
    }

    fun cobaLagiGagal() {
        val g = _info.value.gagal
        if (g.isEmpty()) return
        ubah { it.copy(antre = it.antre + g.map { x -> x.copy(pesan = "") }, gagal = emptyList(), total = it.selesai + it.antre.size + g.size + (if (it.kini != null) 1 else 0)) }
        simpan()
        jalankan()
    }

    /** Sembunyikan ringkasan "selesai" (setelah semua unduhan beres). */
    fun tutupRingkasan() {
        if (_info.value.berjalan) return
        ubah { Info() }
    }

    // ------------------------------------------------------------------ pekerja
    private fun jalankan() {
        val k = _info.value.keadaan
        if (k == Keadaan.Dijeda || k == Keadaan.Tertunda) return
        if (tugas?.isActive == true) return
        tugas = App.scope.launch(Dispatchers.IO) { kerja() }
    }

    private suspend fun kerja() {
        var berhasilBeruntun = 0
        while (true) {
            val s = _info.value
            if (s.keadaan == Keadaan.Dijeda || s.keadaan == Keadaan.Tertunda) return
            val item = s.antre.firstOrNull() ?: break

            if (YouTube.sedangDitahan) {
                tunggu(YouTube.ditahanSampai.value, Keadaan.Ditahan(YouTube.ditahanSampai.value))
                continue
            }
            // lagu yang baru mulai diputar didahulukan
            var t = 0
            while (pemutarMemuat && t < 40) {
                delay(500)
                t++
            }

            ubah { it.copy(antre = it.antre.drop(1), kini = item, keadaan = Keadaan.Mengunduh) }
            try {
                Unduhan.unduhSekarang(App.aku, item.lagu)
                ubah { it.copy(kini = null, selesai = it.selesai + 1) }
                berhasilBeruntun++
            } catch (e: CancellationException) {
                ubah { it.copy(antre = listOf(item) + it.antre, kini = null) }
                simpan()
                throw e
            } catch (e: Exception) {
                if (YouTube.adalahGalatBot(e)) {
                    ubah { it.copy(antre = listOf(item) + it.antre, kini = null) }
                    simpan()
                    val sampai = maxOf(YouTube.ditahanSampai.value, System.currentTimeMillis() + LAMA_DITAHAN)
                    withContext(Dispatchers.Main) { kabar("YouTube menahan unduhan. Dilanjutkan otomatis ± 25 menit lagi.") }
                    tunggu(sampai, Keadaan.Ditahan(sampai))
                    continue
                }
                val pesan = e.message ?: e.javaClass.simpleName
                ubah { it.copy(kini = null, gagal = it.gagal + item.copy(pesan = pesan)) }
            }
            simpan()
            if (_info.value.antre.isEmpty()) break
            if (berhasilBeruntun > 0 && berhasilBeruntun % ISTIRAHAT_SETIAP == 0) {
                tunggu(System.currentTimeMillis() + LAMA_ISTIRAHAT, Keadaan.Istirahat(System.currentTimeMillis() + LAMA_ISTIRAHAT, "Istirahat rutin setiap $ISTIRAHAT_SETIAP lagu"))
            } else {
                val jeda = Random.nextLong(3_000, 8_001)
                tunggu(System.currentTimeMillis() + jeda, Keadaan.Istirahat(System.currentTimeMillis() + jeda, "Jeda antar lagu"))
            }
        }
        val akhir = _info.value
        if (akhir.keadaan != Keadaan.Dijeda && akhir.keadaan != Keadaan.Tertunda) {
            ubah { it.copy(keadaan = Keadaan.Diam, kini = null) }
        }
        simpan()
        if (!akhir.berjalan && akhir.selesai + akhir.gagal.size > 1) {
            withContext(Dispatchers.Main) {
                kabar("✓ Unduhan selesai: ${akhir.selesai} lagu" + if (akhir.gagal.isNotEmpty()) ", ${akhir.gagal.size} gagal" else "")
            }
        }
    }

    /** Tunggu sampai waktu tertentu (berhenti lebih awal kalau dijeda pengguna). */
    private suspend fun tunggu(sampai: Long, keadaan: Keadaan) {
        ubah { it.copy(keadaan = keadaan) }
        while (System.currentTimeMillis() < sampai) {
            if (_info.value.keadaan == Keadaan.Dijeda) return
            delay(250)
        }
        ubah { if (it.keadaan == keadaan) it.copy(keadaan = Keadaan.Mengunduh) else it }
    }

    // ------------------------------------------------------------------ perkiraan
    /** Perkiraan (ukuran byte, waktu detik) untuk mengunduh daftar ini, sudah termasuk jeda & istirahat. */
    fun perkiraan(lagu: List<Lagu>): Pair<Long, Long> {
        val p = App.data.pengaturan.value
        val perDetikAudio = if (p.kualitas == "hemat") 8_000L else 17_000L      // ± 64 / 135 kbps
        val ukuran = lagu.sumOf { (if (it.durasi > 0) it.durasi else 240L) * perDetikAudio }
        val laju = when (p.kecepatan) {
            "rendah" -> 1L * 1024 * 1024
            "tanpa" -> 5L * 1024 * 1024
            else -> 3L * 1024 * 1024
        }
        val n = lagu.size.toLong()
        val detik = ukuran / laju + n * 3 + (n - 1).coerceAtLeast(0) * 11 / 2 + (n / ISTIRAHAT_SETIAP) * (LAMA_ISTIRAHAT / 1000)
        return ukuran to detik
    }

    /** "± 12 menit", "± 1 jam 5 menit", "< 1 menit". */
    fun teksWaktu(detik: Long): String = when {
        detik < 60 -> "< 1 menit"
        detik < 3600 -> "± ${(detik + 30) / 60} menit"
        else -> "± ${detik / 3600} jam ${(detik % 3600) / 60} menit"
    }

    fun teksUkuran(byte: Long): String = when {
        byte >= 1024L * 1024 * 1024 -> "%.1f GB".format(byte / (1024.0 * 1024 * 1024))
        else -> "${(byte / (1024 * 1024)).coerceAtLeast(1)} MB"
    }
}
