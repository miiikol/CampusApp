package com.example.campus.data.remote.dto

import com.example.campus.data.local.entity.CourseEntity

/**
 * 课程网络 DTO，用于从后端接口获取课程数据。
 * 通过 [toEntity] 转换为 Room 实体后存入本地数据库。
 */
data class CourseDto(
    val id: Long,
    val name: String,
    val room: String,
    val teacher: String,
    val dayOfWeek: Int,
    val startSection: Int,
    val endSection: Int,
    val weekRange: String, // 上课周次范围，如 "1-16"
    val color: Int = -1, // 课程表显示颜色
    val isRemote: Boolean = true, // 是否为远程课程
    val baseCourseId: Long = 0, // 被自定义覆盖的原始课程 ID，0 表示新增课程
    val onlyWeek: Int = 0 // 仅在指定周生效（配合 scope="week" 使用），0 表示不限
)

/** 将网络 DTO 转换为 Room 实体 */
fun CourseDto.toEntity(): CourseEntity {
    return CourseEntity(
        id = id,
        name = name,
        room = room,
        teacher = teacher,
        dayOfWeek = dayOfWeek,
        startSection = startSection,
        endSection = endSection,
        weekRange = weekRange,
        color = color,
        isRemote = isRemote,
        baseCourseId = baseCourseId,
        onlyWeek = onlyWeek
    )
}

/**
 * 自定义课程请求体。
 * [scope] 可选 "all"（覆盖整学期）或 "week"（仅覆盖指定周）。
 */
data class CustomizeCourseRequest(
    val userId: String,
    val sourceCourseId: Long? = null,
    val scope: String,
    val week: Int? = null,
    val name: String,
    val room: String,
    val teacher: String,
    val dayOfWeek: Int,
    val startSection: Int,
    val endSection: Int,
    val weekRange: String,
    val color: Int = -1
)
