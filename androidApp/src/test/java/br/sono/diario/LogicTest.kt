package br.sono.diario

import br.sono.diario.data.DiaryEntry
import br.sono.diario.data.Medication
import br.sono.diario.data.MedicationIntake
import br.sono.diario.data.ScaleResult
import br.sono.diario.data.ScaleType
import br.sono.diario.domain.Fmt
import br.sono.diario.domain.PatientInfo
import br.sono.diario.domain.ReportData
import br.sono.diario.domain.Scales
import br.sono.diario.domain.SleepMetrics
import br.sono.diario.export.CsvExport
import br.sono.diario.export.PatientBundle
import br.sono.diario.export.PatientExport
import br.sono.diario.export.WrongPasswordException
import br.sono.diario.JcaCrypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicTest {
    private fun night(bed: Int, tryT: Int, sol: Int, waso: Int, wake: Int, out: Int) = DiaryEntry(
        date = "2026-11-10", bedTime = bed, tryTime = tryT, solMin = sol, awakenings = 2, wasoMin = waso,
        finalWakeTime = wake, outOfBedTime = out, quality = 3,
    )

    @Test fun crossesMidnight() {
        // Cama 23:00, tenta 23:30, 30 min p/ dormir, 40 min acordado, acorda 06:30, levanta 07:00
        val m = SleepMetrics.compute(night(23 * 60, 23 * 60 + 30, 30, 40, 6 * 60 + 30, 7 * 60))
        assertEquals(8 * 60, m.tibMin)            // 23:00 -> 07:00
        assertEquals(7 * 60 - 30 - 40, m.tstMin)  // 23:30 -> 06:30 = 7h, menos SOL e WASO = 5h50
        assertEquals(350 * 100.0 / 480, m.sePct, 0.01)
    }

    @Test fun afterMidnightBedtime() {
        val m = SleepMetrics.compute(night(60, 75, 15, 0, 8 * 60, 8 * 60 + 30))
        assertEquals(7 * 60 + 30, m.tibMin)
        assertEquals(8 * 60 - 75 - 15, m.tstMin)
    }

    @Test fun dayShiftSleep() {
        // Plantonista: deita 08:00, acorda 15:00
        val m = SleepMetrics.compute(night(8 * 60, 8 * 60, 10, 0, 15 * 60, 15 * 60 + 10))
        assertEquals(7 * 60 + 10, m.tibMin)
        assertEquals(7 * 60 - 10, m.tstMin)
    }

    @Test fun entryErrorsAreClamped() {
        // tentou dormir (23:00) antes de deitar (23:30): não pode virar uma noite de 31 h
        val m = SleepMetrics.compute(night(23 * 60 + 30, 23 * 60, 30, 45, 6 * 60, 7 * 60))
        assertEquals(7 * 60 + 30, m.tibMin)
        assertEquals(6 * 60 + 30 - 30 - 45, m.tstMin)
        // levantou (06:50) antes do despertar final (07:00)
        val m2 = SleepMetrics.compute(night(23 * 60, 23 * 60, 0, 0, 7 * 60, 6 * 60 + 50))
        assertEquals(8 * 60, m2.tibMin)
    }

    @Test fun formatting() {
        assertEquals("63,6", Fmt.d1(63.56))
        assertEquals("0,0", Fmt.d1(0.0))
        assertEquals("7h05", Fmt.duration(425.0))
        assertEquals("sex. 13/11", Fmt.dateShort("2026-11-13"))
        assertEquals("13/11/2026", Fmt.date("2026-11-13"))
        assertEquals(14, Fmt.daysBetweenInclusive("2026-10-30", "2026-11-12"))
    }

    /** Arquivo gerado pelo app Android 0.1 (formato anterior) deve continuar abrindo. */
    @Test fun compatibleWithJavaCipherLayout() {
        val key = JcaCrypto.pbkdf2Sha256("segredo1", ByteArray(16) { it.toByte() }, 1000, 32)
        val iv = ByteArray(12) { 7 }
        val c = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        c.init(javax.crypto.Cipher.ENCRYPT_MODE, javax.crypto.spec.SecretKeySpec(key, "AES"), javax.crypto.spec.GCMParameterSpec(128, iv))
        c.updateAAD("SONO".encodeToByteArray())
        val ct = c.doFinal("olá".encodeToByteArray())
        assertEquals("olá", JcaCrypto.aesGcmDecrypt(key, iv, "SONO".encodeToByteArray(), ct)!!.decodeToString())
    }

    @Test fun clockOfMeanBedtime() {
        val m = SleepMetrics.compute(night(23 * 60, 23 * 60, 0, 0, 7 * 60, 7 * 60))
        assertEquals("23:00", Fmt.clockAbs(m.bedAbs.toDouble()))
    }

    @Test fun scaleInterpretation() {
        assertEquals("Insônia clínica moderada", Scales.ISI.interpret(18, emptyList()))
        assertEquals("Sonolência diurna dentro do normal", Scales.ESS.interpret(10, emptyList()))
        // STOP >= 2 + sexo masculino = alto risco mesmo com escore 3
        assertTrue(Scales.STOPBANG.interpret(3, listOf(1, 1, 0, 0, 0, 0, 0, 1)).startsWith("Alto"))
        assertTrue(Scales.STOPBANG.interpret(3, listOf(1, 0, 0, 0, 0, 1, 0, 1)).startsWith("Risco intermediário"))
        assertEquals(28, Scales.ISI.items.sumOf { it.points.max() })
        assertEquals(24, Scales.ESS.items.sumOf { it.points.max() })
        assertEquals(8, Scales.STOPBANG.items.sumOf { it.points.max() })
    }

    private val bundle = PatientBundle(
        exportedAt = 1L, patientCode = "abc", name = "Maria Teste", birthYear = 1970, sex = "Feminino",
        diary = listOf(night(23 * 60, 23 * 60, 20, 10, 6 * 60, 6 * 60 + 20).copy(notes = "ronco; \"alto\"")),
        medications = listOf(Medication(id = 5, name = "Zolpidem", dose = "10 mg")),
        intakes = listOf(MedicationIntake(date = "2026-11-10", medicationId = 5, medName = "Zolpidem", dose = "10 mg", time = 22 * 60 + 45)),
        scales = listOf(ScaleResult(type = ScaleType.ISI, date = "2026-11-10", answers = "3,3,2,3,2,2,3", score = 18)),
    )

    @Test fun encryptedRoundTrip() {
        val enc = PatientExport.encrypt(JcaCrypto, bundle, "segredo1")
        assertTrue(String(enc, 0, 4) == "SONO")
        assertTrue(!String(enc).contains("Maria"))
        assertEquals(bundle, PatientExport.decrypt(JcaCrypto, enc, "segredo1"))
    }

    @Test(expected = WrongPasswordException::class)
    fun wrongPassword() {
        PatientExport.decrypt(JcaCrypto, PatientExport.encrypt(JcaCrypto, bundle, "segredo1"), "outra123")
    }

    @Test fun csvAndFlags() {
        val nights = bundle.diary.map(SleepMetrics::compute)
        val csv = CsvExport.diary(nights, bundle.intakes)
        assertTrue(csv.contains("Zolpidem 10 mg 22:45"))
        assertTrue(csv.contains("\"ronco; \"\"alto\"\"\""))
        val r = ReportData(
            PatientInfo("Maria", 1970, "F"), "2026-11-10", "2026-11-10", nights, SleepMetrics.summarize(nights),
            bundle.medications, bundle.intakes, bundle.scales, emptyList(), "",
        )
        val flags = r.flags()
        assertTrue(flags.any { it.contains("ISI") })
        assertEquals(1, r.daysInPeriod)
    }
}
