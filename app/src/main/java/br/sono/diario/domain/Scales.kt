package br.sono.diario.domain

import br.sono.diario.data.ScaleType

/**
 * Escalas clínicas. Os textos abaixo seguem a estrutura e a pontuação dos instrumentos originais.
 * ATENÇÃO: antes de uso clínico formal ou comercial, substitua os textos pela versão brasileira
 * validada obtida junto ao detentor dos direitos (ISI e ESS: Mapi Research Trust; STOP-Bang:
 * University Health Network / stopbang.ca).
 */
data class ScaleItem(val text: String, val options: List<String>, val points: List<Int> = options.indices.toList())

data class ScaleDef(
    val type: ScaleType,
    val name: String,
    val shortName: String,
    val purpose: String,
    val instructions: String,
    val items: List<ScaleItem>,
    val maxScore: Int,
    val reference: String,
    val interpret: (score: Int, answers: List<Int>) -> String,
)

object Scales {
    private val severity = listOf("Nenhuma", "Leve", "Moderada", "Grave", "Muito grave")

    val ISI = ScaleDef(
        type = ScaleType.ISI,
        name = "Índice de Gravidade de Insônia",
        shortName = "ISI",
        purpose = "Gravidade da insônia nas últimas 2 semanas",
        instructions = "Para cada pergunta, marque a resposta que melhor descreve seu sono nas ÚLTIMAS 2 SEMANAS.",
        items = listOf(
            ScaleItem("Dificuldade para pegar no sono", severity),
            ScaleItem("Dificuldade para manter o sono", severity),
            ScaleItem("Problema de despertar muito cedo", severity),
            ScaleItem(
                "Quão satisfeito(a) você está com seu padrão atual de sono?",
                listOf("Muito satisfeito", "Satisfeito", "Indiferente", "Insatisfeito", "Muito insatisfeito"),
            ),
            ScaleItem(
                "Quanto você acha que outras pessoas percebem que seu problema de sono prejudica sua qualidade de vida?",
                listOf("Nada", "Um pouco", "Moderadamente", "Muito", "Extremamente"),
            ),
            ScaleItem(
                "Quão preocupado(a) ou angustiado(a) você está com seu problema de sono atual?",
                listOf("Nada", "Um pouco", "Moderadamente", "Muito", "Extremamente"),
            ),
            ScaleItem(
                "Quanto seu problema de sono interfere no seu funcionamento diário (cansaço, concentração, memória, humor, trabalho)?",
                listOf("Nada", "Um pouco", "Moderadamente", "Muito", "Extremamente"),
            ),
        ),
        maxScore = 28,
        reference = "Morin CM, 1993; Bastien CH et al., Sleep Med 2001. Versão brasileira: Castro LS, 2011 (UNIFESP).",
        interpret = { s, _ ->
            when {
                s <= 7 -> "Sem insônia clinicamente significativa"
                s <= 14 -> "Insônia subclínica (limiar)"
                s <= 21 -> "Insônia clínica moderada"
                else -> "Insônia clínica grave"
            }
        },
    )

    private val essOptions = listOf(
        "Nenhuma chance de cochilar",
        "Pequena chance de cochilar",
        "Moderada chance de cochilar",
        "Alta chance de cochilar",
    )

    val ESS = ScaleDef(
        type = ScaleType.ESS,
        name = "Escala de Sonolência de Epworth",
        shortName = "Epworth",
        purpose = "Sonolência diurna excessiva",
        instructions = "Qual a chance de você cochilar ou adormecer nas situações abaixo (e não apenas se sentir cansado)? " +
            "Considere seu modo de vida recente. Se não passou por alguma situação, imagine como seria.",
        items = listOf(
            "Sentado e lendo",
            "Assistindo TV",
            "Sentado, quieto, em lugar público (teatro, reunião, palestra)",
            "Como passageiro de carro, trem ou ônibus, andando por uma hora sem parar",
            "Deitado para descansar à tarde, quando as circunstâncias permitem",
            "Sentado e conversando com alguém",
            "Sentado calmamente após o almoço, sem ter tomado bebida alcoólica",
            "Dirigindo um carro, enquanto para por alguns minutos no trânsito",
        ).map { ScaleItem(it, essOptions) },
        maxScore = 24,
        reference = "Johns MW, Sleep 1991. Versão brasileira: Bertolazi AN et al., J Bras Pneumol 2009.",
        interpret = { s, _ ->
            when {
                s <= 10 -> "Sonolência diurna dentro do normal"
                s <= 12 -> "Sonolência diurna excessiva leve"
                s <= 15 -> "Sonolência diurna excessiva moderada"
                else -> "Sonolência diurna excessiva grave"
            }
        },
    )

    private val yesNo = listOf("Não", "Sim")

    val STOPBANG = ScaleDef(
        type = ScaleType.STOPBANG,
        name = "Questionário STOP-Bang",
        shortName = "STOP-Bang",
        purpose = "Triagem de risco para apneia obstrutiva do sono",
        instructions = "Responda sim ou não.",
        items = listOf(
            "S — Você ronca alto (mais alto que falar, ou alto o bastante para ser ouvido com a porta fechada)?",
            "T — Você se sente frequentemente cansado(a), fatigado(a) ou sonolento(a) durante o dia?",
            "O — Alguém já observou você parar de respirar, engasgar ou sufocar durante o sono?",
            "P — Você tem ou está em tratamento para pressão alta?",
            "B — Seu IMC é maior que 35 kg/m²?",
            "A — Você tem mais de 50 anos?",
            "N — A circunferência do seu pescoço é maior que 40 cm?",
            "G — Sexo masculino?",
        ).map { ScaleItem(it, yesNo) },
        maxScore = 8,
        reference = "Chung F et al., Anesthesiology 2008; Chest 2016. Versão brasileira: Duarte RLM et al., J Bras Pneumol 2017.",
        interpret = { s, a ->
            val stop = a.take(4).sum()
            val highByRule = stop >= 2 && (a.getOrElse(7) { 0 } == 1 || a.getOrElse(4) { 0 } == 1 || a.getOrElse(6) { 0 } == 1)
            when {
                s >= 5 || highByRule -> "Alto risco de apneia obstrutiva do sono"
                s >= 3 -> "Risco intermediário de apneia obstrutiva do sono"
                else -> "Baixo risco de apneia obstrutiva do sono"
            }
        },
    )

    val all = listOf(ISI, ESS, STOPBANG)
    fun of(t: ScaleType) = all.first { it.type == t }
}
