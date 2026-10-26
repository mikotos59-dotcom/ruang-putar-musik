package id.ruangputar.musik.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp

private val Gelap = darkColorScheme(
    primary = Color(0xFFFF7A59),
    onPrimary = Color(0xFF1A0E09),
    secondary = Color(0xFFE6B07A),
    background = Color(0xFF121214),
    onBackground = Color(0xFFEDEBE7),
    surface = Color(0xFF17171A),
    onSurface = Color(0xFFEDEBE7),
    surfaceVariant = Color(0xFF26262B),
    onSurfaceVariant = Color(0xFFA9A7A2),
    surfaceContainer = Color(0xFF1C1C20),
    secondaryContainer = Color(0xFF3A2A24),
    onSecondaryContainer = Color(0xFFFFDBCF),
    outline = Color(0xFF3A3A40),
)

private val Terang = lightColorScheme(
    primary = Color(0xFFD9512C),
    onPrimary = Color.White,
    secondary = Color(0xFF9A6A3A),
    background = Color(0xFFF7F5F1),
    onBackground = Color(0xFF1C1B1A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1B1A),
    surfaceVariant = Color(0xFFEDEAE4),
    onSurfaceVariant = Color(0xFF6A6761),
    surfaceContainer = Color(0xFFF1EEE9),
    secondaryContainer = Color(0xFFFBE1D6),
    onSecondaryContainer = Color(0xFF4A1A0C),
    outline = Color(0xFFD4D0C8),
)

/** Batik: hitam pekat, aksen gading & sogan (cokelat batik). */
private val Batik = darkColorScheme(
    primary = Color(0xFFEDE3CF),
    onPrimary = Color(0xFF14110D),
    secondary = Color(0xFFC49A6C),
    background = Color(0xFF0B0A09),
    onBackground = Color(0xFFEDE3CF),
    surface = Color(0xFF12100E),
    onSurface = Color(0xFFEDE3CF),
    surfaceVariant = Color(0xFF221E1A),
    onSurfaceVariant = Color(0xFFA89C88),
    surfaceContainer = Color(0xFF171411),
    secondaryContainer = Color(0xFF2E2720),
    onSecondaryContainer = Color(0xFFEDE3CF),
    outline = Color(0xFF3B342C),
)

/** Batik terang: kain gading/krem, aksen sogan (cokelat batik) — kebalikan Batik gelap. */
private val BatikTerang = lightColorScheme(
    primary = Color(0xFF7A4A22),
    onPrimary = Color(0xFFFBF5EA),
    secondary = Color(0xFF9C6532),
    background = Color(0xFFF5EDDF),
    onBackground = Color(0xFF2A1D12),
    surface = Color(0xFFFBF6EC),
    onSurface = Color(0xFF2A1D12),
    surfaceVariant = Color(0xFFE9DCC6),
    onSurfaceVariant = Color(0xFF6B5641),
    surfaceContainer = Color(0xFFEFE4D1),
    secondaryContainer = Color(0xFFE3CFAE),
    onSecondaryContainer = Color(0xFF3A2615),
    outline = Color(0xFFCBB89A),
)

/** Tema bermotif batik (Beranda memakai motif kawung). */
fun temaBatik(nama: String) = nama == "batik" || nama == "batik-terang"

@Composable
fun TemaRuangPutar(nama: String, isi: @Composable () -> Unit) {
    val skema: ColorScheme = when (nama) {
        "terang" -> Terang
        "gelap" -> Gelap
        "otomatis" -> if (isSystemInDarkTheme()) Gelap else Terang
        "batik-terang" -> BatikTerang
        else -> Batik
    }
    // teks & ikon bawaan mengikuti tema (bukan hitam), juga di luar Surface
    MaterialTheme(colorScheme = skema) {
        CompositionLocalProvider(LocalContentColor provides skema.onBackground, content = isi)
    }
}

/** Motif kawung (empat kelopak mengelilingi titik), digambar berulang. */
@Composable
fun MotifKawung(modifier: Modifier, warna: Color, ukuranDp: Int = 34) {
    Canvas(modifier) {
        val sel = ukuranDp.dp.toPx()
        val garis = Stroke(width = 1.dp.toPx())
        val kelopak = Size(sel * 0.42f, sel * 0.24f)
        var y = 0f
        var baris = 0
        while (y < size.height + sel) {
            var x = if (baris % 2 == 0) 0f else sel / 2
            while (x < size.width + sel) {
                val pusat = Offset(x, y)
                for (sudut in listOf(45f, 135f, 225f, 315f)) {
                    rotate(sudut, pusat) {
                        drawOval(
                            warna,
                            topLeft = Offset(pusat.x + sel * 0.06f, pusat.y - kelopak.height / 2),
                            size = kelopak,
                            style = garis,
                        )
                    }
                }
                drawCircle(warna, radius = sel * 0.04f, center = pusat)
                x += sel
            }
            y += sel / 2
            baris++
        }
    }
}
