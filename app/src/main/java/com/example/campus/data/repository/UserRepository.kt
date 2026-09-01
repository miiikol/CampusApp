package com.example.campus.data.repository

import android.content.Context
import com.example.campus.core.common.Resource
import com.example.campus.data.local.dao.CourseDao
import com.example.campus.data.local.dao.UserDao
import com.example.campus.data.local.entity.UserEntity
import com.example.campus.data.remote.ApiService
import com.example.campus.data.remote.dto.LoginRequest
import com.example.campus.data.remote.dto.ResetPasswordRequest
import com.example.campus.data.remote.dto.UpdateProfileRequest
import com.example.campus.data.remote.dto.toEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

/**
 * 用户数据仓库，聚合远端登录接口与本地用户数据存储。
 *
 * 设计要点：
 * - 对外暴露 Flow，封装为 [Resource] 三态，便于 UI 层以状态机呈现
 * - 登录成功后以本地单源（Room）存储当前用户信息
 * - 将网络异常转换为可读错误消息
 */
class UserRepository @Inject constructor(
    private val api: ApiService,
    private val dao: UserDao,
    private val courseDao: CourseDao,
    @ApplicationContext private val context: Context
) {

    private fun toHttpErrorMessage(e: HttpException): String {
        val url = e.response()?.raw()?.request?.url?.toString()
        // 针对登录/重置密码接口定制 401/404 的错误文案
        val isLoginApi = url?.contains("/auth/login", ignoreCase = true) == true
        val isResetPasswordApi = url?.contains("/auth/reset-password", ignoreCase = true) == true
        val base = when (e.code()) {
            400 -> "请求参数错误(400)"
            401 -> if (isResetPasswordApi) "身份信息不匹配(401)" else "学号或密码错误(401)"
            403 -> "没有权限访问(403)"
            404 -> when {
                isLoginApi -> "用户不存在(404)"
                isResetPasswordApi -> "用户不存在(404)"
                else -> "接口不存在(404)"
            }
            in 500..599 -> "服务器错误(${e.code()})"
            else -> "请求失败(${e.code()})"
        }
        return if (url.isNullOrBlank()) base else "$base：$url"
    }

    /**
     * 执行用户登录，并以 Flow 形式推送 Loading/Success/Error。
     *
     * 成功：
     * - 清理旧用户数据，插入新用户
     * 失败：
     * - 将 HttpException/IOException 转换为用户可读的错误文案
     */
    fun login(studentId: String, password: String): Flow<Resource<UserEntity>> = flow {
        emit(Resource.Loading())
        try {
            val response = withTimeout(15_000) {
                api.login(LoginRequest(studentId, password))
            }
            courseDao.clearAllCourses()
            dao.clearUser()
            dao.insertUser(response.toEntity())
            emit(Resource.Success(response.toEntity()))
        } catch (e: HttpException) {
            emit(Resource.Error(toHttpErrorMessage(e)))
        } catch (e: TimeoutCancellationException) {
            emit(Resource.Error("登录超时，请检查网络或稍后再试"))
        } catch (e: IOException) {
            emit(Resource.Error("无法连接服务器，请确认 WampServer 已启动，且模拟器可访问 10.0.2.2"))
        } catch (e: Exception) {
            emit(Resource.Error("登录失败，请稍后再试"))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 获取当前用户（若未登录则返回 null）。
     */
    fun getUser(): Flow<UserEntity?> = dao.getUser()

    /** 退出登录，清空当前用户与课表缓存。 */
    suspend fun logout() {
        courseDao.clearAllCourses()
        dao.clearUser()
    }

    /**
     * 更新用户昵称，成功后同步本地缓存中的用户名与头像。
     */
    suspend fun updateNickname(userId: String, nickname: String): Resource<Unit> {
        val name = nickname.trim()
        // 本地先做基础校验，避免无意义的网络请求
        if (name.isBlank()) return Resource.Error("昵称不能为空")
        if (name.length > 20) return Resource.Error("昵称过长")
        return try {
            val updated = withTimeout(15_000) {
                api.updateUserProfile(
                    userId = userId,
                    request = UpdateProfileRequest(username = name)
                )
            }
            dao.updateUsername(userId, updated.username)
            dao.updateAvatar(userId, updated.avatarUrl)
            Resource.Success(Unit)
        } catch (e: HttpException) {
            Resource.Error(toHttpErrorMessage(e))
        } catch (e: TimeoutCancellationException) {
            Resource.Error("请求超时，请检查网络或稍后再试")
        } catch (e: IOException) {
            Resource.Error("无法连接服务器，请确认 WampServer 已启动，且模拟器可访问 10.0.2.2")
        } catch (e: Exception) {
            Resource.Error("更新昵称失败")
        }
    }

    /**
     * 更新用户头像。
     *
     * 本地图片先上传换取远端 URL，成功后同步本地缓存。
     */
    suspend fun updateAvatar(userId: String, avatarUrl: String?): Resource<Unit> {
        return try {
            val avatarRaw = avatarUrl?.trim().orEmpty()
            // 本地图片（content/file 协议）需先上传，远端 URL 直接复用
            val finalAvatarUrl = if (avatarRaw.startsWith("content://", ignoreCase = true) ||
                avatarRaw.startsWith("file://", ignoreCase = true)
            ) {
                uploadImage(avatarRaw)
            } else {
                avatarRaw.takeIf { it.isNotBlank() }
            }

            val updated = withTimeout(15_000) {
                api.updateUserProfile(
                    userId = userId,
                    request = UpdateProfileRequest(avatarUrl = finalAvatarUrl)
                )
            }
            dao.updateUsername(userId, updated.username)
            dao.updateAvatar(userId, updated.avatarUrl)
            Resource.Success(Unit)
        } catch (e: HttpException) {
            Resource.Error(toHttpErrorMessage(e))
        } catch (e: TimeoutCancellationException) {
            Resource.Error("请求超时，请检查网络或稍后再试")
        } catch (e: IOException) {
            Resource.Error("无法连接服务器，请确认 WampServer 已启动，且模拟器可访问 10.0.2.2")
        } catch (e: Exception) {
            Resource.Error("更新头像失败")
        }
    }

    private suspend fun uploadImage(uriString: String): String {
        val uri = android.net.Uri.parse(uriString)
        val mimeType = context.contentResolver.getType(uri) ?: "image/*"
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IOException("无法读取图片")

        if (bytes.isEmpty()) throw IOException("空文件")
        if (bytes.size > 5 * 1024 * 1024) throw IOException("图片不能超过 5MB")

        val requestBody = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
        val filename = "avatar.jpg"
        val part = MultipartBody.Part.createFormData("file", filename, requestBody)
        return api.uploadImage(part).url
    }

    /**
     * 通过学号、姓名、身份证号校验身份后重置密码。
     *
     * 以 Flow 形式推送 Loading/Success/Error，成功返回后端提示消息。
     */
    fun resetPassword(
        studentId: String,
        fullName: String,
        idCardNo: String,
        newPassword: String
    ): Flow<Resource<String>> = flow {
        emit(Resource.Loading())
        try {
            val response = withTimeout(15_000) {
                api.resetPassword(
                    ResetPasswordRequest(
                        studentId = studentId,
                        fullName = fullName,
                        idCardNo = idCardNo,
                        newPassword = newPassword
                    )
                )
            }
            emit(Resource.Success(response.message))
        } catch (e: HttpException) {
            emit(Resource.Error(toHttpErrorMessage(e)))
        } catch (e: TimeoutCancellationException) {
            emit(Resource.Error("请求超时，请检查网络或稍后再试"))
        } catch (e: IOException) {
            emit(Resource.Error("无法连接服务器，请确认 WampServer 已启动，且模拟器可访问 10.0.2.2"))
        } catch (e: Exception) {
            emit(Resource.Error("重置失败，请稍后再试"))
        }
    }.flowOn(Dispatchers.IO)
}
