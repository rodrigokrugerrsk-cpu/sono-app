package br.sono.diario.data

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.sqlite.SQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

@Database(
    entities = [
        DiaryEntry::class, Medication::class, MedicationIntake::class,
        ScaleResult::class, Patient::class, ClinicalNote::class,
    ],
    version = 1,
    exportSchema = true,
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): SleepDao
}

@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

/** Nome do arquivo do banco; o mesmo da versão 0.1 do Android, para manter os dados. */
const val DB_NAME = "sono.db"

/** Android usa o SQLite do sistema; o iPhone usa o SQLite embutido. */
fun buildDatabase(builder: RoomDatabase.Builder<AppDatabase>, driver: SQLiteDriver): AppDatabase =
    builder.setDriver(driver).setQueryCoroutineContext(Dispatchers.IO).build()
