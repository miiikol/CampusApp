package com.example.campus.ui.map

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * 地图位置数据类，用于页面间传递选点结果。
 *
 * 实现 [Parcelable] 以便通过 SavedStateHandle 传递。
 */
@Parcelize
data class MapLocation(
    val latitude: Double,
    val longitude: Double,
    val title: String? = null,
    val address: String? = null
) : Parcelable

