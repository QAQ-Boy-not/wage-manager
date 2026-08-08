// DateTimeConverters.kt - Room 类型转换器
//
// 存储格式（统一 TEXT，避免 SQLite 没有原生时间类型）：
//   LocalDate       → "yyyy-MM-dd"
//   LocalDateTime   → "yyyy-MM-dd HH:mm:ss"（写入前 withNano(0) 截断到秒）
//
// 不要直接用 LocalDateTime.toString()：默认带 'T' 分隔符且可能带纳秒。

package com.example.wagemanager.data

import androidx.room.TypeConverter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class DateTimeConverters {

    @TypeConverter
    fun localDateToString(value: LocalDate?): String? {
        return value?.format(DATE_FORMAT)
    }

    @TypeConverter
    fun stringToLocalDate(value: String?): LocalDate? {
        if (value.isNullOrBlank()) return null
        return LocalDate.parse(value, DATE_FORMAT)
    }

    @TypeConverter
    fun localDateTimeToString(value: LocalDateTime?): String? {
        return value?.format(DATETIME_FORMAT)
    }

    @TypeConverter
    fun stringToLocalDateTime(value: String?): LocalDateTime? {
        if (value.isNullOrBlank()) return null
        return LocalDateTime.parse(value, DATETIME_FORMAT)
    }

    companion object {
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        private val DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}
