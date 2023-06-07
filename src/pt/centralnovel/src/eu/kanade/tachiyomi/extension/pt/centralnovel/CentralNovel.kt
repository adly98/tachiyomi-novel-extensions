package eu.kanade.tachiyomi.extension.pt.centralnovel

import android.app.Application
import android.content.SharedPreferences
import eu.kanade.tachiyomi.lib.novelsource.NovelSource
import eu.kanade.tachiyomi.lib.novelsource.createUrl
import eu.kanade.tachiyomi.lib.novelsource.getDefaultNovelToMangaInstance
import eu.kanade.tachiyomi.lib.novelsource.novelInterceptor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Locale

class CentralNovel : ParsedHttpSource(), NovelSource {

    override val name = "Central Novel"

    override val baseUrl = "https://centralnovel.com"

    override val lang = "pt-BR"

    override val supportsLatest = true

    override val client: OkHttpClient by lazy {
        network.cloudflareClient
            .newBuilder()
            .addInterceptor(novelInterceptor())
            .build()
    }

    override fun headersBuilder() = super.headersBuilder().add("Referer", "$baseUrl/")

    override val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override val noveltomanga by lazy { getDefaultNovelToMangaInstance() }

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    override fun popularMangaSelector(): String = "div.serieslist.pop div.imgseries > a.series"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        val imgElement = element.selectFirst("img")!!
        title = imgElement.attr("title")
        thumbnail_url = imgElement.attr("src").substringBefore("=") + "=151,215"
        setUrlWithoutDomain(element.attr("href"))
    }

    override fun popularMangaNextPageSelector(): String? = null

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/series/?page=$page&order=update", headers)

    override fun latestUpdatesSelector() = "div.listupd a.tip"

    override fun latestUpdatesFromElement(element: Element): SManga =
        popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = "div.hpage a.r:contains(Próximo)"

    // =============================== Search ===============================
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return if (query.startsWith(PREFIX_SEARCH)) {
            val slug = query.removePrefix(PREFIX_SEARCH)
            client.newCall(GET("$baseUrl/series/$slug"))
                .asObservableSuccess()
                .map { response ->
                    searchMangaBySlugParse(response, slug)
                }
        } else {
            super.fetchSearchManga(page, query, filters)
        }
    }

    private fun searchMangaBySlugParse(response: Response, slug: String): MangasPage {
        val details = mangaDetailsParse(response)
        details.url = "/series/$slug"
        return MangasPage(listOf(details), false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val params = CNFilters.getSearchParameters(filters)
        val url = "$baseUrl/series/".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            if (query.isNotBlank()) {
                addQueryParameter("s", query)
            }
            if (params.status.isNotBlank()) {
                addQueryParameter("status", params.status)
            }
            if (params.order.isNotBlank()) {
                addQueryParameter("order", params.order)
            }
            if (params.genres.isNotEmpty()) {
                params.genres.forEach { addQueryParameter("genre[]", it) }
            }
            if (params.types.isNotEmpty()) {
                params.types.forEach { addQueryParameter("types[]", it) }
            }
        }.build()
        return GET(url.toString(), headers)
    }

    override fun getFilterList(): FilterList = CNFilters.filterList

    override fun searchMangaSelector() = latestUpdatesSelector()

    override fun searchMangaFromElement(element: Element) =
        popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = latestUpdatesNextPageSelector()

    // =========================== Manga Details ============================
    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val img = document.selectFirst("div.thumb > img")!!
        val info = document.selectFirst("div.ninfo > div.info-content")!!
        title = img.attr("title")
        thumbnail_url = img.attr("src")
        author = info.getInfo("Autor:")
        genre = info.select("div.genxed > a").eachText().joinToString()
        status = info.getInfo("Status:")!!.toStatus()
        var desc = document.selectFirst("div.entry-content")!!.text() + "\n"
        document.selectFirst("div.ninfo > span.alter")?.let {
            desc += "\nTítulos Alternativos: ${it.text()}"
        }
        info.getInfo("Tipo:", false)?.let { desc += "\n$it" }
        info.getInfo("Lançamento::", false)?.let { desc += "\n$it" }
        description = desc
    }

    // ============================== Chapters ==============================
    override fun chapterListSelector() = "div.eplister li > a"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val num = element.selectFirst("div.epl-num")!!.text()
        chapter_number = num.substringAfter("Cap. ")
            .substringBefore(" ")
            .toFloatOrNull() ?: 0F
        name = num + " " + element.selectFirst("div.epl-title")!!.text()
        date_upload = element.selectFirst("div.epl-date")!!.text().toDate()
        setUrlWithoutDomain(element.attr("href"))
    }

    // =============================== Pages ================================
    override fun pageListParse(document: Document): List<Page> {
        val content = document.selectFirst("div.epcontent")!!
        val elements = content.select("p, img")
        val last = elements.last()
        val temporaryList = mutableListOf<Element>()
        val pageList = buildList {
            for (element in elements) {
                when {
                    element == last || element.tagName() == "img" -> {
                        val lines = temporaryList.mapNotNull {
                            it.text().ifEmpty { null }
                        }
                        temporaryList.clear()
                        if (lines.isNotEmpty()) {
                            val textPages = noveltomanga.getTextPages(lines)
                            val offset = lastIndex + 1
                            addAll(
                                textPages.mapIndexed { pageIndex, pageText ->
                                    Page(pageIndex + offset, "", createUrl(pageText))
                                },
                            )
                        }
                        if (element.tagName() == "img") {
                            add(Page(lastIndex + 1, "", element.attr("src")))
                        }
                    }
                    element.tagName() == "p" -> temporaryList.add(element)
                }
            }
        }
        return pageList
    }

    override fun imageUrlParse(document: Document) = ""

    // ============================= Utilities ==============================
    private fun String.toDate(): Long {
        return runCatching { DATE_FORMATTER.parse(trim())?.time }
            .getOrNull() ?: 0L
    }

    private fun String.toStatus() = when (this) {
        "Em andamento" -> SManga.ONGOING
        "Completo" -> SManga.COMPLETED
        "Hiato" -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    private fun Element.getInfo(selector: String, trim: Boolean = true): String? {
        val info = selectFirst("div.spe > span:contains($selector)")
            ?.text()
            ?: return null
        return if (trim) {
            info.substringAfter(selector).trim()
        } else {
            info
        }
    }

    companion object {
        private val DATE_FORMATTER by lazy {
            SimpleDateFormat("MMMMM dd, yyyy", Locale("pt", "BR"))
        }

        const val PREFIX_SEARCH = "slug:"
    }
}
