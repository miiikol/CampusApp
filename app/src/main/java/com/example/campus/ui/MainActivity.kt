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

        navController.addOnDestinationChangedListener { _, destination, _ ->
            updateBottomNavVisibility(bottomNav, destination.id)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                userRepository.getUser().collectLatest { user ->
                    if (user == null) {
                        currentRoleMenu = null
                        bottomNav.menu.clear()
                        bottomNav.removeBadge(R.id.nav_profile)
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

                    val role = normalizeRole(user.role)
                    applyRoleMenu(role, bottomNav, navController)

                    coroutineScope {
                        val uid = user.id
                        launch {
                            while (isActive) {
                                try {
                                    notificationRepository.sync(uid)
                                } catch (_: Exception) {
                                }
                                delay(20_000)
                            }
                        }
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

    private fun applyRoleMenu(role: String, bottomNav: BottomNavigationView, navController: NavController) {
        if (currentRoleMenu == role && bottomNav.menu.size() > 0) return
        currentRoleMenu = role

        val menuRes = if (role == UserRole.ADMIN) {
            R.menu.bottom_nav_menu_admin
        } else {
            R.menu.bottom_nav_menu_student
        }

        bottomNav.menu.clear()
        bottomNav.inflateMenu(menuRes)
        bottomNav.setupWithNavController(navController)
        updateBottomNavVisibility(bottomNav, navController.currentDestination?.id)

        val currentDestinationId = navController.currentDestination?.id ?: return
        val inCurrentMenu = (0 until bottomNav.menu.size()).any { index ->
            bottomNav.menu.getItem(index).itemId == currentDestinationId
        }

        if (!inCurrentMenu && currentDestinationId != R.id.loginFragment && currentDestinationId != R.id.forgotPasswordFragment) {
            navController.navigate(defaultDestination(role))
        }
    }

    private fun defaultDestination(role: String): Int {
        return if (role == UserRole.ADMIN) R.id.nav_admin else R.id.nav_course
    }

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
