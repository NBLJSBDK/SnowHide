package com.nbljsbdk.snowhide.data.repo

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/** Recent 识别结果与窗口锚点的小型持久化仓库。 */
object RecentCalibrationRepository {

    private const val PREFS_NAME = "snowhide_settings"
    private const val KEY_PACKAGES = "swipe_recent_packages"
    private const val KEY_WINDOW_PACKAGE = "swipe_recent_window_package"
    private const val KEY_WINDOW_CLASS = "swipe_recent_window_class"
    private const val MAX_PACKAGES = 50

    private lateinit var prefs: android.content.SharedPreferences
    private val _packages = MutableStateFlow<List<String>>(emptyList())
    val packages: StateFlow<List<String>> = _packages.asStateFlow()

    var windowPackage: String? = null
        private set
    var windowClass: String? = null
        private set

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _packages.value = readPackages()
        windowPackage = prefs.getString(KEY_WINDOW_PACKAGE, null)
        windowClass = prefs.getString(KEY_WINDOW_CLASS, null)
    }

    fun record(packages: Collection<String>, windowPackage: String, windowClass: String) {
        if (!::prefs.isInitialized) return
        val cleaned = packages
            .asSequence()
            .filter { it.isNotBlank() }
            .distinct()
            .take(MAX_PACKAGES)
            .toList()
        if (cleaned.isEmpty()) return
        _packages.value = cleaned
        this.windowPackage = windowPackage
        this.windowClass = windowClass.ifBlank { null }
        val array = JSONArray()
        cleaned.forEach(array::put)
        prefs.edit()
            .putString(KEY_PACKAGES, array.toString())
            .putString(KEY_WINDOW_PACKAGE, this.windowPackage)
            .putString(KEY_WINDOW_CLASS, this.windowClass)
            .apply()
    }

    fun clear() {
        _packages.value = emptyList()
        windowPackage = null
        windowClass = null
        if (::prefs.isInitialized) {
            prefs.edit()
                .remove(KEY_PACKAGES)
                .remove(KEY_WINDOW_PACKAGE)
                .remove(KEY_WINDOW_CLASS)
                .apply()
        }
    }

    private fun readPackages(): List<String> {
        val json = prefs.getString(KEY_PACKAGES, "[]") ?: "[]"
        return runCatching {
            JSONArray(json).let { array ->
                (0 until array.length())
                    .map { array.getString(it) }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .take(MAX_PACKAGES)
            }
        }.getOrDefault(emptyList())
    }
}
