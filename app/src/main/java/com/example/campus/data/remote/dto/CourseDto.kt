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
    val weekRange: String,
    val color: Int = -1,
    val isRemote: Boolean = true,
    val baseCourseId: Long = 0,
    val onlyWeek: Int = 0
)

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
