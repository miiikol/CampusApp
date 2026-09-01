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
    val dayOfWeek: Int, // 1 = 周一，7 = 周日
    val startSection: Int,
    val endSection: Int,
    val weekRange: String, // 周次范围，例如 "1-16"
    @ColumnInfo(defaultValue = "-1")
    val color: Int = -1, // 课程颜色标识，-1 表示使用默认色
    @ColumnInfo(defaultValue = "1")
    val isRemote: Boolean = true, // 是否来自远端同步；false 表示本地自定义
    @ColumnInfo(defaultValue = "0")
    val baseCourseId: Long = 0, // 关联的基础课程 id；0 表示该记录自身即基础课程
    @ColumnInfo(defaultValue = "0")
    val onlyWeek: Int = 0 // 仅在该周生效；0 表示每周期（不限定周次）
)
