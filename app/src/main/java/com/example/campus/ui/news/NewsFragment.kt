package com.example.campus.ui.news

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.campus.core.common.Resource
import com.example.campus.core.ui.SnackType
import com.example.campus.core.ui.showSnack
import com.example.campus.databinding.FragmentNewsBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * 资讯列表页面 Fragment。
 *
 * 展示校园资讯列表，点击条目跳转到资讯详情页。
 * 通过 [NewsViewModel] 管理数据加载与状态刷新。
 */
@AndroidEntryPoint
class NewsFragment : Fragment() {
    
    private var _binding: FragmentNewsBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: NewsViewModel by viewModels()
    private lateinit var newsAdapter: NewsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNewsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        newsAdapter = NewsAdapter { news ->
            findNavController().navigate(
                com.example.campus.R.id.newsDetailFragment,
                bundleOf("newsId" to news.id)
            )
        }
        binding.rvNews.apply {
            adapter = newsAdapter
            layoutManager = LinearLayoutManager(context)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.news.collect { newsList ->
                        newsAdapter.submitList(newsList)
                    }
                }
                
                launch {
                    viewModel.status.collect { status ->
                        when(status) {
                            is Resource.Loading -> {
                                // Show loading if needed
                            }
                            is Resource.Error -> {
                                showSnack(status.message ?: "加载失败", type = SnackType.ERROR)
                            }
                            is Resource.Success -> {
                                // Refresh complete
                            }
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
