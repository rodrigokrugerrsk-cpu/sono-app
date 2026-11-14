package br.sono.diario

import androidx.compose.ui.window.ComposeUIViewController
import br.sono.diario.data.AppMode
import br.sono.diario.data.Prefs
import br.sono.diario.data.buildDatabase
import br.sono.diario.ui.SonoApp
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import platform.UIKit.UIViewController

/** Ponto de entrada chamado pelo app iOS (ContentView.swift). */
fun MainViewController(crypto: SwiftCrypto): UIViewController {
    val platform = IosPlatform(crypto)
    val db = buildDatabase(iosDatabaseBuilder(), BundledSQLiteDriver())
    val prefs = Prefs(platform.store)
    if (prefs.mode == AppMode.PATIENT) platform.scheduleReminder(prefs.reminderEnabled, prefs.reminderMinutes)
    return ComposeUIViewController { SonoApp(platform, db, prefs) }
}
