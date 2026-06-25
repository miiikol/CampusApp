package com.example.campus.core.ui

import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.campus.R
import com.google.android.material.snackbar.Snackbar

/**
 * Snackbar 消息类型，对应不同的背景颜色。
 */
enum class SnackType {
    INFO,
    SUCCESS,
    ERROR
}

/**
 * 在 Fragment 中显示 Snackbar 的扩展函数。
 * @param message 提示文本
 * @param type 消息类型，决定背景色
 * @param anchor 锚点视图，Snackbar 会显示在该视图上方
 */
fun Fragment.showSnack(message: String, type: SnackType = SnackType.INFO, anchor: View? = null) {
    val root = view ?: return
    root.showSnack(message = message, type = type, anchor = anchor)
}

/**
 * 在任意 View 中显示 Snackbar 的扩展函数。
 */
fun View.showSnack(message: String, type: SnackType = SnackType.INFO, anchor: View? = null) {
    val bg = when (type) {
        SnackType.INFO -> ContextCompat.getColor(context, R.color.primary)
        SnackType.SUCCESS -> ContextCompat.getColor(context, R.color.success)
        SnackType.ERROR -> ContextCompat.getColor(context, R.color.error)
    }
    val snack = Snackbar.make(this, message, Snackbar.LENGTH_SHORT)
        .setBackgroundTint(bg)
        .setTextColor(ContextCompat.getColor(context, R.color.white))
    if (anchor != null) {
        snack.setAnchorView(anchor)
    }
    snack.show()
}
