package br.sono.diario.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SleepDao {
    // Diário
    @Query("SELECT * FROM diary WHERE patientId = :p ORDER BY date DESC")
    fun diaryFlow(p: String): Flow<List<DiaryEntry>>

    @Query("SELECT * FROM diary WHERE patientId = :p AND date BETWEEN :from AND :to ORDER BY date")
    suspend fun diaryRange(p: String, from: String, to: String): List<DiaryEntry>

    @Query("SELECT * FROM diary WHERE patientId = :p ORDER BY date")
    suspend fun diaryAll(p: String): List<DiaryEntry>

    @Query("SELECT * FROM diary WHERE patientId = :p AND date = :date LIMIT 1")
    suspend fun diaryByDate(p: String, date: String): DiaryEntry?

    @Query("SELECT * FROM diary WHERE patientId = :p ORDER BY date DESC LIMIT 1")
    suspend fun lastDiary(p: String): DiaryEntry?

    @Upsert
    suspend fun upsertDiary(e: DiaryEntry): Long

    @Delete
    suspend fun deleteDiary(e: DiaryEntry)

    // Medicamentos
    @Query("SELECT * FROM medication WHERE patientId = :p ORDER BY active DESC, name")
    fun medsFlow(p: String): Flow<List<Medication>>

    @Query("SELECT * FROM medication WHERE patientId = :p")
    suspend fun medsAll(p: String): List<Medication>

    @Upsert
    suspend fun upsertMed(m: Medication): Long

    @Delete
    suspend fun deleteMed(m: Medication)

    @Query("SELECT * FROM intake WHERE patientId = :p AND date = :date")
    suspend fun intakesForDate(p: String, date: String): List<MedicationIntake>

    @Query("SELECT * FROM intake WHERE patientId = :p AND date BETWEEN :from AND :to ORDER BY date")
    suspend fun intakesRange(p: String, from: String, to: String): List<MedicationIntake>

    @Query("SELECT * FROM intake WHERE patientId = :p")
    suspend fun intakesAll(p: String): List<MedicationIntake>

    @Query("DELETE FROM intake WHERE patientId = :p AND date = :date")
    suspend fun deleteIntakesForDate(p: String, date: String)

    @Insert
    suspend fun insertIntakes(list: List<MedicationIntake>)

    @Transaction
    suspend fun replaceIntakes(p: String, date: String, list: List<MedicationIntake>) {
        deleteIntakesForDate(p, date)
        insertIntakes(list)
    }

    // Escalas
    @Query("SELECT * FROM scale_result WHERE patientId = :p ORDER BY date DESC, id DESC")
    fun scalesFlow(p: String): Flow<List<ScaleResult>>

    @Query("SELECT * FROM scale_result WHERE patientId = :p ORDER BY date")
    suspend fun scalesAll(p: String): List<ScaleResult>

    @Insert
    suspend fun insertScale(s: ScaleResult)

    @Delete
    suspend fun deleteScale(s: ScaleResult)

    // Profissional
    @Query("SELECT * FROM patient ORDER BY name")
    fun patientsFlow(): Flow<List<Patient>>

    @Query("SELECT * FROM patient WHERE code = :code")
    suspend fun patient(code: String): Patient?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPatient(p: Patient)

    @Query("DELETE FROM patient WHERE code = :code")
    suspend fun deletePatientRow(code: String)

    @Query("SELECT * FROM clinical_note WHERE patientId = :p ORDER BY date DESC, id DESC")
    fun notesFlow(p: String): Flow<List<ClinicalNote>>

    @Query("SELECT * FROM clinical_note WHERE patientId = :p ORDER BY date DESC, id DESC")
    suspend fun notesAll(p: String): List<ClinicalNote>

    @Insert
    suspend fun insertNote(n: ClinicalNote)

    @Delete
    suspend fun deleteNote(n: ClinicalNote)

    @Query("DELETE FROM diary WHERE patientId = :p") suspend fun clearDiary(p: String)
    @Query("DELETE FROM medication WHERE patientId = :p") suspend fun clearMeds(p: String)
    @Query("DELETE FROM intake WHERE patientId = :p") suspend fun clearIntakes(p: String)
    @Query("DELETE FROM scale_result WHERE patientId = :p") suspend fun clearScales(p: String)
    @Query("DELETE FROM clinical_note WHERE patientId = :p") suspend fun clearNotes(p: String)

    @Insert suspend fun insertDiaries(l: List<DiaryEntry>)
    @Insert suspend fun insertMed(m: Medication): Long
    @Insert suspend fun insertScales(l: List<ScaleResult>)

    /** Substitui todos os dados de um paciente pelo conteúdo importado (snapshot). */
    @Transaction
    suspend fun replacePatientData(
        patient: Patient,
        diary: List<DiaryEntry>,
        meds: List<Medication>,
        intakes: List<MedicationIntake>,
        scales: List<ScaleResult>,
    ) {
        val p = patient.code
        clearDiary(p); clearMeds(p); clearIntakes(p); clearScales(p)
        upsertPatient(patient)
        insertDiaries(diary.map { it.copy(id = 0, patientId = p) })
        val idMap = HashMap<Long, Long>()
        meds.forEach { m -> idMap[m.id] = insertMed(m.copy(id = 0, patientId = p)) }
        insertIntakes(intakes.map {
            it.copy(id = 0, patientId = p, medicationId = idMap[it.medicationId] ?: 0)
        })
        insertScales(scales.map { it.copy(id = 0, patientId = p) })
    }

    @Transaction
    suspend fun deletePatient(code: String) {
        clearDiary(code); clearMeds(code); clearIntakes(code); clearScales(code); clearNotes(code)
        deletePatientRow(code)
    }
}
