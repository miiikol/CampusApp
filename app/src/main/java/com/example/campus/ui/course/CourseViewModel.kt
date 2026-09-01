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

    /**
     * 拉取并刷新课程数据，加载与结果状态通过 [refreshState] 暴露。
     */
    fun refresh() {
        launch {
            _refreshState.value = Resource.Loading()
            _refreshState.value = repository.refreshCourses()
        }
    }

    /**
     * 将编辑结果应用到整个学期的所有周（scope = ALL_WEEKS）。
     *
     * @param original 被编辑的原始课程
     * @param updated  编辑后的课程字段
     */
    fun updateAllWeeks(original: CourseEntity, updated: CourseEntity) {
        launch {
            // sourceCourseId 指向被覆盖的源课程：自定义课程取其 baseCourseId，否则回退到自身 id
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

    /**
     * 将编辑结果仅应用到指定周（scope = THIS_WEEK），不影响其它周。
     *
     * @param week 目标周次，非法周次（<=0）会被忽略
     */
    fun updateThisWeek(original: CourseEntity, week: Int, updated: CourseEntity) {
        launch {
            if (week <= 0) return@launch
            // sourceCourseId 指向被覆盖的源课程：自定义课程取其 baseCourseId，否则回退到自身 id
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
                // THIS_WEEK 覆盖：记录被覆盖的基础课程 id，使该周用此自定义课程替换原课程
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

    /**
     * 新增一门覆盖整个学期的课程（scope = ALL_WEEKS）。
     */
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

    /**
     * 新增一门仅出现在指定周的课程（scope = THIS_WEEK）。
     *
     * @param week 目标周次，非法周次（<=0）会被忽略
     */
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
