package com.hedwig.sms.core

import android.content.Context
import android.content.res.Configuration
import com.hedwig.sms.data.SecurePrefs
import java.util.Locale

/**
 * 应用内语言切换 / in-app language switching.
 *
 * 在 Activity.attachBaseContext 里用所选语言包裹 Context；语言改变后 recreate() 即生效。
 * Wrap the Context with the chosen language in attachBaseContext; recreate() applies it.
 * 空字符串表示跟随系统 / empty string means follow the system.
 */
object LocaleHelper {

    fun wrap(base: Context): Context {
        val lang = runCatching { SecurePrefs(base).language }.getOrDefault("")
        if (lang.isBlank()) return base
        val locale = Locale.forLanguageTag(lang)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}
