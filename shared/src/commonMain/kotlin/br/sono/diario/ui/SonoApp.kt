package br.sono.diario.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavHostController
import androidx.savedstate.read
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import br.sono.diario.LocalPlatform
import br.sono.diario.Platform
import br.sono.diario.data.AppDatabase
import br.sono.diario.data.AppMode
import br.sono.diario.data.Prefs
import br.sono.diario.data.SELF
import br.sono.diario.data.ScaleType
import br.sono.diario.data.SleepDao
import br.sono.diario.domain.PatientInfo
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
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlin.time.Duration.Companion.seconds

/** Bloqueia de novo se o app ficou mais de 60 s em segundo plano (permite usar seletor de arquivos/compartilhar). */
private val RELOCK_AFTER = 60.seconds

/** Raiz do app, compartilhada entre Android e iPhone. */
@Composable
fun SonoApp(platform: Platform, db: AppDatabase, prefs: Prefs) {
    CompositionLocalProvider(LocalPlatform provides platform) {
        SonoTheme {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                var locked by rememberSaveable { mutableStateOf(true) }
                var backgroundSince by remember { mutableStateOf<TimeMark?>(null) }
                LifecycleEventEffect(Lifecycle.Event.ON_STOP) { backgroundSince = TimeSource.Monotonic.markNow() }
                LifecycleEventEffect(Lifecycle.Event.ON_START) {
                    if ((backgroundSince?.elapsedNow() ?: 0.seconds) > RELOCK_AFTER) locked = true
                    backgroundSince = null
                }
                val needsLock = prefs.biometricLock && platform.biometricAvailable && prefs.mode != AppMode.NONE
                if (needsLock && locked) {
                    val unlock = { platform.authenticate { ok -> if (ok) locked = false } }
                    LockScreen(unlock)
                    LaunchedEffect(Unit) { unlock() }
                } else {
                    val dao = remember(db) { db.dao() }
                    when (prefs.mode) {
                        AppMode.NONE -> SetupScreen(prefs) { locked = false }
                        AppMode.PATIENT -> PatientNav(prefs, dao)
                        AppMode.PROFESSIONAL -> ProfessionalNav(prefs, dao)
                    }
                }
            }
        }
    }
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

private data class TabDef(val route: String, val label: String, val icon: ImageVector)

@Composable
private fun TabsScaffold(nav: NavHostController, tabs: List<TabDef>, content: @Composable (PaddingValues) -> Unit) {
    val current = nav.currentBackStackEntryAsState().value?.destination?.route
    Scaffold(
        topBar = { TopAppBar(title = { Text(tabs.firstOrNull { it.route == current }?.label ?: "Sono") }) },
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
private fun PatientNav(prefs: Prefs, dao: SleepDao) {
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
                    composable("settings") { SettingsScreen(prefs, dao, pad) }
                }
            }
        }
        composable("edit/{date}") { e ->
            DiaryEditScreen(dao, SELF, e.arguments?.read { getString("date") } ?: "", prefs.sex) { nav.popBackStack() }
        }
        composable("scale/{type}") { e ->
            ScaleFormScreen(dao, SELF, ScaleType.valueOf(e.arguments?.read { getString("type") } ?: "ISI")) { nav.popBackStack() }
        }
    }
}

@Composable
private fun ProfessionalNav(prefs: Prefs, dao: SleepDao) {
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
                    composable("settings") { SettingsScreen(prefs, dao, pad) }
                }
            }
        }
        composable("patient/{code}") { e ->
            PatientDetailScreen(dao, e.arguments?.read { getString("code") } ?: "", prefs.name) { nav.popBackStack() }
        }
    }
}
