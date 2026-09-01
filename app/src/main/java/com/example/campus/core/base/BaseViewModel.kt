package com.example.campus.core.base

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * ViewModel 基类，封装协程启动与统一异常处理。
 * 所有业务 ViewModel 继承此类后，通过 [launch] 启动协程，
 * 异常会被自动捕获并交由 [handleError] 处理，避免因未捕获异常导致应用崩溃。
 */
abstract class BaseViewModel : ViewModel() {

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        handleError(throwable)
    }

    /**
     * 在 viewModelScope 中安全启动协程，自动绑定 ViewModel 生命周期。
     */
    protected fun launch(block: suspend CoroutineScope.() -> Unit) {
        viewModelScope.launch(exceptionHandler) {
            block()
        }
    }

    /**
     * 统一错误处理入口，子类可重写以自定义错误展示逻辑。
     */
    protected open fun handleError(throwable: Throwable) {
        throwable.printStackTrace()
    }
}
