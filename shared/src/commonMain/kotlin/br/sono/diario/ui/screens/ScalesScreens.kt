package br.sono.diario.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.sono.diario.data.ScaleResult
import br.sono.diario.data.ScaleType
import br.sono.diario.data.SleepDao
import br.sono.diario.domain.Fmt
import br.sono.diario.domain.Scales
import kotlinx.coroutines.launch

@Composable
fun ScalesScreen(dao: SleepDao, patientId: String, readOnly: Boolean, onStart: (ScaleType) -> Unit, contentPadding: PaddingValues) {
    val results by remember(patientId) { dao.scalesFlow(patientId) }.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var toDelete by remember { mutableStateOf<ScaleResult?>(null) }

    LazyColumn(
        Modifier.padding(contentPadding), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(Scales.all) { def ->
            val last = results.firstOrNull { it.type == def.type }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(def.name, style = MaterialTheme.typography.titleMedium)
                    Text(def.purpose, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (last != null) {
                        val a = last.answers.split(",").mapNotNull(String::toIntOrNull)
                        Text(
                            "Último: ${last.score}/${def.maxScore} em ${Fmt.date(last.date)} — ${def.interpret(last.score, a)}",
                            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                        )
                    }
                    if (!readOnly) FilledTonalButton(onClick = { onStart(def.type) }) { Text(if (last == null) "Responder" else "Responder novamente") }
                }
            }
        }
        if (results.isNotEmpty()) {
            item { Text("Histórico", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
            items(results, key = { it.id }) { r ->
                val def = Scales.of(r.type)
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(Fmt.date(r.date), Modifier.weight(1f))
                    Text(def.shortName, Modifier.weight(1f))
                    Text("${r.score}/${def.maxScore}", Modifier.weight(0.6f), fontWeight = FontWeight.SemiBold)
                    if (!readOnly) TextButton(onClick = { toDelete = r }) { Text("Excluir") }
                }
            }
        }
        item {
            Text(
                "Referências: " + Scales.all.joinToString(" ") { "${it.shortName}: ${it.reference}" },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
    toDelete?.let { r ->
        AlertDialog(
            onDismissRequest = { toDelete = null }, title = { Text("Excluir resultado?") },
            confirmButton = { TextButton(onClick = { scope.launch { dao.deleteScale(r); toDelete = null } }) { Text("Excluir") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancelar") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScaleFormScreen(dao: SleepDao, patientId: String, type: ScaleType, onBack: () -> Unit) {
    val def = Scales.of(type)
    val answers = remember { mutableStateListOf<Int?>().apply { repeat(def.items.size) { add(null) } } }
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(def.shortName) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(def.name, style = MaterialTheme.typography.titleLarge)
            Text(def.instructions, style = MaterialTheme.typography.bodyMedium)
            def.items.forEachIndexed { i, item ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${i + 1}. ${item.text}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        item.options.forEachIndexed { oi, o ->
                            Row(
                                Modifier.fillMaxWidth().selectable(selected = answers[i] == oi, role = Role.RadioButton) { answers[i] = oi },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = answers[i] == oi, onClick = null)
                                Text("$o (${item.points[oi]})", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
            Button(
                enabled = answers.all { it != null }, modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val pts = answers.mapIndexed { i, a -> def.items[i].points[a!!] }
                    val score = pts.sum()
                    scope.launch {
                        dao.insertScale(ScaleResult(patientId = patientId, type = type, date = Fmt.today(), answers = pts.joinToString(","), score = score))
                        result = score
                    }
                },
            ) { Text("Concluir") }
        }
    }
    result?.let { s ->
        val pts = answers.mapIndexed { i, a -> def.items[i].points[a ?: 0] }
        AlertDialog(
            onDismissRequest = onBack,
            title = { Text("Resultado: $s / ${def.maxScore}") },
            text = { Text(def.interpret(s, pts) + "\n\nEste resultado é uma triagem e deve ser interpretado por um profissional de saúde.") },
            confirmButton = { TextButton(onClick = onBack) { Text("OK") } },
        )
    }
}
