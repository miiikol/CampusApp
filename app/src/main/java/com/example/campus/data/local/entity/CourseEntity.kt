package com.example.campus.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 课程实体，对应本地表 courses。
 *
 * 字段约定：
 * - [dayOfWeek] 使用 1~7 表示周一到周日
 * - [startSection]/[endSection] 表示课程起止节次（包含 end）
 * - [weekRange] 表示周次范围，例如 "1-16"
 */
@Entity(tableName = "courses")
data class CourseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val room: String,
    val teacher: String,
    val dayOfWeek: Int, // 1 = Monday, 7 = Sunday
    val startSection: Int,
    val endSection: Int,
    val weekRange: String, // e.g. "1-16"
    @ColumnInfo(defaultValue = "-1")
    val color: Int = -1,
    @ColumnInfo(defaultValue = "1")
    val isRemote: Boolean = true,
    @ColumnInfo(defaultValue = "0")
    val baseCourseId: Long = 0,
    @ColumnInfo(defaultValue = "0")
    val onlyWeek: Int = 0
)
