// WorksiteDao.kt - 工区表 DAO（V1.3 新增）
//
// 设计要点：
// 1. internal 修饰：DAO 细节不暴露给 UI 层，UI 只走 Repository
// 2. 全部 suspend / Flow：Room 主线程禁止阻塞

package com.example.wagemanager.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
internal interface WorksiteDao {

    /**
     * 插入工区。冲突策略 ABORT：主键重复时抛异常由上层处理。
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(worksite: Worksite)

    /**
     * 按 id 查工区。
     */
    @Query("SELECT * FROM worksites WHERE id = :worksiteId LIMIT 1")
    suspend fun findById(worksiteId: String): Worksite?

    /**
     * 观察所有工区（管理页 + 批量添加下拉用）。
     * 按创建时间倒序，最近建的排前面。
     */
    @Query("SELECT * FROM worksites ORDER BY created_at DESC, name ASC")
    fun observeAll(): Flow<List<Worksite>>

    /**
     * 按名字精确查（同名查重用，V1.3 决策：同名强制改名）。
     */
    @Query(
        """
        SELECT * FROM worksites
        WHERE name = :name
        ORDER BY created_at ASC, id ASC
        """
    )
    suspend fun findByExactName(name: String): List<Worksite>
}