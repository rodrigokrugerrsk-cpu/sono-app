package br.sono.diario.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import br.sono.diario.data.SleepDao
import br.sono.diario.domain.Fmt
import br.sono.diario.domain.PatientInfo
import br.sono.diario.domain.ReportData
import br.sono.diario.domain.Scales
import br.sono.diario.export.CsvExport
import br.sono.diario.export.PatientBundle
import br.sono.diario.export.PatientExport
import br.sono.diario.export.PdfReport
import br.sono.diario.export.Share
import br.sono.diario.ui.components.SectionCard
import br.sono.diario.ui.components.SleepChart
import br.sono.diario.ui.components.StatTile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/** Dados para gerar o arquivo cifrado que o paciente envia ao profissional. */
class BundleSource(val code: String, val info: PatientInfo)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportScreen(
    dao: SleepDao,
    patientId: String,
    info: PatientInfo,
    professionalName: String,
    bundleSource: BundleSource?,
    contentPadding: PaddingValues,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var days by rememberSaveable { mutableStateOf(14) }
    var data by remember { mutableStateOf<ReportData?>(null) }
    var busy by remember { mutableStateOf(false) }
    var askPassword by remember { mutableStateOf(false) }

    val to = Fmt.today()
    val from = LocalDate.now().minusDays(days.toLong() - 1).toString()
    LaunchedEffect(days, patientId) { data = ReportData.load(dao, patientId, info, from, to, professionalName) }

    fun safeName() = info.name.ifBlank { "paciente" }.replace(Regex("[^A-Za-z0-9À-ú]+"), "_")

    fun exportPdf() = scope.launch {
        val d = data ?: return@launch
        busy = true
        val f = withContext(Dispatchers.IO) {
            File(Share.exportDir(ctx), "relatorio_sono_${safeName()}_${d.to}.pdf").also { PdfReport(d).writeTo(it) }
        }
        busy = false
        Share.file(ctx, f, "application/pdf", "Relatório do sono")
    }

    fun exportCsv() = scope.launch {
        val d = data ?: return@launch
        val f = withContext(Dispatchers.IO) {
            File(Share.exportDir(ctx), "diario_sono_${safeName()}_${d.to}.csv").also { it.writeText(CsvExport.diary(d.nights, d.intakes)) }
        }
        Share.file(ctx, f, "text/csv", "Diário do sono (CSV)")
    }

    fun exportBundle(password: String) = scope.launch {
        val src = bundleSource ?: return@launch
        busy = true
        val f = withContext(Dispatchers.Default) {
            val bundle = PatientBundle(
                exportedAt = System.currentTimeMillis(), patientCode = src.code, name = src.info.name,
                birthYear = src.info.birthYear, sex = src.info.sex,
                diary = dao.diaryAll(patientId), medications = dao.medsAll(patientId),
                intakes = dao.intakesAll(patientId), scales = dao.scalesAll(patientId),
            )
            val bytes = PatientExport.encrypt(bundle, password.toCharArray())
            File(Share.exportDir(ctx), "sono_${safeName()}_$to.sono").also { it.writeBytes(bytes) }
        }
        busy = false
        Toast.makeText(ctx, "Informe a senha ao profissional por outro meio (pessoalmente ou por telefone).", Toast.LENGTH_LONG).show()
        Share.file(ctx, f, "application/octet-stream", "Dados do diário do sono")
    }

    Column(
        Modifier.padding(contentPadding).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(7, 14, 28, 90).forEach { d ->
                FilterChip(selected = days == d, onClick = { days = d }, label = { Text("$d dias") })
            }
        }
        val d = data
        if (d == null) { CircularProgressIndicator(); return@Column }
        val s = d.summary
        Text(
            "${Fmt.date(d.from)} a ${Fmt.date(d.to)} · ${d.nights.size} de ${d.daysInPeriod} noites registradas",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (d.nights.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 2) {
                val m = Modifier.weight(1f)
                StatTile("Sono total (média)", Fmt.duration(s.tst.mean), "DP ${Fmt.d0(s.tst.sd)} min", m, warn = s.tst.mean < 360)
                StatTile("Eficiência", "${Fmt.d0(s.se.mean)}%", "meta ≥ 85%", m, warn = s.se.mean < 85)
                StatTile("Para adormecer", "${Fmt.d0(s.sol.mean)} min", "latência (SOL)", m, warn = s.sol.mean > 30)
                StatTile("Acordado à noite", "${Fmt.d0(s.waso.mean)} min", "${Fmt.d1(s.nwak.mean)} despertares", m, warn = s.waso.mean > 30)
                StatTile("Deitar (média)", Fmt.clockAbs(s.bedtimeClock.mean), "DP ${Fmt.d0(s.bedtimeClock.sd)} min", m)
                StatTile("Despertar (média)", Fmt.clockAbs(s.wakeClock.mean), "DP ${Fmt.d0(s.wakeClock.sd)} min", m)
                StatTile("Qualidade", "${Fmt.d1(s.quality.mean)} / 5", QUALITY[(s.quality.mean.toInt() - 1).coerceIn(0, 4)], m)
                StatTile("Descanso", "${Fmt.d1(s.restedness.mean)} / 5", null, m)
            }
            val flags = d.flags()
            if (flags.isNotEmpty()) SectionCard("Pontos de atenção") {
                flags.forEach { Text("• $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            }
            SectionCard("Gráfico do sono") { SleepChart(d.nights, d.intakes) }
            SectionCard("Hábitos") {
                Text(
                    "Cochilos em ${s.napDays} dias · cafeína ${Fmt.d1(s.caffeineMean)} doses/dia · álcool em ${s.alcoholNights} noites · " +
                        "exercício em ${s.exerciseDays} dias · telas em ${s.screenNights} noites",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (s.cpapNightsRecorded > 0) Text(
                    "CPAP: ≥ 4 h em ${s.cpapNights4h} de ${s.cpapNightsRecorded} noites · média ${Fmt.d1(s.cpapHoursMean)} h",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            Text("Nenhuma noite registrada neste período.", style = MaterialTheme.typography.bodyLarge)
        }
        if (d.scales.isNotEmpty()) SectionCard("Escalas mais recentes") {
            Scales.all.forEach { def ->
                d.scales.filter { it.type == def.type }.maxByOrNull { it.date }?.let { r ->
                    val a = r.answers.split(",").mapNotNull(String::toIntOrNull)
                    Text("${def.shortName}: ${r.score}/${def.maxScore} (${Fmt.date(r.date)}) — ${def.interpret(r.score, a)}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Button(onClick = { exportPdf() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.PictureAsPdf, null); Text("  Gerar relatório PDF")
        }
        OutlinedButton(onClick = { exportCsv() }, enabled = !busy && d.nights.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.TableChart, null); Text("  Exportar planilha (CSV)")
        }
        if (bundleSource != null) {
            OutlinedButton(onClick = { askPassword = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Lock, null); Text("  Enviar dados ao profissional")
            }
            Text(
                "Gera um arquivo protegido por senha com todo o seu histórico, para o profissional importar no app dele.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (busy) CircularProgressIndicator()
    }

    if (askPassword) PasswordDialog(
        title = "Senha do arquivo",
        text = "Crie uma senha (mínimo 6 caracteres) e informe-a ao profissional pessoalmente ou por telefone, nunca junto com o arquivo.",
        confirm = true,
        onDismiss = { askPassword = false },
        onOk = { askPassword = false; exportBundle(it) },
    )
}

@Composable
fun PasswordDialog(title: String, text: String, confirm: Boolean, onDismiss: () -> Unit, onOk: (String) -> Unit, error: String? = null) {
    var p1 by remember { mutableStateOf("") }
    var p2 by remember { mutableStateOf("") }
    val valid = p1.length >= 6 && (!confirm || p1 == p2)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    p1, { p1 = it }, label = { Text("Senha") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                if (confirm) OutlinedTextField(
                    p2, { p2 = it }, label = { Text("Confirmar senha") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = p2.isNotEmpty() && p1 != p2,
                )
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = { onOk(p1) }, enabled = valid) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
