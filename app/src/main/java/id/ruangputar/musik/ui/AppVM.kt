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
import id.ruangputar.musik.inti.DitahanYouTube
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

class AppVM(app: Application) : AndroidViewModel(app) {
    val data = App.data

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
        durasi = if (c.duration == C.TIME_UNSET) 0 else c.duration.coerceAtLeast(0)
        posisi = c.currentPosition.coerceAtLeast(0)
        antrean = (0 until c.mediaItemCount).mapNotNull { laguDari(c.getMediaItemAt(it)) }
        indeksKini = c.currentMediaItemIndex
        acak = c.shuffleModeEnabled
        ulang = c.repeatMode
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
        c.setMediaItems(daftar.map(::item), mulai.coerceIn(0, daftar.size - 1), 0)
        c.prepare()
        c.play()
    }

    fun putarAcak(daftar: List<Lagu>) = putar(daftar.shuffled())

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
        kabar(if (data.alihkanIkuti(nama, url)) "Mengikuti $nama" else "Berhenti mengikuti $nama")
    }

    fun unduh(ctx: Context, lagu: Lagu) {
        if (lagu.lokal) return kabar("Lagu ini sudah ada di HP")
        if (data.unduhan.value.containsKey(lagu.id)) return kabar("Sudah diunduh")
        kabar("Mengunduh “${lagu.judul}”…")
        Unduhan.unduh(ctx, lagu) { kabar(it) }
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
        if (id != null) return bukaTautan(t)
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
        val id = YouTube.idDari(url) ?: return kabar("Link itu bukan link lagu YouTube")
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
        val h = Halaman(this) { p -> YouTube.laguChannel(url, nama, p) }
        halamanPenyanyi = h
        penyanyiDibuka = Penyanyi(nama, url)
        pemutarTerbuka = false
        h.mulai()
    }

    fun tutupPenyanyi() {
        penyanyiDibuka = null
        halamanPenyanyi = null
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
}
