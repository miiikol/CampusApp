package com.example.campus.ui.course

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.example.campus.R
import com.example.campus.data.local.entity.CourseEntity
import kotlin.math.max

/**
 * 课表周视图自定义控件。
 *
 * 功能：
 * - 绘制 7 天 × 12 节的网格
 * - 根据课程的星期与节次绘制课程块，并显示课程名称
 *
 * 扩展方向：
 * - 支持点击事件（查看详情）
 * - 增加课程冲突叠加展示策略
 * - 增加多周切换与不同配色方案
 */
class CourseScheduleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    fun interface OnCourseClickListener {
        fun onCourseClick(course: CourseEntity)
    }

    fun interface OnEmptyCellClickListener {
        fun onEmptyCellClick(dayOfWeek: Int, section: Int)
    }

    init {
        isClickable = true
        isFocusable = true
    }

    private val linePaint = Paint().apply {
        color = Color.LTGRAY
        strokeWidth = 2f
    }

    private val textPaint = Paint().apply {
        color = Color.BLACK
        textSize = 30f
        textAlign = Paint.Align.CENTER
    }

    private val coursePaint = Paint().apply {
        color = Color.parseColor("#3F51B5")
        style = Paint.Style.FILL
    }

    private val courseStrokePaint = Paint().apply {
        color = Color.TRANSPARENT
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }

    private val conflictStrokePaint = Paint().apply {
        color = Color.parseColor("#D32F2F")
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }

    private val courseTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = 24f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    private var courses: List<CourseEntity> = emptyList()
    private var currentWeek: Int = 1
    private var onCourseClickListener: OnCourseClickListener? = null
    private var onEmptyCellClickListener: OnEmptyCellClickListener? = null
    private var selectedCourseId: Long? = null

    private var cachedWidth = 0
    private var cachedHeight = 0
    private var cachedWeek = -1
    private var cachedCoursesHash = 0
    private var cachedRenderItems: List<RenderItem> = emptyList()

    /**
     * 设置并刷新要展示的课程列表。
     */
    fun setCourses(newCourses: List<CourseEntity>) {
        courses = newCourses
        cachedCoursesHash = 0
        invalidate()
    }

    fun setCurrentWeek(week: Int) {
        val newWeek = week.coerceAtLeast(1)
        if (currentWeek == newWeek) return
        currentWeek = newWeek
        invalidate()
    }

    fun getCurrentWeek(): Int = currentWeek

    fun setOnCourseClickListener(listener: OnCourseClickListener?) {
        onCourseClickListener = listener
    }

    fun setOnEmptyCellClickListener(listener: OnEmptyCellClickListener?) {
        onEmptyCellClickListener = listener
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()
        val cellWidth = width / 8 // 1 列为标签，7 列为周一至周日
        val cellHeight = height / 13 // 1 行为表头，12 行为节次

        // 绘制网格线
        for (i in 0..8) {
            canvas.drawLine(i * cellWidth, 0f, i * cellWidth, height, linePaint)
        }
        for (i in 0..13) {
            canvas.drawLine(0f, i * cellHeight, width, i * cellHeight, linePaint)
        }

        // 绘制表头：星期
        val days = resources.getStringArray(R.array.week_days)
        for (i in days.indices) {
            canvas.drawText(days[i], i * cellWidth + cellWidth / 2, cellHeight / 2 + 10, textPaint)
        }

        // 绘制节次序号
        for (i in 1..12) {
            canvas.drawText(i.toString(), cellWidth / 2, i * cellHeight + cellHeight / 2 + 10, textPaint)
        }

        val renderItems = getOrBuildRenderItems(cellWidth, cellHeight)
        renderItems.forEach { item ->
            coursePaint.color = item.backgroundColor
            canvas.drawRect(item.left, item.top, item.right, item.bottom, coursePaint)

            if (item.isConflict) {
                canvas.drawRect(item.left, item.top, item.right, item.bottom, conflictStrokePaint)
            } else if (item.course.id == selectedCourseId) {
                courseStrokePaint.color = Color.WHITE
                canvas.drawRect(item.left, item.top, item.right, item.bottom, courseStrokePaint)
            }

            drawCourseText(
                canvas = canvas,
                left = item.left,
                top = item.top,
                right = item.right,
                bottom = item.bottom,
                name = item.course.name,
                room = item.course.room
            )
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            return true
        }
        if (event.action == MotionEvent.ACTION_UP) {
            val widthF = width.toFloat()
            val heightF = height.toFloat()
            if (widthF <= 0f || heightF <= 0f) return super.onTouchEvent(event)

            val cellWidth = widthF / 8
            val cellHeight = heightF / 13
            val hit = getOrBuildRenderItems(cellWidth, cellHeight)
                .firstOrNull { event.x >= it.left && event.x <= it.right && event.y >= it.top && event.y <= it.bottom }

            if (hit != null) {
                selectedCourseId = hit.course.id
                invalidate()
                onCourseClickListener?.onCourseClick(hit.course)
                performClick()
                return true
            }

            val column = (event.x / cellWidth).toInt()
            val row = (event.y / cellHeight).toInt()
            val day = column
            val section = row
            if (day in 1..7 && section in 1..12) {
                selectedCourseId = null
                invalidate()
                onEmptyCellClickListener?.onEmptyCellClick(day, section)
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private data class RenderItem(
        val course: CourseEntity,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val isConflict: Boolean,
        val backgroundColor: Int
    )

    private data class TempItem(
        val course: CourseEntity,
        val day: Int,
        val start: Int,
        val end: Int,
        val columnIndex: Int,
        var totalColumns: Int
    )

    private fun getOrBuildRenderItems(cellWidth: Float, cellHeight: Float): List<RenderItem> {
        val w = width
        val h = height
        val newHash = courses.contentHashForRender()
        if (
            cachedWidth == w &&
            cachedHeight == h &&
            cachedWeek == currentWeek &&
            cachedCoursesHash == newHash
        ) {
            return cachedRenderItems
        }

        cachedWidth = w
        cachedHeight = h
        cachedWeek = currentWeek
        cachedCoursesHash = newHash

        val padding = 6f
        val innerPadding = 3f

        val overriddenBaseIds = courses
            .asSequence()
            .filter { it.onlyWeek == currentWeek && it.baseCourseId != 0L }
            .map { it.baseCourseId }
            .toSet()

        val filtered = courses
            .asSequence()
            .filter { course ->
                val day = course.dayOfWeek
                val start = course.startSection
                val end = course.endSection
                day in 1..7 &&
                    start in 1..12 &&
                    end in 1..12 &&
                    end >= start &&
                    weekRangeContains(course.weekRange, currentWeek)
            }
            .filter { course ->
                !(course.onlyWeek == 0 && course.baseCourseId == 0L && course.id in overriddenBaseIds)
            }
            .toList()

        val itemsByDay = filtered.groupBy { it.dayOfWeek }
        val renderItems = ArrayList<RenderItem>(filtered.size)

        for (day in 1..7) {
            val dayCourses = itemsByDay[day].orEmpty()
            if (dayCourses.isEmpty()) continue

            val sorted = dayCourses.sortedWith(compareBy<CourseEntity>({ it.startSection }, { it.endSection }, { it.name }))
            val active = ArrayList<TempItem>()
            val componentItems = ArrayList<TempItem>()
            var componentMaxConcurrent = 0

            fun finalizeComponent() {
                if (componentItems.isEmpty()) return
                val columns = max(1, componentMaxConcurrent)
                componentItems.forEach { it.totalColumns = columns }
                componentItems.clear()
                componentMaxConcurrent = 0
            }

            val tempItems = ArrayList<TempItem>(sorted.size)
            sorted.forEach { course ->
                val start = course.startSection
                val end = course.endSection

                active.removeAll { it.end < start }
                if (active.isEmpty()) {
                    finalizeComponent()
                }

                val maxCol = active.maxOfOrNull { it.columnIndex } ?: -1
                val used = BooleanArray(maxCol + 8)
                active.forEach { item ->
                    if (item.columnIndex in used.indices) {
                        used[item.columnIndex] = true
                    }
                }
                var col = 0
                while (col < used.size && used[col]) col++

                val temp = TempItem(
                    course = course,
                    day = day,
                    start = start,
                    end = end,
                    columnIndex = col,
                    totalColumns = 1
                )
                active.add(temp)
                componentItems.add(temp)
                tempItems.add(temp)
                componentMaxConcurrent = max(componentMaxConcurrent, active.size)
            }
            finalizeComponent()

            tempItems.forEach { temp ->
                val dayLeft = day * cellWidth + padding
                val dayRight = (day + 1) * cellWidth - padding
                val totalWidth = max(1f, dayRight - dayLeft)

                val cols = max(1, temp.totalColumns)
                val colWidth = totalWidth / cols
                val left = dayLeft + temp.columnIndex * colWidth + innerPadding
                val right = dayLeft + (temp.columnIndex + 1) * colWidth - innerPadding

                val top = temp.start * cellHeight + padding
                val bottom = (temp.end + 1) * cellHeight - padding

                renderItems.add(
                    RenderItem(
                        course = temp.course,
                        left = left,
                        top = top,
                        right = right,
                        bottom = bottom,
                        isConflict = cols > 1,
                        backgroundColor = if (cols > 1) Color.parseColor("#E53935") else colorForCourse(temp.course)
                    )
                )
            }
        }

        cachedRenderItems = renderItems
        return renderItems
    }

    private fun drawCourseText(
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        name: String,
        room: String
    ) {
        val maxWidth = max(0f, right - left - 12f)
        val maxHeight = max(0f, bottom - top - 12f)
        if (maxWidth <= 0f || maxHeight <= 0f) return

        val lines = ArrayList<String>(2)
        lines.add(name)
        if (room.isNotBlank()) lines.add(room)

        val fm = courseTextPaint.fontMetrics
        val lineHeight = (fm.descent - fm.ascent)
        val maxLines = max(1, (maxHeight / lineHeight).toInt())
        val drawLines = lines.take(maxLines)

        val totalTextHeight = drawLines.size * lineHeight
        var y = top + (bottom - top - totalTextHeight) / 2f - fm.ascent
        val centerX = (left + right) / 2f

        drawLines.forEach { raw ->
            val text = ellipsize(raw, maxWidth, courseTextPaint)
            canvas.drawText(text, centerX, y, courseTextPaint)
            y += lineHeight
        }
    }

    private fun ellipsize(text: String, maxWidth: Float, paint: Paint): String {
        if (paint.measureText(text) <= maxWidth) return text
        val ellipsis = "…"
        val ellipsisWidth = paint.measureText(ellipsis)
        if (ellipsisWidth >= maxWidth) return ellipsis

        var end = text.length
        while (end > 0) {
            val candidate = text.substring(0, end) + ellipsis
            if (paint.measureText(candidate) <= maxWidth) return candidate
            end--
        }
        return ellipsis
    }

    private fun colorForCourse(course: CourseEntity): Int {
        if (course.color != -1) return course.color
        val palette = intArrayOf(
            Color.parseColor("#3F51B5"),
            Color.parseColor("#009688"),
            Color.parseColor("#5E35B1"),
            Color.parseColor("#3949AB"),
            Color.parseColor("#00897B"),
            Color.parseColor("#546E7A"),
            Color.parseColor("#6D4C41"),
            Color.parseColor("#7CB342")
        )
        val key = "${course.name}|${course.teacher}|${course.room}"
        val idx = (key.hashCode().ushr(1)) % palette.size
        return palette[idx]
    }

    private fun List<CourseEntity>.contentHashForRender(): Int {
        var result = 1
        for (c in this) {
            result = 31 * result + c.id.hashCode()
            result = 31 * result + c.name.hashCode()
            result = 31 * result + c.room.hashCode()
            result = 31 * result + c.teacher.hashCode()
            result = 31 * result + c.dayOfWeek
            result = 31 * result + c.startSection
            result = 31 * result + c.endSection
            result = 31 * result + c.weekRange.hashCode()
            result = 31 * result + c.color
            result = 31 * result + c.baseCourseId.hashCode()
            result = 31 * result + c.onlyWeek
        }
        return result
    }

    companion object {
        fun weekRangeContains(weekRange: String, week: Int): Boolean {
            if (week <= 0) return false
            val parsed = parseWeekRanges(weekRange)
            if (parsed.ranges.isEmpty()) return true
            if (parsed.parity == Parity.ODD && week % 2 == 0) return false
            if (parsed.parity == Parity.EVEN && week % 2 != 0) return false
            return parsed.ranges.any { week in it }
        }

        fun weekRangeMax(weekRange: String): Int? {
            val parsed = parseWeekRanges(weekRange)
            return parsed.ranges.maxOfOrNull { it.last }
        }

        private enum class Parity { ODD, EVEN }

        private data class ParsedWeekRanges(val ranges: List<IntRange>, val parity: Parity?)

        private fun parseWeekRanges(raw: String): ParsedWeekRanges {
            val normalized = raw
                .replace("周", "")
                .replace(" ", "")
                .replace("\t", "")
                .trim()

            val parity = when {
                normalized.contains("单") || normalized.contains("odd", ignoreCase = true) -> Parity.ODD
                normalized.contains("双") || normalized.contains("even", ignoreCase = true) -> Parity.EVEN
                else -> null
            }

            val withoutParens = normalized
                .replace(Regex("\\(.*?\\)"), "")
                .replace(Regex("（.*?）"), "")

            val parts = withoutParens.split(Regex("[,，;；]"))
            val ranges = ArrayList<IntRange>()
            val pattern = Regex("(\\d+)(?:\\s*[-~—]\\s*(\\d+))?")
            parts.forEach { part ->
                val m = pattern.find(part) ?: return@forEach
                val start = m.groupValues[1].toIntOrNull() ?: return@forEach
                val end = m.groupValues.getOrNull(2)?.toIntOrNull() ?: start
                val s = minOf(start, end)
                val e = maxOf(start, end)
                if (s > 0 && e > 0) {
                    ranges.add(s..e)
                }
            }
            return ParsedWeekRanges(ranges, parity)
        }
    }
}
