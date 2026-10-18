package id.ruangputar.musik.inti

import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.util.concurrent.TimeUnit

/**
 * Dua klien HTTP:
 *  - [api]   : permintaan kecil ke YouTube (cari, info lagu) — tanpa batas kecepatan.
 *  - [media] : unduhan suara (pemutar & Offline) — lewat [Pembatas], batas BERSAMA untuk semua unduhan.
 */
object Jaringan {
    const val AGEN = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"

    val pembatas = Pembatas()

    val api: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val media: OkHttpClient = api.newBuilder()
        .addNetworkInterceptor(PembatasInterceptor(pembatas))
        .build()
}

/** Token bucket sederhana. batas = byte/detik, 0 = tanpa batas. */
class Pembatas {
    @Volatile
    var batas: Long = 0
        set(nilai) {
            synchronized(this) {
                field = nilai.coerceAtLeast(0)
                token = 0.0
                waktu = System.nanoTime()
            }
        }

    private var token = 0.0
    private var waktu = System.nanoTime()

    fun ambil(n: Long) {
        while (true) {
            val tunggu: Long
            synchronized(this) {
                val b = batas
                if (b <= 0) return
                val kini = System.nanoTime()
                val maks = b * 0.5
                token = minOf(maks, token + (kini - waktu) / 1e9 * b)
                waktu = kini
                if (token >= n || token >= maks) {
                    token -= n
                    return
                }
                tunggu = (((minOf(n.toDouble(), maks) - token) / b) * 1000).toLong().coerceIn(5, 250)
            }
            try {
                Thread.sleep(tunggu)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return
            }
        }
    }
}

private class PembatasInterceptor(private val pembatas: Pembatas) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val r = chain.proceed(chain.request())
        val badan = r.body ?: return r
        return r.newBuilder().body(BadanTerbatas(badan, pembatas)).build()
    }
}

private class BadanTerbatas(private val asli: ResponseBody, private val pembatas: Pembatas) : ResponseBody() {
    private val sumber: BufferedSource by lazy {
        object : ForwardingSource(asli.source()) {
            override fun read(sink: Buffer, byteCount: Long): Long {
                val n = minOf(byteCount, 16L * 1024)
                pembatas.ambil(n)
                return super.read(sink, n)
            }
        }.buffer()
    }

    override fun contentType(): MediaType? = asli.contentType()
    override fun contentLength(): Long = asli.contentLength()
    override fun source(): BufferedSource = sumber
}

/** Penghubung NewPipeExtractor ke OkHttp. HTTP 429 = YouTube meminta captcha (dikira bot). */
class DownloaderNewPipe : Downloader() {
    override fun execute(request: Request): Response {
        val metode = request.httpMethod()
        val data = request.dataToSend()
        val badan = when {
            data != null -> data.toRequestBody()
            metode == "POST" || metode == "PUT" -> ByteArray(0).toRequestBody()
            else -> null
        }
        val b = okhttp3.Request.Builder()
            .method(metode, badan)
            .url(request.url())
            .header("User-Agent", Jaringan.AGEN)
        request.headers().forEach { (nama, nilai) ->
            b.removeHeader(nama)
            nilai.forEach { b.addHeader(nama, it) }
        }
        Jaringan.api.newCall(b.build()).execute().use { r ->
            if (r.code == 429) throw ReCaptchaException("reCaptcha Challenge requested", request.url())
            val teks = r.body?.string()
            return Response(r.code, r.message, r.headers.toMultimap(), teks, r.request.url.toString())
        }
    }
}
