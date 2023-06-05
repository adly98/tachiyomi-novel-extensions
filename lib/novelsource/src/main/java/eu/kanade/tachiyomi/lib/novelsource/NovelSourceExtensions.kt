package eu.kanade.tachiyomi.lib.novelsource

import android.graphics.Bitmap
import android.net.Uri
import android.text.Layout.Alignment
import android.widget.Toast
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.claudemirovsky.noveltomanga.DefaultThemes
import org.claudemirovsky.noveltomanga.NovelToManga
import java.io.ByteArrayOutputStream

fun NovelSource.createUrl(page: CharSequence) = "http://$NOVELTOMANGA_HOST/${Uri.encode(page.toString())}"

fun NovelSource.novelInterceptor(): Interceptor {
    val interceptor = object : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val url = request.url
            if (url.host != NOVELTOMANGA_HOST) {
                return chain.proceed(request)
            }
            val page = url.pathSegments.first()
            val bitmap = noveltomanga.drawPage(page)
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 0, stream)
            val responseBody = stream.toByteArray().toResponseBody("image/png".toMediaType())
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(responseBody)
                .build()
        }
    }
    return interceptor
}

fun NovelSource.hasInterceptor(): Boolean {
    return client.interceptors.toString().contains(::novelInterceptor.name)
}

fun NovelSource.getDefaultNovelToMangaInstance(): NovelToManga {
    return NovelToManga().apply {
        val centered = preferences.getBoolean(PREF_CENTERED_KEY, PREF_CENTERED_DEFAULT)
        alignment = if (centered) Alignment.ALIGN_CENTER else Alignment.ALIGN_NORMAL
        fontSize = preferences.getInt(PREF_FONTSIZE_KEY, PREF_FONTSIZE_DEFAULT).toFloat()
        margin = preferences.getInt(PREF_MARGIN_KEY, PREF_MARGIN_DEFAULT).toFloat()
        pageHeight = preferences.getInt(PREF_HEIGHT_KEY, PREF_HEIGHT_DEFAULT)
        pageWidth = preferences.getInt(PREF_WIDTH_KEY, PREF_WIDTH_DEFAULT)
        theme = DefaultThemes.valueOf(preferences.getString(PREF_THEME_KEY, PREF_THEME_DEFAULT)!!)
    }
}

fun NovelSource.addNovelSourcePreferences(screen: PreferenceScreen) {
    val novelSourcePrefIntl = NovelSourcePrefsIntl(lang)
    val themePref = ListPreference(screen.context).apply {
        key = PREF_THEME_KEY
        title = novelSourcePrefIntl.prefThemeTitle
        entries = novelSourcePrefIntl.prefThemeEntries
        entryValues = PREF_THEME_VALUES
        setDefaultValue(PREF_THEME_DEFAULT)
        summary = "%s"
        setOnPreferenceChangeListener { _, newValue ->
            val selected = newValue as String
            val index = findIndexOfValue(selected)
            val entry = entryValues[index] as String
            noveltomanga.theme = DefaultThemes.valueOf(entry)
            preferences.edit().putString(key, entry).commit()
        }
    }

    val centeredPref = SwitchPreferenceCompat(screen.context).apply {
        key = PREF_CENTERED_KEY
        title = novelSourcePrefIntl.prefCenteredTitle
        setDefaultValue(PREF_CENTERED_DEFAULT)

        setOnPreferenceChangeListener { _, newValue ->
            val checkValue = newValue as Boolean
            noveltomanga.alignment = when (checkValue) {
                true -> Alignment.ALIGN_CENTER
                else -> Alignment.ALIGN_NORMAL
            }
            preferences.edit().putBoolean(key, checkValue).commit()
        }
    }

    val fontSizePref = SeekBarPreference(screen.context).apply {
        key = PREF_FONTSIZE_KEY
        title = novelSourcePrefIntl.prefFontSizeTitle
        setDefaultValue(PREF_FONTSIZE_DEFAULT)
        setMin(PREF_FONTSIZE_MIN)
        setMax(PREF_FONTSIZE_MAX)
        setShowSeekBarValue(true)

        setOnPreferenceChangeListener { _, newValue ->
            val num = newValue as Int
            noveltomanga.fontSize = num.toFloat()
            preferences.edit().putInt(key, num).commit()
        }
    }

    val marginWidthPref = SeekBarPreference(screen.context).apply {
        key = PREF_MARGIN_KEY
        title = novelSourcePrefIntl.prefMarginTitle
        setDefaultValue(PREF_MARGIN_DEFAULT)
        setMin(PREF_MARGIN_MIN)
        setMax(PREF_MARGIN_MAX)
        setShowSeekBarValue(true)

        setOnPreferenceChangeListener { _, newValue ->
            val num = newValue as Int
            noveltomanga.margin = num.toFloat()
            preferences.edit().putInt(key, num).commit()
        }
    }

    val pageHeightPref = SeekBarPreference(screen.context).apply {
        key = PREF_HEIGHT_KEY
        title = novelSourcePrefIntl.prefHeightTitle
        setDefaultValue(PREF_HEIGHT_DEFAULT)
        setMin(PREF_PAGESIZE_MIN)
        setMax(PREF_PAGESIZE_MAX)
        setShowSeekBarValue(true)

        setOnPreferenceChangeListener { _, newValue ->
            val num = newValue as Int
            noveltomanga.pageHeight = num
            preferences.edit().putInt(key, num).commit()
        }
    }

    val pageWidthPref = SeekBarPreference(screen.context).apply {
        key = PREF_WIDTH_KEY
        title = novelSourcePrefIntl.prefWidthTitle
        setDefaultValue(PREF_WIDTH_DEFAULT)
        setMin(PREF_PAGESIZE_MIN)
        setMax(PREF_PAGESIZE_MAX)
        setShowSeekBarValue(true)

        setOnPreferenceChangeListener { _, newValue ->
            val num = newValue as Int
            noveltomanga.pageWidth = num
            preferences.edit().putInt(key, num).commit()
        }
    }

    val resetPref = SwitchPreferenceCompat(screen.context).apply {
        key = PREF_RESET_KEY
        title = novelSourcePrefIntl.prefResetTitle
        setDefaultValue(PREF_RESET_DEFAULT)

        setOnPreferenceChangeListener { _, newValue ->
            val checkValue = newValue as Boolean
            if (checkValue) {
                preferences.edit().apply {
                    noveltomanga.theme = DefaultThemes.valueOf(PREF_THEME_DEFAULT)
                    putString(PREF_THEME_KEY, PREF_THEME_DEFAULT)

                    noveltomanga.alignment = Alignment.ALIGN_NORMAL
                    putBoolean(PREF_CENTERED_KEY, PREF_CENTERED_DEFAULT)

                    noveltomanga.fontSize = PREF_FONTSIZE_DEFAULT.toFloat()
                    putInt(PREF_FONTSIZE_KEY, PREF_FONTSIZE_DEFAULT)

                    noveltomanga.margin = PREF_MARGIN_DEFAULT.toFloat()
                    putInt(PREF_MARGIN_KEY, PREF_MARGIN_DEFAULT)

                    noveltomanga.pageHeight = PREF_HEIGHT_DEFAULT
                    putInt(PREF_HEIGHT_KEY, PREF_HEIGHT_DEFAULT)

                    noveltomanga.pageWidth = PREF_WIDTH_DEFAULT
                    putInt(PREF_WIDTH_KEY, PREF_WIDTH_DEFAULT)

                    Toast.makeText(screen.context, novelSourcePrefIntl.reopenPreferencesText, Toast.LENGTH_LONG).show()
                }.commit()
            } else { true }
        }
    }

    screen.addPreference(themePref)
    screen.addPreference(centeredPref)
    screen.addPreference(fontSizePref)
    screen.addPreference(marginWidthPref)
    screen.addPreference(pageHeightPref)
    screen.addPreference(pageWidthPref)
    screen.addPreference(resetPref)
}
