// Worksite.kt - 工区表 Entity（V1.3 新增）
//
// 表达"去哪干活"：小区 / 单元 / 楼层 / 房间
// 字段最小化（V1.3 决策）：name + address
// 用户不暴露 UUID（V1.3 强制结论）
//
// Schema 版本：1（V1.3 引入，首次创建）

package com.example.wagemanager.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(
    tableName = "worksites",
    indices = [
        Index(
            value = ["name"],
            name = "index_worksites_name"
        )
    ]
)
data class Worksite(
    @PrimaryKey
    val id: String,

    /** 工区简称（"望京 SOHO T3 12 层"） */
    val name: String,

    /** 详细地址 */
    val address: String,

    @ColumnInfo(name = "created_at")
    val createdAt: LocalDateTime
)