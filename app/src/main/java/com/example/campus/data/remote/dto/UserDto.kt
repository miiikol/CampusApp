package com.example.campus.data.remote.dto

import com.example.campus.data.local.entity.UserEntity
import com.example.campus.core.common.normalizeRole

/**
 * 用户网络 DTO。
 * [role] 经 [normalizeRole] 转换为标准角色常量后通过 [toEntity] 存入本地。
 */
data class UserDto(
    val id: String,
    val username: String,
    val studentId: String,
    val avatarUrl: String?,
    val token: String?,
    val role: String?
)

/**
 * 更新用户资料请求体。
 */
data class UpdateProfileRequest(
    val username: String? = null,
    val avatarUrl: String? = null
)

/**
 * 图片上传响应，返回图片访问 URL。
 */
data class ImageUploadResponse(
    val url: String
)

/** 将网络 DTO 转换为 Room 实体，同时对角色进行标准化处理 */
fun UserDto.toEntity(): UserEntity {
    return UserEntity(
        id = id,
        username = username,
        studentId = studentId,
        avatarUrl = avatarUrl,
        token = token,
        role = normalizeRole(role)
    )
}
