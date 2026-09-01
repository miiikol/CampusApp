package com.example.campus.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.campus.R
import com.example.campus.core.common.UserRole
import com.example.campus.core.common.normalizeRole
import com.example.campus.data.repository.NotificationRepository
import com.example.campus.data.repository.UserRepository
import com.google.android.material.bottomnavigation.BottomNavigationView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 应用主 Activity，整个 App 的唯一容器。
 *
 * 核心职责：
 * - 管理底部导航栏的可见性与角色菜单切换（学生/管理员两套菜单）
 * - 监听登录状态变化：未登录 → 跳转登录页，已登录 → 按角色展示对应主页
 * - 定时同步通知并实时更新未读角标数量
 * - 控制各详情页/子页面隐藏底部导航栏
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var notificationRepository: NotificationRepository

    private var currentRoleMenu: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)

        // 目标页变化时，控制底部导航栏显隐（登录页/详情页隐藏）
        navController.addOnDestinationChangedListener { _, destination, _ ->
            updateBottomNavVisibility(bottomNav, destination.id)
        }
        // 导航联动只绑定一次，避免角色切换反复 inflateMenu 导致监听器累积
        bottomNav.setupWithNavController(navController)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                userRepository.getUser().collectLatest { user ->
                    // 未登录：清空菜单并跳转登录页
                    if (user == null) {
                        currentRoleMenu = null
                        bottomNav.menu.clear()
                        try {
                            bottomNav.removeBadge(R.id.nav_profile)
                        } catch (_: Exception) {
                            // 菜单项不存在时移除角标可能抛异常，忽略即可
                        }
                        updateBottomNavVisibility(bottomNav, navController.currentDestination?.id)
                        if (navController.currentDestination?.id != R.id.loginFragment) {
                            navController.navigate(
                                R.id.loginFragment,
                                null,
                                NavOptions.Builder()
                                    .setLaunchSingleTop(true)
                                    .setPopUpTo(R.id.nav_graph, true)
                                    .build()
                            )
                        }
                        return@collectLatest
                    }

                    // 已登录：按角色切换底部菜单
                    val role = normalizeRole(user.role)
                    applyRoleMenu(role, bottomNav, navController)

                    coroutineScope {
                        val uid = user.id
                        // 定时同步通知（异常静默忽略，避免中断循环）
                        launch {
                            while (isActive) {
                                try {
                                    notificationRepository.sync(uid)
                                } catch (_: Exception) {
                                }
                                delay(20_000)
                            }
                        }
                        // 未读通知数实时更新到"我的"角标，最多显示 99
                        launch {
                            notificationRepository.unreadCount(uid).collectLatest { count ->
                                val c = count.coerceAtLeast(0)
                                try {
                                    if (c == 0) {
                                        bottomNav.removeBadge(R.id.nav_profile)
                                    } else {
                                        val badge = bottomNav.getOrCreateBadge(R.id.nav_profile)
                                        badge.isVisible = true
                                        badge.number = c.coerceAtMost(99)
                                    }
                                } catch (_: Exception) {
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** 按角色加载对应菜单，若当前页不在新菜单中则跳转到角色默认首页。 */
    private fun applyRoleMenu(role: String, bottomNav: BottomNavigationView, navController: NavController) {
        if (currentRoleMenu == role && bottomNav.menu.size() > 0) return
        currentRoleMenu = role

        // 管理员与学生使用不同的底部菜单
        val menuRes = if (role == UserRole.ADMIN) {
            R.menu.bottom_nav_menu_admin
        } else {
            R.menu.bottom_nav_menu_student
        }

        bottomNav.menu.clear()
        bottomNav.inflateMenu(menuRes)
        updateBottomNavVisibility(bottomNav, navController.currentDestination?.id)

        // 若当前页面不属于新菜单，则回退到该角色的默认主页
        val currentDestinationId = navController.currentDestination?.id ?: return
        val inCurrentMenu = (0 until bottomNav.menu.size()).any { index ->
            bottomNav.menu.getItem(index).itemId == currentDestinationId
        }

        if (!inCurrentMenu && currentDestinationId != R.id.loginFragment && currentDestinationId != R.id.forgotPasswordFragment) {
            navController.navigate(defaultDestination(role))
        }
    }

    /** 角色默认主页：管理员进管理页，学生进课程页。 */
    private fun defaultDestination(role: String): Int {
        return if (role == UserRole.ADMIN) R.id.nav_admin else R.id.nav_course
    }

    /** 依据目标页决定是否隐藏底部导航栏（登录/找回/详情/地图页隐藏）。 */
    private fun updateBottomNavVisibility(bottomNav: BottomNavigationView, destinationId: Int?) {
        if (destinationId == null) {
            bottomNav.visibility = View.GONE
            return
        }
        val shouldHide =
            destinationId == R.id.loginFragment ||
                destinationId == R.id.forgotPasswordFragment ||
                destinationId == R.id.mapPickerFragment ||
                destinationId == R.id.mapViewerFragment ||
                destinationId == R.id.newsDetailFragment ||
                destinationId == R.id.marketDetailFragment

        bottomNav.visibility = if (shouldHide || bottomNav.menu.size() == 0) View.GONE else View.VISIBLE
    }
}
