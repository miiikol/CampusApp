package com.example.campus.testing

import com.example.campus.data.local.dao.UserDao
import com.example.campus.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * UserDao 的测试替身（Fake），基于内存 [MutableStateFlow] 模拟单用户存储。
 */
class FakeUserDao : UserDao {
    private val state = MutableStateFlow<UserEntity?>(null)

    fun setUser(user: UserEntity?) {
        state.value = user
    }

    fun snapshot(): UserEntity? = state.value

    override fun getUser(): Flow<UserEntity?> = state

    override suspend fun getCurrentUser(): UserEntity? = state.value

    override suspend fun insertUser(user: UserEntity) {
        state.value = user
    }

    override suspend fun updateUsername(id: String, username: String) {
        state.value = state.value?.copy(username = username)
    }

    override suspend fun updateAvatar(id: String, avatarUrl: String?) {
        state.value = state.value?.copy(avatarUrl = avatarUrl)
    }

    override suspend fun clearUser() {
        state.value = null
    }
}
