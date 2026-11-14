package br.sono.diario

import androidx.test.core.app.ApplicationProvider
import br.sono.diario.data.DiaryEntry
import br.sono.diario.data.Medication
import br.sono.diario.data.MedicationIntake
import br.sono.diario.data.ScaleResult
import br.sono.diario.data.ScaleType
import br.sono.diario.domain.PatientInfo
import br.sono.diario.domain.ReportData
import br.sono.diario.domain.SleepMetrics
import br.sono.diario.export.PdfReport
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PdfTest {
    @Test fun generatesPdf() {
        val start = LocalDate.of(2026, 10, 30)
        val nights = (0 until 14).map { i ->
            DiaryEntry(
                date = start.plusDays(i.toLong()).toString(), bedTime = 22 * 60 + 30 + (i % 4) * 20, tryTime = 23 * 60 + (i % 3) * 15,
                solMin = 20 + (i % 5) * 10, awakenings = i % 4, wasoMin = (i % 4) * 15, finalWakeTime = 6 * 60 + (i % 3) * 20,
                outOfBedTime = 7 * 60, quality = 1 + i % 5, caffeineDoses = i % 3, alcoholDoses = if (i % 6 == 0) 2 else 0,
                cpapUsed = true, cpapHours = (3 + i % 4).toFloat(),
            )
        }.map(SleepMetrics::compute)
        val intakes = nights.filterIndexed { i, _ -> i % 2 == 0 }.map {
            MedicationIntake(date = it.entry.date, medicationId = 1, medName = "Zolpidem", dose = "10 mg", time = 22 * 60 + 40)
        }
        val d = ReportData(
            PatientInfo("Maria Teste", 1970, "Feminino"), nights.first().entry.date, nights.last().entry.date, nights,
            SleepMetrics.summarize(nights), listOf(Medication(id = 1, name = "Zolpidem", dose = "10 mg")), intakes,
            listOf(ScaleResult(type = ScaleType.ISI, date = "2026-11-12", answers = "3,3,2,3,2,2,3", score = 18)),
            emptyList(), "Dra. Ana",
        )
        val out = File(System.getProperty("pdfOut") ?: File(ApplicationProvider.getApplicationContext<App>().cacheDir, "r.pdf").path)
        // PdfDocument não existe no Robolectric: desenha as mesmas páginas em imagens PNG.
        val pages = mutableListOf<android.graphics.Bitmap>()
        val sink = object : br.sono.diario.export.PageSink {
            override fun start(number: Int, width: Int, height: Int): android.graphics.Canvas {
                val b = android.graphics.Bitmap.createBitmap(width * 2, height * 2, android.graphics.Bitmap.Config.ARGB_8888)
                b.eraseColor(android.graphics.Color.WHITE); pages += b
                return android.graphics.Canvas(b).apply { scale(2f, 2f) }
            }
            override fun finish() {}
            override fun close(file: File) {
                pages.forEachIndexed { i, b ->
                    File(file.parentFile, file.nameWithoutExtension + "_p${i + 1}.png").outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                }
            }
        }
        PdfReport(d, sink).writeTo(out)
        assertTrue(pages.isNotEmpty())
        println("Páginas: ${pages.size}")
    }
}
