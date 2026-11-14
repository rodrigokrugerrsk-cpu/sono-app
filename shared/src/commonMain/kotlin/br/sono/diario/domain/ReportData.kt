package br.sono.diario.domain

import br.sono.diario.data.ClinicalNote
import br.sono.diario.data.Medication
import br.sono.diario.data.MedicationIntake
import br.sono.diario.data.ScaleResult
import br.sono.diario.data.SleepDao

data class PatientInfo(val name: String, val birthYear: Int?, val sex: String)

data class ReportData(
    val patient: PatientInfo,
    val from: String,
    val to: String,
    val nights: List<NightMetrics>,
    val summary: Summary,
    val meds: List<Medication>,
    val intakes: List<MedicationIntake>,
    val scales: List<ScaleResult>,
    val notes: List<ClinicalNote>,
    val professionalName: String,
) {
    val daysInPeriod: Int get() = Fmt.daysBetweenInclusive(from, to)

    /** Alertas quantitativos usados em pesquisa de insônia e higiene do sono. */
    fun flags(): List<String> {
        if (nights.isEmpty()) return emptyList()
        val s = summary
        val out = mutableListOf<String>()
        if (s.se.mean < 85) out += "Eficiência média do sono abaixo de 85% (${Fmt.d1(s.se.mean)}%)"
        if (s.sol.mean > 30) out += "Latência média para adormecer acima de 30 min (${Fmt.d0(s.sol.mean)} min)"
        if (s.waso.mean > 30) out += "Tempo acordado após adormecer acima de 30 min (${Fmt.d0(s.waso.mean)} min)"
        if (s.tst.mean < 360) out += "Tempo total de sono médio abaixo de 6 h (${Fmt.duration(s.tst.mean)})"
        if (s.bedtimeClock.sd > 60) out += "Horário de deitar irregular (DP de ${Fmt.d0(s.bedtimeClock.sd)} min)"
        if (s.wakeClock.sd > 60) out += "Horário de despertar irregular (DP de ${Fmt.d0(s.wakeClock.sd)} min)"
        if (s.hangoverNights > 0) out += "Efeito residual matinal de medicamento em ${s.hangoverNights} noite(s)"
        if (s.cpapNightsRecorded > 0) {
            val pct = s.cpapNights4h * 100.0 / s.cpapNightsRecorded
            if (pct < 70) out += "Adesão ao CPAP abaixo do critério de 70% das noites com ≥ 4 h (${Fmt.d0(pct)}%)"
        }
        val isi = scales.filter { it.type.name == "ISI" }.maxByOrNull { it.date }
        if (isi != null && isi.score >= 15) out += "ISI mais recente indica insônia clínica (${isi.score})"
        val ess = scales.filter { it.type.name == "ESS" }.maxByOrNull { it.date }
        if (ess != null && ess.score > 10) out += "Epworth mais recente indica sonolência excessiva (${ess.score})"
        val sb = scales.filter { it.type.name == "STOPBANG" }.maxByOrNull { it.date }
        if (sb != null && sb.score >= 3) out += "STOP-Bang mais recente: risco intermediário/alto de apneia (${sb.score})"
        return out
    }

    companion object {
        suspend fun load(
            dao: SleepDao, patientId: String, patient: PatientInfo, from: String, to: String, professionalName: String,
        ): ReportData {
            val nights = dao.diaryRange(patientId, from, to).map(SleepMetrics::compute)
            return ReportData(
                patient = patient, from = from, to = to, nights = nights,
                summary = SleepMetrics.summarize(nights),
                meds = dao.medsAll(patientId),
                intakes = dao.intakesRange(patientId, from, to),
                scales = dao.scalesAll(patientId),
                notes = dao.notesAll(patientId),
                professionalName = professionalName,
            )
        }
    }
}
