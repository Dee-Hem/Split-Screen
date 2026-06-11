package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "split_shortcuts")
data class SplitShortcut(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val topPackage: String,
    val bottomPackage: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface SplitShortcutDao {
    @Query("SELECT * FROM split_shortcuts ORDER BY createdAt DESC")
    fun getAllShortcuts(): Flow<List<SplitShortcut>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShortcut(shortcut: SplitShortcut): Long

    @Delete
    suspend fun deleteShortcut(shortcut: SplitShortcut)

    @Query("SELECT * FROM split_shortcuts WHERE id = :id")
    suspend fun getShortcutById(id: Long): SplitShortcut?
}

@Database(entities = [SplitShortcut::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun splitShortcutDao(): SplitShortcutDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "split_screen_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
