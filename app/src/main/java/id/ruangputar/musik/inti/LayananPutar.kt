package id.ruangputar.musik.inti

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import id.ruangputar.musik.App
import id.ruangputar.musik.MainActivity
import kotlinx.coroutines.runBlocking
import java.io.FileNotFoundException
import java.io.IOException

/**
 * Pemutar di latar belakang: notifikasi media, layar kunci, tombol headset/Bluetooth.
 *
 * Antrean berisi alamat "rp://<idVideo>". Alamat stream YouTube asli baru dicari saat lagu itu
 * akan dimuat (ResolvingDataSource), lalu datanya lewat cache — lagu yang sudah pernah diputar
 * penuh diambil dari cache tanpa internet.
 */
@OptIn(UnstableApi::class)
class LayananPutar : MediaSessionService() {

    private var sesi: MediaSession? = null
    private val sudahDicobaUlang = HashSet<String>()

    override fun onCreate() {
        super.onCreate()
        val app = application as App

        val hulu = OkHttpDataSource.Factory(Jaringan.media).setUserAgent(Jaringan.AGEN)
        val penerjemah = ResolvingDataSource.Factory(hulu) { spec -> terjemahkan(spec) }
        val denganCache = CacheDataSource.Factory()
            .setCache(app.cache)
            .setUpstreamDataSourceFactory(penerjemah)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        // file lokal (content://, file://) dibaca langsung; selain itu lewat cache + YouTube
        val sumber = DefaultDataSource.Factory(this, denganCache)

        val pemutar = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(sumber))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)       // headset dicabut -> jeda
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        pemutar.addListener(PendengarPemutar(pemutar))

        val buka = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        sesi = MediaSession.Builder(this, pemutar)
            .setCallback(PanggilanSesi())
            .setSessionActivity(buka)
            .build()
    }

    /** rp://ID -> alamat stream YouTube (dicari saat itu juga, di thread pemuat ExoPlayer). */
    private fun terjemahkan(spec: DataSpec): DataSpec {
        if (spec.uri.scheme != "rp") return spec
        val id = spec.uri.host ?: return spec
        val hemat = App.data.pengaturan.value.kualitas == "hemat"
        val suara = try {
            runBlocking { YouTube.suara(id, hemat) }
        } catch (e: Exception) {
            throw IOException(e.message ?: "Gagal mengambil alamat lagu", e)
        }
        return spec.withUri(Uri.parse(suara.url))
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = sesi

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = sesi?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0 || p.playbackState == Player.STATE_ENDED) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        sesi?.run {
            player.release()
            release()
        }
        sesi = null
        App.data.simpanSekarang()
        super.onDestroy()
    }

    /** Item dari aplikasi hanya membawa mediaId + info lagu; alamat diisi di sini. */
    private inner class PanggilanSesi : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> =
            Futures.immediateFuture(mediaItems.map { lengkapi(it) }.toMutableList())
    }

    private fun lengkapi(item: MediaItem): MediaItem {
        val id = item.mediaId
        return when {
            id.startsWith("file:") -> item.buildUpon().setUri(Uri.parse(id.removePrefix("file:"))).build()
            id.startsWith("yt:") -> {
                val vid = id.removePrefix("yt:")
                val lokal = App.data.unduhan.value[vid]            // sudah diunduh -> putar file-nya
                if (lokal != null) {
                    item.buildUpon().setUri(Uri.parse(lokal)).build()
                } else {
                    item.buildUpon().setUri(Uri.parse("rp://$vid")).setCustomCacheKey(vid).build()
                }
            }
            else -> item
        }
    }

    private inner class PendengarPemutar(private val p: ExoPlayer) : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val lagu = Lagu.dariTeks(mediaItem?.mediaMetadata?.extras?.getString("lagu")) ?: return
            App.data.catatPutar(lagu)
        }

        override fun onPlayerError(error: PlaybackException) {
            val item = p.currentMediaItem ?: return
            val vid = item.mediaId.removePrefix("yt:")
            if (YouTube.adalahGalatBot(error)) return       // ditahan YouTube: jangan mencoba ulang
            var x: Throwable? = error
            var ditolak = false
            var fileHilang = false
            while (x != null) {
                if (x is HttpDataSource.InvalidResponseCodeException && x.responseCode in setOf(403, 410)) ditolak = true
                if (x is FileNotFoundException || x is SecurityException) fileHilang = true
                x = x.cause
            }
            if (!item.mediaId.startsWith("yt:")) return
            if (fileHilang && App.data.unduhan.value.containsKey(vid)) {
                // file unduhan sudah dihapus dari HP: putar dari internet lagi
                App.data.lupakanUnduhan(vid)
                p.replaceMediaItem(p.currentMediaItemIndex, lengkapi(item))
                p.prepare()
                return
            }
            if (ditolak && sudahDicobaUlang.add(vid)) {
                // alamat stream kedaluwarsa / ditolak: ambil alamat baru sekali
                YouTube.lupakan(vid)
                p.prepare()
            }
        }
    }
}
