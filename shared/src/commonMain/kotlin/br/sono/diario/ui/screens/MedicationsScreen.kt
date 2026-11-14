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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.sono.diario.data.DrugClass
import br.sono.diario.data.Medication
import br.sono.diario.data.SleepDao
import br.sono.diario.domain.Fmt
import br.sono.diario.ui.components.SwitchRow
import br.sono.diario.ui.components.TimeField
import kotlinx.coroutines.launch

@Composable
fun MedicationsScreen(dao: SleepDao, patientId: String, readOnly: Boolean, contentPadding: PaddingValues) {
    val meds by remember(patientId) { dao.medsFlow(patientId) }.collectAsState(initial = emptyList())
    var editing by remember { mutableStateOf<Medication?>(null) }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize().padding(contentPadding)) {
        LazyColumn(contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (meds.isEmpty()) item {
                Text(
                    "Nenhum medicamento cadastrado. Inclua remédios para dormir, melatonina, fitoterápicos e outros que possam afetar o sono.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(meds, key = { it.id }) { m ->
                Card(
                    onClick = { if (!readOnly) editing = m }, modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("${m.name} · ${m.dose}", style = MaterialTheme.typography.titleMedium)
                        Text(
                            listOfNotNull(
                                m.drugClass.label,
                                if (m.asNeeded) "se necessário" else "uso contínuo",
                                m.usualTime?.let { "às ${Fmt.clock(it)}" },
                                if (!m.active) "SUSPENSO" else null,
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (!readOnly) ExtendedFloatingActionButton(
            onClick = { editing = Medication(patientId = patientId, name = "", dose = "", startDate = Fmt.today()) },
            icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Medicamento") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    editing?.let { m ->
        MedicationDialog(
            initial = m,
            onDismiss = { editing = null },
            onSave = { scope.launch { dao.upsertMed(it); editing = null } },
            onDelete = if (m.id != 0L) ({ scope.launch { dao.deleteMed(m); editing = null } }) else null,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MedicationDialog(initial: Medication, onDismiss: () -> Unit, onSave: (Medication) -> Unit, onDelete: (() -> Unit)?) {
    var name by remember { mutableStateOf(initial.name) }
    var dose by remember { mutableStateOf(initial.dose) }
    var cls by remember { mutableStateOf(initial.drugClass) }
    var prn by remember { mutableStateOf(initial.asNeeded) }
    var time by remember { mutableStateOf(initial.usualTime) }
    var active by remember { mutableStateOf(initial.active) }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "Novo medicamento" else "Editar medicamento") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nome") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(dose, { dose = it }, label = { Text("Dose (ex.: 10 mg)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Box {
                    OutlinedCard(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                            Text("Classe", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(cls.label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DrugClass.entries.forEach { c ->
                            DropdownMenuItem(text = { Text(c.label) }, onClick = { cls = c; expanded = false })
                        }
                    }
                }
                SwitchRow("Uso \"se necessário\"", prn, { prn = it })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TimeField("Horário habitual", time, { time = it }, Modifier.weight(1f))
                    if (time != null) TextButton(onClick = { time = null }) { Text("Limpar") }
                }
                SwitchRow("Em uso", active, { active = it }, "Desative quando o medicamento for suspenso.")
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Excluir medicamento", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onSave(initial.copy(name = name.trim(), dose = dose.trim(), drugClass = cls, asNeeded = prn, usualTime = time, active = active)) },
            ) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
