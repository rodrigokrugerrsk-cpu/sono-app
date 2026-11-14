package br.sono.diario.export

import br.sono.diario.ReportCanvas
import br.sono.diario.ReportDocument
import br.sono.diario.domain.Fmt
import br.sono.diario.domain.ReportData
import br.sono.diario.domain.Scales

private fun rgb(r: Int, g: Int, b: Int) = (r shl 16) or (g shl 8) or b

/** Estilo de texto (equivalente simplificado ao Paint do Android). */
private class Paint(val textSize: Float, val color: Int, val bold: Boolean)

/** Gera o relatório clínico em PDF (A4). O desenho é o mesmo no Android e no iPhone. */
class PdfReport(private val d: ReportData, private val doc: ReportDocument) {
    private val pageW = 595
    private val pageH = 842
    private val margin = 40f
    private val contentW = pageW - 2 * margin

    private var open = false
    private lateinit var c: ReportCanvas
    private var y = 0f
    private var pageNo = 0

    private val ink = rgb(30, 34, 52)
    private val muted = rgb(100, 106, 125)
    private val accent = rgb(38, 48, 107)
    private val sleepColor = rgb(52, 74, 170)
    private val bedColor = rgb(196, 205, 236)
    private val grid = rgb(222, 225, 234)
    private val warn = rgb(176, 60, 30)

    private fun paint(size: Float, color: Int = ink, bold: Boolean = false) = Paint(size, color, bold)
    private fun Paint.measureText(s: String) = c.measure(s, textSize, bold)
    private fun drawText(s: String, x: Float, y: Float, p: Paint) = c.text(s, x, y, p.textSize, p.color, p.bold)

    private val body = paint(9f)
    private val bodyBold = paint(9f, bold = true)
    private val small = paint(7.5f, muted)
    private val h1 = paint(17f, accent, true)
    private val h2 = paint(12f, accent, true)

    private fun newPage() {
        finishPage()
        pageNo++
        c = doc.newPage(pageW, pageH)
        open = true
        y = margin
    }

    private fun finishPage() {
        if (open) {
            val footer = "Sono by Dra Alessandra — ferramenta de apoio ao acompanhamento clínico; não substitui avaliação médica nem polissonografia."
            drawText(footer, margin, pageH - 22f, small)
            val pn = "Página $pageNo"
            drawText(pn, pageW - margin - small.measureText(pn), pageH - 22f, small)
            doc.finishPage()
        }
        open = false
    }

    private fun ensure(h: Float) { if (y + h > pageH - 50f) newPage() }

    private fun text(s: String, p: Paint = body, x: Float = margin, lineH: Float = p.textSize * 1.45f) {
        wrap(s, p, contentW - (x - margin)).forEach { line ->
            ensure(lineH); y += lineH; drawText(line, x, y - lineH * 0.28f, p)
        }
    }

    private fun wrap(s: String, p: Paint, w: Float): List<String> {
        val out = mutableListOf<String>()
        s.split("\n").forEach { para ->
            var line = ""
            para.split(" ").forEach { word ->
                val t = if (line.isEmpty()) word else "$line $word"
                if (p.measureText(t) > w && line.isNotEmpty()) { out += line; line = word } else line = t
            }
            out += line
        }
        return out
    }

    private fun heading(s: String) {
        ensure(40f); y += 14f
        drawText(s, margin, y + 10f, h2)
        y += 16f
        c.line(margin, y, pageW - margin, y, grid, 1f)
        y += 4f
    }

    /** Tabela simples; [widths] em frações da largura útil. */
    private fun table(headers: List<String>, rows: List<List<String>>, widths: List<Float>, fontSize: Float = 8.5f) {
        val hp = paint(fontSize, muted, true)
        val rp = paint(fontSize)
        val rowH = fontSize * 1.9f
        fun drawRow(cells: List<String>, p: Paint, shade: Boolean) {
            ensure(rowH)
            if (shade) c.rect(margin, y, pageW - margin, y + rowH, rgb(245, 246, 251))
            var x = margin + 3f
            cells.forEachIndexed { i, cell ->
                val w = widths[i] * contentW
                var t = cell
                while (p.measureText(t) > w - 4f && t.length > 1) t = t.dropLast(2) + "…"
                drawText(t, x, y + rowH * 0.66f, p)
                x += w
            }
            y += rowH
        }
        drawRow(headers, hp, false)
        rows.forEachIndexed { i, r ->
            if (y + rowH > pageH - 50f) { newPage(); drawRow(headers, hp, false) }
            drawRow(r, rp, i % 2 == 0)
        }
    }

    private fun header() {
        newPage()
        drawText("Relatório do Sono", margin, y + 16f, h1)
        val brand = paint(10f, rgb(196, 110, 150), true)
        val bt = "Sono by Dra Alessandra"
        drawText(bt, pageW - margin - brand.measureText(bt), y + 14f, brand)
        y += 26f
        val p = d.patient
        val age = p.birthYear?.let { "${Fmt.currentYear() - it} anos" } ?: "idade não informada"
        text("Paciente: ${p.name.ifBlank { "—" }}   ·   $age   ·   Sexo: ${p.sex.ifBlank { "—" }}", bodyBold)
        text("Período: ${Fmt.date(d.from)} a ${Fmt.date(d.to)}   ·   Noites registradas: ${d.nights.size} de ${d.daysInPeriod} dias")
        val gen = Fmt.dateTime(Fmt.now())
        text("Gerado em $gen" + if (d.professionalName.isNotBlank()) "   ·   Profissional: ${d.professionalName}" else "", small)
        text("Instrumento: diário baseado no Consensus Sleep Diary (Carney et al., Sleep 2012).", small)
    }

    private fun summary() {
        heading("Resumo do período")
        if (d.nights.isEmpty()) { text("Nenhuma noite registrada no período."); return }
        val s = d.summary
        val mins: (Double) -> String = { Fmt.d0(it) + " min" }
        fun row(name: String, st: br.sono.diario.domain.Stat, f: (Double) -> String, sdf: (Double) -> String = f) =
            listOf(name, f(st.mean), if (st.n > 1) sdf(st.sd) else "—", f(st.min), f(st.max))
        val dur: (Double) -> String = { Fmt.duration(it) }
        val pct: (Double) -> String = { Fmt.d1(it) + "%" }
        val num: (Double) -> String = Fmt::d1
        table(
            listOf("Métrica", "Média", "DP", "Mín.", "Máx."),
            listOf(
                row("Tempo total de sono (TST)", s.tst, dur),
                row("Tempo na cama (TIB)", s.tib, dur),
                row("Eficiência do sono (SE)", s.se, pct),
                row("Latência do sono (SOL)", s.sol, mins),
                row("Acordado após adormecer (WASO)", s.waso, mins),
                row("Nº de despertares (NWAK)", s.nwak, num),
                row("Qualidade subjetiva (1–5)", s.quality, num),
                row("Sensação de descanso (1–5)", s.restedness, num),
                row("Horário de deitar", s.bedtimeClock, { Fmt.clockAbs(it) }, mins),
                row("Horário do despertar final", s.wakeClock, { Fmt.clockAbs(it) }, mins),
            ),
            listOf(0.40f, 0.15f, 0.15f, 0.15f, 0.15f),
        )
        text("Despertar antes do planejado: ${s.earlyWakeNights} noite(s).", small)

        val flags = d.flags()
        if (flags.isNotEmpty()) {
            y += 6f
            text("Pontos de atenção", paint(9.5f, warn, true))
            flags.forEach { text("•  $it", paint(9f, warn), margin + 6f) }
        }
    }

    /** Gráfico estilo "sleep log": cada linha é uma noite, eixo de 18:00 a 14:00. */
    private fun sleepChart() {
        heading("Gráfico do sono")
        if (d.nights.isEmpty()) return
        val start = 6 * 60   // 18:00 em minutos desde o meio-dia
        val end = 26 * 60    // 14:00 do dia seguinte
        val labelW = 62f
        val x0 = margin + labelW
        val w = contentW - labelW
        fun xOf(abs: Int) = x0 + (abs.coerceIn(start, end) - start) * w / (end - start)
        val rowH = 11f
        val chartH = rowH * d.nights.size + 18f
        if (chartH < pageH - 100f) ensure(chartH + 30f)
        val top = y
        // eixo
        for (h in 0..20 step 2) {
            val x = xOf(start + h * 60)
            c.line(x, top + 10f, x, top + 10f + rowH * d.nights.size, grid, 0.6f)
            val label = Fmt.clock(start + h * 60 + 12 * 60)
            drawText(label, x - small.measureText(label) / 2, top + 7f, small)
        }
        y = top + 10f
        val intakeByDate = d.intakes.groupBy { it.date }
        d.nights.forEach { n ->
            if (y + rowH > pageH - 50f) { newPage() }
            drawText(Fmt.dateShort(n.entry.date), margin, y + rowH * 0.75f, small)
            c.rect(xOf(n.bedAbs), y + 2.5f, xOf(n.outAbs), y + rowH - 2.5f, bedColor)
            c.rect(xOf(n.sleepOnsetAbs), y + 1.5f, xOf(n.wakeAbs), y + rowH - 1.5f, sleepColor)
            intakeByDate[n.entry.date].orEmpty().forEach { i ->
                val t = i.time ?: return@forEach
                val abs = ((t - 12 * 60 + 1440) % 1440)
                val x = xOf(abs)
                c.triangle(x, y + 1f, x - 3f, y - 3.5f, x + 3f, y - 3.5f, warn)
            }
            y += rowH
        }
        y += 4f
        val lx = margin
        c.rect(lx, y + 3f, lx + 14f, y + 9f, bedColor); drawText("Na cama", lx + 18f, y + 9f, small)
        c.rect(lx + 70f, y + 2f, lx + 84f, y + 10f, sleepColor); drawText("Dormindo (do adormecer ao despertar final)", lx + 88f, y + 9f, small)
        val tx = lx + 300f
        c.triangle(tx + 4f, y + 10f, tx, y + 3f, tx + 8f, y + 3f, warn)
        drawText("Medicamento", tx + 12f, y + 9f, small)
        y += 14f
        text("Despertares noturnos (WASO) não aparecem no gráfico; ver tabela diária.", small)
    }

    private fun factors() {
        if (d.nights.isEmpty()) return
        heading("Hábitos e fatores associados")
        val s = d.summary
        val n = s.nights
        val rows = mutableListOf(
            listOf("Cochilos", "${s.napDays} de $n dias · média ${Fmt.d0(s.napMinMean)} min/dia"),
            listOf("Cafeína", "média ${Fmt.d1(s.caffeineMean)} doses/dia"),
            listOf("Álcool", "${s.alcoholNights} de $n noites · média ${Fmt.d1(s.alcoholMean)} doses"),
            listOf("Exercício físico", "${s.exerciseDays} de $n dias"),
            listOf("Telas na hora antes de deitar", "${s.screenNights} de $n noites"),
            listOf("Estresse (0–4)", "média ${Fmt.d1(s.stressMean)}"),
            listOf("Dor (0–10)", "média ${Fmt.d1(s.painMean)}"),
        )
        val shift = d.nights.count { it.entry.shiftWork }
        if (shift > 0) rows += listOf("Trabalho em turno", "$shift dia(s)")
        val mens = d.nights.count { it.entry.menstruation }
        if (mens > 0) rows += listOf("Menstruação", "$mens dia(s)")
        if (s.cpapNightsRecorded > 0) {
            rows += listOf(
                "CPAP",
                "usado em ${s.cpapNightsUsed} de ${s.cpapNightsRecorded} noites · média ${Fmt.d1(s.cpapHoursMean)} h · ≥ 4 h em ${s.cpapNights4h} noites",
            )
        }
        table(listOf("Fator", "No período"), rows, listOf(0.35f, 0.65f))
    }

    private fun medications() {
        heading("Medicamentos")
        if (d.meds.isEmpty()) { text("Nenhum medicamento cadastrado."); return }
        val counts = d.intakes.groupBy { it.medName }.mapValues { e -> e.value.map { it.date }.distinct().size }
        table(
            listOf("Medicamento", "Dose", "Classe", "Uso", "Situação", "Noites no período"),
            d.meds.map {
                listOf(
                    it.name, it.dose, it.drugClass.label, if (it.asNeeded) "Se necessário" else "Contínuo",
                    if (it.active) "Em uso" else "Suspenso", "${counts[it.name] ?: 0} de ${d.nights.size}",
                )
            },
            listOf(0.2f, 0.11f, 0.29f, 0.14f, 0.1f, 0.16f),
        )
    }

    private fun scales() {
        heading("Escalas")
        if (d.scales.isEmpty()) { text("Nenhuma escala respondida."); return }
        table(
            listOf("Data", "Escala", "Escore", "Interpretação"),
            d.scales.sortedByDescending { it.date }.map {
                val def = Scales.of(it.type)
                val answers = it.answers.split(",").mapNotNull(String::toIntOrNull)
                listOf(Fmt.date(it.date), def.shortName, "${it.score} / ${def.maxScore}", def.interpret(it.score, answers))
            },
            listOf(0.15f, 0.15f, 0.13f, 0.57f),
        )
        text(Scales.all.joinToString("  ") { "${it.shortName}: ${it.reference}" }, small)
    }

    private fun daily() {
        if (d.nights.isEmpty()) return
        heading("Registro diário")
        val byDate = d.intakes.groupBy { it.date }
        table(
            listOf("Data", "Deitou", "SOL", "Desp.", "WASO", "Acordou", "Levant.", "TST", "SE", "Qual.", "Medicamentos / obs."),
            d.nights.map { n ->
                val e = n.entry
                val meds = byDate[e.date].orEmpty().joinToString(", ") { it.medName }
                listOf(
                    Fmt.dateShort(e.date), Fmt.clock(e.bedTime), "${e.solMin}", "${e.awakenings}", "${e.wasoMin}",
                    Fmt.clock(e.finalWakeTime), Fmt.clock(e.outOfBedTime), Fmt.duration(n.tstMin.toDouble()),
                    Fmt.d0(n.sePct) + "%", "${e.quality}", listOf(meds, e.notes).filter { it.isNotBlank() }.joinToString(" · "),
                )
            },
            listOf(0.09f, 0.07f, 0.05f, 0.05f, 0.06f, 0.08f, 0.07f, 0.07f, 0.06f, 0.05f, 0.35f),
            fontSize = 7.5f,
        )
        text("SOL, WASO em minutos. TST = tempo total de sono; SE = eficiência (TST ÷ tempo na cama).", small)
    }

    private fun notes() {
        if (d.notes.isEmpty()) return
        heading("Anotações clínicas")
        d.notes.forEach {
            text(Fmt.date(it.date), bodyBold)
            text(it.text)
            y += 4f
        }
    }

    fun writeTo(path: String) {
        header(); summary(); sleepChart(); factors(); medications(); scales(); daily(); notes()
        finishPage()
        doc.save(path)
    }
}
