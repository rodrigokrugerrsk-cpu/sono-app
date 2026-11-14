package br.sono.diario.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import br.sono.diario.LocalPlatform
import br.sono.diario.rememberFilePicker
import androidx.compose.ui.unit.dp
import br.sono.diario.data.ClinicalNote
import br.sono.diario.data.Patient
import br.sono.diario.data.SleepDao
import br.sono.diario.domain.Fmt
import br.sono.diario.domain.PatientInfo
import br.sono.diario.export.PatientExport
import br.sono.diario.export.WrongPasswordException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PatientsScreen(dao: SleepDao, onOpen: (String) -> Unit, contentPadding: PaddingValues) {
    val platform = LocalPlatform.current
    val scope = rememberCoroutineScope()
    val patients by remember { dao.patientsFlow() }.collectAsState(initial = emptyList())
    var pendingFile by remember { mutableStateOf<ByteArray?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val pickFile = rememberFilePicker { bytes ->
        if (bytes != null) { error = null; pendingFile = bytes }
    }

    fun import(bytes: ByteArray, password: String) = scope.launch {
        busy = true
        try {
            val bundle = withContext(Dispatchers.Default) { PatientExport.decrypt(platform.crypto, bytes, password) }
            val existed = dao.patient(bundle.patientCode) != null
            dao.replacePatientData(
                Patient(
                    code = bundle.patientCode, name = bundle.name, birthYear = bundle.birthYear, sex = bundle.sex,
                    importedAt = Fmt.nowMillis(), lastEntryDate = bundle.diary.maxOfOrNull { it.date },
                ),
                bundle.diary, bundle.medications, bundle.intakes, bundle.scales,
            )
            pendingFile = null
            message = (if (existed) "Dados de ${bundle.name} atualizados" else "${bundle.name} importado(a)") +
                ": ${bundle.diary.size} noites, ${bundle.scales.size} escalas."
        } catch (e: WrongPasswordException) {
            error = "Senha incorreta."
        } catch (e: Exception) {
            pendingFile = null
            message = "Não foi possível importar: ${e.message}"
        } finally {
            busy = false
        }
    }

    Box(Modifier.fillMaxSize().padding(contentPadding)) {
        LazyColumn(contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (patients.isEmpty()) item {
                Text(
                    "Nenhum paciente ainda. Peça ao paciente para tocar em \"Enviar dados ao profissional\" na aba Relatório do app dele " +
                        "e enviar o arquivo .sono a você. Depois toque em Importar e informe a senha combinada.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(patients, key = { it.code }) { p ->
                Card(
                    onClick = { onOpen(p.code) }, modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(p.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            listOfNotNull(
                                p.birthYear?.let { "${Fmt.currentYear() - it} anos" }, p.sex.ifBlank { null },
                                p.lastEntryDate?.let { "último registro ${Fmt.date(it)}" }, "importado ${Fmt.date(Fmt.dateOfMillis(p.importedAt))}",
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = pickFile,
            icon = { Icon(Icons.Outlined.FileOpen, null) }, text = { Text("Importar") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
        if (busy) CircularProgressIndicator(Modifier.align(Alignment.Center))
    }

    pendingFile?.let { bytes ->
        if (!busy) PasswordDialog(
            title = "Senha do arquivo", text = "Informe a senha que o paciente criou ao exportar.",
            confirm = false, error = error, onDismiss = { pendingFile = null }, onOk = { import(bytes, it) },
        )
    }
    message?.let {
        AlertDialog(onDismissRequest = { message = null }, text = { Text(it) }, confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientDetailScreen(dao: SleepDao, code: String, professionalName: String, onBack: () -> Unit) {
    var patient by remember { mutableStateOf<Patient?>(null) }
    var tab by rememberSaveable { mutableStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(code) { patient = dao.patient(code) }
    val p = patient ?: return
    val tabs = listOf("Relatório", "Diário", "Remédios", "Escalas", "Notas")

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(p.name) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
                    actions = { IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Remover paciente") } },
                )
                PrimaryScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
                    tabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
                }
            }
        },
    ) { pad ->
        when (tab) {
            0 -> ReportScreen(dao, code, PatientInfo(p.name, p.birthYear, p.sex), professionalName, null, pad)
            1 -> DiaryListScreen(dao, code, readOnly = true, onOpen = {}, contentPadding = pad)
            2 -> MedicationsScreen(dao, code, readOnly = true, contentPadding = pad)
            3 -> ScalesScreen(dao, code, readOnly = true, onStart = {}, contentPadding = pad)
            else -> NotesTab(dao, code, pad)
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Remover ${p.name}?") },
        text = { Text("Todos os dados importados e as anotações deste paciente serão apagados deste aparelho.") },
        confirmButton = { TextButton(onClick = { scope.launch { dao.deletePatient(code); withContext(Dispatchers.Main) { onBack() } } }) { Text("Remover") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
    )
}

@Composable
private fun NotesTab(dao: SleepDao, code: String, pad: PaddingValues) {
    val notes by remember(code) { dao.notesFlow(code) }.collectAsState(initial = emptyList())
    var text by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    text, { text = it }, modifier = Modifier.fillMaxWidth(), minLines = 3,
                    label = { Text("Nova anotação clínica") },
                )
                Button(
                    enabled = text.isNotBlank(),
                    onClick = { scope.launch { dao.insertNote(ClinicalNote(patientId = code, date = Fmt.today(), text = text.trim())); text = "" } },
                ) { Text("Adicionar") }
                Text(
                    "As anotações ficam só neste aparelho e aparecem no relatório PDF. Não são enviadas ao paciente.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(notes, key = { it.id }) { n ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(Fmt.date(n.date), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = { scope.launch { dao.deleteNote(n) } }) { Text("Excluir") }
                    }
                    Text(n.text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
