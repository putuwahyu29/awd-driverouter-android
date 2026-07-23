package com.awd.driverouter.data.local

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class AppTheme { LIGHT, DARK, SYSTEM }
enum class AllocationStrategy { ROUND_ROBIN, WEIGHTED_ROUND_ROBIN, MOST_FREE, LEAST_USED, CUSTOM_ORDER, MIRROR, MANUAL }
enum class AppLanguage(val code: String) { ENGLISH("en"), INDONESIAN("in") }
enum class SyncMode { ONE_WAY, TWO_WAY }

@Singleton
class SettingsManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    
    private val _theme = MutableStateFlow(loadTheme())
    val theme: StateFlow<AppTheme> = _theme

    private val _strategy = MutableStateFlow(loadStrategy())
    val strategy: StateFlow<AllocationStrategy> = _strategy

    private val _language = MutableStateFlow(loadLanguage())
    val language: StateFlow<AppLanguage> = _language

    private val _isAppLockEnabled = MutableStateFlow(prefs.getBoolean("app_lock_enabled", false))
    val isAppLockEnabled: StateFlow<Boolean> = _isAppLockEnabled

    private val _downloadLocationUri = MutableStateFlow(prefs.getString("download_location_uri", null))
    val downloadLocationUri: StateFlow<String?> = _downloadLocationUri

    private val _downloadLocationName = MutableStateFlow(prefs.getString("download_location_name", null))
    val downloadLocationName: StateFlow<String?> = _downloadLocationName

    private val _customAccountOrder = MutableStateFlow(loadCustomOrder())
    val customAccountOrder: StateFlow<List<String>> = _customAccountOrder

    private fun loadTheme(): AppTheme {
        val themeName = prefs.getString("app_theme", AppTheme.SYSTEM.name)
        return try { AppTheme.valueOf(themeName!!) } catch (e: Exception) { AppTheme.SYSTEM }
    }

    fun setTheme(theme: AppTheme) {
        prefs.edit().putString("app_theme", theme.name).apply()
        _theme.value = theme
    }

    private fun loadStrategy(): AllocationStrategy {
        val strategyName = prefs.getString("allocation_strategy", AllocationStrategy.MOST_FREE.name)
        return try { AllocationStrategy.valueOf(strategyName!!) } catch (e: Exception) { AllocationStrategy.MOST_FREE }
    }

    fun setStrategy(strategy: AllocationStrategy) {
        prefs.edit().putString("allocation_strategy", strategy.name).apply()
        _strategy.value = strategy
    }

    private fun loadLanguage(): AppLanguage {
        val langCode = prefs.getString("app_language", AppLanguage.ENGLISH.code)
        return AppLanguage.entries.find { it.code == langCode } ?: AppLanguage.ENGLISH
    }

    fun setLanguage(language: AppLanguage) {
        prefs.edit().putString("app_language", language.code).apply()
        _language.value = language
    }

    fun setAppLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("app_lock_enabled", enabled).apply()
        _isAppLockEnabled.value = enabled
    }

    fun setDownloadLocation(uri: String?, name: String?) {
        prefs.edit()
            .putString("download_location_uri", uri)
            .putString("download_location_name", name)
            .apply()
        _downloadLocationUri.value = uri
        _downloadLocationName.value = name
    }

    private fun loadCustomOrder(): List<String> {
        val orderStr = prefs.getString("custom_account_order", "") ?: ""
        return if (orderStr.isEmpty()) emptyList() else orderStr.split(",")
    }

    fun setCustomAccountOrder(order: List<String>) {
        prefs.edit().putString("custom_account_order", order.joinToString(",")).apply()
        _customAccountOrder.value = order
    }
}
