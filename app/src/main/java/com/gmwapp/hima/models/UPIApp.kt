package com.gmwapp.hima.models

import android.content.pm.ResolveInfo

data class UPIApp(
    val packageName: String,
    val appName: String,
    val resolveInfo: ResolveInfo,
    var isSelected: Boolean = false
)







