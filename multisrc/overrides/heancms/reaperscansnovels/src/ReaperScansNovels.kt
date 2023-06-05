package eu.kanade.tachiyomi.extension.pt.reaperscansnovels

import android.app.Application
import android.content.SharedPreferences
import eu.kanade.tachiyomi.lib.novelsource.NovelSource
import eu.kanade.tachiyomi.lib.novelsource.createUrl
import eu.kanade.tachiyomi.lib.novelsource.getDefaultNovelToMangaInstance
import eu.kanade.tachiyomi.lib.novelsource.novelInterceptor
import eu.kanade.tachiyomi.multisrc.heancms.Genre
import eu.kanade.tachiyomi.multisrc.heancms.HeanCms
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.Page
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.Jsoup
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.TimeZone

class ReaperScansNovels : NovelSource, HeanCms(
    "Reaper Scans (Novels)",
    "https://reaperscans.net",
    "pt-BR",
) {
    override val client: OkHttpClient by lazy {
        super.client.newBuilder()
            .rateLimitHost(apiUrl.toHttpUrl(), 5, 1)
            .addInterceptor(novelInterceptor())
            .build()
    }

    override val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override val coverPath: String = ""

    override val entriesType = "Novel"

    override val noveltomanga by lazy { getDefaultNovelToMangaInstance() }

    override val dateFormat: SimpleDateFormat = super.dateFormat.apply {
        timeZone = TimeZone.getTimeZone("GMT+01:00")
    }

    // ============================= Page list ==============================
    override fun pageListParse(response: Response): List<Page> {
        return response.parseAs<HeanCmsNovelDto>().content.orEmpty()
            .let(Jsoup::parse)
            .let {
                val lines = it.select("p").eachText()

                val textPages = noveltomanga.getTextPages(lines)

                textPages.mapIndexed { pageIndex, pageText ->
                    Page(pageIndex, "", createUrl(pageText))
                }
            }
    }

    // ============================== Filters ===============================
    override fun getGenreList(): List<Genre> = listOf(
        Genre("Artes Marciais", 2),
        Genre("Aventura", 10),
        Genre("Ação", 9),
        Genre("Comédia", 14),
        Genre("Drama", 15),
        Genre("Escolar", 7),
        Genre("Fantasia", 11),
        Genre("Ficção científica", 16),
        Genre("Guerra", 17),
        Genre("Isekai", 18),
        Genre("Jogo", 12),
        Genre("Mangá", 24),
        Genre("Manhua", 23),
        Genre("Manhwa", 22),
        Genre("Mecha", 19),
        Genre("Mistério", 20),
        Genre("Nacional", 8),
        Genre("Realidade Virtual", 21),
        Genre("Retorno", 3),
        Genre("Romance", 5),
        Genre("Segunda vida", 4),
        Genre("Seinen", 1),
        Genre("Shounen", 13),
        Genre("Terror", 6),
    )

    // ============================= Utilities ==============================
    @Serializable
    data class HeanCmsNovelDto(val content: String? = null)

    private inline fun <reified T> Response.parseAs(): T {
        return use { it.body.string().let(json::decodeFromString) }
    }
}
