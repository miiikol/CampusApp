package com.example.campus.ui.course

import androidx.lifecycle.viewModelScope
import com.example.campus.core.base.BaseViewModel
import com.example.campus.core.common.Resource
import com.example.campus.data.local.entity.CourseEntity
import com.example.campus.data.repository.CourseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 课程表 ViewModel，管理课程数据的加载与周视图编辑。
 *
 * 支持四种编辑操作：
 * - 编辑单周课程（仅影响指定周）
 * - 编辑全部周课程（影响整个学期）
 * - 新增课程到全部周
 * - 新增课程到指定周
 *
 * 自定义课程（isRemote=false）会覆盖对应位置的基础课程。
 */
@HiltViewModel
class CourseViewModel @Inject constructor(
    private val repository: CourseRepository
) : BaseViewModel() {

    private val _courses = MutableStateFlow<List<CourseEntity>>(emptyList())
    val courses: StateFlow<List<CourseEntity>> = _courses.asStateFlow()

    private val _refreshState = MutableStateFlow<Resource<Unit>?>(null)
    val refreshState: StateFlow<Resource<Unit>?> = _refreshState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.courses.collectLatest {
                _courses.value = it
            }
        }
        refresh()
    }

    fun refresh() {
        launch {
            _refreshState.value = Resource.Loading()
            _refreshState.value = repository.refreshCourses()
        }
    }

    fun updateAllWeeks(original: CourseEntity, updated: CourseEntity) {
        launch {
            val sourceCourseId = if (original.baseCourseId != 0L) original.baseCourseId else original.id
            val toSave = original.copy(
                name = updated.name,
                room = updated.room,
                teacher = updated.teacher,
                dayOfWeek = updated.dayOfWeek,
                startSection = updated.startSection,
                endSection = updated.endSection,
                weekRange = updated.weekRange,
                color = updated.color,
                isRemote = false
            )
            repository.customizeCourse(
                sourceCourseId = sourceCourseId,
                scope = "ALL_WEEKS",
                week = null,
                course = toSave
            )
        }
    }

    fun updateThisWeek(original: CourseEntity, week: Int, updated: CourseEntity) {
        launch {
            if (week <= 0) return@launch
            val sourceCourseId = if (original.baseCourseId != 0L) original.baseCourseId else original.id
            val toSave = original.copy(
                name = updated.name,
                room = updated.room,
                teacher = updated.teacher,
                dayOfWeek = updated.dayOfWeek,
                startSection = updated.startSection,
                endSection = updated.endSection,
                weekRange = week.toString(),
                color = updated.color,
                isRemote = false,
                baseCourseId = sourceCourseId,
                onlyWeek = week
            )
            repository.customizeCourse(
                sourceCourseId = sourceCourseId,
                scope = "THIS_WEEK",
                week = week,
                course = toSave
            )
        }
    }

    fun addAllWeeks(course: CourseEntity) {
        launch {
            val toSave = CourseEntity(
                id = 0,
                name = course.name,
                room = course.room,
                teacher = course.teacher,
                dayOfWeek = course.dayOfWeek,
                startSection = course.startSection,
                endSection = course.endSection,
                weekRange = course.weekRange,
                color = course.color,
                isRemote = false,
                baseCourseId = 0,
                onlyWeek = 0
            )
            repository.customizeCourse(
                sourceCourseId = null,
                scope = "ALL_WEEKS",
                week = null,
                course = toSave
            )
        }
    }

    fun addThisWeek(week: Int, course: CourseEntity) {
        launch {
            if (week <= 0) return@launch
            val toSave = CourseEntity(
                id = 0,
                name = course.name,
                room = course.room,
                teacher = course.teacher,
                dayOfWeek = course.dayOfWeek,
                startSection = course.startSection,
                endSection = course.endSection,
                weekRange = week.toString(),
                color = course.color,
                isRemote = false,
                baseCourseId = 0,
                onlyWeek = week
            )
            repository.customizeCourse(
                sourceCourseId = null,
                scope = "THIS_WEEK",
                week = week,
                course = toSave
            )
        }
    }
}
