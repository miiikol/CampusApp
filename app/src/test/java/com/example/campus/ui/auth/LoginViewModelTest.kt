package com.example.campus.ui.auth
import com.example.campus.core.common.Resource
import com.example.campus.data.local.entity.UserEntity
import com.example.campus.data.repository.UserRepository
import com.example.campus.testing.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * 针对 [LoginViewModel] 登录流程的单元测试：
 * 验证空输入立即返回错误、成功与失败时状态流的更新。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = mockk<UserRepository>(relaxed = true)

    private lateinit var viewModel: LoginViewModel

    @Before
    fun setUp() {
        // 每个测试前重建 ViewModel，保证状态隔离
        viewModel = LoginViewModel(repository)
    }

    @Test
    fun login_emptyStudentId_returnsErrorImmediately() {
        viewModel.login("", "password")

        val state = viewModel.loginState.value
        assertNotNull(state)
        assertTrue(state is Resource.Error)
        assertEquals("学号和密码不能为空", (state as Resource.Error).message)
    }

    @Test
    fun login_emptyPassword_returnsErrorImmediately() {
        viewModel.login("student01", "")

        val state = viewModel.loginState.value
        assertNotNull(state)
        assertTrue(state is Resource.Error)
        assertEquals("学号和密码不能为空", (state as Resource.Error).message)
    }

    @Test
    fun login_success_updatesStateFlowWithSuccess() = runTest {
        val user = UserEntity(
            id = "u1", username = "张三", studentId = "student01",
            avatarUrl = null, token = "token123", role = "student"
        )
        // 模拟先发 Loading 再发 Success 的登录流
        every { repository.login("student01", "pass123") } returns flow {
            emit(Resource.Loading())
            emit(Resource.Success(user))
        }

        viewModel.login("student01", "pass123")
        // 推进测试调度器，等待协程中的状态更新完成
        advanceUntilIdle()

        val state = viewModel.loginState.value
        assertNotNull(state)
        assertTrue(state is Resource.Success)
        assertEquals("u1", (state as Resource.Success).data?.id)
    }

    @Test
    fun login_networkError_updatesStateFlowWithError() = runTest {
        every { repository.login("student01", "wrong") } returns flowOf(
            Resource.Error("学号或密码错误(401)")
        )

        viewModel.login("student01", "wrong")
        // 推进测试调度器，等待状态流更新为错误
        advanceUntilIdle()

        val state = viewModel.loginState.value
        assertNotNull(state)
        assertTrue(state is Resource.Error)
        assertTrue((state as Resource.Error).message?.contains("401") == true)
    }
}
