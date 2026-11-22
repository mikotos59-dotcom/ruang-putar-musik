package id.ruangputar.musik.inti

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Potongan ulang (A–B) lagu yang sedang diputar — tombol gunting.
 * Diatur dari aplikasi, dijaga oleh LayananPutar (tetap jalan saat layar mati).
 * Hilang otomatis saat lagu berganti.
 */
object Potong {
    data class Rentang(val mediaId: String, val awalMs: Long, val akhirMs: Long? = null)

    private val _rentang = MutableStateFlow<Rentang?>(null)
    val rentang: StateFlow<Rentang?> = _rentang

    /** Dipanggil LayananPutar supaya mulai memantau posisi. */
    @Volatile var saatBerubah: () -> Unit = {}

    fun atur(r: Rentang?) {
        _rentang.value = r
        saatBerubah()
    }
}
