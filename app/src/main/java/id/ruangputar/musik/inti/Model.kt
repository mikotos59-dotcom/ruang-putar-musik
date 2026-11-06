package id.ruangputar.musik.inti

import org.json.JSONArray
import org.json.JSONObject

/** Satu lagu. Online: [id] = ID video YouTube. Offline: [uri] = content:// file di HP. */
data class Lagu(
    val id: String,
    val judul: String,
    val artis: String = "",
    val durasi: Long = 0,          // detik
    val sampul: String = "",       // URL gambar
    val channel: String = "",      // URL channel penyanyi
    val uri: String = "",          // file lokal (Offline)
) {
    val lokal: Boolean get() = uri.isNotEmpty()

    /** Kunci unik untuk favorit/riwayat. */
    val kunci: String get() = if (lokal) uri else id

    /** mediaId yang dikirim ke layanan pemutar. */
    val mediaId: String get() = if (lokal) "file:$uri" else "yt:$id"

    val urlHalaman: String get() = "https://www.youtube.com/watch?v=$id"

    fun keJson(): JSONObject = JSONObject()
        .put("id", id).put("judul", judul).put("artis", artis).put("durasi", durasi)
        .put("sampul", sampul).put("channel", channel).put("uri", uri)

    companion object {
        fun dariJson(o: JSONObject) = Lagu(
            id = o.optString("id"),
            judul = o.optString("judul", "Tanpa judul"),
            artis = o.optString("artis"),
            durasi = o.optLong("durasi"),
            sampul = o.optString("sampul"),
            channel = o.optString("channel"),
            uri = o.optString("uri"),
        )

        fun dariTeks(t: String?): Lagu? = try {
            if (t.isNullOrEmpty()) null else dariJson(JSONObject(t))
        } catch (e: Exception) {
            null
        }
    }
}

/** Rak Beranda: hasil pencarian kata kunci, dengan batas durasi (0 = semua). */
data class Rak(
    val id: String,
    val nama: String,
    val kueri: String,
    val maksMenit: Int = 5,
    val channel: String = "",   // diisi = rak lagu dari channel penyanyi ini (kueri = nama penyanyi)
    val minDetik: Int = -1,     // durasi minimal; -1 = ikut Pengaturan, 0 = tanpa batas
) {
    /** Durasi minimal yang berlaku untuk rak ini (detik). */
    fun minBerlaku(p: Pengaturan) = if (minDetik < 0) p.minDetik else minDetik

    fun keJson(): JSONObject = JSONObject().put("id", id).put("nama", nama).put("kueri", kueri)
        .put("maks", maksMenit).put("channel", channel).put("min", minDetik)

    companion object {
        fun dariJson(o: JSONObject) = Rak(
            o.optString("id"), o.optString("nama"), o.optString("kueri"), o.optInt("maks", 5), o.optString("channel"), o.optInt("min", -1),
        )
    }
}

data class Penyanyi(val nama: String, val url: String) {
    fun keJson(): JSONObject = JSONObject().put("nama", nama).put("url", url)

    companion object {
        fun dariJson(o: JSONObject) = Penyanyi(o.optString("nama"), o.optString("url"))
    }
}

data class Pengaturan(
    val tema: String = "batik",          // gelap | terang | batik
    val kualitas: String = "tinggi",     // tinggi | hemat (kuota)
    val kecepatan: String = "normal",    // rendah | normal | tanpa
    val cacheMb: Int = 512,
    val filterCari: String = "lagu",     // lagu (YouTube Music) | video (semua video)
    val minDetik: Int = 60,              // sembunyikan lagu lebih pendek dari ini (0 = tampilkan semua)
) {
    fun keJson(): JSONObject = JSONObject().put("tema", tema).put("kualitas", kualitas)
        .put("kecepatan", kecepatan).put("cacheMb", cacheMb).put("filterCari", filterCari).put("minDetik", minDetik)

    val batasByte: Long
        get() = when (kecepatan) {
            "rendah" -> 1L * 1024 * 1024
            "tanpa" -> 0L
            else -> 3L * 1024 * 1024
        }

    companion object {
        fun dariJson(o: JSONObject?) = if (o == null) Pengaturan() else Pengaturan(
            tema = o.optString("tema", "batik"),
            kualitas = o.optString("kualitas", "tinggi"),
            kecepatan = o.optString("kecepatan", "normal"),
            cacheMb = o.optInt("cacheMb", 512),
            filterCari = o.optString("filterCari", "lagu"),
            minDetik = o.optInt("minDetik", 60),
        )
    }
}

val RAK_BAWAAN = listOf(
    Rak("pop-id", "Pop Indonesia", "lagu pop indonesia terbaru", 5),
    Rak("sunda", "Lagu Sunda", "lagu sunda populer", 5),
    Rak("phonk", "Phonk / Funk", "phonk funk", 5),
    Rak("lofi", "Lo-fi", "lofi hip hop chill", 0),
)

fun <T> JSONArray?.daftar(ubah: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    val hasil = ArrayList<T>(length())
    for (i in 0 until length()) optJSONObject(i)?.let { hasil.add(ubah(it)) }
    return hasil
}

fun formatWaktu(detik: Long): String {
    val d = detik.coerceAtLeast(0)
    val j = d / 3600
    val m = (d % 3600) / 60
    val s = d % 60
    return if (j > 0) "%d:%02d:%02d".format(j, m, s) else "%d:%02d".format(m, s)
}
