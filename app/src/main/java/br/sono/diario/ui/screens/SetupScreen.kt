package br.sono.diario.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import br.sono.diario.data.AppMode
import br.sono.diario.data.Prefs
import br.sono.diario.ui.components.BrandLogo
import br.sono.diario.ui.components.ChoiceChips

@Composable
fun SetupScreen(prefs: Prefs, onDone: () -> Unit) {
    var mode by rememberSaveable { mutableStateOf(AppMode.NONE) }
    var name by rememberSaveable { mutableStateOf("") }
    var year by rememberSaveable { mutableStateOf("") }
    var sex by rememberSaveable { mutableStateOf<Int?>(null) }
    val sexes = listOf("Feminino", "Masculino", "Outro")

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BrandLogo(Modifier.align(Alignment.CenterHorizontally))
        Text(
            "Registre seu sono, medicamentos e escalas clínicas, e gere relatórios para avaliação de distúrbios do sono. " +
                "Os dados ficam apenas neste aparelho.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text("Como você vai usar o app?", style = MaterialTheme.typography.titleMedium)
        ModeCard(
            selected = mode == AppMode.PATIENT, icon = { Icon(Icons.Outlined.Bedtime, null) },
            title = "Sou paciente", text = "Preencho o diário do sono e envio os dados ao meu profissional de saúde.",
        ) { mode = AppMode.PATIENT }
        ModeCard(
            selected = mode == AppMode.PROFESSIONAL, icon = { Icon(Icons.Outlined.MedicalServices, null) },
            title = "Sou profissional de saúde", text = "Importo os arquivos dos pacientes, vejo gráficos, relatórios e faço anotações.",
        ) { mode = AppMode.PROFESSIONAL }

        if (mode != AppMode.NONE) {
            OutlinedTextField(
                value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text(if (mode == AppMode.PATIENT) "Seu nome" else "Seu nome e registro (ex.: Dra. Ana — CRM 12345)") },
                singleLine = true,
            )
            if (mode == AppMode.PATIENT) {
                OutlinedTextField(
                    value = year, onValueChange = { year = it.filter(Char::isDigit).take(4) },
                    modifier = Modifier.fillMaxWidth(), label = { Text("Ano de nascimento") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                ChoiceChips("Sexo", sexes, sex) { sex = it }
            }
            Text(
                "Ao continuar, você concorda que os dados de saúde informados sejam armazenados neste aparelho " +
                    "e compartilhados somente quando você mesmo exportar um arquivo (LGPD, art. 11).",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = {
                    prefs.setProfile(mode, name.trim(), year.toIntOrNull(), sex?.let { sexes[it] } ?: "")
                    if (mode == AppMode.PROFESSIONAL) prefs.updateProfessionalName(name.trim())
                    onDone()
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Concordo e continuar") }
        }
    }
}

@Composable
private fun ModeCard(selected: Boolean, icon: @Composable () -> Unit, title: String, text: String, onClick: () -> Unit) {
    Card(
        onClick = onClick, modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            icon()
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
