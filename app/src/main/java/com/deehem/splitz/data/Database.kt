package com.deehem.splitz.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "split_shortcuts")
data class SplitShortcut(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val topPackage: String,
    val bottomPackage: String,
    @ColumnInfo(name = "folder", defaultValue = "NULL")
    val folder: String? = null,
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

    @Query("UPDATE split_shortcuts SET folder = :newFolder WHERE folder = :oldFolder")
    suspend fun renameFolder(oldFolder: String, newFolder: String)

    @Query("UPDATE split_shortcuts SET folder = NULL WHERE folder = :folder")
    suspend fun deleteFolder(folder: String)
}

@Database(entities = [SplitShortcut::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun splitShortcutDao(): SplitShortcutDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE split_shortcuts ADD COLUMN folder TEXT DEFAULT NULL")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "split_screen_db"
                )
                .addMigrations(MIGRATION_1_2)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
