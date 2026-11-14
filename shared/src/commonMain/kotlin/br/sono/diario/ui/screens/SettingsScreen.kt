package br.sono.diario.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import br.sono.diario.LocalPlatform
import androidx.compose.ui.unit.dp
import br.sono.diario.data.AppMode
import br.sono.diario.data.Prefs
import br.sono.diario.data.SELF
import br.sono.diario.data.SleepDao
import br.sono.diario.ui.components.SectionCard
import br.sono.diario.ui.components.SwitchRow
import br.sono.diario.ui.components.TimeField
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(prefs: Prefs, dao: SleepDao, contentPadding: PaddingValues) {
    val platform = LocalPlatform.current
    val scope = rememberCoroutineScope()
    var confirmWipe by remember { mutableStateOf(false) }
    var confirmMode by remember { mutableStateOf(false) }
    val patient = prefs.mode == AppMode.PATIENT

    Column(
        Modifier.padding(contentPadding).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard("Perfil") {
            Text(prefs.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                if (patient) listOfNotNull(prefs.birthYear?.toString(), prefs.sex.ifBlank { null }).joinToString(" · ").ifBlank { "Paciente" }
                else "Modo profissional",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (patient) {
                var pro by remember { mutableStateOf(prefs.professionalName) }
                OutlinedTextField(
                    pro, { pro = it; prefs.updateProfessionalName(it) }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Profissional que me acompanha (aparece no relatório)") }, singleLine = true,
                )
            }
        }

        SectionCard("Segurança") {
            SwitchRow(
                "Exigir biometria para abrir", prefs.biometricLock, { prefs.updateBiometric(it) },
                if (platform.biometricAvailable) "Digital, Face ID ou o código de bloqueio do aparelho."
                else "Configure uma digital, Face ID ou bloqueio de tela no aparelho para ativar a proteção.",
            )
        }

        if (patient) SectionCard("Lembrete diário", "Notificação para preencher o diário pela manhã, se ainda não tiver preenchido.") {
            SwitchRow("Ativar lembrete", prefs.reminderEnabled, { on ->
                if (on) platform.requestNotificationPermission()
                prefs.updateReminder(on, prefs.reminderMinutes)
                platform.scheduleReminder(on, prefs.reminderMinutes)
            })
            if (prefs.reminderEnabled) TimeField("Horário", prefs.reminderMinutes, { m ->
                prefs.updateReminder(true, m); platform.scheduleReminder(true, m)
            }, Modifier.fillMaxWidth())
        }

        SectionCard("Dados") {
            Text(
                "Todos os dados ficam somente neste aparelho, sem conta nem servidor, e não entram no backup na nuvem. " +
                    "Eles só saem daqui quando você exporta um arquivo.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = { confirmMode = true }) {
                Text(if (patient) "Mudar para modo profissional" else "Mudar para modo paciente")
            }
            if (patient) OutlinedButton(onClick = { confirmWipe = true }) { Text("Apagar todos os meus registros", color = MaterialTheme.colorScheme.error) }
        }

        Text(
            "Sono by Dra Alessandra 0.1 — ferramenta de apoio ao acompanhamento clínico. Não substitui consulta médica, polissonografia " +
                "ou actigrafia. Baseado no Consensus Sleep Diary (Carney et al., 2012), ISI, Epworth e STOP-Bang.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (confirmWipe) AlertDialog(
        onDismissRequest = { confirmWipe = false },
        title = { Text("Apagar todos os registros?") },
        text = { Text("Diário, medicamentos e escalas deste aparelho serão apagados permanentemente.") },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    dao.clearDiary(SELF); dao.clearMeds(SELF); dao.clearIntakes(SELF); dao.clearScales(SELF)
                    confirmWipe = false
                }
            }) { Text("Apagar", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text("Cancelar") } },
    )
    if (confirmMode) AlertDialog(
        onDismissRequest = { confirmMode = false },
        title = { Text("Mudar de modo?") },
        text = { Text("Os dados dos dois modos são mantidos separadamente; você pode voltar quando quiser.") },
        confirmButton = {
            TextButton(onClick = {
                confirmMode = false
                prefs.setProfile(if (patient) AppMode.PROFESSIONAL else AppMode.PATIENT, prefs.name, prefs.birthYear, prefs.sex)
            }) { Text("Mudar") }
        },
        dismissButton = { TextButton(onClick = { confirmMode = false }) { Text("Cancelar") } },
    )
}
