// Worker.kt - 工人表 Entity
//
// 字段对应需求文档第六章 workers 表：
//   id TEXT PK（白名单 worker_id 或 manual_xxx）
//   name TEXT NN（工人姓名）
//   qr_raw TEXT（原始二维码内容，可空）
//   qrcode_path TEXT（收款码图片本地路径，可空；manual_ 工人为空）
//   is_manual INT=0（0=扫码，1=手动）
//   first_work_date TEXT（首次登记日期 yyyy-MM-dd）

package com.example.wagemanager.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(
    tableName = "workers",
    indices = [
        Index(
            value = ["name"],
            name = "index_workers_name"
        )
    ]
)
data class Worker(
    @PrimaryKey
    val id: String,

    val name: String,

    @ColumnInfo(name = "qr_raw")
    val qrRaw: String? = null,

    @ColumnInfo(name = "qrcode_path")
    val qrcodePath: String? = null,

    @ColumnInfo(name = "is_manual", defaultValue = "0")
    val isManual: Boolean = false,

    @ColumnInfo(name = "first_work_date")
    val firstWorkDate: LocalDate? = null
)
