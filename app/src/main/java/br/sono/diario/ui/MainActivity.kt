package br.sono.diario.ui

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import br.sono.diario.App
import br.sono.diario.data.AppMode
import br.sono.diario.data.SELF
import br.sono.diario.data.ScaleType
import br.sono.diario.domain.PatientInfo
import br.sono.diario.reminder.Reminder
import br.sono.diario.ui.components.BrandLogo
import br.sono.diario.ui.screens.BundleSource
import br.sono.diario.ui.screens.DiaryEditScreen
import br.sono.diario.ui.screens.DiaryListScreen
import br.sono.diario.ui.screens.MedicationsScreen
import br.sono.diario.ui.screens.PatientDetailScreen
import br.sono.diario.ui.screens.PatientsScreen
import br.sono.diario.ui.screens.ReportScreen
import br.sono.diario.ui.screens.ScaleFormScreen
import br.sono.diario.ui.screens.ScalesScreen
import br.sono.diario.ui.screens.SettingsScreen
import br.sono.diario.ui.screens.SetupScreen
import br.sono.diario.ui.theme.SonoTheme

class MainActivity : FragmentActivity() {
    private var locked by mutableStateOf(true)
    private var lastBackground = 0L

    /** Bloqueia novamente se o app ficou mais de 60 s em segundo plano (permite usar seletor de arquivos/compartilhar). */
    private val relockAfterMs = 60_000L

    private val app get() = application as App

    private fun biometricAvailable() =
        BiometricManager.from(this).canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        locked = savedInstanceState?.getBoolean("locked") ?: true
        val prefs = app.prefs
        if (prefs.mode == AppMode.PATIENT) Reminder.schedule(this, prefs.reminderEnabled, prefs.reminderMinutes)
        setContent {
            SonoTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val needsLock = prefs.biometricLock && biometricAvailable() && prefs.mode != AppMode.NONE
                    if (needsLock && locked) {
                        LockScreen { authenticate() }
                        LaunchedEffect(Unit) { authenticate() }
                    } else {
                        Root()
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("locked", locked)
    }

    override fun onStop() {
        super.onStop()
        lastBackground = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        if (lastBackground > 0 && SystemClock.elapsedRealtime() - lastBackground > relockAfterMs) locked = true
    }

    private fun authenticate() {
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { locked = false }
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Sono by Dra Alessandra")
                .setSubtitle("Confirme sua identidade para acessar seus dados de saúde")
                .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
                .build()
        )
    }

    @Composable
    private fun LockScreen(onUnlock: () -> Unit) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BrandLogo()
            Icon(Icons.Outlined.Fingerprint, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
            Text("Seus dados estão protegidos.", textAlign = TextAlign.Center)
            Button(onClick = onUnlock) { Text("Desbloquear") }
        }
    }

    @Composable
    private fun Root() {
        val prefs = app.prefs
        when (prefs.mode) {
            AppMode.NONE -> SetupScreen(prefs) { locked = false }
            AppMode.PATIENT -> PatientNav()
            AppMode.PROFESSIONAL -> ProfessionalNav()
        }
    }

    private data class TabDef(val route: String, val label: String, val icon: ImageVector)

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun TabsScaffold(nav: NavHostController, tabs: List<TabDef>, content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit) {
        val current = nav.currentBackStackEntryAsState().value?.destination?.route
        Scaffold(
            topBar = { TopAppBar(title = { Text(tabs.firstOrNull { it.route == current }?.label ?: "Diário do Sono") }) },
            bottomBar = {
                NavigationBar {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = current == t.route,
                            onClick = {
                                nav.navigate(t.route) {
                                    popUpTo(tabs.first().route) { saveState = true }
                                    launchSingleTop = true; restoreState = true
                                }
                            },
                            icon = { Icon(t.icon, null) }, label = { Text(t.label) },
                        )
                    }
                }
            },
        ) { pad -> content(pad) }
    }

    @Composable
    private fun PatientNav() {
        val prefs = app.prefs
        val dao = app.db.dao()
        val nav = rememberNavController()
        val tabs = listOf(
            TabDef("diary", "Diário", Icons.Outlined.Bedtime),
            TabDef("meds", "Remédios", Icons.Outlined.Medication),
            TabDef("scales", "Escalas", Icons.Outlined.Checklist),
            TabDef("report", "Relatório", Icons.Outlined.Assessment),
            TabDef("settings", "Ajustes", Icons.Outlined.Settings),
        )
        val info = PatientInfo(prefs.name, prefs.birthYear, prefs.sex)
        NavHost(nav, startDestination = "tabs") {
            composable("tabs") {
                val inner = rememberNavController()
                TabsScaffold(inner, tabs) { pad ->
                    NavHost(inner, startDestination = "diary") {
                        composable("diary") { DiaryListScreen(dao, SELF, readOnly = false, onOpen = { nav.navigate("edit/$it") }, contentPadding = pad) }
                        composable("meds") { MedicationsScreen(dao, SELF, readOnly = false, contentPadding = pad) }
                        composable("scales") { ScalesScreen(dao, SELF, readOnly = false, onStart = { nav.navigate("scale/${it.name}") }, contentPadding = pad) }
                        composable("report") {
                            ReportScreen(dao, SELF, info, prefs.professionalName, BundleSource(prefs.patientCode, info), pad)
                        }
                        composable("settings") { SettingsScreen(prefs, dao, biometricAvailable(), pad) }
                    }
                }
            }
            composable("edit/{date}") { e ->
                DiaryEditScreen(dao, SELF, e.arguments?.getString("date")!!, prefs.sex) { nav.popBackStack() }
            }
            composable("scale/{type}") { e ->
                ScaleFormScreen(dao, SELF, ScaleType.valueOf(e.arguments?.getString("type")!!)) { nav.popBackStack() }
            }
        }
    }

    @Composable
    private fun ProfessionalNav() {
        val prefs = app.prefs
        val dao = app.db.dao()
        val nav = rememberNavController()
        val tabs = listOf(
            TabDef("patients", "Pacientes", Icons.Outlined.Groups),
            TabDef("settings", "Ajustes", Icons.Outlined.Settings),
        )
        NavHost(nav, startDestination = "tabs") {
            composable("tabs") {
                val inner = rememberNavController()
                TabsScaffold(inner, tabs) { pad ->
                    NavHost(inner, startDestination = "patients") {
                        composable("patients") { PatientsScreen(dao, onOpen = { nav.navigate("patient/$it") }, contentPadding = pad) }
                        composable("settings") { SettingsScreen(prefs, dao, biometricAvailable(), pad) }
                    }
                }
            }
            composable("patient/{code}") { e ->
                PatientDetailScreen(dao, e.arguments?.getString("code")!!, prefs.name) { nav.popBackStack() }
            }
        }
    }
}
