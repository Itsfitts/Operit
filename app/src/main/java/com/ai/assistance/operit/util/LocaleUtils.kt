package com.ai.assistance.operit.util

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.ai.assistance.operit.data.preferences.preferencesManager
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/** 语言工具类，用于管理应用的国际化设置 */
object LocaleUtils {

    /**
     * 语言信息数据类
     * @param code 语言代码（如zh、en）
     * @param displayName 显示名称（英文）
     * @param nativeName 本地名称（语言自身的称呼）
     */
    data class Language(val code: String, val displayName: String, val nativeName: String)

    /** 获取支持的语言列表 */
    fun getSupportedLanguages(): List<Language> {
        return listOf(
                Language("zh", "Chinese", "中文"),
                Language("en", "English", "English")
                // 可以添加更多支持的语言
                )
    }

    /**
     * 获取当前应用设置的语言
     * @param context 上下文
     * @return 当前语言代码，如zh、en
     */
    fun getCurrentLanguage(context: Context): String {
        // Always return English as the default language
        return "en"
    }

    /** 获取系统当前语言 */
    private fun getCurrentSystemLanguage(context: Context): String {
        val currentLocale =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    context.resources.configuration.locales.get(0)
                } else {
                    context.resources.configuration.locale
                }

        return currentLocale.language
    }

    /**
     * 设置应用语言
     * @param context 上下文
     * @param languageCode 语言代码，如zh、en
     */
    fun setAppLanguage(context: Context, languageCode: String) {
        // Always use English regardless of the passed languageCode
        val locale = Locale("en")
        
        // 设置默认语言
        Locale.setDefault(locale)
        
        // 根据Android版本应用语言设置
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ 使用AppCompatDelegate API
            val localeList = LocaleListCompat.create(locale)
            AppCompatDelegate.setApplicationLocales(localeList)
        } else {
            // 较旧版本Android使用资源配置
            try {
                val config = Configuration(context.resources.configuration)
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    val localeList = LocaleList(locale)
                    LocaleList.setDefault(localeList)
                    config.setLocales(localeList)
                } else {
                    config.locale = locale
                }
                
                // 更新上下文资源配置
                @Suppress("DEPRECATION")
                context.resources.updateConfiguration(config, context.resources.displayMetrics)
                
                // 尝试更新Activity
                try {
                    val ctx = context.applicationContext
                    if (ctx is ContextWrapper) {
                        val baseContext = ctx.baseContext
                        if (baseContext != null) {
                            @Suppress("DEPRECATION")
                            baseContext.resources.updateConfiguration(
                                config, 
                                baseContext.resources.displayMetrics
                            )
                        }
                    }
                } catch (e: Exception) {
                    // 忽略无法更新的上下文
                }
            } catch (e: Exception) {
                // 错误时静默处理
            }
        }
    }
}
