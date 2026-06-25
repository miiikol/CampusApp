package com.example.campus.core.common

/**
 * 用户角色常量定义，用于区分学生与管理员身份。
 * 后端返回的角色字段可能为多种形式，统一经 [normalizeRole] 转换为标准值。
 */
object UserRole {
    const val STUDENT = "student"
    const val ADMIN = "admin"
}

/**
 * 将后端返回的角色字符串规范化为 [UserRole] 常量。
 * 支持中文"管理员"、英文"admin/administrator"等变体，其余均视为学生。
 */
fun normalizeRole(role: String?): String {
    return when (role?.trim()?.lowercase()) {
        "admin", "administrator", "管理员" -> UserRole.ADMIN
        else -> UserRole.STUDENT
    }
}
