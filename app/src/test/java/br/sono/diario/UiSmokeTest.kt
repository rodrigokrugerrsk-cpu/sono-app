package br.sono.diario

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToLog
import androidx.test.core.app.ApplicationProvider
import br.sono.diario.data.SELF
import br.sono.diario.ui.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
@org.robolectric.annotation.LooperMode(org.robolectric.annotation.LooperMode.Mode.PAUSED)
class UiSmokeTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @org.junit.Before fun log() { org.robolectric.shadows.ShadowLog.stream = System.out }

    private fun click(text: String) {
        val n = rule.onAllNodesWithText(text, useUnmergedTree = true).onFirst()
        try { n.performScrollTo() } catch (_: AssertionError) {}
        n.performClick()
    }

    @Test fun patientFlow() {
        click("Sou paciente")
        rule.onAllNodes(hasText("Seu nome")).onFirst().performTextInput("Maria Teste")
        click("Concordo e continuar")
        rule.waitForIdle()
        rule.onNodeWithText("Registrar noite", useUnmergedTree = true).assertExists()

        // Cadastra um medicamento (diálogos com campo de texto não ficam ociosos no Robolectric; insere direto)
        val app = ApplicationProvider.getApplicationContext<App>()
        runBlocking { app.db.dao().upsertMed(br.sono.diario.data.Medication(name = "Zolpidem", dose = "10 mg", usualTime = 22 * 60 + 30)) }
        click("Remédios"); rule.waitForIdle()
        rule.onNodeWithText("Zolpidem", substring = true, useUnmergedTree = true).assertExists()

        // Registra uma noite
        click("Diário"); rule.waitForIdle()
        click("Registrar noite"); rule.waitForIdle()
        rule.onNodeWithText("A noite", useUnmergedTree = true).assertExists()
        click("Salvar"); rule.waitForIdle()
        Thread.sleep(300); rule.waitForIdle()
        assertEquals(1, runBlocking { app.db.dao().diaryAll(SELF).size })
        // Medicamento de uso contínuo vem marcado por padrão na noite nova
        assertEquals("Zolpidem", runBlocking { app.db.dao().intakesAll(SELF).single().medName })

        // Escala
        click("Escalas"); rule.waitForIdle()
        rule.onNodeWithText("Escala de Sonolência de Epworth", useUnmergedTree = true).assertExists()
        rule.onAllNodesWithText("Responder", useUnmergedTree = true)[1].performClick(); rule.waitForIdle()
        repeat(8) { rule.onAllNodesWithText("Pequena chance de cochilar (1)", useUnmergedTree = true)[it].performScrollTo().performClick() }
        rule.onNodeWithText("Concluir", useUnmergedTree = true).performScrollTo().performClick()
        rule.waitForIdle(); Thread.sleep(300); rule.waitForIdle()
        rule.onNodeWithText("Resultado: 8 / 24", useUnmergedTree = true).assertExists()
        click("OK"); rule.waitForIdle()

        // Relatório e ajustes
        click("Relatório"); rule.waitForIdle(); Thread.sleep(300); rule.waitForIdle()
        rule.onNodeWithText("Gerar relatório PDF", substring = true, useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Gráfico do sono", useUnmergedTree = true).assertExists()
        click("Ajustes"); rule.waitForIdle()
        rule.onNodeWithText("Exigir biometria para abrir", useUnmergedTree = true).assertExists()
    }

    @Test fun professionalFlow() {
        click("Sou profissional de saúde")
        rule.onAllNodes(hasText("Seu nome", substring = true)).onFirst().performTextInput("Dra. Ana")
        click("Concordo e continuar"); rule.waitForIdle()
        rule.onNodeWithText("Importar", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Nenhum paciente ainda", substring = true, useUnmergedTree = true).assertExists()
    }
}
