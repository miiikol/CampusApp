package com.example.campus.data.repository

import com.example.campus.core.common.Resource
import com.example.campus.data.remote.ApiService
import com.example.campus.data.remote.dto.AdminPendingReviewDto
import com.example.campus.data.remote.dto.AdminReviewActionRequest
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

/**
 * 管理员审核数据仓库，负责获取待审核内容列表与执行审核操作。
 *
 * 审核流程：管理员查看待审核项 → 选择通过(approve)或驳回(reject) → 调用 [reviewItem]。
 * 该类不涉及本地缓存（审核强依赖实时远端数据）。
 */
class AdminRepository @Inject constructor(
    private val api: ApiService
) {

    /**
     * 获取待审核内容列表。
     *
     * @param adminId 管理员用户 ID
     * @return 成功时为待审核列表，失败时为可读错误消息（不涉及本地缓存）
     */
    suspend fun getPendingReviews(adminId: String): Resource<List<AdminPendingReviewDto>> {
        return try {
            Resource.Success(api.getPendingReviews(adminId))
        } catch (e: HttpException) {
            Resource.Error("请求失败(${e.code()})")
        } catch (e: IOException) {
            Resource.Error("无法连接服务器，请确认后端已启动")
        } catch (e: Exception) {
            Resource.Error("加载待审核失败")
        }
    }

    /**
     * 执行审核操作（通过 approve / 驳回 reject）。
     *
     * @param adminId 管理员用户 ID
     * @param itemType 审核内容类型（如 news / market / lost_found）
     * @param itemId 被审核内容 ID
     * @param action 审核动作（approve / reject）
     * @return 成功后端返回消息，失败时为可读错误消息
     */
    suspend fun reviewItem(
        adminId: String,
        itemType: String,
        itemId: String,
        action: String
    ): Resource<String> {
        return try {
            val resp = api.reviewPendingItem(
                AdminReviewActionRequest(
                    adminId = adminId,
                    itemType = itemType,
                    itemId = itemId,
                    action = action
                )
            )
            Resource.Success(resp.message)
        } catch (e: HttpException) {
            Resource.Error("操作失败(${e.code()})")
        } catch (e: IOException) {
            Resource.Error("无法连接服务器，请确认后端已启动")
        } catch (e: Exception) {
            Resource.Error("审核操作失败")
        }
    }
}
