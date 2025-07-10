package com.ai.assistance.operit.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ai.assistance.operit.data.model.PreferenceProfile
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.userPreferencesDataStore: DataStore<Preferences> by
        preferencesDataStore(name = "user_preferences")

// 全局单例实例
lateinit var preferencesManager: UserPreferencesManager
    private set

fun initUserPreferencesManager(context: Context) {
    preferencesManager = UserPreferencesManager(context)

    // 在后台初始化默认配置
    GlobalScope.launch {
        val profiles = preferencesManager.profileListFlow.first()
        if (profiles.isEmpty() || !profiles.contains("default")) {
            preferencesManager.createProfile("默认配置", isDefault = true)
        }
    }
}

class UserPreferencesManager(private val context: Context) {
    companion object {
        // 基本偏好相关键
        private val ACTIVE_PROFILE_ID = stringPreferencesKey("active_profile_id")
        private val PROFILE_LIST = stringPreferencesKey("profile_list")

        // 应用语言设置
        private val APP_LANGUAGE = stringPreferencesKey("app_language")

        // 分类锁定状态
        private val BIRTH_DATE_LOCKED = booleanPreferencesKey("birth_date_locked")
        private val PERSONALITY_LOCKED = booleanPreferencesKey("personality_locked")
        private val IDENTITY_LOCKED = booleanPreferencesKey("identity_locked")
        private val OCCUPATION_LOCKED = booleanPreferencesKey("occupation_locked")
        private val AI_STYLE_LOCKED = booleanPreferencesKey("ai_style_locked")

        // 主题设置相关键
        private val THEME_MODE = stringPreferencesKey("theme_mode")
        private val USE_SYSTEM_THEME = booleanPreferencesKey("use_system_theme")
        private val CUSTOM_PRIMARY_COLOR = intPreferencesKey("custom_primary_color")
        private val CUSTOM_SECONDARY_COLOR = intPreferencesKey("custom_secondary_color")
        private val USE_CUSTOM_COLORS = booleanPreferencesKey("use_custom_colors")
        private val USE_BACKGROUND_IMAGE = booleanPreferencesKey("use_background_image")
        private val BACKGROUND_IMAGE_URI = stringPreferencesKey("background_image_uri")
        private val BACKGROUND_IMAGE_OPACITY = floatPreferencesKey("background_image_opacity")

        // 背景媒体类型和视频设置
        private val BACKGROUND_MEDIA_TYPE = stringPreferencesKey("background_media_type")
        private val VIDEO_BACKGROUND_MUTED = booleanPreferencesKey("video_background_muted")
        private val VIDEO_BACKGROUND_LOOP = booleanPreferencesKey("video_background_loop")

        // 默认配置文件ID
        private const val DEFAULT_PROFILE_ID = "default"

        // 主题模式常量
        const val THEME_MODE_LIGHT = "light"
        const val THEME_MODE_DARK = "dark"

        // 背景媒体类型常量
        const val MEDIA_TYPE_IMAGE = "image"
        const val MEDIA_TYPE_VIDEO = "video"
        
        // 默认语言
        const val DEFAULT_LANGUAGE = "en"
    }

    // 获取应用语言设置
    val appLanguage: Flow<String> = 
            context.userPreferencesDataStore.data.map { preferences ->
                preferences[APP_LANGUAGE] ?: DEFAULT_LANGUAGE
            }
    
    // 保存应用语言设置
    suspend fun saveAppLanguage(languageCode: String) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[APP_LANGUAGE] = languageCode
        }
    }
    
    // 同步获取当前语言设置
    fun getCurrentLanguage(): String {
        return runBlocking {
            appLanguage.first()
        }
    }

    // 获取当前激活的用户偏好配置文件ID
    val activeProfileIdFlow: Flow<String> =
            context.userPreferencesDataStore.data.map { preferences ->
                preferences[ACTIVE_PROFILE_ID] ?: DEFAULT_PROFILE_ID
            }

    // 获取配置文件列表
    val profileListFlow: Flow<List<String>> =
            context.userPreferencesDataStore.data.map { preferences ->
                val profileListJson = preferences[PROFILE_LIST] ?: "[]"
                try {
                    val profileList =
                            Json.decodeFromString<List<String>>(profileListJson).toMutableList()
                    // 确保默认配置总是在列表中，即使在存储中不存在
                    if (!profileList.contains(DEFAULT_PROFILE_ID)) {
                        profileList.add(0, DEFAULT_PROFILE_ID)
                    }
                    profileList
                } catch (e: Exception) {
                    // 如果解析失败，至少返回包含默认配置的列表
                    listOf(DEFAULT_PROFILE_ID)
                }
            }

    // 主题相关Flow
    val themeMode: Flow<String> =
            context.userPreferencesDataStore.data.map { preferences ->
                preferences[THEME_MODE] ?: THEME_MODE_LIGHT
            }

    val useSystemTheme: Flow<Boolean> =
            context.userPreferencesDataStore.data.map { preferences ->
                preferences[USE_SYSTEM_THEME] ?: true
            }

    val customPrimaryColor: Flow<Int?> =
            context.userPreferencesDataStore.data.map { preferences ->
                preferences[CUSTOM_PRIMARY_COLOR]
            }

    val customSecondaryColor: Flow<Int?> =
            context.userPreferencesDataStore.data.map { preferences ->
                preferences[CUSTOM_SECONDARY_COLOR]
            }

    val useCustomColors: Flow<Boolean> =
            context.userPreferencesDataStore.data.map { preferences ->
                preferences[USE_CUSTOM_COLORS] ?: false
            }

    // 背景图片相关Flow
    val useBackgroundImage: Flow<Boolean> =
            context.userPreferencesDataStore.data.map { preferences ->
                preferences[USE_BACKGROUND_IMAGE] ?: false
            }

    val backgroundImageUri: Flow<String?> =
            context.userPreferencesDataStore.data.map { preferences ->
                preferences[BACKGROUND_IMAGE_URI]
            }

    val backgroundImageOpacity: Flow<Float> =
            context.userPreferencesDataStore.data.map { preferences ->
                preferences[BACKGROUND_IMAGE_OPACITY] ?: 0.3f
            }

    // 背景媒体类型相关Flow
    val backgroundMediaType: Flow<String> =
            context.userPreferencesDataStore.data.map { preferences ->
                preferences[BACKGROUND_MEDIA_TYPE] ?: MEDIA_TYPE_IMAGE
            }

    val videoBackgroundMuted: Flow<Boolean> =
            context.userPreferencesDataStore.data.map { preferences ->
                preferences[VIDEO_BACKGROUND_MUTED] ?: true
            }

    val videoBackgroundLoop: Flow<Boolean> =
            context.userPreferencesDataStore.data.map { preferences ->
                preferences[VIDEO_BACKGROUND_LOOP] ?: true
            }

    // 保存主题设置
    suspend fun saveThemeSettings(
            themeMode: String? = null,
            useSystemTheme: Boolean? = null,
            customPrimaryColor: Int? = null,
            customSecondaryColor: Int? = null,
            useCustomColors: Boolean? = null,
            useBackgroundImage: Boolean? = null,
            backgroundImageUri: String? = null,
            backgroundImageOpacity: Float? = null,
            backgroundMediaType: String? = null,
            videoBackgroundMuted: Boolean? = null,
            videoBackgroundLoop: Boolean? = null
    ) {
        context.userPreferencesDataStore.edit { preferences ->
            themeMode?.let { preferences[THEME_MODE] = it }
            useSystemTheme?.let { preferences[USE_SYSTEM_THEME] = it }
            customPrimaryColor?.let { preferences[CUSTOM_PRIMARY_COLOR] = it }
            customSecondaryColor?.let { preferences[CUSTOM_SECONDARY_COLOR] = it }
            useCustomColors?.let { preferences[USE_CUSTOM_COLORS] = it }
            useBackgroundImage?.let { preferences[USE_BACKGROUND_IMAGE] = it }
            backgroundImageUri?.let {
                // Simply store the URI as a string in preferences
                // No need to take persistent permissions as we're using internal storage
                preferences[BACKGROUND_IMAGE_URI] = it
            }
            backgroundImageOpacity?.let { preferences[BACKGROUND_IMAGE_OPACITY] = it }
            backgroundMediaType?.let { preferences[BACKGROUND_MEDIA_TYPE] = it }
            videoBackgroundMuted?.let { preferences[VIDEO_BACKGROUND_MUTED] = it }
            videoBackgroundLoop?.let { preferences[VIDEO_BACKGROUND_LOOP] = it }
        }
    }

    // 重置主题设置到默认值
    suspend fun resetThemeSettings() {
        context.userPreferencesDataStore.edit { preferences ->
            preferences.remove(THEME_MODE)
            preferences.remove(USE_SYSTEM_THEME)
            preferences.remove(CUSTOM_PRIMARY_COLOR)
            preferences.remove(CUSTOM_SECONDARY_COLOR)
            preferences.remove(USE_CUSTOM_COLORS)
            preferences.remove(USE_BACKGROUND_IMAGE)
            preferences.remove(BACKGROUND_IMAGE_URI)
            preferences.remove(BACKGROUND_IMAGE_OPACITY)
            preferences.remove(BACKGROUND_MEDIA_TYPE)
            preferences.remove(VIDEO_BACKGROUND_MUTED)
            preferences.remove(VIDEO_BACKGROUND_LOOP)
        }
    }

    // 获取指定配置文件的用户偏好
    fun getUserPreferencesFlow(profileId: String = ""): Flow<PreferenceProfile> {
        return context.userPreferencesDataStore.data.map { preferences ->
            val targetProfileId =
                    if (profileId.isEmpty()) {
                        preferences[ACTIVE_PROFILE_ID] ?: DEFAULT_PROFILE_ID
                    } else {
                        profileId
                    }

            val profileKey = stringPreferencesKey("profile_$targetProfileId")
            val profileJson = preferences[profileKey]

            if (profileJson != null) {
                try {
                    Json.decodeFromString<PreferenceProfile>(profileJson)
                } catch (e: Exception) {
                    createDefaultProfile(targetProfileId)
                }
            } else {
                createDefaultProfile(targetProfileId)
            }
        }
    }

    // 创建默认的配置文件
    private fun createDefaultProfile(profileId: String): PreferenceProfile {
        return PreferenceProfile(
                id = profileId,
                name = if (profileId == DEFAULT_PROFILE_ID) "默认配置" else profileId,
                birthDate = 0L,
                gender = "",
                occupation = "",
                personality = "",
                identity = "",
                aiStyle = "",
                isInitialized = false
        )
    }

    // 获取分类锁定状态
    val categoryLockStatusFlow: Flow<Map<String, Boolean>> =
            context.userPreferencesDataStore.data.map { preferences ->
                mapOf(
                        "birthDate" to (preferences[BIRTH_DATE_LOCKED] ?: false),
                        "personality" to (preferences[PERSONALITY_LOCKED] ?: false),
                        "identity" to (preferences[IDENTITY_LOCKED] ?: false),
                        "occupation" to (preferences[OCCUPATION_LOCKED] ?: false),
                        "aiStyle" to (preferences[AI_STYLE_LOCKED] ?: false)
                )
            }

    // 检查指定分类是否被锁定
    fun isCategoryLocked(category: String): Boolean {
        return runBlocking {
            val lockStatusMap = categoryLockStatusFlow.first()
            lockStatusMap[category] ?: false
        }
    }

    // 设置分类锁定状态
    suspend fun setCategoryLocked(category: String, locked: Boolean) {
        context.userPreferencesDataStore.edit { preferences ->
            when (category) {
                "birthDate" -> preferences[BIRTH_DATE_LOCKED] = locked
                "personality" -> preferences[PERSONALITY_LOCKED] = locked
                "identity" -> preferences[IDENTITY_LOCKED] = locked
                "occupation" -> preferences[OCCUPATION_LOCKED] = locked
                "aiStyle" -> preferences[AI_STYLE_LOCKED] = locked
            }
        }
    }

    // 同步检查偏好是否已初始化
    fun isPreferencesInitialized(): Boolean {
        return runBlocking {
            val activeProfile = getUserPreferencesFlow().first()
            activeProfile.isInitialized
        }
    }

    // 创建新的配置文件
    suspend fun createProfile(name: String, isDefault: Boolean = false): String {
        val profileId =
                if (isDefault && name == "默认配置") DEFAULT_PROFILE_ID
                else "profile_${System.currentTimeMillis()}"
        val newProfile =
                PreferenceProfile(
                        id = profileId,
                        name = name,
                        birthDate = 0L,
                        gender = "",
                        occupation = "",
                        personality = "",
                        identity = "",
                        aiStyle = "",
                        isInitialized = false
                )

        context.userPreferencesDataStore.edit { preferences ->
            // 添加到配置文件列表
            val currentList =
                    try {
                        val listJson = preferences[PROFILE_LIST] ?: "[]"
                        Json.decodeFromString<List<String>>(listJson).toMutableList()
                    } catch (e: Exception) {
                        mutableListOf()
                    }

            if (!currentList.contains(profileId)) {
                currentList.add(profileId)
            }

            preferences[PROFILE_LIST] = Json.encodeToString(currentList)

            // 保存配置文件内容
            val profileKey = stringPreferencesKey("profile_$profileId")
            preferences[profileKey] = Json.encodeToString(newProfile)

            // 默认锁定出生日期
            preferences[BIRTH_DATE_LOCKED] = true
        }

        return profileId
    }

    // 设置激活的配置文件
    suspend fun setActiveProfile(profileId: String) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[ACTIVE_PROFILE_ID] = profileId
        }
    }

    // 更新指定配置文件
    suspend fun updateProfile(profile: PreferenceProfile) {
        context.userPreferencesDataStore.edit { preferences ->
            val profileKey = stringPreferencesKey("profile_${profile.id}")
            preferences[profileKey] = Json.encodeToString(profile)
        }
    }

    // 更新配置文件中的特定分类
    suspend fun updateProfileCategory(
            profileId: String = "",
            birthDate: Long? = null,
            gender: String? = null,
            personality: String? = null,
            identity: String? = null,
            occupation: String? = null,
            aiStyle: String? = null
    ) {
        val targetProfileId =
                if (profileId.isEmpty()) {
                    context.userPreferencesDataStore.data.first()[ACTIVE_PROFILE_ID]
                            ?: DEFAULT_PROFILE_ID
                } else {
                    profileId
                }

        val currentProfile = getUserPreferencesFlow(targetProfileId).first()

        // 检查每个分类的锁定状态，如果锁定则不更新
        val updatedProfile =
                currentProfile.copy(
                        birthDate =
                                if (birthDate != null && !isCategoryLocked("birthDate")) birthDate
                                else currentProfile.birthDate,
                        gender =
                                if (gender != null && !isCategoryLocked("gender")) gender
                                else currentProfile.gender,
                        personality =
                                if (personality != null && !isCategoryLocked("personality"))
                                        personality
                                else currentProfile.personality,
                        identity =
                                if (identity != null && !isCategoryLocked("identity")) identity
                                else currentProfile.identity,
                        occupation =
                                if (occupation != null && !isCategoryLocked("occupation"))
                                        occupation
                                else currentProfile.occupation,
                        aiStyle =
                                if (aiStyle != null && !isCategoryLocked("aiStyle")) aiStyle
                                else currentProfile.aiStyle,
                        isInitialized = true
                )

        updateProfile(updatedProfile)
    }

    // 删除配置文件
    suspend fun deleteProfile(profileId: String) {
        if (profileId == DEFAULT_PROFILE_ID) {
            // 不允许删除默认配置
            return
        }

        context.userPreferencesDataStore.edit { preferences ->
            // 从列表中删除
            val currentList =
                    try {
                        val listJson = preferences[PROFILE_LIST] ?: "[]"
                        Json.decodeFromString<List<String>>(listJson).toMutableList()
                    } catch (e: Exception) {
                        mutableListOf()
                    }

            currentList.remove(profileId)
            preferences[PROFILE_LIST] = Json.encodeToString(currentList)

            // 删除配置文件内容
            val profileKey = stringPreferencesKey("profile_$profileId")
            preferences.remove(profileKey)

            // 如果当前活动的是被删除的配置文件，则切换到默认配置
            if (preferences[ACTIVE_PROFILE_ID] == profileId) {
                preferences[ACTIVE_PROFILE_ID] = DEFAULT_PROFILE_ID
            }
        }
    }

    // 重置用户偏好
    suspend fun resetPreferences() {
        context.userPreferencesDataStore.edit { preferences -> preferences.clear() }
    }
}
