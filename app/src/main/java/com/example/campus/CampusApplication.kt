package com.example.campus

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * 应用入口 Application 类。
 *
 * @HiltAndroidApp 注解触发 Hilt 依赖注入框架的自动代码生成，
 * 是本应用 DI 容器的根节点。如需初始化第三方库，在 [onCreate] 中完成。
 */
@HiltAndroidApp
class CampusApplication : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}
