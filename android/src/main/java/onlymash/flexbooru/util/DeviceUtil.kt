package onlymash.flexbooru.util

import android.annotation.SuppressLint
import android.os.Build


//https://github.com/tachiyomiorg/tachiyomi/blob/master/core/src/main/java/eu/kanade/tachiyomi/util/system/DeviceUtil.kt
object DeviceUtil {

    val isMiui by lazy {
        getSystemProperty("ro.miui.ui.version.name")?.isNotEmpty() ?: false
    }

    @SuppressLint("PrivateApi")
    fun isMiuiOptimizationDisabled(): Boolean {
        val sysProp = getSystemProperty("persist.sys.miui_optimization")
        if (sysProp == "0" || sysProp == "false") {
            return true
        }

        return try {
            Class.forName("android.miui.AppOpsUtils")
                .getDeclaredMethod("isXOptMode")
                .invoke(null) as Boolean
        } catch (e: Exception) {
            false
        }
    }

    val isSamsung by lazy {
        Build.MANUFACTURER.equals("samsung", ignoreCase = true)
    }

    val invalidDefaultBrowsers = listOf(
        "android",
        "com.huawei.android.internal.app",
        "com.zui.resolver",
    )

    @SuppressLint("PrivateApi")
    private fun getSystemProperty(key: String?): String? {
        return try {
            Class.forName("android.os.SystemProperties")
                .getDeclaredMethod("get", String::class.java)
                .invoke(null, key) as String
        } catch (e: Exception) {
            null
        }
    }
}