package com.example.campus.ui.news

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.campus.R
import com.example.campus.core.common.Resource
import com.example.campus.core.ui.SnackType
import com.example.campus.core.ui.showSnack
import com.example.campus.databinding.FragmentNewsDetailBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 资讯详情页面 Fragment。
 *
 * 展示资讯完整内容（标题、封面图、发布日期、正文），
 * 并提供评论列表、评论回复、点赞、管理员屏蔽评论等交互功能。
 */
@AndroidEntryPoint
class NewsDetailFragment : Fragment() {

    private var _binding: FragmentNewsDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: NewsDetailViewModel by viewModels()
    private lateinit var commentAdapter: CommentAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNewsDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // 初始化评论适配器，点赞/回复/屏蔽事件交由 ViewModel 处理
        commentAdapter = CommentAdapter(
            isAdmin = { viewModel.isAdmin.value },
            onBan = { viewModel.banComment(it.id) },
            onReply = { viewModel.setReplyTarget(it) },
            onToggleLike = { viewModel.toggleCommentLike(it.id) }
        )
        binding.rvComments.layoutManager = LinearLayoutManager(requireContext())
        binding.rvComments.adapter = commentAdapter

        // 返回、点赞、发送评论与取消回复等交互监听
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }
        binding.btnLikeNews.setOnClickListener {
            viewModel.toggleNewsLike()
        }
        binding.btnSendComment.setOnClickListener {
            // 校验评论内容非空后提交
            val content = binding.etComment.text?.toString().orEmpty()
            if (content.isBlank()) {
                showSnack("评论不能为空", type = SnackType.ERROR)
                return@setOnClickListener
            }
            viewModel.submitComment(content)
        }
        binding.btnCancelReply.setOnClickListener {
            viewModel.setReplyTarget(null)
        }
        observeViewModel()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 收集资讯详情并渲染标题、日期、正文与封面
                launch {
                    viewModel.news.collect { news ->
                        if (news == null) {
                            binding.tvTitle.text = "未找到资讯"
                            binding.tvDate.text = ""
                            binding.tvContent.text = ""
                            binding.ivCover.setImageResource(R.drawable.ic_launcher_foreground)
                            return@collect
                        }

                        binding.tvTitle.text = news.title
                        binding.tvDate.text = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            .format(Date(news.publishDate))
                        binding.tvContent.text = news.content.ifBlank { news.summary }

                        if (!news.imageUrl.isNullOrBlank()) {
                            Glide.with(binding.root.context)
                                .load(news.imageUrl)
                                .placeholder(R.drawable.ic_launcher_foreground)
                                .error(R.drawable.ic_launcher_foreground)
                                .into(binding.ivCover)
                        } else {
                            binding.ivCover.setImageResource(R.drawable.ic_launcher_foreground)
                        }
                    }
                }
                // 评论列表刷新
                launch {
                    viewModel.comments.collect { comments ->
                        commentAdapter.submitList(comments)
                    }
                }
                // 回复目标状态：控制回复提示栏的显隐与输入框提示语
                launch {
                    viewModel.replyTarget.collect { target ->
                        if (target == null) {
                            binding.replyBar.visibility = View.GONE
                            binding.tvReplying.text = ""
                            binding.etComment.hint = getString(R.string.news_comment_hint)
                        } else {
                            binding.replyBar.visibility = View.VISIBLE
                            binding.tvReplying.text = "回复 @${target.username}"
                            binding.etComment.hint = "输入回复内容"
                        }
                    }
                }
                // 资讯点赞状态：展示点赞数及当前是否已赞
                launch {
                    viewModel.newsLikeStatus.collect { like ->
                        if (like == null) {
                            binding.btnLikeNews.text = "点赞 0"
                        } else {
                            val prefix = if (like.likedByMe) "已赞" else "点赞"
                            binding.btnLikeNews.text = "$prefix ${like.likeCount}"
                        }
                    }
                }
                // 加载评论出错时提示
                launch {
                    viewModel.status.collect { status ->
                        if (status is Resource.Error) {
                            showSnack(status.message ?: "加载评论失败", type = SnackType.ERROR)
                        }
                    }
                }
                // 评论/点赞/屏蔽等操作结果：成功清空输入框并提示，失败仅提示
                launch {
                    viewModel.commentActionStatus.collect { status ->
                        when (status) {
                            is Resource.Success -> {
                                binding.etComment.setText("")
                                showSnack(status.data ?: "操作成功", type = SnackType.SUCCESS)
                                viewModel.clearActionStatus()
                            }
                            is Resource.Error -> {
                                showSnack(status.message ?: "操作失败", type = SnackType.ERROR)
                                viewModel.clearActionStatus()
                            }
                            else -> Unit
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
