package br.sono.diario

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import br.sono.diario.data.AppDatabase
import br.sono.diario.data.Prefs
import br.sono.diario.reminder.Reminder

class App : Application() {
    lateinit var db: AppDatabase
        private set
    lateinit var prefs: Prefs
        private set

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.build(this)
        prefs = Prefs(this)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(Reminder.CHANNEL, "Lembrete do diário", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }
}
