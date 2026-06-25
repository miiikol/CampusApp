package com.example.campus.ui.course

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ArrayAdapter
import android.widget.CheckedTextView
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.campus.R
import com.example.campus.core.ui.SnackType
import com.example.campus.core.ui.showSnack
import com.example.campus.data.local.entity.CourseEntity
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.max

/**
 * 课程表页面 Fragment。
 *
 * 核心交互：
 * - 以周视图网格展示课表（基于 [CourseScheduleView] 自定义控件）
 * - 支持周切换、点击课程查看详情并编辑
 * - 支持在空白课格添加新课程
 * - 课程颜色可自定义选择
 */
@AndroidEntryPoint
class CourseFragment : Fragment(R.layout.fragment_course) {
    
    private val viewModel: CourseViewModel by viewModels()

    private enum class EditScope { THIS_WEEK, ALL_WEEKS }

    private data class ColorOption(val name: String, val color: Int)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val scheduleView = view.findViewById<CourseScheduleView>(R.id.courseScheduleView)
        val btnPrevWeek = view.findViewById<MaterialButton>(R.id.btnPrevWeek)
        val btnNextWeek = view.findViewById<MaterialButton>(R.id.btnNextWeek)
        val tvWeekLabel = view.findViewById<TextView>(R.id.tvWeekLabel)
        val fabAddCourse = view.findViewById<FloatingActionButton>(R.id.fabAddCourse)

        var currentWeek = 1
        var maxWeek = 1
        var latestCourses: List<CourseEntity> = emptyList()

        fun updateWeekUI() {
            tvWeekLabel.text = getString(R.string.course_week_format, currentWeek)
            btnPrevWeek.isEnabled = currentWeek > 1
            btnNextWeek.isEnabled = currentWeek < maxWeek
        }

        btnPrevWeek.setOnClickListener {
            if (currentWeek <= 1) return@setOnClickListener
            currentWeek -= 1
            scheduleView.setCurrentWeek(currentWeek)
            updateWeekUI()
        }

        btnNextWeek.setOnClickListener {
            if (currentWeek >= maxWeek) return@setOnClickListener
            currentWeek += 1
            scheduleView.setCurrentWeek(currentWeek)
            updateWeekUI()
        }

        tvWeekLabel.setOnClickListener {
            if (maxWeek <= 1) return@setOnClickListener
            val items = (1..maxWeek).map { getString(R.string.course_week_format, it) }.toTypedArray()
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.course_select_week)
                .setSingleChoiceItems(items, currentWeek - 1) { dialog, which ->
                    currentWeek = which + 1
                    scheduleView.setCurrentWeek(currentWeek)
                    updateWeekUI()
                    dialog.dismiss()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        scheduleView.setOnCourseClickListener { course ->
            val baseCourse = if (course.baseCourseId != 0L) {
                latestCourses.firstOrNull { it.id == course.baseCourseId } ?: course
            } else {
                course
            }
            val days = resources.getStringArray(R.array.week_days)
            val dayText = days.getOrNull(course.dayOfWeek).orEmpty()
            val message = buildString {
                append("教师：").append(course.teacher).append('\n')
                append("地点：").append(course.room).append('\n')
                append("时间：").append(dayText).append(" 第")
                    .append(course.startSection).append('-').append(course.endSection).append("节").append('\n')
                append("周次：").append(course.weekRange)
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(course.name)
                .setMessage(message)
                .setNegativeButton("编辑所有周") { _, _ ->
                    showEditDialog(
                        scope = EditScope.ALL_WEEKS,
                        original = baseCourse,
                        currentWeek = currentWeek
                    )
                }
                .setNeutralButton("编辑本周") { _, _ ->
                    showEditDialog(
                        scope = EditScope.THIS_WEEK,
                        original = course,
                        currentWeek = currentWeek
                    )
                }
                .setPositiveButton("关闭", null)
                .show()
        }

        scheduleView.setOnEmptyCellClickListener { dayOfWeek, section ->
            showAddDialog(
                dayOfWeekPrefill = dayOfWeek,
                sectionPrefill = section,
                currentWeek = currentWeek
            )
        }

        fabAddCourse.setOnClickListener {
            showAddDialog(
                dayOfWeekPrefill = null,
                sectionPrefill = null,
                currentWeek = currentWeek
            )
        }

        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            viewModel.courses.collectLatest { courses ->
                latestCourses = courses
                scheduleView.setCourses(courses)
                maxWeek = max(
                    1,
                    courses.mapNotNull { CourseScheduleView.weekRangeMax(it.weekRange) }.maxOrNull() ?: 1
                )
                if (currentWeek > maxWeek) {
                    currentWeek = maxWeek
                }
                scheduleView.setCurrentWeek(currentWeek)
                updateWeekUI()
            }
        }

        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            viewModel.refreshState.collectLatest { state ->
                val err = (state as? com.example.campus.core.common.Resource.Error)?.message
                if (!err.isNullOrBlank()) {
                    showSnack(err, type = SnackType.ERROR)
                }
            }
        }

        scheduleView.setCurrentWeek(currentWeek)
        updateWeekUI()
    }

    private fun buildColorOptions(): List<ColorOption> {
        return listOf(
            ColorOption("自动配色", -1),
            ColorOption("靛蓝", Color.parseColor("#3F51B5")),
            ColorOption("青绿", Color.parseColor("#009688")),
            ColorOption("紫色", Color.parseColor("#5E35B1")),
            ColorOption("深蓝", Color.parseColor("#3949AB")),
            ColorOption("墨绿", Color.parseColor("#00897B")),
            ColorOption("蓝灰", Color.parseColor("#546E7A")),
            ColorOption("棕色", Color.parseColor("#6D4C41")),
            ColorOption("草绿", Color.parseColor("#7CB342")),
            ColorOption("橙色", Color.parseColor("#F4511E"))
        )
    }

    private fun showEditDialog(scope: EditScope, original: CourseEntity, currentWeek: Int) {
        val context = requireContext()
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padding = dp(16)
            setPadding(padding, padding, padding, padding)
        }

        val etName = EditText(context).apply {
            hint = "课程名称"
            setText(original.name)
        }
        val etTeacher = EditText(context).apply {
            hint = "教师"
            setText(original.teacher)
        }
        val etRoom = EditText(context).apply {
            hint = "地点"
            setText(original.room)
        }
        val etDay = EditText(context).apply {
            hint = "星期(周一-周日)"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(original.dayOfWeek.toString())
        }
        val etStart = EditText(context).apply {
            hint = "开始节次(1-12)"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(original.startSection.toString())
        }
        val etEnd = EditText(context).apply {
            hint = "结束节次(1-12)"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(original.endSection.toString())
        }

        val etWeekRange = EditText(context).apply {
            hint = "周次(如 1-16 / 1,3,5 / 单周 / 双周)"
            setText(if (scope == EditScope.THIS_WEEK) currentWeek.toString() else original.weekRange)
            isEnabled = scope != EditScope.THIS_WEEK
        }

        val colorOptions = buildColorOptions()

        var selectedColor = original.color
        val tvColor = TextView(context).apply {
            textSize = 16f
            text = "颜色："
        }
        val tvColorValue = TextView(context).apply {
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            layoutParams = lp
            textSize = 16f
            text = colorLabelFor(selectedColor)
        }
        val colorPreview = View(context).apply {
            val size = dp(24)
            layoutParams = LinearLayout.LayoutParams(size, size)
            setBackgroundColor(colorPreviewColor(selectedColor, original))
        }

        fun openColorPicker() {
            val checked = colorOptions.indexOfFirst { it.color == selectedColor }.coerceAtLeast(0)
            val adapter = object : ArrayAdapter<ColorOption>(
                context,
                android.R.layout.simple_list_item_single_choice,
                colorOptions
            ) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val v = super.getView(position, convertView, parent) as CheckedTextView
                    val opt = getItem(position) ?: return v
                    v.text = colorLabelFor(opt.color, opt.name)

                    val size = dp(16)
                    val swatch = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(4).toFloat()
                        if (opt.color == -1) {
                            setColor(Color.TRANSPARENT)
                            setStroke(dp(1), Color.LTGRAY)
                        } else {
                            setColor(opt.color)
                        }
                        setBounds(0, 0, size, size)
                    }
                    v.setCompoundDrawables(swatch, null, null, null)
                    v.compoundDrawablePadding = dp(12)
                    return v
                }
            }
            MaterialAlertDialogBuilder(context)
                .setTitle("选择颜色")
                .setSingleChoiceItems(adapter, checked) { dialog, which ->
                    selectedColor = colorOptions[which].color
                    tvColorValue.text = colorLabelFor(selectedColor)
                    colorPreview.setBackgroundColor(colorPreviewColor(selectedColor, original))
                    dialog.dismiss()
                }
                .setNegativeButton("取消", null)
                .show()
        }

        val colorClickable = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            isClickable = true
            isFocusable = true
            setOnClickListener { openColorPicker() }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams = lp
        }
        colorClickable.addView(tvColor)
        colorClickable.addView(tvColorValue)
        colorClickable.addView(colorPreview)

        fun addRow(v: View) {
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(10)
            v.layoutParams = lp
            container.addView(v)
        }

        addRow(etName)
        addRow(etTeacher)
        addRow(etRoom)
        addRow(etDay)
        addRow(etStart)
        addRow(etEnd)
        addRow(etWeekRange)
        addRow(colorClickable)

        val title = if (scope == EditScope.THIS_WEEK) "编辑本周课程" else "编辑课程"
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(container)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = etName.text?.toString().orEmpty().trim()
                val teacher = etTeacher.text?.toString().orEmpty().trim()
                val room = etRoom.text?.toString().orEmpty().trim()
                val day = etDay.text?.toString().orEmpty().trim().toIntOrNull()
                val start = etStart.text?.toString().orEmpty().trim().toIntOrNull()
                val end = etEnd.text?.toString().orEmpty().trim().toIntOrNull()
                val weekRange = etWeekRange.text?.toString().orEmpty().trim()

                if (name.isBlank()) {
                    showSnack("课程名称不能为空", type = SnackType.ERROR)
                    return@setOnClickListener
                }
                if (day == null || day !in 1..7) {
                    showSnack("星期需要在 周一 - 周日 之间", type = SnackType.ERROR)
                    return@setOnClickListener
                }
                if (start == null || start !in 1..12 || end == null || end !in 1..12 || end < start) {
                    showSnack("节次需要在 1-12 之间，且结束节次不小于开始节次", type = SnackType.ERROR)
                    return@setOnClickListener
                }

                val updated = original.copy(
                    name = name,
                    teacher = teacher,
                    room = room,
                    dayOfWeek = day,
                    startSection = start,
                    endSection = end,
                    weekRange = if (scope == EditScope.THIS_WEEK) currentWeek.toString() else weekRange,
                    color = selectedColor
                )

                if (scope == EditScope.THIS_WEEK) {
                    viewModel.updateThisWeek(original = original, week = currentWeek, updated = updated)
                } else {
                    viewModel.updateAllWeeks(original = original, updated = updated)
                }
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun showAddDialog(dayOfWeekPrefill: Int?, sectionPrefill: Int?, currentWeek: Int) {
        val context = requireContext()
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padding = dp(16)
            setPadding(padding, padding, padding, padding)
        }

        val initialDay = (dayOfWeekPrefill ?: 1).coerceIn(1, 7)
        val initialSection = (sectionPrefill ?: 1).coerceIn(1, 12)

        val etName = EditText(context).apply {
            hint = "课程名称"
        }
        val etTeacher = EditText(context).apply {
            hint = "教师"
        }
        val etRoom = EditText(context).apply {
            hint = "地点"
        }
        val etDay = EditText(context).apply {
            hint = "星期(周一-周日)"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(initialDay.toString())
        }
        val etStart = EditText(context).apply {
            hint = "开始节次(1-12)"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(initialSection.toString())
        }
        val etEnd = EditText(context).apply {
            hint = "结束节次(1-12)"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(initialSection.toString())
        }
        val etWeekRange = EditText(context).apply {
            hint = "周次(如 1-16 / 1,3,5 / 单周 / 双周)"
            setText("1-16")
        }

        val colorOptions = buildColorOptions()
        var selectedColor = -1

        val tvColor = TextView(context).apply {
            textSize = 16f
            text = "颜色："
        }
        val tvColorValue = TextView(context).apply {
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            layoutParams = lp
            textSize = 16f
            text = colorLabelFor(selectedColor)
        }
        val colorPreview = View(context).apply {
            val size = dp(24)
            layoutParams = LinearLayout.LayoutParams(size, size)
            setBackgroundColor(colorPreviewColor(selectedColor, CourseEntity(name = "", room = "", teacher = "", dayOfWeek = initialDay, startSection = initialSection, endSection = initialSection, weekRange = "1-16")))
        }

        fun openColorPicker() {
            val checked = colorOptions.indexOfFirst { it.color == selectedColor }.coerceAtLeast(0)
            val adapter = object : ArrayAdapter<ColorOption>(
                context,
                android.R.layout.simple_list_item_single_choice,
                colorOptions
            ) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val v = super.getView(position, convertView, parent) as CheckedTextView
                    val opt = getItem(position) ?: return v
                    v.text = colorLabelFor(opt.color, opt.name)

                    val size = dp(16)
                    val swatch = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(4).toFloat()
                        if (opt.color == -1) {
                            setColor(Color.TRANSPARENT)
                            setStroke(dp(1), Color.LTGRAY)
                        } else {
                            setColor(opt.color)
                        }
                        setBounds(0, 0, size, size)
                    }
                    v.setCompoundDrawables(swatch, null, null, null)
                    v.compoundDrawablePadding = dp(12)
                    return v
                }
            }
            MaterialAlertDialogBuilder(context)
                .setTitle("选择颜色")
                .setSingleChoiceItems(adapter, checked) { dialog, which ->
                    selectedColor = colorOptions[which].color
                    tvColorValue.text = colorLabelFor(selectedColor)
                    val previewSeed = CourseEntity(
                        name = etName.text?.toString().orEmpty(),
                        teacher = etTeacher.text?.toString().orEmpty(),
                        room = etRoom.text?.toString().orEmpty(),
                        dayOfWeek = initialDay,
                        startSection = initialSection,
                        endSection = initialSection,
                        weekRange = etWeekRange.text?.toString().orEmpty().ifBlank { "1-16" }
                    )
                    colorPreview.setBackgroundColor(colorPreviewColor(selectedColor, previewSeed))
                    dialog.dismiss()
                }
                .setNegativeButton("取消", null)
                .show()
        }

        val colorClickable = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            isClickable = true
            isFocusable = true
            setOnClickListener { openColorPicker() }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams = lp
        }
        colorClickable.addView(tvColor)
        colorClickable.addView(tvColorValue)
        colorClickable.addView(colorPreview)

        fun addRow(v: View) {
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(10)
            v.layoutParams = lp
            container.addView(v)
        }

        addRow(etName)
        addRow(etTeacher)
        addRow(etRoom)
        addRow(etDay)
        addRow(etStart)
        addRow(etEnd)
        addRow(etWeekRange)
        addRow(colorClickable)

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle("添加课程")
            .setView(container)
            .setNegativeButton("取消", null)
            .setNeutralButton("添加本周", null)
            .setPositiveButton("添加所有周", null)
            .create()

        fun readAndValidate(): CourseEntity? {
            val name = etName.text?.toString().orEmpty().trim()
            val teacher = etTeacher.text?.toString().orEmpty().trim()
            val room = etRoom.text?.toString().orEmpty().trim()
            val day = etDay.text?.toString().orEmpty().trim().toIntOrNull()
            val start = etStart.text?.toString().orEmpty().trim().toIntOrNull()
            val end = etEnd.text?.toString().orEmpty().trim().toIntOrNull()
            val weekRange = etWeekRange.text?.toString().orEmpty().trim()

            if (name.isBlank()) {
                showSnack("课程名称不能为空", type = SnackType.ERROR)
                return null
            }
            if (day == null || day !in 1..7) {
                showSnack("星期需要在 周一 - 周日 之间", type = SnackType.ERROR)
                return null
            }
            if (start == null || start !in 1..12 || end == null || end !in 1..12 || end < start) {
                showSnack("节次需要在 1-12 之间，且结束节次不小于开始节次", type = SnackType.ERROR)
                return null
            }
            val finalWeekRange = weekRange.ifBlank { "1-16" }
            return CourseEntity(
                id = 0,
                name = name,
                teacher = teacher,
                room = room,
                dayOfWeek = day,
                startSection = start,
                endSection = end,
                weekRange = finalWeekRange,
                color = selectedColor,
                isRemote = false,
                baseCourseId = 0,
                onlyWeek = 0
            )
        }

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                val course = readAndValidate() ?: return@setOnClickListener
                viewModel.addThisWeek(currentWeek, course)
                dialog.dismiss()
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val course = readAndValidate() ?: return@setOnClickListener
                viewModel.addAllWeeks(course)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun colorLabelFor(color: Int): String {
        val hex = if (color == -1) null else String.format("#%06X", 0xFFFFFF and color)
        val name = when (color) {
            -1 -> "自动配色"
            Color.parseColor("#3F51B5") -> "靛蓝"
            Color.parseColor("#009688") -> "青绿"
            Color.parseColor("#5E35B1") -> "紫色"
            Color.parseColor("#3949AB") -> "深蓝"
            Color.parseColor("#00897B") -> "墨绿"
            Color.parseColor("#546E7A") -> "蓝灰"
            Color.parseColor("#6D4C41") -> "棕色"
            Color.parseColor("#7CB342") -> "草绿"
            Color.parseColor("#F4511E") -> "橙色"
            else -> null
        }
        return when {
            color == -1 -> "自动配色"
            name != null && hex != null -> "$name $hex"
            hex != null -> hex
            else -> "自动配色"
        }
    }

    private fun colorLabelFor(color: Int, name: String): String {
        val hex = if (color == -1) null else String.format("#%06X", 0xFFFFFF and color)
        return if (color == -1) name else "$name $hex"
    }

    private fun colorPreviewColor(selected: Int, original: CourseEntity): Int {
        if (selected != -1) return selected
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
        val key = "${original.name}|${original.teacher}|${original.room}"
        val idx = (key.hashCode().ushr(1)) % palette.size
        return palette[idx]
    }
}
