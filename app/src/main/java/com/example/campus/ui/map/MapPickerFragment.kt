package com.example.campus.ui.map

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.MapsInitializer
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.core.PoiItem
import com.amap.api.services.geocoder.GeocodeResult
import com.amap.api.services.geocoder.GeocodeSearch
import com.amap.api.services.geocoder.RegeocodeQuery
import com.amap.api.services.geocoder.RegeocodeResult
import com.amap.api.services.help.Inputtips
import com.amap.api.services.help.InputtipsQuery
import com.amap.api.services.help.Tip
import com.amap.api.services.poisearch.PoiResult
import com.amap.api.services.poisearch.PoiSearch
import com.example.campus.databinding.FragmentMapPickerBinding
import com.example.campus.core.ui.SnackType
import com.example.campus.core.ui.showSnack
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 地图选点页面 Fragment。
 *
 * 基于高德地图 SDK 实现位置选择功能：
 * - 地图点击选点
 * - 搜索 POI 定位
 * - 输入提示联想
 * - 定位到当前设备位置
 *
 * 选点结果通过 SavedStateHandle 回传给上一个页面。
 */
class MapPickerFragment : Fragment() {

    private var _binding: FragmentMapPickerBinding? = null
    private val binding get() = _binding!!

    private var mapView: MapView? = null
    private var aMap: AMap? = null
    private var selectedMarker: Marker? = null
    private var selectedLatLng: LatLng? = null
    private var geocodeSearch: GeocodeSearch? = null
    private var poiSearch: PoiSearch? = null
    private var tipsJob: Job? = null
    private val tipsAdapter = TipsAdapter { tip ->
        onTipSelected(tip)
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        // 仅当用户授权后才启用定位；被拒绝时不再自动重复请求，避免无限弹窗循环
        if (result.values.any { it }) {
            enableMyLocationIfPermitted()
        } else {
            showSnack("未授予定位权限，无法定位到当前位置", type = SnackType.ERROR)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapPickerBinding.inflate(inflater, container, false)
        initAmapPrivacy()
        try {
            mapView = binding.mapView
            mapView?.onCreate(savedInstanceState)
            aMap = mapView?.map
            setupMap()
        } catch (t: Throwable) {
            showSnack("地图初始化失败：${t.message ?: "未知错误"}", type = SnackType.ERROR)
        }
        setupUi()

        return binding.root
    }

    private fun initAmapPrivacy() {
        val ctx = requireContext().applicationContext
        try {
            MapsInitializer.updatePrivacyShow(ctx, true, true)
            MapsInitializer.updatePrivacyAgree(ctx, true)
        } catch (_: Throwable) {
        }
        try {
            AMapLocationClient.updatePrivacyShow(ctx, true, true)
            AMapLocationClient.updatePrivacyAgree(ctx, true)
        } catch (_: Throwable) {
        }
    }

    private fun setupMap() {
        aMap?.uiSettings?.isMyLocationButtonEnabled = true
        aMap?.uiSettings?.isZoomControlsEnabled = true
        aMap?.moveCamera(CameraUpdateFactory.zoomTo(16f))

        // Map click to select point
        aMap?.setOnMapClickListener { latLng ->
            setSelectedLocation(latLng, null)
            reverseGeocode(latLng)
        }

        aMap?.setOnMarkerClickListener { marker ->
            val latLng = marker.position
            setSelectedLocation(latLng, marker.title)
            val snippet = marker.snippet?.takeIf { it.isNotBlank() }
            if (snippet != null) {
                binding.tvAddress.text = snippet
            } else {
                reverseGeocode(latLng)
            }
            true
        }

        // 尝试启用定位
        enableMyLocationIfPermitted()
    }

    private fun setupUi() {
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnConfirm.setOnClickListener {
            selectedLatLng?.let { latLng ->
                val result = MapLocation(
                    latitude = latLng.latitude,
                    longitude = latLng.longitude,
                    title = selectedMarker?.title?.takeIf { it.isNotBlank() }
                        ?: binding.etKeyword.text?.toString()?.takeIf { it.isNotBlank() },
                    address = binding.tvAddress.text?.toString()
                )
                findNavController().previousBackStackEntry
                    ?.savedStateHandle
                    ?.set("selected_location", result)
                findNavController().popBackStack()
            }
        }

        binding.rvTips.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTips.adapter = tipsAdapter
        binding.etKeyword.doOnTextChanged { text, _, _, _ ->
            val keyword = text?.toString()?.trim().orEmpty()
            binding.btnSearch.isEnabled = keyword.isNotEmpty()
            // 输入即触发联想提示（内部做了防抖）
            requestInputTips(keyword)
        }
        binding.btnSearch.setOnClickListener {
            val keyword = binding.etKeyword.text?.toString()?.trim().orEmpty()
            if (keyword.isNotEmpty()) {
                binding.rvTips.visibility = View.GONE
                searchPoiBySdk(keyword)
            }
        }
    }

    private fun setSelectedLocation(latLng: LatLng, title: String?) {
        selectedLatLng = latLng
        selectedMarker?.remove()
        selectedMarker = aMap?.addMarker(
            MarkerOptions()
                .position(latLng)
                .title(title ?: "选定位置")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
        )
        aMap?.animateCamera(CameraUpdateFactory.newLatLng(latLng))
    }

    private fun enableMyLocationIfPermitted() {
        val hasFine = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            return
        }

        try {
            aMap?.isMyLocationEnabled = true
            val client = AMapLocationClient(requireContext().applicationContext)
            val option = AMapLocationClientOption()
            option.locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
            option.isOnceLocation = true
            client.setLocationOption(option)
            client.setLocationListener { loc ->
                if (loc == null) {
                    showSnack("定位失败：未获取到位置信息", type = SnackType.ERROR)
                } else if (loc.errorCode == 0) {
                    val latLng = LatLng(loc.latitude, loc.longitude)
                    aMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
                } else {
                    showSnack("定位失败(${loc.errorCode})：${loc.errorInfo}", type = SnackType.ERROR)
                }
                client.stopLocation()
                client.onDestroy()
            }
            client.startLocation()
        } catch (t: Throwable) {
            showSnack("定位不可用：${t.message ?: "未知错误"}", type = SnackType.ERROR)
        }
    }

    private fun reverseGeocode(latLng: LatLng) {
        if (!ensureAmapKeyConfigured()) return
        val search = geocodeSearch ?: GeocodeSearch(requireContext().applicationContext).also { s ->
            s.setOnGeocodeSearchListener(object : GeocodeSearch.OnGeocodeSearchListener {
                override fun onRegeocodeSearched(result: RegeocodeResult?, rCode: Int) {
                    if (!isAdded || _binding == null) return
                    if (rCode == 1000) {
                        val address = result?.regeocodeAddress?.formatAddress?.takeIf { it.isNotBlank() }
                        if (address != null) {
                            binding.tvAddress.text = address
                        } else {
                            showSnack("未获取到地址信息", type = SnackType.INFO)
                        }
                    } else {
                        showSnack("逆地理编码失败：$rCode", type = SnackType.ERROR)
                    }
                }

                override fun onGeocodeSearched(result: GeocodeResult?, rCode: Int) = Unit
            })
        }
        geocodeSearch = search
        val query = RegeocodeQuery(
            LatLonPoint(latLng.latitude, latLng.longitude),
            200f,
            GeocodeSearch.AMAP
        )
        search.getFromLocationAsyn(query)
    }

    private fun requestInputTips(keyword: String) {
        tipsJob?.cancel()
        if (keyword.isBlank()) {
            tipsAdapter.submit(emptyList())
            binding.rvTips.visibility = View.GONE
            return
        }

        tipsJob = viewLifecycleOwner.lifecycleScope.launch {
            // 延迟 250ms 防抖，避免连续输入时频繁请求
            delay(250)
            if (!isAdded || _binding == null) return@launch
            if (!ensureAmapKeyConfigured()) return@launch
            val query = InputtipsQuery(keyword, null)
            query.cityLimit = false
            val inputtips = Inputtips(requireContext().applicationContext, query)
            inputtips.setInputtipsListener { tips, rCode ->
                if (!isAdded || _binding == null) return@setInputtipsListener
                if (rCode != 1000) {
                    tipsAdapter.submit(emptyList())
                    binding.rvTips.visibility = View.GONE
                    return@setInputtipsListener
                }
                val list = tips.orEmpty()
                    .filter { it.name?.isNotBlank() == true }
                    .take(8)
                tipsAdapter.submit(list)
                binding.rvTips.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
            }
            inputtips.requestInputtipsAsyn()
        }
    }

    private fun onTipSelected(tip: Tip) {
        binding.rvTips.visibility = View.GONE
        val name = tip.name?.takeIf { it.isNotBlank() }.orEmpty()
        if (name.isNotBlank()) {
            binding.etKeyword.setText(name)
            binding.etKeyword.setSelection(name.length)
        }

        val point = tip.point
        if (point != null) {
            val latLng = LatLng(point.latitude, point.longitude)
            setSelectedLocation(latLng, name.ifBlank { null })
            reverseGeocode(latLng)
        } else {
            val display = buildString {
                if (name.isNotBlank()) append(name)
                val district = tip.district?.takeIf { it.isNotBlank() }
                val address = tip.address?.takeIf { it.isNotBlank() }
                if (district != null) {
                    if (isNotEmpty()) append(" · ")
                    append(district)
                }
                if (address != null) {
                    if (isNotEmpty()) append(" · ")
                    append(address)
                }
            }
            if (display.isNotBlank()) {
                binding.tvAddress.text = display
            }
        }
    }

    private fun searchPoiBySdk(keyword: String) {
        if (!ensureAmapKeyConfigured()) return
        val center = selectedLatLng ?: aMap?.cameraPosition?.target
        if (center == null) {
            showSnack("地图尚未就绪，无法搜索", type = SnackType.INFO)
            return
        }

        val query = PoiSearch.Query(keyword, null, null).apply {
            pageSize = 10
            pageNum = 1
        }
        val search = PoiSearch(requireContext().applicationContext, query).also { s ->
            s.bound = PoiSearch.SearchBound(
                LatLonPoint(center.latitude, center.longitude),
                5000,
                true
            )
            s.setOnPoiSearchListener(object : PoiSearch.OnPoiSearchListener {
                override fun onPoiSearched(result: PoiResult?, rCode: Int) {
                    if (!isAdded || _binding == null) return
                    if (rCode != 1000) {
                        showSnack("搜索失败：$rCode", type = SnackType.ERROR)
                        return
                    }
                    val pois = result?.pois.orEmpty()
                    showPoiMarkers(pois, keyword)
                }

                override fun onPoiItemSearched(item: PoiItem?, rCode: Int) = Unit
            })
        }
        poiSearch = search
        search.searchPOIAsyn()
    }

    private fun showPoiMarkers(pois: List<PoiItem>, keyword: String) {
        if (pois.isEmpty()) {
            showSnack("未找到相关地点，请换个关键词试试", type = SnackType.INFO)
            return
        }

        aMap?.clear()
        selectedMarker = null

        val first = pois.firstOrNull()
        val firstPoint = first?.latLonPoint
        if (first != null && firstPoint != null) {
            val firstLatLng = LatLng(firstPoint.latitude, firstPoint.longitude)
            setSelectedLocation(firstLatLng, first.title)
            binding.tvAddress.text = first.snippet.orEmpty()
        }

        pois.take(5).forEach { poi ->
            val point = poi.latLonPoint ?: return@forEach
            val latLng = LatLng(point.latitude, point.longitude)
            aMap?.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title(poi.title.takeIf { it.isNotBlank() } ?: keyword)
                    .snippet(poi.snippet?.takeIf { it.isNotBlank() })
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
            )
        }
    }

    private fun ensureAmapKeyConfigured(): Boolean {
        val key = try {
            val appInfo = requireContext().packageManager.getApplicationInfo(
                requireContext().packageName,
                PackageManager.GET_META_DATA
            )
            appInfo.metaData?.getString("com.amap.api.v2.apikey")?.takeIf { it.isNotBlank() }
        } catch (_: Throwable) {
            null
        }
        val ok = !key.isNullOrBlank() && key != "REPLACE_WITH_YOUR_AMAP_KEY"
        if (!ok) {
            showSnack("请先配置高德 Key（AMAP_API_KEY）", type = SnackType.ERROR)
        }
        return ok
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView?.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        tipsJob?.cancel()
        mapView?.onDestroy()
        _binding = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView?.onSaveInstanceState(outState)
    }

    private class TipsAdapter(
        private val onClick: (Tip) -> Unit
    ) : RecyclerView.Adapter<TipsAdapter.VH>() {

        private val items = ArrayList<Tip>()

        fun submit(newItems: List<Tip>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_2, parent, false)
            return VH(view, onClick)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        class VH(
            itemView: View,
            private val onClick: (Tip) -> Unit
        ) : RecyclerView.ViewHolder(itemView) {
            private val title = itemView.findViewById<android.widget.TextView>(android.R.id.text1)
            private val sub = itemView.findViewById<android.widget.TextView>(android.R.id.text2)
            private var current: Tip? = null

            init {
                itemView.setOnClickListener {
                    current?.let(onClick)
                }
            }

            fun bind(tip: Tip) {
                current = tip
                title.text = tip.name.orEmpty()
                sub.text = buildString {
                    val district = tip.district?.takeIf { it.isNotBlank() }
                    val address = tip.address?.takeIf { it.isNotBlank() }
                    if (district != null) append(district)
                    if (address != null) {
                        if (isNotEmpty()) append(" ")
                        append(address)
                    }
                }
            }
        }
    }
}
