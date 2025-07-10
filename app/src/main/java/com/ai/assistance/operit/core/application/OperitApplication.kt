package com.ai.assistance.operit.core.application

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import coil.ImageLoader
import coil.disk.DiskCache
import coil.request.CachePolicy
import com.ai.assistance.operit.core.tools.system.AndroidShellExecutor
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.mcp.MCPImageCache
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.preferences.initAndroidPermissionPreferences
import com.ai.assistance.operit.data.preferences.initUserPreferencesManager
import com.ai.assistance.operit.data.preferences.preferencesManager
import com.ai.assistance.operit.ui.features.chat.webview.LocalWebServer
import com.ai.assistance.operit.util.GlobalExceptionHandler
import com.ai.assistance.operit.util.LocaleUtils
import com.ai.assistance.operit.util.SerializationSetup
import com.ai.assistance.operit.util.TextSegmenter
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

/** Application class for Operit */
class OperitApplication : Application() {

    companion object {
        /** Global JSON instance with custom serializers */
        lateinit var json: Json
            private set

        // 全局应用实例
        lateinit var instance: OperitApplication
            private set

        // 全局ImageLoader实例，用于高效缓存图片
        lateinit var globalImageLoader: ImageLoader
            private set

        private const val TAG = "OperitApplication"
    }

    // 应用级协程作用域
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 懒加载数据库实例
    private val database by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 在所有其他初始化之前设置全局异常处理器
        Thread.setDefaultUncaughtExceptionHandler(GlobalExceptionHandler(this))

        // Initialize the JSON serializer with our custom module
        json = Json {
            serializersModule = SerializationSetup.module
            ignoreUnknownKeys = true
            isLenient = true
            prettyPrint = false
            encodeDefaults = true
        }

        // 初始化用户偏好管理器
        initUserPreferencesManager(applicationContext)

        // 初始化Android权限偏好管理器
        initAndroidPermissionPreferences(applicationContext)

        // 在最早时机初始化并应用语言设置
        initializeAppLanguage()

        // 初始化AndroidShellExecutor上下文
        AndroidShellExecutor.setContext(applicationContext)

        // 初始化PDFBox资源加载器
        PDFBoxResourceLoader.init(getApplicationContext());

        // 初始化图片缓存
        MCPImageCache.initialize(applicationContext)

        // 初始化TextSegmenter
        applicationScope.launch { TextSegmenter.initialize(applicationContext) }

        // 预加载数据库
        applicationScope.launch {
            // 简单访问数据库以触发初始化
            database.problemDao().getProblemCount()
        }

        // 初始化全局图片加载器，设置强大的缓存策略
        globalImageLoader =
                ImageLoader.Builder(this)
                        .crossfade(true)
                        .respectCacheHeaders(true)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .diskCache {
                            DiskCache.Builder()
                                    .directory(filesDir.resolve("image_cache"))
                                    .maxSizeBytes(50 * 1024 * 1024) // 50MB磁盘缓存上限，比百分比更精确
                                    .build()
                        }
                        .memoryCache {
                            // 设置内存缓存最大大小为应用可用内存的15%
                            coil.memory.MemoryCache.Builder(this).maxSizePercent(0.15).build()
                        }
                        .build()
    }

    /** 初始化应用语言设置 */
    private fun initializeAppLanguage() {
        try {
            // Always use English
            val languageCode = "en"

            Log.d(TAG, "Setting language: $languageCode")

            // Apply language setting immediately
            val locale = Locale(languageCode)
            // Set default language
            Locale.setDefault(locale)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13+ use AppCompatDelegate API
                val localeList = LocaleListCompat.create(locale)
                AppCompatDelegate.setApplicationLocales(localeList)
                Log.d(TAG, "Using AppCompatDelegate to set language: $languageCode")
            } else {
                // Older Android versions - partial update here, more complete in attachBaseContext
                val config = Configuration()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    val localeList = LocaleList(locale)
                    LocaleList.setDefault(localeList)
                    config.setLocales(localeList)
                } else {
                    config.locale = locale
                }

                resources.updateConfiguration(config, resources.displayMetrics)
                Log.d(TAG, "Using Configuration to set language: $languageCode")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize language settings", e)
        }
    }

    override fun attachBaseContext(base: Context) {
        // Apply language settings before attaching base context
        try {
            // Always use English
            val code = "en"
            val locale = Locale(code)
            val config = Configuration(base.resources.configuration)

            // Set language configuration
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val localeList = LocaleList(locale)
                LocaleList.setDefault(localeList)
                config.setLocales(localeList)
            } else {
                config.locale = locale
                Locale.setDefault(locale)
            }

            // Create new context with configuration
            val context = base.createConfigurationContext(config)
            super.attachBaseContext(context)
            Log.d(TAG, "Successfully applied base context language: $code")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply base context language", e)
            super.attachBaseContext(base)
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        // 在应用终止时关闭LocalWebServer服务器
        try {
            val webServer = LocalWebServer.getInstance(applicationContext)
            if (webServer.isRunning()) {
                webServer.stop()
                Log.d(TAG, "应用终止，已关闭本地Web服务器")
            }
        } catch (e: Exception) {
            Log.e(TAG, "关闭本地Web服务器失败: ${e.message}", e)
        }
    }
}
