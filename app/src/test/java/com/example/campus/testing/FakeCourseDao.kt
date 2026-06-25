package com.example.campus.testing

import com.example.campus.data.local.dao.CourseDao
import com.example.campus.data.local.entity.CourseEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * CourseDao 的测试替身（Fake），基于内存 [MutableStateFlow] 模拟课程数据存储。
 */
class FakeCourseDao : CourseDao {
    private val state = MutableStateFlow<List<CourseEntity>>(emptyList())

    fun snapshot(): List<CourseEntity> = state.value

    override fun getAllCourses(): Flow<List<CourseEntity>> = state

    override fun getCoursesByDay(day: Int): Flow<List<CourseEntity>> {
        return MutableStateFlow(state.value.filter { it.dayOfWeek == day })
    }

    override suspend fun insertCourses(courses: List<CourseEntity>) {
        state.value = courses
    }

    override suspend fun upsertCourse(course: CourseEntity): Long {
        val current = state.value.toMutableList()
        val existing = current.indexOfFirst { it.id == course.id }
        if (existing >= 0) {
            current[existing] = course
        } else {
            current.add(course.copy(id = (current.maxOfOrNull { it.id } ?: 0) + 1))
        }
        state.value = current
        return course.id
    }

    override suspend fun getCourseById(id: Long): CourseEntity? {
        return state.value.firstOrNull { it.id == id }
    }

    override suspend fun getOverrideCourse(baseCourseId: Long, week: Int): CourseEntity? {
        return state.value.firstOrNull { it.baseCourseId == baseCourseId && it.onlyWeek == week }
    }

    override suspend fun getCustomizedBaseCourseIds(): List<Long> {
        return state.value.filter { !it.isRemote && it.baseCourseId == 0L && it.onlyWeek == 0 }
            .map { it.id }
    }

    override suspend fun deleteRemoteBaseCourses() {
        state.value = state.value.filter { !(it.isRemote && it.baseCourseId == 0L && it.onlyWeek == 0) }
    }

    override suspend fun deleteCourseById(id: Long) {
        state.value = state.value.filter { it.id != id }
    }

    override suspend fun clearAllCourses() {
        state.value = emptyList()
    }
}
