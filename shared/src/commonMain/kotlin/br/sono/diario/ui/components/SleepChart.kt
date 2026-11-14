package br.sono.diario.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import br.sono.diario.data.MedicationIntake
import br.sono.diario.domain.Fmt
import br.sono.diario.domain.NightMetrics
import br.sono.diario.ui.theme.ChartColors

/** Gráfico "sleep log": uma linha por noite, eixo das 18:00 às 14:00. */
@Composable
fun SleepChart(nights: List<NightMetrics>, intakes: List<MedicationIntake>) {
    val dark = isSystemInDarkTheme()
    val asleep = if (dark) ChartColors.asleepDark else ChartColors.asleep
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val start = 6 * 60
    val end = 26 * 60
    val medsByDate = intakes.groupBy { it.date }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row {
            Box(Modifier.width(64.dp))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("18h", "22h", "02h", "06h", "10h", "14h").forEach {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = labelColor)
                }
            }
        }
        nights.forEach { n ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    Fmt.dateShort(n.entry.date), Modifier.width(64.dp),
                    style = MaterialTheme.typography.labelSmall, color = labelColor,
                )
                Canvas(Modifier.weight(1f).height(16.dp)) {
                    fun x(abs: Int) = (abs.coerceIn(start, end) - start) * size.width / (end - start)
                    for (h in 0..20 step 4) {
                        val gx = x(start + h * 60)
                        drawLine(gridColor, Offset(gx, 0f), Offset(gx, size.height), strokeWidth = 1f)
                    }
                    val r = CornerRadius(4f, 4f)
                    drawRoundRect(
                        ChartColors.inBed, Offset(x(n.bedAbs), size.height * 0.25f),
                        Size(x(n.outAbs) - x(n.bedAbs), size.height * 0.5f), r,
                    )
                    drawRoundRect(
                        asleep, Offset(x(n.sleepOnsetAbs), size.height * 0.1f),
                        Size((x(n.wakeAbs) - x(n.sleepOnsetAbs)).coerceAtLeast(0f), size.height * 0.8f), r,
                    )
                    medsByDate[n.entry.date].orEmpty().forEach { i ->
                        val t = i.time ?: return@forEach
                        drawCircle(ChartColors.med, radius = size.height * 0.22f, center = Offset(x((t - 12 * 60 + 1440) % 1440), size.height / 2))
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Legend(ChartColors.inBed, "Na cama"); Legend(asleep, "Dormindo"); Legend(ChartColors.med, "Medicamento")
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
