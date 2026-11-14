package br.sono.diario.export

import br.sono.diario.data.MedicationIntake
import br.sono.diario.domain.Fmt
import br.sono.diario.domain.NightMetrics

/** CSV com separador ';' e vírgula decimal, que abre direto no Excel em português. */
object CsvExport {
    private fun b(v: Boolean?) = when (v) { true -> "sim"; false -> "não"; null -> "" }
    private fun q(s: String) = "\"" + s.replace("\"", "\"\"").replace("\n", " ") + "\""

    fun diary(nights: List<NightMetrics>, intakes: List<MedicationIntake>): String {
        val byDate = intakes.groupBy { it.date }
        val sb = StringBuilder("﻿")
        sb.appendLine(
            listOf(
                "data", "deitou", "tentou_dormir", "latencia_min", "despertares", "waso_min", "despertar_final",
                "levantou", "tempo_na_cama_min", "tempo_total_sono_min", "eficiencia_pct", "qualidade_1a5",
                "descanso_1a5", "despertou_cedo", "cochilos", "cochilo_min", "cafeina_doses", "ultima_cafeina",
                "alcool_doses", "exercicio", "telas", "estresse_0a4", "dor_0a10", "turno", "menstruacao",
                "cpap_usou", "cpap_horas", "efeito_residual_0a3", "medicamentos", "observacoes",
            ).joinToString(";")
        )
        nights.forEach { n ->
            val e = n.entry
            sb.appendLine(
                listOf(
                    e.date, Fmt.clock(e.bedTime), Fmt.clock(e.tryTime), e.solMin, e.awakenings, e.wasoMin,
                    Fmt.clock(e.finalWakeTime), Fmt.clock(e.outOfBedTime), n.tibMin, n.tstMin, Fmt.d1(n.sePct),
                    e.quality, e.restedness, b(e.wokeEarlier), e.napCount, e.napMin, e.caffeineDoses,
                    e.lastCaffeineTime?.let { Fmt.clock(it) } ?: "", e.alcoholDoses, b(e.exercise), b(e.screensBeforeBed),
                    e.stress, e.pain, b(e.shiftWork), b(e.menstruation), b(e.cpapUsed),
                    e.cpapHours?.let { Fmt.d1(it.toDouble()) } ?: "", e.morningHangover,
                    q(byDate[e.date].orEmpty().joinToString(" | ") { "${it.medName} ${it.dose} ${Fmt.clock(it.time)}" }),
                    q(e.notes),
                ).joinToString(";")
            )
        }
        return sb.toString()
    }
}
