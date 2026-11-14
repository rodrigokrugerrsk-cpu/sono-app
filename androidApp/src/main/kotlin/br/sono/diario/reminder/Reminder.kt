package br.sono.diario.reminder

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import br.sono.diario.App
import br.sono.diario.R
import br.sono.diario.data.SELF
import br.sono.diario.domain.Fmt
import br.sono.diario.ui.MainActivity
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

object Reminder {
    const val CHANNEL = "diary_reminder"
    private const val WORK = "daily_diary_reminder"

    fun schedule(context: Context, enabled: Boolean, minutes: Int) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) { wm.cancelUniqueWork(WORK); return }
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(minutes / 60, minutes % 60)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val req = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
            .build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, req)
    }
}

class ReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as App
        if (app.db.dao().diaryByDate(SELF, Fmt.today()) != null) return Result.success()
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= 33
        ) return Result.success()
        val pi = PendingIntent.getActivity(
            app, 0, Intent(app, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(app, Reminder.CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Como foi sua noite?")
            .setContentText("Leva 1 minuto: preencha o diário do sono de hoje.")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(app).notify(1, n)
        return Result.success()
    }
}
