// AppDatabase.kt - Room 数据库主类
//
// 设计要点：
// 1. 单例 + Application lazy 持有（避免 Activity 之间重复创建）
// 2. 触发器在 onCreate 里 execSQL 注册：兜底校验 wage_cent > 0 和 work_date ≤ 今天
//    防止后续代码绕过 Repository 直接写库时插入非法数据
// 3. 不用 fallbackToDestructiveMigration()：宁可崩也不静默清数据
// 4. exportSchema = true：CI 跑完下载 schema 1.json 入库，便于后续迁移参考

package com.example.wagemanager.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Worker::class,
        WageRecord::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(DateTimeConverters::class)
abstract class AppDatabase : RoomDatabase() {

    // AppDatabase 是 public（被 Application / Repository 持有）
    // Dao 自身和 dao() 方法仍是 internal（同 module 内 Repository 才能调）
    // 这样既能让外部类型正常传递，又隐藏 Dao 实现细节
    internal abstract fun workerDao(): WorkerDao
    internal abstract fun wageRecordDao(): WageRecordDao

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
                .build()
        }

        /**
         * 数据库级兜底校验：防止任何代码绕过 Repository 直接写库时插入非法数据。
         * Repository 仍然要先校验以便 UI 拿到可理解的错误，而不是 SQLite 异常。
         */
        private val ConstraintCallback = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                // INSERT 触发器：拒绝 wage_cent <= 0 或未来日期
                db.execSQL(
                    """
                    CREATE TRIGGER wage_records_validate_insert
                    BEFORE INSERT ON wage_records
                    FOR EACH ROW
                    WHEN NEW.wage_cent <= 0
                         OR NEW.work_date > strftime('%Y-%m-%d','now','localtime')
                    BEGIN
                        SELECT RAISE(ABORT, 'invalid wage record: wage_cent must > 0 and work_date must <= today');
                    END
                    """.trimIndent()
                )
                // UPDATE 触发器：同上
                db.execSQL(
                    """
                    CREATE TRIGGER wage_records_validate_update
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
