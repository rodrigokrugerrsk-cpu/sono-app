package br.sono.diario.domain

import br.sono.diario.data.DiaryEntry
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Métricas derivadas do Consensus Sleep Diary.
 * Os horários são colocados numa linha do tempo contínua que começa ao meio-dia da véspera,
 * o que trata corretamente a passagem da meia-noite e também o sono diurno (trabalho em turnos).
 */
data class NightMetrics(
    val entry: DiaryEntry,
    /** Minutos desde o meio-dia da véspera. */
    val bedAbs: Int,
    val tryAbs: Int,
    val sleepOnsetAbs: Int,
    val wakeAbs: Int,
    val outAbs: Int,
    val tibMin: Int,
    val tstMin: Int,
    val sePct: Double,
)

object SleepMetrics {
    private const val NOON = 12 * 60

    fun compute(e: DiaryEntry): NightMetrics {
        val bed = (e.bedTime - NOON + 1440) % 1440
        // "Tentei dormir" antes de "fui para a cama" é erro de preenchimento: considera o mesmo horário.
        val tryT = maxOf(bed, (e.tryTime - NOON + 1440) % 1440)
        var wake = (e.finalWakeTime - NOON + 1440) % 1440
        if (wake < tryT) wake += 1440   // despertar depois do meio-dia (sono diurno longo)
        // "Saí da cama" antes do despertar final também é erro: considera o mesmo horário.
        var out = (e.outOfBedTime - NOON + 1440) % 1440
        if (out < wake) out = if (out + 1440 - wake <= 6 * 60) out + 1440 else wake
        val onset = tryT + e.solMin
        val tib = out - bed
        val tst = max(0, wake - tryT - e.solMin - e.wasoMin)
        val se = if (tib > 0) tst * 100.0 / tib else 0.0
        return NightMetrics(e, bed, tryT, onset, wake, out, tib, tst, se)
    }

    fun summarize(nights: List<NightMetrics>): Summary {
        fun stat(v: List<Double>) = Stat.of(v)
        return Summary(
            nights = nights.size,
            tst = stat(nights.map { it.tstMin.toDouble() }),
            tib = stat(nights.map { it.tibMin.toDouble() }),
            sol = stat(nights.map { it.entry.solMin.toDouble() }),
            waso = stat(nights.map { it.entry.wasoMin.toDouble() }),
            nwak = stat(nights.map { it.entry.awakenings.toDouble() }),
            se = stat(nights.map { it.sePct }),
            quality = stat(nights.map { it.entry.quality.toDouble() }),
            restedness = stat(nights.map { it.entry.restedness.toDouble() }),
            bedtimeClock = stat(nights.map { it.bedAbs.toDouble() }),
            wakeClock = stat(nights.map { it.wakeAbs.toDouble() }),
            earlyWakeNights = nights.count { it.entry.wokeEarlier },
            napDays = nights.count { it.entry.napCount > 0 },
            napMinMean = nights.map { it.entry.napMin.toDouble() }.averageOrZero(),
            caffeineMean = nights.map { it.entry.caffeineDoses.toDouble() }.averageOrZero(),
            alcoholNights = nights.count { it.entry.alcoholDoses > 0 },
            alcoholMean = nights.map { it.entry.alcoholDoses.toDouble() }.averageOrZero(),
            exerciseDays = nights.count { it.entry.exercise },
            screenNights = nights.count { it.entry.screensBeforeBed },
            stressMean = nights.map { it.entry.stress.toDouble() }.averageOrZero(),
            painMean = nights.map { it.entry.pain.toDouble() }.averageOrZero(),
            cpapNightsRecorded = nights.count { it.entry.cpapUsed != null },
            cpapNightsUsed = nights.count { it.entry.cpapUsed == true },
            cpapHoursMean = nights.mapNotNull { n -> n.entry.cpapHours?.toDouble()?.takeIf { n.entry.cpapUsed == true } }.averageOrZero(),
            cpapNights4h = nights.count { it.entry.cpapUsed == true && (it.entry.cpapHours ?: 0f) >= 4f },
            hangoverNights = nights.count { it.entry.morningHangover > 0 },
        )
    }

    private fun List<Double>.averageOrZero() = if (isEmpty()) 0.0 else average()
}

data class Stat(val mean: Double, val sd: Double, val min: Double, val max: Double, val n: Int) {
    companion object {
        fun of(v: List<Double>): Stat {
            if (v.isEmpty()) return Stat(0.0, 0.0, 0.0, 0.0, 0)
            val m = v.average()
            val sd = if (v.size > 1) sqrt(v.sumOf { (it - m) * (it - m) } / (v.size - 1)) else 0.0
            return Stat(m, sd, v.min(), v.max(), v.size)
        }
    }
}

data class Summary(
    val nights: Int,
    val tst: Stat, val tib: Stat, val sol: Stat, val waso: Stat, val nwak: Stat, val se: Stat,
    val quality: Stat, val restedness: Stat,
    val bedtimeClock: Stat, val wakeClock: Stat,
    val earlyWakeNights: Int,
    val napDays: Int, val napMinMean: Double,
    val caffeineMean: Double,
    val alcoholNights: Int, val alcoholMean: Double,
    val exerciseDays: Int, val screenNights: Int,
    val stressMean: Double, val painMean: Double,
    val cpapNightsRecorded: Int, val cpapNightsUsed: Int, val cpapHoursMean: Double, val cpapNights4h: Int,
    val hangoverNights: Int,
)

object Fmt {
    val iso: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val br = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val brShort = DateTimeFormatter.ofPattern("EEE dd/MM", Locale("pt", "BR"))

    fun today(): String = LocalDate.now().format(iso)
    fun date(iso: String): String = LocalDate.parse(iso).format(br)
    fun dateShort(iso: String): String = LocalDate.parse(iso).format(brShort)

    fun clock(min: Int?): String {
        if (min == null) return "—"
        val m = ((min % 1440) + 1440) % 1440
        return "%02d:%02d".format(m / 60, m % 60)
    }

    /** Converte minutos desde o meio-dia da véspera para hora do relógio. */
    fun clockAbs(abs: Double): String = clock((abs.roundToInt() + 12 * 60))

    fun duration(min: Double): String {
        val t = min.roundToInt()
        return "%dh%02d".format(t / 60, t % 60)
    }

    fun d1(v: Double) = "%.1f".format(Locale("pt", "BR"), v)
    fun d0(v: Double) = "%.0f".format(Locale("pt", "BR"), v)
}
