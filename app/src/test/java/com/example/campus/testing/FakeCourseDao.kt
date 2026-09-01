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

    override suspend fun clearAllCourses() {
        state.value = emptyList()
    }
}
