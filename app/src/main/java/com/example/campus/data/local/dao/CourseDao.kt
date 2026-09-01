package com.example.campus.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.campus.data.local.entity.CourseEntity
import kotlinx.coroutines.flow.Flow

/**
 * 课程表（courses）的数据访问接口。
 *
 * 说明：
 * - 查询接口返回 [Flow]，用于在课程数据更新后自动刷新 UI
 * - 写入使用 REPLACE 策略，便于“远端刷新覆盖本地缓存”的实现
 */
@Dao
interface CourseDao {
    /**
     * 获取全部课程列表。
     */
    @Query("SELECT * FROM courses")
    fun getAllCourses(): Flow<List<CourseEntity>>

    /**
     * 按星期获取课程列表。
     *
     * @param day 星期，通常 1~7 表示周一到周日（与实体字段约定一致）
     */
    @Query("SELECT * FROM courses WHERE dayOfWeek = :day")
    fun getCoursesByDay(day: Int): Flow<List<CourseEntity>>

    /**
     * 批量插入/更新课程。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourses(courses: List<CourseEntity>)

    /** 清空课程表。 */
    @Query("DELETE FROM courses")
    suspend fun clearAllCourses()
}
