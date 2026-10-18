package id.ruangputar.musik.inti

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Semua data aplikasi dalam satu file JSON (data.json di penyimpanan internal aplikasi). */
class Penyimpanan(private val file: File, private val scope: CoroutineScope) {

    private val _favorit = MutableStateFlow<List<Lagu>>(emptyList())
    private val _riwayat = MutableStateFlow<List<Lagu>>(emptyList())
    private val _rak = MutableStateFlow(RAK_BAWAAN)
    private val _penyanyi = MutableStateFlow<List<Penyanyi>>(emptyList())
    private val _unduhan = MutableStateFlow<Map<String, String>>(emptyMap())   // id video -> content uri
    private val _pengaturan = MutableStateFlow(Pengaturan())
    private val hitungan = HashMap<String, JSONObject>()                      // id -> {n, t, lagu}

    val favorit: StateFlow<List<Lagu>> = _favorit
    val riwayat: StateFlow<List<Lagu>> = _riwayat
    val rak: StateFlow<List<Rak>> = _rak
    val penyanyi: StateFlow<List<Penyanyi>> = _penyanyi
    val unduhan: StateFlow<Map<String, String>> = _unduhan
    val pengaturan: StateFlow<Pengaturan> = _pengaturan

    private var tugasSimpan: Job? = null

    init {
        muat()
    }

    private fun muat() {
        val d = try {
            if (file.exists()) JSONObject(file.readText()) else return
        } catch (e: Exception) {
            file.renameTo(File(file.parentFile, "data.rusak.json"))
            return
        }
        _favorit.value = d.optJSONArray("favorit").daftar(Lagu::dariJson)
        _riwayat.value = d.optJSONArray("riwayat").daftar(Lagu::dariJson)
        d.optJSONArray("rak")?.let { _rak.value = it.daftar(Rak::dariJson) }
        _penyanyi.value = d.optJSONArray("penyanyi").daftar(Penyanyi::dariJson)
        _pengaturan.value = Pengaturan.dariJson(d.optJSONObject("pengaturan"))
        d.optJSONObject("unduhan")?.let { o ->
            _unduhan.value = o.keys().asSequence().associateWith { o.optString(it) }
        }
        d.optJSONObject("hitungan")?.let { o ->
            o.keys().forEach { k -> o.optJSONObject(k)?.let { hitungan[k] = it } }
        }
    }

    /** Disimpan sebentar kemudian (beberapa perubahan beruntun = sekali tulis). */
    private fun simpanNanti() {
        tugasSimpan?.cancel()
        tugasSimpan = scope.launch {
            delay(400)
            simpanSekarang()
        }
    }

    @Synchronized
    fun simpanSekarang() {
        val d = JSONObject()
            .put("versi", 1)
            .put("favorit", JSONArray(_favorit.value.map { it.keJson() }))
            .put("riwayat", JSONArray(_riwayat.value.map { it.keJson() }))
            .put("rak", JSONArray(_rak.value.map { it.keJson() }))
            .put("penyanyi", JSONArray(_penyanyi.value.map { it.keJson() }))
            .put("pengaturan", _pengaturan.value.keJson())
            .put("unduhan", JSONObject(_unduhan.value as Map<*, *>))
            .put("hitungan", JSONObject().also { o -> synchronized(hitungan) { hitungan.forEach { (k, v) -> o.put(k, v) } } })
        val sementara = File(file.parentFile, file.name + ".tmp")
        sementara.writeText(d.toString())
        sementara.renameTo(file)
    }

    // ------------------------------------------------------------ favorit
    fun apakahFavorit(lagu: Lagu) = _favorit.value.any { it.kunci == lagu.kunci }

    fun alihkanFavorit(lagu: Lagu): Boolean {
        val ada = apakahFavorit(lagu)
        _favorit.value = if (ada) _favorit.value.filterNot { it.kunci == lagu.kunci } else listOf(lagu) + _favorit.value
        simpanNanti()
        return !ada
    }

    // ------------------------------------------------------------ riwayat & sering diputar
    fun catatPutar(lagu: Lagu) {
        _riwayat.value = (listOf(lagu) + _riwayat.value.filterNot { it.kunci == lagu.kunci }).take(300)
        if (!lagu.lokal) {
            synchronized(hitungan) {
                val h = hitungan.getOrPut(lagu.kunci) { JSONObject().put("n", 0) }
                h.put("n", h.optInt("n") + 1).put("t", System.currentTimeMillis()).put("lagu", lagu.keJson())
            }
        }
        simpanNanti()
    }

    fun hapusRiwayat() {
        _riwayat.value = emptyList()
        simpanNanti()
    }

    /** Diurutkan dari paling sering diputar; yang sudah lama tidak diputar sedikit diturunkan. */
    fun seringDiputar(jumlah: Int = 20): List<Lagu> {
        val kini = System.currentTimeMillis()
        return synchronized(hitungan) {
            hitungan.values.filter { it.optInt("n") >= 2 }
                .map { h ->
                    val hari = (kini - h.optLong("t")) / 86_400_000.0
                    (h.optInt("n") / (1 + hari / 30)) to Lagu.dariJson(h.optJSONObject("lagu") ?: JSONObject())
                }
                .sortedByDescending { it.first }
                .map { it.second }
                .filter { it.id.isNotEmpty() }
                .take(jumlah)
        }
    }

    // ------------------------------------------------------------ rak
    fun tambahRak(nama: String, kueri: String, maksMenit: Int, channel: String = "", minDetik: Int = -1) {
        _rak.value = _rak.value + Rak("r${System.currentTimeMillis()}", nama, kueri, maksMenit, channel, minDetik)
        simpanNanti()
    }

    fun ubahRak(rak: Rak) {
        _rak.value = _rak.value.map { if (it.id == rak.id) rak else it }
        simpanNanti()
    }

    fun punyaRakChannel(url: String) = url.isNotEmpty() && _rak.value.any { it.channel == url }

    fun hapusRak(id: String) {
        _rak.value = _rak.value.filterNot { it.id == id }
        simpanNanti()
    }

    fun geserRak(id: String, arah: Int) {
        val d = _rak.value.toMutableList()
        val i = d.indexOfFirst { it.id == id }
        val j = i + arah
        if (i in d.indices && j in d.indices) {
            d[i] = d[j].also { d[j] = d[i] }
            _rak.value = d
            simpanNanti()
        }
    }

    fun kembalikanRakBawaan() {
        _rak.value = RAK_BAWAAN
        simpanNanti()
    }

    // ------------------------------------------------------------ penyanyi
    fun mengikuti(url: String) = url.isNotEmpty() && _penyanyi.value.any { it.url == url }

    fun alihkanIkuti(nama: String, url: String): Boolean {
        if (url.isEmpty()) return false
        val ada = mengikuti(url)
        _penyanyi.value = if (ada) _penyanyi.value.filterNot { it.url == url } else listOf(Penyanyi(nama, url)) + _penyanyi.value
        simpanNanti()
        return !ada
    }

    // ------------------------------------------------------------ unduhan & pengaturan
    fun catatUnduhan(id: String, uri: String) {
        _unduhan.value = _unduhan.value + (id to uri)
        simpanNanti()
    }

    fun lupakanUnduhan(id: String) {
        _unduhan.value = _unduhan.value - id
        simpanNanti()
    }

    fun ubahPengaturan(ubah: (Pengaturan) -> Pengaturan) {
        _pengaturan.value = ubah(_pengaturan.value)
        simpanNanti()
    }
}
