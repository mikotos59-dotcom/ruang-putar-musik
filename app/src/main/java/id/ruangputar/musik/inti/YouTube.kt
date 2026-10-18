package id.ruangputar.musik.inti

import id.ruangputar.musik.App

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import java.util.concurrent.ConcurrentHashMap

/** Satu halaman hasil (pencarian / channel) + penanda halaman berikutnya. */
class Hasil(val lagu: List<Lagu>, val berikut: Page?) {
    val adaBerikut: Boolean get() = berikut != null
}

/** Alamat suara yang siap diputar/diunduh. */
class Suara(val url: String, val mime: String, val ext: String, val bitrate: Int, val berlakuSampai: Long)

class DitahanYouTube : Exception("YouTube sementara menahan permintaan (dikira bot)")

object YouTube {
    private val layanan get() = ServiceList.YouTube

    fun mulai() {
        NewPipe.init(DownloaderNewPipe(), Localization("id", "ID"), ContentCountry("ID"))
    }

    // ------------------------------------------------------------------ "dikira bot"
    private val _ditahanSampai = MutableStateFlow(0L)

    /** Waktu (ms) sampai permintaan latar (rak, dsb.) diistirahatkan karena YouTube mencurigai bot. */
    val ditahanSampai: StateFlow<Long> = _ditahanSampai

    val sedangDitahan: Boolean get() = System.currentTimeMillis() < _ditahanSampai.value

    fun adalahGalatBot(e: Throwable?): Boolean {
        var x = e
        while (x != null) {
            if (x is ReCaptchaException || x is DitahanYouTube) return true
            val nama = x.javaClass.simpleName
            val pesan = (x.message ?: "").lowercase()
            if ("NotBot" in nama || "not a bot" in pesan || "sign in to confirm" in pesan || "429" in pesan) return true
            x = x.cause
        }
        return false
    }

    /** Jalankan permintaan; galat "bukan bot" -> istirahat 30 menit (tanpa mencoba ulang). */
    private suspend fun <T> jalankan(blok: () -> T): T = withContext(Dispatchers.IO) {
        try {
            blok().also { Pembaruan.catatBerhasil() }
        } catch (e: Exception) {
            if (adalahGalatBot(e)) {
                _ditahanSampai.value = System.currentTimeMillis() + 30 * 60_000L
                throw DitahanYouTube()
            }
            // gagal berkali-kali bisa berarti YouTube berubah -> cek apakah ada versi aplikasi baru
            if (e !is java.io.IOException) Pembaruan.catatGagal(App.aku)
            throw e
        }
    }

    // ------------------------------------------------------------------ pencarian
    /**
     * filter "lagu" = YouTube Music (lagu satuan, tanpa kompilasi); "video" = semua video.
     * maksDetik > 0: hanya yang durasinya <= itu.
     */
    suspend fun cari(kueri: String, halaman: Page? = null, filter: String = "lagu", maksDetik: Int = 0): Hasil = jalankan {
        val jenis = if (filter == "video") YoutubeSearchQueryHandlerFactory.VIDEOS else YoutubeSearchQueryHandlerFactory.MUSIC_SONGS
        val qh = layanan.searchQHFactory.fromQuery(kueri, listOf(jenis), "")
        val (item, berikut) = if (halaman == null) {
            val info = SearchInfo.getInfo(layanan, qh)
            info.relatedItems to info.nextPage
        } else {
            val p = SearchInfo.getMoreItems(layanan, qh, halaman)
            p.items to p.nextPage
        }
        Hasil(keLagu(item, maksDetik), berikut?.takeIf { Page.isValid(it) })
    }

    private fun keLagu(item: List<InfoItem>, maksDetik: Int): List<Lagu> = item
        .filterIsInstance<StreamInfoItem>()
        .filter { it.streamType != StreamType.LIVE_STREAM && it.streamType != StreamType.AUDIO_LIVE_STREAM }
        .filter { maksDetik <= 0 || (it.duration in 1..maksDetik.toLong()) }
        .mapNotNull { s ->
            val id = idDari(s.url) ?: return@mapNotNull null
            Lagu(
                id = id,
                judul = s.name ?: "Tanpa judul",
                artis = s.uploaderName ?: "",
                durasi = s.duration.coerceAtLeast(0),
                sampul = gambar(s.thumbnails, 320),
                channel = s.uploaderUrl ?: "",
            )
        }
        .distinctBy { it.id }

    fun idDari(url: String?): String? = try {
        if (url.isNullOrEmpty()) null else layanan.streamLHFactory.getId(url)
    } catch (e: Exception) {
        null
    }

    /** Gambar terbesar yang tersedia (diperkecil lagi saat ditampilkan, jadi tetap tajam). */
    private fun gambar(daftar: List<Image>, @Suppress("UNUSED_PARAMETER") targetTinggi: Int): String {
        if (daftar.isEmpty()) return ""
        return (daftar.filter { it.height > 0 }.maxByOrNull { it.height } ?: daftar.last()).url
    }

    // ------------------------------------------------------------------ alamat suara
    private val simpananSuara = ConcurrentHashMap<String, Suara>()

    /** Alamat stream suara. Disimpan selama masih berlaku (lebih sedikit permintaan ke YouTube). */
    suspend fun suara(id: String, hemat: Boolean, untukUnduh: Boolean = false): Suara {
        val k = "$id|$hemat|$untukUnduh"
        simpananSuara[k]?.let { if (it.berlakuSampai > System.currentTimeMillis()) return it }
        val s = jalankan {
            val info = StreamInfo.getInfo(layanan, "https://www.youtube.com/watch?v=$id")
            pilihSuara(info.audioStreams, hemat, untukUnduh)
        }
        simpananSuara[k] = s
        return s
    }

    /**
     * Semua pilihan stream untuk diunduh, urut dari yang paling cocok:
     * M4A kualitas tertinggi dulu, lalu format lain. Selalu alamat baru (tidak dari simpanan),
     * supaya kalau satu ditolak YouTube (HTTP 403) masih ada cadangan.
     */
    suspend fun suaraUnduh(id: String): List<Suara> = jalankan {
        val info = StreamInfo.getInfo(layanan, "https://www.youtube.com/watch?v=$id")
        val calon = info.audioStreams.filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
        val utama = calon.filter { it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL }.ifEmpty { calon }
        if (utama.isEmpty()) throw IllegalStateException("Tidak ada stream suara untuk lagu ini.")
        utama.sortedWith(compareBy<AudioStream>({ if (it.format == MediaFormat.M4A) 0 else 1 }, { -bitrate(it) }))
            .map { keSuara(it) }
            .distinctBy { it.url }
    }

    /** Judul, penyanyi, dsb. dari ID video (link yang dibagikan). Alamat suaranya ikut disimpan. */
    suspend fun infoLagu(id: String): Lagu = jalankan {
        val info = StreamInfo.getInfo(layanan, "https://www.youtube.com/watch?v=$id")
        runCatching { simpananSuara["$id|false|false"] = pilihSuara(info.audioStreams, false, false) }
        Lagu(
            id = id,
            judul = info.name ?: "Tanpa judul",
            artis = info.uploaderName ?: "",
            durasi = info.duration.coerceAtLeast(0),
            sampul = gambar(info.thumbnails, 320),
            channel = info.uploaderUrl ?: "",
        )
    }

    /** Dipanggil saat alamat ditolak (HTTP 403): ambil alamat baru berikutnya. */
    fun lupakan(id: String) {
        simpananSuara.keys.removeAll { it.startsWith("$id|") }
    }

    private fun pilihSuara(daftar: List<AudioStream>, hemat: Boolean, untukUnduh: Boolean): Suara {
        val calon = daftar.filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            // suara asli dulu (bukan dubbing / deskripsi audio)
            .sortedBy { if (it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL) 0 else 1 }
        if (calon.isEmpty()) throw IllegalStateException("Tidak ada stream suara untuk lagu ini.")
        val utama = calon.filter { it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL }
            .ifEmpty { calon }
        // unduhan: utamakan M4A (bisa diputar di semua HP dan pemutar musik)
        val kumpulan = if (untukUnduh) utama.filter { it.format == MediaFormat.M4A }.ifEmpty { utama } else utama
        val pilih = if (hemat) {
            kumpulan.filter { bitrate(it) >= 48_000 }.minByOrNull { bitrate(it) } ?: kumpulan.minBy { bitrate(it) }
        } else {
            kumpulan.maxBy { bitrate(it) }
        }
        return keSuara(pilih)
    }

    private fun keSuara(pilih: AudioStream): Suara {
        val url = pilih.content
        val (mime, ext) = when (pilih.format) {
            MediaFormat.M4A -> "audio/mp4" to "m4a"
            MediaFormat.WEBMA_OPUS, MediaFormat.OPUS -> "audio/webm" to "webm"
            MediaFormat.WEBMA -> "audio/webm" to "webm"
            MediaFormat.MP3 -> "audio/mpeg" to "mp3"
            else -> "audio/mp4" to "m4a"
        }
        return Suara(url, mime, ext, bitrate(pilih), kedaluwarsa(url))
    }

    private fun bitrate(a: AudioStream) = if (a.averageBitrate > 0) a.averageBitrate * 1000 else a.bitrate

    private fun kedaluwarsa(url: String): Long {
        val kini = System.currentTimeMillis()
        val batas = kini + 3 * 3600_000L
        val m = Regex("[?&/]expire[=/](\\d{9,11})").find(url) ?: return batas
        return minOf(batas, m.groupValues[1].toLong() * 1000 - 15 * 60_000L)
    }

    // ------------------------------------------------------------------ penyanyi (channel)
    private val tabChannel = ConcurrentHashMap<String, ListLinkHandler>()

    /** Channel yang tidak punya tab lagu/video (mis. channel "Topic" / artis YouTube Music) -> cari nama penyanyinya. */
    private val channelLewatCari = ConcurrentHashMap<String, Boolean>()

    suspend fun laguChannel(url: String, nama: String, halaman: Page? = null): Hasil {
        if (channelLewatCari[url] == true) return cariPenyanyi(nama, halaman)
        val dariTab = try {
            jalankan {
                val tab = tabChannel[url] ?: run {
                    val tabs = ChannelInfo.getInfo(layanan, url).tabs
                    (tabs.firstOrNull { ChannelTabs.TRACKS in it.contentFilters }
                        ?: tabs.firstOrNull { ChannelTabs.VIDEOS in it.contentFilters })
                        ?.also { tabChannel[url] = it }
                }
                when {
                    tab == null -> null
                    halaman == null -> ChannelTabInfo.getInfo(layanan, tab).let { t ->
                        Hasil(keLagu(t.relatedItems, 0), t.nextPage?.takeIf { Page.isValid(it) })
                    }
                    else -> ChannelTabInfo.getMoreItems(layanan, tab, halaman).let { p ->
                        Hasil(keLagu(p.items, 0), p.nextPage?.takeIf { Page.isValid(it) })
                    }
                }
            }
        } catch (e: DitahanYouTube) {
            throw e
        } catch (e: Exception) {
            null
        }
        if (dariTab != null && (dariTab.lagu.isNotEmpty() || halaman != null)) return dariTab
        if (nama.isBlank()) throw IllegalStateException("Channel ini tidak punya daftar lagu.")
        channelLewatCari[url] = true
        return cariPenyanyi(nama, null)
    }

    /** Lagu penyanyi lewat pencarian YouTube Music; yang penyanyinya cocok ditaruh paling atas. */
    private suspend fun cariPenyanyi(nama: String, halaman: Page?): Hasil {
        val h = cari(nama, halaman, "lagu")
        val n = nama.lowercase()
        val (cocok, lain) = h.lagu.partition { n in it.artis.lowercase() }
        return Hasil(cocok + lain, h.berikut)
    }

    // ------------------------------------------------------------------ rak Beranda (simpanan 12 jam)
    private val simpananRak = ConcurrentHashMap<String, Pair<Long, List<Lagu>>>()

    suspend fun isiRak(rak: Rak, paksa: Boolean = false): List<Lagu> {
        val k = "${rak.kueri}|${rak.maksMenit}|${rak.channel}"
        val c = simpananRak[k]
        if (!paksa && c != null && System.currentTimeMillis() - c.first < 12 * 3600_000L) return c.second
        if (sedangDitahan) return c?.second ?: throw DitahanYouTube()
        val hasil = if (rak.channel.isNotEmpty()) {
            val maks = rak.maksMenit * 60L
            laguChannel(rak.channel, rak.kueri).lagu.filter { maks <= 0 || it.durasi in 1..maks }.take(20)
        } else {
            cari(rak.kueri, null, "lagu", rak.maksMenit * 60).lagu.take(20)
        }
        simpananRak[k] = System.currentTimeMillis() to hasil
        return hasil
    }
}
