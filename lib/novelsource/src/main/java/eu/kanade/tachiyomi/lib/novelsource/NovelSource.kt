package eu.kanade.tachiyomi.lib.novelsource

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
import okhttp3.OkHttpClient
import org.claudemirovsky.noveltomanga.NovelToManga

interface NovelSource : ConfigurableSource {
    val client: OkHttpClient

    val lang: String

    val preferences: SharedPreferences

    val noveltomanga: NovelToManga

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        addNovelSourcePreferences(screen)
    }
}
