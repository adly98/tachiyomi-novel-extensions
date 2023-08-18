package eu.kanade.tachiyomi.extension.en.wuxiap

import android.app.Application
import android.content.SharedPreferences
import eu.kanade.tachiyomi.lib.novelsource.NovelSource
import eu.kanade.tachiyomi.lib.novelsource.createUrl
import eu.kanade.tachiyomi.lib.novelsource.getDefaultNovelToMangaInstance
import eu.kanade.tachiyomi.lib.novelsource.novelInterceptor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class Wuxiap : ParsedHttpSource(), NovelSource {

    override val name = "Wuxiap"

    override val baseUrl = "https://wuxiap.com"

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
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/list/all/all-onclick-${page - 1}.html", headers)

    override fun popularMangaSelector(): String = "ul.novel-list li.novel-item"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        val imgElement = element.selectFirst("img")!!
        title = imgElement.attr("alt")
        thumbnail_url = baseUrl + imgElement.attr("data-src")
        setUrlWithoutDomain(baseUrl + element.selectFirst("a")!!.attr("href"))
    }

    override fun popularMangaNextPageSelector(): String = "ul.pagination li a:contains(>)"

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/list/all/all-lastdotime-${page - 1}.html", headers)

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga =
        popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaSelector()

    // =============================== Search ===============================
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return if (query.startsWith(PREFIX_SEARCH)) {
            val slug = query.removePrefix(PREFIX_SEARCH)
            client.newCall(GET("$baseUrl/novel/$slug"))
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
        details.url = "/novel/$slug"
        return MangasPage(listOf(details), false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val params = WuxiapFilters.getSearchParameters(filters)
        return if(query.isNotBlank()){
            val data = FormBody.Builder()
                .add("show", "title")
                .add("tempid", "1")
                .add("tbname", "news")
                .add("keyboard", query)
                .build()
            val newUrl = client.newCall(POST("$baseUrl/e/search/index.php", headers, data)).execute()
            GET("${newUrl.header("Location")}&page=${page - 1}")
        }
        else if(Regex("top-hot|completed") in params.genres)
            GET("$baseUrl/sort/${params.genres + params.status}?page=$page")
        else
            GET("$baseUrl/genre/${params.genres + params.status}?page=$page")
    }

    override fun getFilterList(): FilterList = WuxiapFilters.filterList

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) =
        popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaSelector()

    // =========================== Manga Details ============================
    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val info = document.selectFirst("div.header-body")!!
        title = info.select(".novel-title").text()
        thumbnail_url = baseUrl + document.select("img").attr("data-src")
        author = info.select("span[itemprop=author]").text()
        genre = info.select("div.categories ul li a").eachText().joinToString()
        status = info.select("span:contains(Status) strong").text().toStatus()
        description = document.selectFirst("div.summary div.content")!!.text()
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

    companion object {
        const val PREFIX_SEARCH = "slug:"
    }
}
