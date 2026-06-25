package com.example.campus.data.repository

import com.example.campus.core.common.Resource
import com.example.campus.data.local.dao.CourseDao
import com.example.campus.data.local.dao.UserDao
import com.example.campus.data.local.entity.CourseEntity
import com.example.campus.data.remote.ApiService
import com.example.campus.data.remote.dto.CustomizeCourseRequest
import com.example.campus.data.remote.dto.toEntity
import kotlinx.coroutines.flow.Flow
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

/**
 * 课程数据仓库，负责课程的本地读取与远端刷新。
 *
 * 策略：
 * - UI 层通过 [courses] 订阅本地 Room 中的课程数据
 * - 调用 [refreshCourses] 从远端拉取后覆盖本地缓存
 */
class CourseRepository @Inject constructor(
    private val api: ApiService,
    private val dao: CourseDao,
    private val userDao: UserDao
) {

    private fun toHttpErrorMessage(e: HttpException): String {
        val url = e.response()?.raw()?.request?.url?.toString()
        val base = when (e.code()) {
            400 -> "请求参数错误(400)"
            401 -> "未登录或登录已过期(401)"
            403 -> "没有权限访问(403)"
            404 -> "接口不存在(404)，请确认后端路由与 BASE_URL"
            in 500..599 -> "服务器错误(${e.code()})"
            else -> "请求失败(${e.code()})"
        }
        return if (url.isNullOrBlank()) base else "$base：$url"
    }

    val courses: Flow<List<CourseEntity>> = dao.getAllCourses()

    suspend fun clearLocalCourses() = dao.clearAllCourses()

    /**
     * 从远端获取课程并刷新本地缓存。
     *
     * 成功：
     * - 清空旧数据，插入最新课程列表
     * 失败：
     * - 返回 [Resource.Error]，不影响现有本地缓存
     */
    suspend fun refreshCourses(): Resource<Unit> {
        return try {
            val currentUser = userDao.getCurrentUser()
            if (currentUser == null || (currentUser.id.isBlank() && currentUser.studentId.isBlank())) {
                return Resource.Error("未登录，无法加载课表")
            }
            val remoteCourses = api.getCourses(
                userId = currentUser.id,
                studentId = currentUser.studentId
            )
            dao.clearAllCourses()
            dao.insertCourses(remoteCourses.map { it.toEntity() })
            Resource.Success(Unit)
        } catch (e: HttpException) {
            Resource.Error(toHttpErrorMessage(e))
        } catch (e: IOException) {
            Resource.Error("无法连接服务器，请确认 WampServer 已启动，且模拟器可访问 10.0.2.2")
        }
    }

    suspend fun customizeCourse(
        sourceCourseId: Long?,
        scope: String,
        week: Int?,
        course: CourseEntity
    ): Resource<Unit> {
        return try {
            val currentUser = userDao.getCurrentUser()
            val userId = currentUser?.id?.takeIf { it.isNotBlank() }
                ?: return Resource.Error("未登录，无法修改课表")
            api.customizeCourse(
                CustomizeCourseRequest(
                    userId = userId,
                    sourceCourseId = sourceCourseId,
                    scope = scope,
                    week = week,
                    name = course.name,
                    room = course.room,
                    teacher = course.teacher,
                    dayOfWeek = course.dayOfWeek,
                    startSection = course.startSection,
                    endSection = course.endSection,
                    weekRange = course.weekRange,
                    color = course.color
                )
            )
            refreshCourses()
        } catch (e: HttpException) {
            Resource.Error(toHttpErrorMessage(e))
        } catch (e: IOException) {
            Resource.Error("无法连接服务器，请确认 WampServer 已启动，且模拟器可访问 10.0.2.2")
        } catch (e: Exception) {
            Resource.Error("课表保存失败")
        }
    }
}
