package br.sono.diario.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import br.sono.diario.AndroidPlatformBase
import br.sono.diario.App
import br.sono.diario.data.AppMode
import br.sono.diario.reminder.Reminder

class MainActivity : FragmentActivity() {
    private val app get() = application as App

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    private val platform by lazy { createPlatform() }

    private fun createPlatform() = object : AndroidPlatformBase(this) {
        override val biometricAvailable: Boolean
            get() = BiometricManager.from(context).canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL) ==
                BiometricManager.BIOMETRIC_SUCCESS

        override fun scheduleReminder(enabled: Boolean, minutes: Int) = Reminder.schedule(context, enabled, minutes)

        override fun requestNotificationPermission() {
            if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        override fun authenticate(onResult: (Boolean) -> Unit) {
            val prompt = BiometricPrompt(this@MainActivity, ContextCompat.getMainExecutor(context), object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(true)
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onResult(false)
            })
            prompt.authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Sono by Dra Alessandra")
                    .setSubtitle("Confirme sua identidade para acessar seus dados de saúde")
                    .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
                    .build()
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = app.prefs
        if (prefs.mode == AppMode.PATIENT) Reminder.schedule(this, prefs.reminderEnabled, prefs.reminderMinutes)
        setContent { SonoApp(platform, app.db, prefs) }
    }
}
