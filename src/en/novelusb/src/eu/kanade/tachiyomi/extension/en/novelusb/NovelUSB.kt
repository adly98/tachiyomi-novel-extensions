package eu.kanade.tachiyomi.extension.en.novelusb

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
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelUSB : ParsedHttpSource(), NovelSource {

    override val name = "Novel USB"

    override val baseUrl = "https://novelusb.com"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    override fun headersBuilder() = super.headersBuilder().add("Referer", "$baseUrl/")

    override val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override val noveltomanga by lazy { getDefaultNovelToMangaInstance() }

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/sort/top-view?page=$page", headers)

    override fun popularMangaSelector(): String = "div.list-novel div.row"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        val imgElement = element.selectFirst("img")!!
        title = imgElement.attr("alt")
        thumbnail_url = imgElement.attr("src")
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
    }

    override fun popularMangaNextPageSelector(): String = "ul.pagination li.next a"

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/sort/daily-update?page=$page", headers)

    override fun latestUpdatesSelector() = "div.archive div.row"

    override fun latestUpdatesFromElement(element: Element): SManga =
        popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

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
        val params = NovelUSBFilters.getSearchParameters(filters)
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

    override fun getFilterList(): FilterList = NovelUSBFilters.filterList

    override fun searchMangaSelector() = latestUpdatesSelector()

    override fun searchMangaFromElement(element: Element) =
        popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = latestUpdatesNextPageSelector()

    // =========================== Manga Details ============================
    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val info = document.selectFirst("ul.info-meta")!!
        title = document.select("div.desc h3.title").text()
        thumbnail_url = document.select("div.info-holder img").attr("src")
        author = info.getInfo("Autor:")
        genre = info.select("li:contains(Genre:) a").eachText().joinToString()
        status = info.getInfo("Status:")!!.toStatus()
        description = document.selectFirst("div.desc-text")!!.text()
    }

    // ============================== Chapters ==============================
    override fun chapterListSelector() = "ul.list-chapter li a"

    override fun chapterListParse(response: Response): List<SChapter> {
        val novelID = response.asJsoup().selectFirst("a#btn-follow")!!.attr("data-id")
        val chapters = client.newCall(GET("$baseUrl/ajax/chapter-archive?novelId=$novelID")).execute().asJsoup()
        chapters.select(chapterListSelector()).map {
            chapterFromElement(it)
        }
        return super.chapterListParse(response)
    }

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val title = element.attr("title")
        chapter_number = title.substringAfter("Chapter ")
            .substringBefore(" ")
            .toFloatOrNull() ?: 0F
        name = title
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

    private fun String.toStatus() = when (this) {
        "Ongoing" -> SManga.ONGOING
        "Completed" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    private fun Element.getInfo(selector: String, trim: Boolean = true): String? {
        val info = selectFirst("li:contains($selector)")
            ?.text()
            ?: return null
        return if (trim) {
            info.substringAfter(selector).trim()
        } else {
            info
        }
    }

    companion object {
        const val PREFIX_SEARCH = "slug:"
    }
}
