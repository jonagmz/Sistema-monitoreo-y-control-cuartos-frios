package com.jonagmz.cuartofrio.ui.componentes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jonagmz.cuartofrio.ui.Formato
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

data class Punto(val tiempo: Long, val valor: Float)
data class Serie(val nombre: String, val puntos: List<Punto>, val color: Color, val unidad: String)

/**
 * Gráfica de líneas con tiempo en el eje X.
 * - rangoPermitido: banda verde suave (p. ej. entre las alarmas de temperatura); fuera de ella se ve en rojo.
 * - referencia: línea punteada (p. ej. el objetivo).
 * - Se corta la línea donde faltan datos más de [huecoMaxMs] (cortes de luz, SMS perdidos).
 * - Al tocar o arrastrar muestra el valor más cercano.
 */
@Composable
fun Grafica(
    series: List<Serie>,
    desde: Long,
    hasta: Long,
    modifier: Modifier = Modifier,
    alto: Dp = 200.dp,
    rangoPermitido: ClosedFloatingPointRange<Float>? = null,
    referencia: Float? = null,
    yMinimo: Float? = null,
    yMaximo: Float? = null,
    huecoMaxMs: Long = 50 * 60_000L,
    decimales: Int = 1,
) {
    val medidor = rememberTextMeasurer()
    val colores = MaterialTheme.colorScheme
    val estiloEje = TextStyle(fontSize = 11.sp, color = colores.onSurfaceVariant)
    val estiloTooltip = TextStyle(fontSize = 12.sp, color = colores.inverseOnSurface)
    var tocado by remember { mutableStateOf<Float?>(null) }

    val valores = series.flatMap { s -> s.puntos.map { it.valor } } + listOfNotNull(referencia, rangoPermitido?.start, rangoPermitido?.endInclusive)
    var minimo = yMinimo ?: (valores.minOrNull() ?: 0f)
    var maximo = yMaximo ?: (valores.maxOrNull() ?: 1f)
    if (maximo - minimo < 1f) { minimo -= 0.5f; maximo += 0.5f }
    val margen = (maximo - minimo) * 0.08f
    if (yMinimo == null) minimo -= margen
    if (yMaximo == null) maximo += margen
    val paso = pasoBonito((maximo - minimo) / 4f)
    minimo = floor(minimo / paso) * paso
    maximo = ceil(maximo / paso) * paso

    Canvas(
        modifier
            .fillMaxWidth()
            .height(alto)
            .pointerInput(series) {
                detectTapGestures(onPress = { tocado = it.x; tryAwaitRelease(); })
            }
            .pointerInput(series) {
                detectDragGestures(onDragEnd = { tocado = null }, onDragCancel = { tocado = null }) { cambio, _ -> tocado = cambio.position.x }
            },
    ) {
        val izquierda = 40.dp.toPx()
        val abajo = 20.dp.toPx()
        val ancho = size.width - izquierda
        val altura = size.height - abajo
        val rangoT = (hasta - desde).coerceAtLeast(1)
        fun x(t: Long) = izquierda + (t - desde).toFloat() / rangoT * ancho
        fun y(v: Float) = altura - (v - minimo) / (maximo - minimo) * altura

        // banda permitida y fuera de rango
        rangoPermitido?.let { r ->
            val arriba = y(r.endInclusive.coerceAtMost(maximo)).coerceIn(0f, altura)
            val abajoR = y(r.start.coerceAtLeast(minimo)).coerceIn(0f, altura)
            drawRect(Color(0x14E53935), Offset(izquierda, 0f), Size(ancho, arriba))
            drawRect(Color(0x1443A047), Offset(izquierda, arriba), Size(ancho, abajoR - arriba))
            drawRect(Color(0x141E88E5), Offset(izquierda, abajoR), Size(ancho, altura - abajoR))
        }

        // cuadrícula y etiquetas del eje Y
        var v = minimo
        while (v <= maximo + paso / 2) {
            val yy = y(v)
            drawLine(colores.outlineVariant.copy(alpha = 0.6f), Offset(izquierda, yy), Offset(size.width, yy), 1f)
            val texto = medidor.measure(if (paso >= 1f) "%.0f".format(v) else "%.1f".format(v), estiloEje)
            drawText(texto, topLeft = Offset(izquierda - texto.size.width - 6.dp.toPx(), yy - texto.size.height / 2))
            v += paso
        }
        // etiquetas del eje X
        for (i in 0..4) {
            val t = desde + rangoT * i / 4
            val texto = medidor.measure(Formato.etiquetaEje(t, rangoT), estiloEje)
            val xx = (x(t) - texto.size.width / 2).coerceIn(izquierda, size.width - texto.size.width)
            drawText(texto, topLeft = Offset(xx, altura + 4.dp.toPx()))
        }

        referencia?.let {
            drawLine(colores.primary.copy(alpha = 0.7f), Offset(izquierda, y(it)), Offset(size.width, y(it)), 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
        }

        for (serie in series) {
            val camino = Path()
            var anterior: Punto? = null
            for (p in serie.puntos) {
                if (p.tiempo < desde || p.tiempo > hasta) continue
                if (anterior == null || p.tiempo - anterior.tiempo > huecoMaxMs) camino.moveTo(x(p.tiempo), y(p.valor))
                else camino.lineTo(x(p.tiempo), y(p.valor))
                anterior = p
            }
            drawPath(camino, serie.color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            // un punto suelto (sin vecinos) no dibuja línea: se marca con un círculo
            serie.puntos.forEachIndexed { i, p ->
                val solo = (i == 0 || p.tiempo - serie.puntos[i - 1].tiempo > huecoMaxMs) &&
                    (i == serie.puntos.lastIndex || serie.puntos[i + 1].tiempo - p.tiempo > huecoMaxMs)
                if (solo && p.tiempo in desde..hasta) drawCircle(serie.color, 3.dp.toPx(), Offset(x(p.tiempo), y(p.valor)))
            }
        }

        // valor tocado
        tocado?.let { tx ->
            val t = desde + ((tx - izquierda) / ancho * rangoT).toLong()
            val lineas = series.mapNotNull { s ->
                s.puntos.minByOrNull { abs(it.tiempo - t) }?.takeIf { abs(it.tiempo - t) < rangoT / 20 }?.let { s to it }
            }
            if (lineas.isEmpty()) return@let
            val px = x(lineas.first().second.tiempo)
            drawLine(colores.onSurfaceVariant, Offset(px, 0f), Offset(px, altura), 1.dp.toPx())
            lineas.forEach { (s, p) -> drawCircle(s.color, 5.dp.toPx(), Offset(x(p.tiempo), y(p.valor))) }
            val texto = medidor.measure(
                (listOf(Formato.fechaHora(lineas.first().second.tiempo)) +
                    lineas.map { (s, p) -> "${s.nombre}: ${"%.${decimales}f".format(p.valor)} ${s.unidad}" }).joinToString("\n"),
                estiloTooltip,
            )
            val ancho2 = texto.size.width + 16.dp.toPx()
            val alto2 = texto.size.height + 12.dp.toPx()
            val bx = (px + 8.dp.toPx()).let { if (it + ancho2 > size.width) px - ancho2 - 8.dp.toPx() else it }
            drawRoundRect(colores.inverseSurface, Offset(bx, 4.dp.toPx()), Size(ancho2, alto2), CornerRadius(8.dp.toPx()))
            drawText(texto, topLeft = Offset(bx + 8.dp.toPx(), 10.dp.toPx()))
        }
    }
}

private fun pasoBonito(crudo: Float): Float {
    val opciones = floatArrayOf(0.2f, 0.5f, 1f, 2f, 5f, 10f, 20f, 25f, 50f)
    return opciones.firstOrNull { it >= crudo } ?: 100f
}
