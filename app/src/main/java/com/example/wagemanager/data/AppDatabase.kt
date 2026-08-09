// AppDatabase.kt - Room 数据库主类（V1.3 schema version = 2）
//
// V1.3 变更（相对 V1.2 schema 1）：
// - entities 加 [Worker::class, WageRecord::class, Worksite::class]
// - version 1 → 2
// - Migration 1→2：CREATE TABLE worksites + ALTER TABLE wage_records ADD COLUMN worksite_id / notes
//
// 设计要点：
// 1. 单例 + Application lazy 持有
// 2. 触发器在 onCreate 里 execSQL 注册：兜底校验 wage_cent > 0 和 work_date ≤ 今天
// 3. 不用 fallbackToDestructiveMigration()
// 4. exportSchema = true：CI 跑完下载 schema 1.json / 2.json 入库

package com.example.wagemanager.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Worker::class,
        WageRecord::class,
        Worksite::class   // V1.3 新增
    ],
    version = 2,            // V1.2 是 1，V1.3 升到 2
    exportSchema = true
)
@TypeConverters(DateTimeConverters::class)
abstract class AppDatabase : RoomDatabase() {

    // Dao 是 internal（同 module 内 Repository 才能调），所以 abstract fun 也必须 internal
    internal abstract fun workerDao(): WorkerDao
    internal abstract fun wageRecordDao(): WageRecordDao
    internal abstract fun worksiteDao(): WorksiteDao

    companion object {
        private const val DB_NAME = "wage_manager.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: build(context).also { instance = it }
            }
        }

        private fun build(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DB_NAME
            )
                .addCallback(ConstraintCallback)
                .addMigrations(MIGRATION_1_2)
                .build()
        }

        /**
         * Schema 迁移 1 → 2（V1.3）
         *
         * 步骤：
         * 1. 新增 worksites 表
         * 2. wage_records 加 worksite_id 列（可空）
         * 3. wage_records 加 notes 列（可空）
         *
         * 现有数据完全保留（ALTER TABLE ... ADD COLUMN 不破坏数据）。
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 1. 新增 worksites 表
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS worksites (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        address TEXT NOT NULL,
                        created_at TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_worksites_name ON worksites(name)"
                )

                // 2. wage_records 加 worksite_id 列（可空）
                database.execSQL(
                    "ALTER TABLE wage_records ADD COLUMN worksite_id TEXT"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_wage_records_worksite_id ON wage_records(worksite_id)"
                )

                // 3. wage_records 加 notes 列（可空）
                database.execSQL(
                    "ALTER TABLE wage_records ADD COLUMN notes TEXT"
                )
            }
        }

        /**
         * 数据库级兜底校验：防止任何代码绕过 Repository 直接写库时插入非法数据。
         */
        private val ConstraintCallback = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                installTriggers(db)
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                // 兼容老版本（schema 1）升级到 schema 2 后首次打开，
                // 也需要确保触发器存在（防御性）。
                installTriggers(db)
            }

            private fun installTriggers(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS wage_records_validate_insert
                    BEFORE INSERT ON wage_records
                    FOR EACH ROW
                    WHEN NEW.wage_cent <= 0
                         OR NEW.work_date > strftime('%Y-%m-%d','now','localtime')
                    BEGIN
                        SELECT RAISE(ABORT, 'invalid wage record: wage_cent must > 0 and work_date must <= today');
                    END
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS wage_records_validate_update
                    BEFORE UPDATE ON wage_records
                    FOR EACH ROW
                    WHEN NEW.wage_cent <= 0
                         OR NEW.work_date > strftime('%Y-%m-%d','now','localtime')
                    BEGIN
                        SELECT RAISE(ABORT, 'invalid wage record: wage_cent must > 0 and work_date must <= today');
                    END
                    """.trimIndent()
                )
            }
        }
    }
}