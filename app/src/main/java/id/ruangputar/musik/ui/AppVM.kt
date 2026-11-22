package id.ruangputar.musik.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import id.ruangputar.musik.App
import id.ruangputar.musik.inti.Potong
import id.ruangputar.musik.inti.formatWaktu
import id.ruangputar.musik.inti.AntreanUnduh
import id.ruangputar.musik.inti.DitahanYouTube
import id.ruangputar.musik.inti.InfoPlaylist
import id.ruangputar.musik.inti.Playlist
import id.ruangputar.musik.inti.Hasil
import id.ruangputar.musik.inti.LayananPutar
import id.ruangputar.musik.inti.Lagu
import id.ruangputar.musik.inti.Penyanyi
import id.ruangputar.musik.inti.Rak
import id.ruangputar.musik.inti.Unduhan
import id.ruangputar.musik.inti.YouTube
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.Page

enum class Tab { BERANDA, CARI, PUSTAKA, PENGATURAN }

/** Daftar berhalaman (hasil cari / lagu penyanyi) dengan tombol ◀ ▶. */
class Halaman(private val vm: AppVM, private val ambil: suspend (Page?) -> Hasil) {
    var daftar by mutableStateOf<List<Hasil>>(emptyList())
        private set
    var indeks by mutableIntStateOf(0)
        private set
    var memuat by mutableStateOf(false)
        private set
    var galat by mutableStateOf<String?>(null)
        private set

    val kini: List<Lagu> get() = daftar.getOrNull(indeks)?.lagu ?: emptyList()
    val bisaMundur: Boolean get() = indeks > 0 && !memuat
    val bisaMaju: Boolean get() = !memuat && (indeks < daftar.size - 1 || daftar.getOrNull(indeks)?.adaBerikut == true)

    fun mulai() {
        daftar = emptyList()
        indeks = 0
        muat(null)
    }

    fun maju() {
        if (indeks < daftar.size - 1) indeks++ else daftar.getOrNull(indeks)?.berikut?.let { muat(it) }
    }

    fun mundur() {
        if (indeks > 0) indeks--
    }

    private fun muat(halaman: Page?) {
        memuat = true
        galat = null
        vm.viewModelScope.launch {
            try {
                val h = ambil(halaman)
                daftar = daftar + h
                indeks = daftar.size - 1
            } catch (e: DitahanYouTube) {
                galat = "YouTube sedang menahan permintaan (dikira bot). Coba lagi beberapa saat lagi."
            } catch (e: Exception) {
                galat = "Gagal memuat: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                memuat = false
            }
        }
    }
}

/** Halaman playlist YouTube yang dibuka dari link: dimuat bertahap (± 100 lagu per halaman). */
class HalamanPlaylistYt(val url: String) {
    var info by mutableStateOf<InfoPlaylist?>(null)
    var lagu by mutableStateOf<List<Lagu>>(emptyList())
    var berikut: Page? = null
    var adaBerikut by mutableStateOf(false)
    var memuat by mutableStateOf(false)
    var galat by mutableStateOf<String?>(null)
    /** Sedang diimpor: jumlah lagu yang sudah terkumpul (-1 = tidak sedang mengimpor). */
    var impor by mutableIntStateOf(-1)
}

class AppVM(app: Application) : AndroidViewModel(app) {
    val data = App.data

    init {
        AntreanUnduh.kabar = { kabar(it) }
        viewModelScope.launch {
            delay(15_000)
            lengkapiFotoPenyanyi()
        }
    }

    /** Sub-tab Pustaka: 0 Favorit, 1 Playlist, 2 Riwayat, 3 Offline, 4 Penyanyi. */
    var subPustaka by mutableIntStateOf(0)

    // ------------------------------------------------------------------ navigasi
    var tab by mutableStateOf(Tab.BERANDA)
    var pemutarTerbuka by mutableStateOf(false)
    var penyanyiDibuka by mutableStateOf<Penyanyi?>(null)
        private set
    var halamanPenyanyi: Halaman? = null
        private set

    private val _pesan = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val pesan: SharedFlow<String> = _pesan
    fun kabar(teks: String) {
        _pesan.tryEmit(teks)
    }

    // ------------------------------------------------------------------ pemutar
    private var controller: MediaController? = null
    private var future: ListenableFuture<MediaController>? = null
    private var tugasPosisi: Job? = null

    var laguKini by mutableStateOf<Lagu?>(null)
        private set
    var main by mutableStateOf(false)
        private set
    var memuatLagu by mutableStateOf(false)
        private set
    var posisi by mutableLongStateOf(0L)
        private set
    var durasi by mutableLongStateOf(0L)
        private set
    var antrean by mutableStateOf<List<Lagu>>(emptyList())
        private set
    var indeksKini by mutableIntStateOf(-1)
        private set
    var acak by mutableStateOf(false)
        private set
    var ulang by mutableIntStateOf(Player.REPEAT_MODE_OFF)
        private set
    var galatPutar by mutableStateOf<String?>(null)
        private set

    private val pendengar = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = perbarui()
    }

    fun sambung(ctx: Context) {
        if (future != null) return
        val token = SessionToken(ctx, ComponentName(ctx, LayananPutar::class.java))
        val f = MediaController.Builder(ctx, token).buildAsync()
        future = f
        f.addListener({
            val c = try {
                f.get()
            } catch (e: Exception) {
                return@addListener
            }
            controller = c
            c.addListener(pendengar)
            perbarui()
            tautanTertunda?.let {
                tautanTertunda = null
                bukaTautan(it)
            }
            tugasPosisi?.cancel()
            tugasPosisi = viewModelScope.launch {
                while (isActive) {
                    controller?.let { posisi = it.currentPosition.coerceAtLeast(0) }
                    delay(500)
                }
            }
        }, ContextCompat.getMainExecutor(ctx))
    }

    fun lepas() {
        tugasPosisi?.cancel()
        controller?.removeListener(pendengar)
        controller = null
        future?.let { MediaController.releaseFuture(it) }
        future = null
    }

    override fun onCleared() {
        lepas()
        super.onCleared()
    }

    private fun perbarui() {
        val c = controller ?: return
        laguKini = laguDari(c.currentMediaItem)
        main = c.isPlaying
        memuatLagu = c.playbackState == Player.STATE_BUFFERING
        AntreanUnduh.pemutarMemuat = memuatLagu
        durasi = if (c.duration == C.TIME_UNSET) 0 else c.duration.coerceAtLeast(0)
        posisi = c.currentPosition.coerceAtLeast(0)
        antrean = (0 until c.mediaItemCount).mapNotNull { laguDari(c.getMediaItemAt(it)) }
        indeksKini = c.currentMediaItemIndex
        acak = c.shuffleModeEnabled
        ulang = c.repeatMode
        cekLanjutan(c)
        galatPutar = c.playerError?.let {
            if (YouTube.sedangDitahan) "YouTube sedang menahan permintaan (dikira bot). Lagu yang sudah di cache / Offline tetap bisa diputar."
            else "Tidak bisa memutar lagu ini (${it.errorCodeName})."
        }
    }

    private fun laguDari(item: MediaItem?): Lagu? = Lagu.dariTeks(item?.mediaMetadata?.extras?.getString("lagu"))

    private fun item(lagu: Lagu): MediaItem {
        val meta = MediaMetadata.Builder()
            .setTitle(lagu.judul)
            .setArtist(lagu.artis)
            .setExtras(Bundle().apply { putString("lagu", lagu.keJson().toString()) })
        if (lagu.sampul.isNotEmpty()) meta.setArtworkUri(Uri.parse(lagu.sampul))
        return MediaItem.Builder().setMediaId(lagu.mediaId).setMediaMetadata(meta.build()).build()
    }

    /** Putar daftar ini mulai dari lagu ke-[mulai] (antrean diganti). */
    fun putar(daftar: List<Lagu>, mulai: Int = 0) {
        val c = controller ?: return kabar("Pemutar belum siap, coba lagi.")
        if (daftar.isEmpty()) return
        ambilLanjutan = null
        c.setMediaItems(daftar.map(::item), mulai.coerceIn(0, daftar.size - 1), 0)
        c.prepare()
        c.play()
    }

    fun putarAcak(daftar: List<Lagu>) = putar(daftar.shuffled())

    /**
     * Playlist YouTube yang diputar bertahap: saat antrean tinggal 5 lagu,
     * 100 lagu berikutnya diambil diam-diam. Daftar kosong = sudah habis.
     */
    private var ambilLanjutan: (suspend () -> List<Lagu>)? = null
    private var mengambilLanjutan = false

    private fun cekLanjutan(c: MediaController) {
        val ambil = ambilLanjutan ?: return
        if (mengambilLanjutan || c.mediaItemCount == 0 || c.currentMediaItemIndex < c.mediaItemCount - 5) return
        mengambilLanjutan = true
        viewModelScope.launch {
            try {
                val baru = ambil()
                if (ambilLanjutan !== ambil) return@launch
                if (baru.isEmpty()) {
                    ambilLanjutan = null
                } else {
                    val cc = controller ?: return@launch
                    val ada = antrean.map { it.kunci }.toSet()
                    cc.addMediaItems(baru.filter { it.kunci !in ada }.map(::item))
                }
            } catch (e: Exception) {
                // dicoba lagi saat lagu berikutnya
            } finally {
                mengambilLanjutan = false
            }
        }
    }

    fun antre(lagu: Lagu) {
        val c = controller ?: return
        c.addMediaItem(item(lagu))
        if (c.mediaItemCount == 1) {
            c.prepare()
            c.play()
        }
        kabar("Ditambahkan ke antrean")
    }

    fun putarBerikutnya(lagu: Lagu) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return putar(listOf(lagu))
        c.addMediaItem(c.currentMediaItemIndex + 1, item(lagu))
        kabar("Diputar setelah lagu ini")
    }

    fun putarDiAntrean(i: Int) {
        controller?.run {
            seekToDefaultPosition(i)
            prepare()
            play()
        }
    }

    fun hapusDiAntrean(i: Int) {
        controller?.removeMediaItem(i)
    }

    fun geserDiAntrean(dari: Int, ke: Int) {
        val c = controller ?: return
        if (ke in 0 until c.mediaItemCount) c.moveMediaItem(dari, ke)
    }

    fun putarJeda() {
        val c = controller ?: return
        when {
            c.isPlaying -> c.pause()
            c.playbackState == Player.STATE_IDLE || c.playerError != null -> {
                c.prepare()
                c.play()
            }
            c.playbackState == Player.STATE_ENDED -> {
                c.seekToDefaultPosition(0)
                c.play()
            }
            else -> c.play()
        }
    }

    fun berikutnya() {
        controller?.seekToNextMediaItem()
    }

    fun sebelumnya() {
        controller?.seekToPrevious()
    }

    fun geser(ms: Long) {
        controller?.seekTo(ms.coerceAtLeast(0))
        posisi = ms
    }

    fun alihAcak() {
        controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    fun alihUlang() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    // ------------------------------------------------------------------ potongan ulang (gunting)
    /** Klik 1 = tandai awal, klik 2 = tandai akhir lalu ulang dari awal, klik 3 = hapus. */
    fun alihPotong() {
        val c = controller ?: return
        val id = c.currentMediaItem?.mediaId ?: return
        val kini = c.currentPosition.coerceAtLeast(0)
        val r = Potong.rentang.value?.takeIf { it.mediaId == id }
        when {
            r == null -> {
                Potong.atur(Potong.Rentang(id, kini))
                kabar("Awal potongan ${formatWaktu(kini / 1000)} — tekan gunting lagi di bagian akhir")
            }
            r.akhirMs == null -> {
                if (kini < r.awalMs + 1000) {
                    kabar("Akhir potongan harus setelah ${formatWaktu(r.awalMs / 1000)}")
                    return
                }
                Potong.atur(r.copy(akhirMs = kini))
                c.seekTo(r.awalMs)
                posisi = r.awalMs
                kabar("Mengulang ${formatWaktu(r.awalMs / 1000)} – ${formatWaktu(kini / 1000)}")
            }
            else -> {
                Potong.atur(null)
                kabar("Potongan dihapus")
            }
        }
    }

    // ------------------------------------------------------------------ timer tidur
    var timerSampai by mutableLongStateOf(0L)
        private set
    private var tugasTimer: Job? = null

    fun setelTimer(menit: Int) {
        tugasTimer?.cancel()
        if (menit <= 0) {
            timerSampai = 0
            kabar("Timer tidur dimatikan")
            return
        }
        timerSampai = System.currentTimeMillis() + menit * 60_000L
        tugasTimer = viewModelScope.launch {
            delay(menit * 60_000L)
            controller?.pause()
            timerSampai = 0
        }
        kabar("Musik berhenti dalam $menit menit")
    }

    // ------------------------------------------------------------------ favorit, ikuti, unduh
    fun alihFavorit(lagu: Lagu) {
        kabar(if (data.alihkanFavorit(lagu)) "♥ Ditambahkan ke Favorit" else "Dihapus dari Favorit")
    }

    fun alihIkuti(nama: String, url: String) {
        if (url.isEmpty()) return kabar("Channel penyanyi ini tidak diketahui")
        val ikut = data.alihkanIkuti(nama, url, YouTube.fotoTersimpan(url) ?: "")
        kabar(if (ikut) "Mengikuti $nama" else "Berhenti mengikuti $nama")
        if (ikut && YouTube.fotoTersimpan(url) == null) {
            viewModelScope.launch { runCatching { YouTube.fotoChannel(url) }.getOrNull()?.let { data.aturFotoPenyanyi(url, it) } }
        }
    }

    /** Penyanyi yang diikuti tapi belum punya foto profil: dilengkapi pelan-pelan. */
    private suspend fun lengkapiFotoPenyanyi() {
        for (p in data.penyanyi.value.filter { it.foto.isEmpty() }) {
            if (YouTube.sedangDitahan) break
            runCatching { YouTube.fotoChannel(p.url) }.getOrNull()?.let { data.aturFotoPenyanyi(p.url, it) }
            delay(2500)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun unduh(ctx: Context, lagu: Lagu) {
        if (lagu.lokal) return kabar("Lagu ini sudah ada di HP")
        if (Unduhan.sudahDiunduh(lagu.id)) return kabar("Sudah ada di Offline")
        if (AntreanUnduh.tambah(listOf(lagu)) == 0) return kabar("Sudah ada di antrean unduhan")
        kabar(if (AntreanUnduh.info.value.antre.size > 1) "Ditambahkan ke antrean unduhan" else "Mengunduh “${lagu.judul}”…")
    }

    // ------------------------------------------------------------------ cari
    var kueri by mutableStateOf("")
    var judulCari by mutableStateOf("")
        private set
    var hasilCari: Halaman? by mutableStateOf(null)
        private set

    /** minDetik: -1 = ikut Pengaturan (durasi minimal). */
    fun cari(teks: String, maksMenit: Int = 0, judul: String = "", minDetik: Int = -1) {
        val t = teks.trim()
        if (t.isEmpty()) return
        val id = if (t.startsWith("http")) YouTube.idDari(t) else null
        if (id != null || (t.startsWith("http") && YouTube.adalahPlaylist(t))) return bukaTautan(t)
        kueri = t
        judulCari = judul.ifEmpty { "Hasil: $t" }
        val filter = data.pengaturan.value.filterCari
        val min = if (minDetik < 0) data.pengaturan.value.minDetik else minDetik
        cariTerakhir = Triple(maksMenit, judul, minDetik)
        hasilCari = Halaman(this) { p -> YouTube.cari(t, p, filter, maksMenit * 60, min) }.also { it.mulai() }
        tab = Tab.CARI
    }

    private var cariTerakhir = Triple(0, "", -1)

    fun ubahFilterCari(filter: String) {
        data.ubahPengaturan { it.copy(filterCari = filter) }
        if (kueri.isNotEmpty()) cari(kueri)
    }

    /** Durasi minimal (Pengaturan) diubah: muat ulang pencarian & rak yang ikut pengaturan. */
    fun ubahMinDurasi(detik: Int) {
        data.ubahPengaturan { it.copy(minDetik = detik) }
        val (maks, judul, min) = cariTerakhir
        if (kueri.isNotEmpty() && hasilCari != null && min < 0) cari(kueri, maks, judul)
        data.rak.value.filter { it.minDetik < 0 }.forEach { muatRak(it, paksa = true) }
    }

    private var tautanTertunda: String? = null

    /** Link YouTube yang dibagikan / ditempel: judulnya diambil, lalu langsung diputar. */
    fun bukaTautan(teks: String) {
        if (controller == null) {
            tautanTertunda = teks                      // diputar begitu pemutar tersambung
            return
        }
        val url = Regex("https?://\\S+").find(teks)?.value ?: return kabar("Tidak ada link di teks itu")
        val id = YouTube.idDari(url)
        if (id == null) {
            if (YouTube.adalahPlaylist(url)) return bukaPlaylistYouTube(url)
            return kabar("Link itu bukan link lagu / playlist YouTube")
        }
        kabar("Membuka link…")
        viewModelScope.launch {
            val lagu = try {
                YouTube.infoLagu(id)
            } catch (e: DitahanYouTube) {
                return@launch kabar("YouTube sedang menahan permintaan. Coba lagi nanti.")
            } catch (e: Exception) {
                Lagu(id = id, judul = "Lagu YouTube")
            }
            putar(listOf(lagu))
            pemutarTerbuka = true
        }
    }

    // ------------------------------------------------------------------ penyanyi
    fun bukaPenyanyi(nama: String, url: String) {
        if (url.isEmpty()) return kabar("Channel penyanyi ini tidak diketahui")
        val h = Halaman(this) { p ->
            YouTube.laguChannel(url, nama, p).also {
                // foto profil ikut diketahui saat channel dibuka
                YouTube.fotoTersimpan(url)?.let { f ->
                    if (penyanyiDibuka?.url == url) penyanyiDibuka = penyanyiDibuka?.copy(foto = f)
                    data.aturFotoPenyanyi(url, f)
                }
            }
        }
        halamanPenyanyi = h
        val foto = data.penyanyi.value.firstOrNull { it.url == url }?.foto?.ifEmpty { null } ?: YouTube.fotoTersimpan(url) ?: ""
        penyanyiDibuka = Penyanyi(nama, url, foto)
        pemutarTerbuka = false
        h.mulai()
    }

    fun tutupPenyanyi() {
        penyanyiDibuka = null
        halamanPenyanyi = null
    }

    /** Pindah tab bawah: halaman penyanyi / playlist yang terbuka ditutup. */
    fun bukaTab(t: Tab) {
        tutupPenyanyi()
        playlistDibuka = null
        playlistYt = null
        tab = t
    }

    // ------------------------------------------------------------------ playlist sendiri
    /** Playlist yang sedang dibuka (id), di dalam tab Pustaka. */
    var playlistDibuka by mutableStateOf<String?>(null)
        private set

    /** Lagu yang sedang mau dimasukkan ke playlist (jendela "Tambah ke playlist"). */
    var tambahKePlaylist by mutableStateOf<List<Lagu>?>(null)

    /** Playlist yang sedang ditawarkan untuk diunduh (jendela "Unduh playlist"). */
    var unduhPlaylist by mutableStateOf<String?>(null)

    fun bukaPlaylist(id: String) {
        penyanyiDibuka = null
        halamanPenyanyi = null
        playlistYt = null
        playlistDibuka = id
        subPustaka = 1
        tab = Tab.PUSTAKA
        pemutarTerbuka = false
    }

    fun tutupPlaylist() {
        playlistDibuka = null
    }

    fun buatPlaylist(nama: String, lagu: List<Lagu> = emptyList()): Playlist {
        val p = data.buatPlaylist(nama, lagu)
        kabar("Playlist “${p.nama}” dibuat")
        return p
    }

    /** Masukkan lagu ke playlist. Playlist offline: lagu baru langsung masuk antrean unduhan. */
    fun masukkanKePlaylist(id: String, lagu: List<Lagu>) {
        val p = data.playlistDengan(id) ?: return
        val masuk = lagu.filter { data.tambahKePlaylist(id, it) }
        when {
            masuk.isEmpty() -> kabar(if (lagu.size == 1) "Sudah ada di ${p.nama}" else "Semua lagu sudah ada di ${p.nama}")
            p.offline -> {
                val n = AntreanUnduh.tambah(masuk, p.nama)
                kabar("Ditambahkan ke ${p.nama}" + if (n > 0) " — sedang diunduh" else "")
            }
            else -> kabar(if (masuk.size == 1) "Ditambahkan ke ${p.nama}" else "${masuk.size} lagu ditambahkan ke ${p.nama}")
        }
    }

    /** Keluarkan lagu dari playlist; [hapusFile] = file unduhannya ikut dihapus. */
    fun keluarkanDariPlaylist(id: String, lagu: Lagu, hapusFile: Boolean) {
        data.hapusDariPlaylist(id, lagu.kunci)
        if (hapusFile && !lagu.lokal) Unduhan.hapusFile(getApplication<Application>(), lagu.id)
    }

    /** Perlu bertanya "hapus juga file unduhannya?" saat lagu dikeluarkan dari playlist ini? */
    fun perluTanyaFile(id: String, lagu: Lagu): Boolean {
        val p = data.playlistDengan(id) ?: return false
        return p.offline && !lagu.lokal && Unduhan.sudahDiunduh(lagu.id) && !data.diPlaylistOffline(lagu.kunci, kecuali = id)
    }

    /** Mulai unduh playlist. [semua] = seluruh lagu -> playlist ditandai offline (lagu baru ikut diunduh). */
    fun mulaiUnduhPlaylist(id: String, daftar: List<Lagu>, semua: Boolean) {
        val p = data.playlistDengan(id) ?: return
        if (semua) data.aturOfflinePlaylist(id, true)
        val n = AntreanUnduh.tambah(daftar, p.nama)
        kabar(if (n > 0) "Mengunduh $n lagu dari ${p.nama}" else "Semua lagu itu sudah diunduh")
    }

    /** Hapus file unduhan lagu-lagu playlist ini (kecuali yang masih dipakai playlist offline lain). Playlist tetap ada. */
    fun hapusUnduhanPlaylist(id: String) {
        val p = data.playlistDengan(id) ?: return
        data.aturOfflinePlaylist(id, false)
        var n = 0
        p.lagu.filter { !it.lokal && Unduhan.sudahDiunduh(it.id) && !data.diPlaylistOffline(it.kunci, kecuali = id) }
            .forEach { if (Unduhan.hapusFile(getApplication<Application>(), it.id)) n++ }
        kabar("$n file unduhan dihapus. ${p.nama} kembali jadi playlist online.")
    }

    fun hapusPlaylist(id: String) {
        val p = data.playlistDengan(id) ?: return
        data.hapusPlaylist(id)
        if (playlistDibuka == id) playlistDibuka = null
        kabar("Playlist “${p.nama}” dihapus")
    }

    // ------------------------------------------------------------------ playlist YouTube
    var playlistYt by mutableStateOf<HalamanPlaylistYt?>(null)
        private set

    fun bukaPlaylistYouTube(url: String) {
        val h = HalamanPlaylistYt(url)
        pemutarTerbuka = false
        penyanyiDibuka = null
        halamanPenyanyi = null
        playlistDibuka = null
        playlistYt = h
        h.memuat = true
        viewModelScope.launch {
            try {
                val (info, hasil) = YouTube.bukaPlaylist(url)
                h.info = info
                h.lagu = hasil.lagu
                h.berikut = hasil.berikut
                h.adaBerikut = hasil.berikut != null
            } catch (e: DitahanYouTube) {
                h.galat = "YouTube sedang menahan permintaan (dikira bot). Coba lagi beberapa saat lagi."
            } catch (e: Exception) {
                h.galat = "Playlist tidak bisa dibuka (mungkin pribadi atau sudah dihapus)."
            } finally {
                h.memuat = false
            }
        }
    }

    fun tutupPlaylistYt() {
        playlistYt = null
    }

    fun muatLagiPlaylistYt(h: HalamanPlaylistYt) {
        val hal = h.berikut ?: return
        if (h.memuat) return
        h.memuat = true
        viewModelScope.launch {
            try {
                val r = YouTube.lanjutPlaylist(h.url, hal)
                val ada = h.lagu.map { it.id }.toSet()
                h.lagu = h.lagu + r.lagu.filter { it.id !in ada }
                h.berikut = r.berikut
                h.adaBerikut = r.berikut != null
            } catch (e: Exception) {
                kabar(if (e is DitahanYouTube) "YouTube sedang menahan permintaan. Coba lagi nanti." else "Gagal memuat lagu berikutnya")
            } finally {
                h.memuat = false
            }
        }
    }

    /** Putar bertahap: yang sudah dimuat diputar, 100 berikutnya diambil saat antrean tinggal 5 lagu. */
    fun putarPlaylistYt(h: HalamanPlaylistYt, mulai: Int = 0, acak: Boolean = false) {
        if (h.lagu.isEmpty()) return
        if (acak) putarAcak(h.lagu) else putar(h.lagu, mulai)
        var hal = h.berikut
        if (hal != null && !acak) {
            ambilLanjutan = {
                val sekarang = hal
                if (sekarang == null) {
                    emptyList()
                } else {
                    val r = YouTube.lanjutPlaylist(h.url, sekarang)
                    hal = r.berikut
                    r.lagu
                }
            }
        }
    }

    /** Ambil semua halaman (maks. 1.000 lagu, dengan jeda), lalu simpan jadi playlist sendiri. */
    fun imporPlaylistYt(h: HalamanPlaylistYt) {
        if (h.impor >= 0) return
        val info = h.info ?: return
        h.impor = h.lagu.size
        viewModelScope.launch {
            val semua = h.lagu.toMutableList()
            var hal = h.berikut
            var terputus = false
            while (hal != null && semua.size < AppVM.BATAS_IMPOR) {
                delay(1200)                                              // pelan-pelan, supaya tidak dikira bot
                try {
                    val r = YouTube.lanjutPlaylist(h.url, hal)
                    val ada = semua.map { it.id }.toSet()
                    semua += r.lagu.filter { it.id !in ada }
                    hal = r.berikut
                    h.impor = semua.size
                } catch (e: Exception) {
                    terputus = true
                    break
                }
            }
            val daftar = semua.take(AppVM.BATAS_IMPOR)
            val p = data.buatPlaylist(info.nama, daftar, info.url)
            h.impor = -1
            kabar(
                "Disimpan: “${p.nama}” (${daftar.size} lagu)" + when {
                    terputus -> " — sebagian tidak terambil, coba impor lagi nanti"
                    hal != null -> " — dibatasi $AppVM.BATAS_IMPOR lagu"
                    else -> ""
                },
            )
            bukaPlaylist(p.id)
        }
    }


    // ------------------------------------------------------------------ rak beranda
    val isiRak = mutableStateMapOf<String, List<Lagu>>()
    val pesanRak = mutableStateMapOf<String, String>()

    fun muatRak(rak: Rak, paksa: Boolean = false) {
        if (!paksa && isiRak.containsKey(rak.id)) return
        pesanRak[rak.id] = "Memuat…"
        viewModelScope.launch {
            try {
                isiRak[rak.id] = YouTube.isiRak(rak, paksa)
                pesanRak.remove(rak.id)
            } catch (e: DitahanYouTube) {
                pesanRak[rak.id] = "Ditunda: YouTube sedang membatasi permintaan."
            } catch (e: Exception) {
                pesanRak[rak.id] = "Tidak bisa memuat. Periksa koneksi internet."
            }
        }
    }

    fun lihatSemuaRak(rak: Rak) {
        if (rak.channel.isNotEmpty()) bukaPenyanyi(rak.kueri, rak.channel) else cari(rak.kueri, rak.maksMenit, rak.nama, rak.minDetik)
    }

    /** Tombol "+ Rak" di halaman penyanyi: rak Beranda berisi lagu dari channel ini (maks 10 menit, tanpa kompilasi). */
    fun alihRakPenyanyi(p: Penyanyi) {
        val ada = data.rak.value.firstOrNull { it.channel == p.url }
        if (ada != null) {
            data.hapusRak(ada.id)
            kabar("Rak ${p.nama} dihapus dari Beranda")
        } else {
            data.tambahRak(p.nama, p.nama, 10, p.url)
            kabar("Rak ${p.nama} ditambahkan ke Beranda")
        }
    }

    companion object {
        const val BATAS_IMPOR = 1000
    }
}
