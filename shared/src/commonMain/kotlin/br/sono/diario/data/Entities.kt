package br.sono.diario.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** Identificador dos dados do próprio usuário do aparelho (modo paciente). */
const val SELF = "self"

/**
 * Uma noite do diário de sono, baseada no Consensus Sleep Diary (Carney et al., Sleep 2012).
 * [date] é a data da manhã em que o diário é preenchido (yyyy-MM-dd).
 * Horários são minutos desde a meia-noite (0..1439).
 */
@Serializable
@Entity(tableName = "diary", indices = [Index(value = ["patientId", "date"], unique = true)])
data class DiaryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientId: String = SELF,
    val date: String,
    // Consensus Sleep Diary - núcleo
    val bedTime: Int,               // Q1 hora em que foi para a cama
    val tryTime: Int,               // Q2 hora em que tentou dormir
    val solMin: Int,                // Q3 quanto tempo para adormecer
    val awakenings: Int,            // Q4 número de despertares
    val wasoMin: Int,               // Q5 tempo total acordado nesses despertares
    val finalWakeTime: Int,         // Q6 hora do despertar final
    val outOfBedTime: Int,          // Q7 hora em que saiu da cama
    val quality: Int,               // Q8 qualidade 1 (muito ruim) .. 5 (muito boa)
    val restedness: Int = 3,        // sensação de descanso 1..5
    val wokeEarlier: Boolean = false, // despertou antes do planejado (EMA)
    // Fatores diurnos (dia anterior)
    val napCount: Int = 0,
    val napMin: Int = 0,
    val caffeineDoses: Int = 0,
    val lastCaffeineTime: Int? = null,
    val alcoholDoses: Int = 0,
    val exercise: Boolean = false,
    val screensBeforeBed: Boolean = false,
    val stress: Int = 0,            // 0..4
    val pain: Int = 0,              // 0..10
    val shiftWork: Boolean = false,
    val menstruation: Boolean = false,
    // CPAP
    val cpapUsed: Boolean? = null,
    val cpapHours: Float? = null,
    // Efeito residual de medicamentos pela manhã 0 nenhum .. 3 intenso
    val morningHangover: Int = 0,
    val notes: String = "",
)

enum class DrugClass(val label: String) {
    BENZO("Benzodiazepínico"),
    Z_DRUG("Hipnótico Z (zolpidem, eszopiclona)"),
    MELATONIN("Melatonina / agonista melatoninérgico"),
    ANTIDEPRESSANT("Antidepressivo sedativo (trazodona, mirtazapina...)"),
    ANTIPSYCHOTIC("Antipsicótico (quetiapina...)"),
    ANTIHISTAMINE("Anti-histamínico"),
    ORE_ANTAGONIST("Antagonista de orexina"),
    GABAPENTINOID("Gabapentinoide"),
    DOPAMINERGIC("Dopaminérgico (pramipexol...)"),
    STIMULANT("Estimulante / promotor de vigília"),
    HERBAL("Fitoterápico"),
    OTHER("Outro"),
}

@Serializable
@Entity(tableName = "medication")
data class Medication(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientId: String = SELF,
    val name: String,
    val dose: String,               // ex.: "10 mg"
    val drugClass: DrugClass = DrugClass.OTHER,
    val asNeeded: Boolean = false,  // "se necessário"
    val usualTime: Int? = null,
    val active: Boolean = true,
    val startDate: String? = null,
)

/** Tomada de medicamento na noite referente ao diário [date]. */
@Serializable
@Entity(tableName = "intake", indices = [Index("patientId", "date")])
data class MedicationIntake(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientId: String = SELF,
    val date: String,
    val medicationId: Long,
    val medName: String,
    val dose: String,
    val time: Int? = null,
)

enum class ScaleType { ISI, ESS, STOPBANG }

@Serializable
@Entity(tableName = "scale_result", indices = [Index("patientId", "type")])
data class ScaleResult(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientId: String = SELF,
    val type: ScaleType,
    val date: String,
    val answers: String,            // valores separados por vírgula
    val score: Int,
)

/** Paciente importado (modo profissional). */
@Entity(tableName = "patient")
data class Patient(
    @PrimaryKey val code: String,
    val name: String,
    val birthYear: Int?,
    val sex: String,
    val importedAt: Long,
    val lastEntryDate: String?,
)

@Entity(tableName = "clinical_note", indices = [Index("patientId")])
data class ClinicalNote(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientId: String,
    val date: String,
    val text: String,
)
