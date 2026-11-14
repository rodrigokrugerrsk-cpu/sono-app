package br.sono.diario.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.sono.diario.data.DiaryEntry
import br.sono.diario.data.Medication
import br.sono.diario.data.MedicationIntake
import br.sono.diario.data.SleepDao
import br.sono.diario.domain.Fmt
import br.sono.diario.domain.SleepMetrics
import br.sono.diario.ui.components.ChoiceChips
import br.sono.diario.ui.components.SectionCard
import br.sono.diario.ui.components.Stepper
import br.sono.diario.ui.components.SwitchRow
import br.sono.diario.ui.components.TimeField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

val QUALITY = listOf("Muito ruim", "Ruim", "Regular", "Boa", "Muito boa")
val RESTED = listOf("Nada", "Pouco", "Razoável", "Bem", "Muito bem")

@Composable
fun DiaryListScreen(
    dao: SleepDao, patientId: String, readOnly: Boolean, onOpen: (String) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val entries by remember(patientId) { dao.diaryFlow(patientId) }.collectAsState(initial = null)
    val today = Fmt.today()
    Box(Modifier.fillMaxSize().padding(contentPadding)) {
        val list = entries
        if (list != null && list.isEmpty()) {
            Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Nenhuma noite registrada ainda.", style = MaterialTheme.typography.titleMedium)
                if (!readOnly) Text(
                    "Preencha o diário todas as manhãs, logo ao acordar. São necessárias pelo menos 2 semanas para uma boa avaliação.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!readOnly && list != null && list.none { it.date == today }) {
                item {
                    Card(
                        onClick = { onOpen(today) }, modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Como foi sua noite?", style = MaterialTheme.typography.titleMedium)
                            Text("Toque para preencher o diário de hoje.", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            items(list.orEmpty(), key = { it.id }) { e ->
                val m = SleepMetrics.compute(e)
                Card(
                    onClick = { onOpen(e.date) }, modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(Fmt.dateShort(e.date), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${Fmt.clock(e.tryTime)} → ${Fmt.clock(e.finalWakeTime)} · ${QUALITY[e.quality - 1]}",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(Fmt.duration(m.tstMin.toDouble()), style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Eficiência ${Fmt.d0(m.sePct)}%", style = MaterialTheme.typography.bodySmall,
                                color = if (m.sePct < 85) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        if (!readOnly) {
            ExtendedFloatingActionButton(
                onClick = { onOpen(today) },
                icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Registrar noite") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryEditScreen(dao: SleepDao, patientId: String, initialDate: String, sex: String, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var date by remember { mutableStateOf(initialDate) }
    var pickDate by remember { mutableStateOf(false) }
    var existing by remember { mutableStateOf<DiaryEntry?>(null) }
    var loaded by remember { mutableStateOf(false) }

    var bed by remember { mutableStateOf(23 * 60) }
    var tryT by remember { mutableStateOf(23 * 60 + 15) }
    var sol by remember { mutableStateOf(15) }
    var nwak by remember { mutableStateOf(1) }
    var waso by remember { mutableStateOf(10) }
    var wake by remember { mutableStateOf(7 * 60) }
    var out by remember { mutableStateOf(7 * 60 + 15) }
    var earlier by remember { mutableStateOf(false) }
    var quality by remember { mutableStateOf(2) }
    var rested by remember { mutableStateOf(2) }
    var napCount by remember { mutableStateOf(0) }
    var napMin by remember { mutableStateOf(0) }
    var caffeine by remember { mutableStateOf(0) }
    var lastCaffeine by remember { mutableStateOf<Int?>(null) }
    var alcohol by remember { mutableStateOf(0) }
    var exercise by remember { mutableStateOf(false) }
    var screens by remember { mutableStateOf(false) }
    var stress by remember { mutableStateOf(0) }
    var pain by remember { mutableStateOf(0) }
    var shift by remember { mutableStateOf(false) }
    var menstruation by remember { mutableStateOf(false) }
    var cpap by remember { mutableStateOf<Boolean?>(null) }
    var cpapHours by remember { mutableStateOf(0) }
    var hangover by remember { mutableStateOf(0) }
    var notes by remember { mutableStateOf("") }
    var meds by remember { mutableStateOf<List<Medication>>(emptyList()) }
    val taken = remember { mutableStateListOf<Pair<Long, Int?>>() } // medId, horário
    var confirmDelete by remember { mutableStateOf(false) }

    fun apply(e: DiaryEntry, timesOnly: Boolean) {
        bed = e.bedTime; tryT = e.tryTime; wake = e.finalWakeTime; out = e.outOfBedTime
        cpap = e.cpapUsed?.let { true }
        if (timesOnly) return
        sol = e.solMin; nwak = e.awakenings; waso = e.wasoMin; earlier = e.wokeEarlier
        quality = e.quality - 1; rested = e.restedness - 1; napCount = e.napCount; napMin = e.napMin
        caffeine = e.caffeineDoses; lastCaffeine = e.lastCaffeineTime; alcohol = e.alcoholDoses
        exercise = e.exercise; screens = e.screensBeforeBed; stress = e.stress; pain = e.pain
        shift = e.shiftWork; menstruation = e.menstruation; cpap = e.cpapUsed
        cpapHours = e.cpapHours?.toInt() ?: 0; hangover = e.morningHangover; notes = e.notes
    }

    LaunchedEffect(date) {
        meds = dao.medsAll(patientId).filter { it.active }
        val e = dao.diaryByDate(patientId, date)
        existing = e
        taken.clear()
        if (e != null) {
            apply(e, timesOnly = false)
            taken.addAll(dao.intakesForDate(patientId, date).map { it.medicationId to it.time })
        } else {
            // Pré-preenche horários da última noite registrada para agilizar o preenchimento.
            dao.lastDiary(patientId)?.let { apply(it, timesOnly = true) }
            taken.addAll(meds.filter { !it.asNeeded }.map { it.id to it.usualTime })
        }
        loaded = true
    }

    fun build() = DiaryEntry(
        id = existing?.id ?: 0, patientId = patientId, date = date,
        bedTime = bed, tryTime = tryT, solMin = sol, awakenings = nwak, wasoMin = waso,
        finalWakeTime = wake, outOfBedTime = out, quality = quality + 1, restedness = rested + 1,
        wokeEarlier = earlier, napCount = napCount, napMin = napMin, caffeineDoses = caffeine,
        lastCaffeineTime = if (caffeine > 0) lastCaffeine else null, alcoholDoses = alcohol,
        exercise = exercise, screensBeforeBed = screens, stress = stress, pain = pain,
        shiftWork = shift, menstruation = menstruation, cpapUsed = cpap,
        cpapHours = if (cpap == true) cpapHours.toFloat() else null,
        morningHangover = hangover, notes = notes.trim(),
    )

    fun save() = scope.launch {
        dao.upsertDiary(build())
        dao.replaceIntakes(patientId, date, taken.mapNotNull { (id, t) ->
            meds.firstOrNull { it.id == id }?.let { MedicationIntake(patientId = patientId, date = date, medicationId = id, medName = it.name, dose = it.dose, time = t) }
        })
        withContext(Dispatchers.Main) { onBack() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "Nova noite" else "Editar noite") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
                actions = {
                    if (existing != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Excluir") }
                    TextButton(onClick = { save() }, enabled = loaded) { Text("Salvar") }
                },
            )
        },
    ) { pad ->
        if (!loaded) return@Scaffold
        val preview = SleepMetrics.compute(build().copy(quality = quality + 1))
        Column(
            Modifier.padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AssistChip(
                onClick = { pickDate = true },
                label = { Text("Manhã de ${Fmt.dateShort(date)} — noite anterior") },
                leadingIcon = { Icon(Icons.Outlined.CalendarMonth, null) },
            )

            SectionCard("A noite", "Responda sobre a noite passada, de preferência ao acordar.") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimeField("Fui para a cama", bed, { bed = it }, Modifier.weight(1f))
                    TimeField("Tentei dormir", tryT, { tryT = it }, Modifier.weight(1f))
                }
                Stepper("Tempo até adormecer", sol, { sol = it }, step = 5, max = 600, unit = " min")
                Stepper("Vezes que acordei", nwak, { nwak = it }, max = 30)
                if (nwak > 0) Stepper("Tempo acordado (total)", waso, { waso = it }, step = 5, max = 600, unit = " min")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimeField("Acordei de vez", wake, { wake = it }, Modifier.weight(1f))
                    TimeField("Saí da cama", out, { out = it }, Modifier.weight(1f))
                }
                SwitchRow("Acordei mais cedo do que queria", earlier, { earlier = it })
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Text(
                        "Sono estimado: ${Fmt.duration(preview.tstMin.toDouble())} · tempo na cama ${Fmt.duration(preview.tibMin.toDouble())} · eficiência ${Fmt.d0(preview.sePct)}%",
                        Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            SectionCard("Como foi") {
                ChoiceChips("Qualidade do sono", QUALITY, quality) { quality = it }
                ChoiceChips("Ao acordar me senti descansado(a)", RESTED, rested) { rested = it }
            }

            SectionCard("Medicamentos para dormir", if (meds.isEmpty()) "Cadastre seus medicamentos na aba Remédios." else "Marque o que tomou na noite passada.") {
                meds.forEach { m ->
                    val idx = taken.indexOfFirst { it.first == m.id }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = idx >= 0, onCheckedChange = { c ->
                            if (c) taken.add(m.id to m.usualTime) else taken.removeAll { it.first == m.id }
                        })
                        Column(Modifier.weight(1f)) {
                            Text("${m.name} ${m.dose}", style = MaterialTheme.typography.bodyLarge)
                            if (m.asNeeded) Text("se necessário", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (idx >= 0) TimeField("Horário", taken[idx].second, { t -> taken[idx] = m.id to t }, Modifier.weight(0.8f))
                    }
                }
                if (meds.isNotEmpty()) ChoiceChips("Sonolência ou \"ressaca\" pela manhã", listOf("Nenhuma", "Leve", "Moderada", "Intensa"), hangover) { hangover = it }
            }

            SectionCard("Ontem, durante o dia") {
                Stepper("Cochilos", napCount, { napCount = it; if (it == 0) napMin = 0 }, max = 10)
                if (napCount > 0) Stepper("Tempo total de cochilo", napMin, { napMin = it }, step = 10, max = 600, unit = " min")
                Stepper("Cafeína (xícaras, latas, doses)", caffeine, { caffeine = it }, max = 20)
                if (caffeine > 0) TimeField("Última cafeína", lastCaffeine, { lastCaffeine = it }, Modifier.fillMaxWidth())
                Stepper("Bebida alcoólica (doses)", alcohol, { alcohol = it }, max = 30)
                SwitchRow("Fiz exercício físico", exercise, { exercise = it })
                SwitchRow("Usei celular/TV na cama ou na hora antes de dormir", screens, { screens = it })
                ChoiceChips("Estresse", listOf("Nenhum", "Leve", "Moderado", "Alto", "Muito alto"), stress) { stress = it }
                Stepper("Dor (0 a 10)", pain, { pain = it }, max = 10)
                SwitchRow("Trabalhei em turno / plantão", shift, { shift = it })
                if (sex != "Masculino") SwitchRow("Estou menstruada", menstruation, { menstruation = it })
            }

            SectionCard("CPAP", "Apenas para quem usa aparelho de pressão positiva.") {
                ChoiceChips(null, listOf("Não uso", "Usei", "Não usei"), when (cpap) { null -> 0; true -> 1; false -> 2 }) {
                    cpap = when (it) { 1 -> true; 2 -> false; else -> null }
                }
                if (cpap == true) Stepper("Horas de uso", cpapHours, { cpapHours = it }, max = 14, unit = " h")
            }

            OutlinedTextField(
                value = notes, onValueChange = { notes = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text("Observações (sonhos, ronco, eventos...)") }, minLines = 2,
            )
            Button(onClick = { save() }, modifier = Modifier.fillMaxWidth()) { Text("Salvar") }
        }
    }

    if (pickDate) DiaryDatePicker(date, onDismiss = { pickDate = false }) { date = it; pickDate = false }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Excluir este registro?") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        existing?.let { dao.deleteDiary(it) }
                        dao.deleteIntakesForDate(patientId, date)
                        withContext(Dispatchers.Main) { onBack() }
                    }
                }) { Text("Excluir") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

/** Escolha da data do registro; datas futuras ficam bloqueadas. */
@Composable
private fun DiaryDatePicker(current: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    fun toMillis(iso: String) = LocalDate.parse(iso).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
    val todayMillis = toMillis(Fmt.today())
    val state = rememberDatePickerState(
        initialSelectedDateMillis = toMillis(current),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMillis
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { ms ->
                    onPick(Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.UTC).date.toString())
                } ?: onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    ) { DatePicker(state = state) }
}
