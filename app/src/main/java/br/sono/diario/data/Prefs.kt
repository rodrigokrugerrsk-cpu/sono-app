package br.sono.diario.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.UUID

enum class AppMode { NONE, PATIENT, PROFESSIONAL }

/** Preferências do aparelho, expostas como estado do Compose. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    var mode by mutableStateOf(AppMode.valueOf(sp.getString("mode", AppMode.NONE.name)!!))
        private set
    var name by mutableStateOf(sp.getString("name", "")!!)
        private set
    var birthYear by mutableStateOf(sp.getInt("birthYear", 0).takeIf { it > 0 })
        private set
    var sex by mutableStateOf(sp.getString("sex", "")!!)
        private set
    var biometricLock by mutableStateOf(sp.getBoolean("biometric", true))
        private set
    var reminderEnabled by mutableStateOf(sp.getBoolean("reminder", true))
        private set
    var reminderMinutes by mutableStateOf(sp.getInt("reminderMin", 8 * 60))
        private set
    var professionalName by mutableStateOf(sp.getString("proName", "")!!)
        private set

    /** Código único e estável deste paciente, usado para identificar a importação. */
    val patientCode: String = sp.getString("code", null) ?: UUID.randomUUID().toString().also {
        sp.edit().putString("code", it).apply()
    }

    fun setProfile(mode: AppMode, name: String, birthYear: Int?, sex: String) {
        this.mode = mode; this.name = name; this.birthYear = birthYear; this.sex = sex
        sp.edit().putString("mode", mode.name).putString("name", name)
            .putInt("birthYear", birthYear ?: 0).putString("sex", sex).apply()
    }

    fun updateBiometric(v: Boolean) { biometricLock = v; sp.edit().putBoolean("biometric", v).apply() }
    fun updateReminder(enabled: Boolean, minutes: Int) {
        reminderEnabled = enabled; reminderMinutes = minutes
        sp.edit().putBoolean("reminder", enabled).putInt("reminderMin", minutes).apply()
    }
    fun updateProfessionalName(v: String) { professionalName = v; sp.edit().putString("proName", v).apply() }
}
