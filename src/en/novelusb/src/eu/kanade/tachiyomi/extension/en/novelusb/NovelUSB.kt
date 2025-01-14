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
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/sort/top-view?page=$page", headers)

    override fun popularMangaSelector(): String = "div.archive div.row"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        val imgElement = element.selectFirst("img")!!
        title = imgElement.attr("alt")
        thumbnail_url = imgElement.attr("src").replace("novel_200_89","novel")
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
    }

    override fun popularMangaNextPageSelector(): String = "ul.pagination li.next a"

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/sort/daily-update?page=$page", headers)

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga =
        popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaSelector()

    // =============================== Search ===============================
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return if (query.startsWith(PREFIX_SEARCH)) {
            val slug = query.removePrefix(PREFIX_SEARCH)
            client.newCall(GET("$baseUrl/novel-book/$slug"))
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
        details.url = "/novel-book/$slug"
        return MangasPage(listOf(details), false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val params = NovelUSBFilters.getSearchParameters(filters)
        return if(query.isNotBlank())
            GET("$baseUrl/search?keyword=$query&page=$page", headers)
        else if(Regex("top-hot|completed") in params.genres)
            GET("$baseUrl/sort/${params.genres + params.status}?page=$page")
        else
            GET("$baseUrl/genre/${params.genres + params.status}?page=$page")
    }

    override fun getFilterList(): FilterList = NovelUSBFilters.filterList

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) =
        popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaSelector()

    // =========================== Manga Details ============================
    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val info = document.selectFirst("ul.info-meta")!!
        title = document.select("div.books div.desc h3.title").text()
        thumbnail_url = document.select("div.info-holder img").attr("src")
        author = info.getInfo("Author:")
        genre = info.select("li:contains(Genre:) a").eachText().joinToString()
        status = info.getInfo("Status:")!!.toStatus()
        description = document.selectFirst("div.desc-text")!!.text()
    }

    // ============================== Chapters ==============================
    override fun chapterListSelector() = "ul.list-chapter li a"

    override fun chapterListParse(response: Response): List<SChapter> {
        val novelID = response.body.string().substringAfter("this.page.identifier = '").substringBefore("';")
        val chapters = client.newCall(GET("$baseUrl/ajax/chapter-archive?novelId=$novelID")).execute().asJsoup()
        return chapters.select(chapterListSelector()).map {
            chapterFromElement(it)
        }.reversed()
    }

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val title = element.attr("title")
        chapter_number = title.substringAfter("Chapter ")
            .substringBefore(" ").substringBefore(":")
            .toFloatOrNull() ?: 0F
        name = title
        setUrlWithoutDomain(element.attr("href"))
    }

    // =============================== Pages ================================
    override fun pageListParse(document: Document): List<Page> {
        val content = document.selectFirst("div#chr-content")!!
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
