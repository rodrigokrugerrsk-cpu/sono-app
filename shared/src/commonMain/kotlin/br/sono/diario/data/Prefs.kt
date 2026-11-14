package br.sono.diario.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import br.sono.diario.KeyValueStore
import kotlin.uuid.Uuid

enum class AppMode { NONE, PATIENT, PROFESSIONAL }

/** Preferências do aparelho, expostas como estado do Compose. */
class Prefs(private val sp: KeyValueStore) {
    var mode by mutableStateOf(AppMode.entries.firstOrNull { it.name == sp.getString("mode") } ?: AppMode.NONE)
        private set
    var name by mutableStateOf(sp.getString("name") ?: "")
        private set
    var birthYear by mutableStateOf(sp.getInt("birthYear", 0).takeIf { it > 0 })
        private set
    var sex by mutableStateOf(sp.getString("sex") ?: "")
        private set
    var biometricLock by mutableStateOf(sp.getBoolean("biometric", true))
        private set
    var reminderEnabled by mutableStateOf(sp.getBoolean("reminder", true))
        private set
    var reminderMinutes by mutableStateOf(sp.getInt("reminderMin", 8 * 60))
        private set
    var professionalName by mutableStateOf(sp.getString("proName") ?: "")
        private set

    /** Código único e estável deste paciente, usado para identificar a importação. */
    val patientCode: String = sp.getString("code") ?: Uuid.random().toString().also { sp.putString("code", it) }

    fun setProfile(mode: AppMode, name: String, birthYear: Int?, sex: String) {
        this.mode = mode; this.name = name; this.birthYear = birthYear; this.sex = sex
        sp.putString("mode", mode.name); sp.putString("name", name)
        sp.putInt("birthYear", birthYear ?: 0); sp.putString("sex", sex)
    }

    fun updateBiometric(v: Boolean) { biometricLock = v; sp.putBoolean("biometric", v) }
    fun updateReminder(enabled: Boolean, minutes: Int) {
        reminderEnabled = enabled; reminderMinutes = minutes
        sp.putBoolean("reminder", enabled); sp.putInt("reminderMin", minutes)
    }
    fun updateProfessionalName(v: String) { professionalName = v; sp.putString("proName", v) }
}
