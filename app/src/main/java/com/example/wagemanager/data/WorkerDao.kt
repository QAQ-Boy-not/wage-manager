// WorkerDao.kt - 工人表 DAO
//
// 设计要点：
// 1. internal 修饰符：DAO 细节不暴露给 UI 层，UI 只走 Repository
// 2. 全部 suspend / Flow：Room 主线程禁止阻塞（KSP 2.6+ 默认就会校验）
// 3. 同名查重用精确 "=" 匹配，不走 LIKE 模糊查询（V1.1 强制"同名复用"口径）

package com.example.wagemanager.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
internal interface WorkerDao {

    /**
     * 插入工人记录。冲突策略 ABORT：主键重复时抛异常由上层处理。
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(worker: Worker)

    /**
     * 按主键查工人。
     */
    @Query("SELECT * FROM workers WHERE id = :workerId LIMIT 1")
    suspend fun findById(workerId: String): Worker?

    /**
     * 按姓名精确查同名工人（= 匹配，不模糊）。
     * 用于同名复用决策：0 → 允许新建；≥1 → 弹复用/新建选择。
     * 排序按首次登记日期和 ID，方便稳定展示。
     */
    @Query(
        """
        SELECT * FROM workers
        WHERE name = :name
        ORDER BY first_work_date ASC, id ASC
        """
    )
    suspend fun findByExactName(name: String): List<Worker>

    /**
     * 一次性查所有工人（按首次登记日期升序）。
     * V1.3 工人选择器用，预期工人数量 < 100。
     */
    @Query(
        """
        SELECT * FROM workers
        ORDER BY first_work_date ASC, id ASC
        """
    )
    suspend fun findAll(): List<Worker>
}
