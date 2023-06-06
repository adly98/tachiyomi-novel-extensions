package eu.kanade.tachiyomi.extension.pt.mangahost

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservable
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class MangaHost : ParsedHttpSource() {

    // Hardcode the id because the name was wrong and the language wasn't specific.
    override val id: Long = 3926812845500643354

    override val name = "Mangá Host"

    override val baseUrl = "https://mangahosted.com"

    override val lang = "pt-BR"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(3, 1, TimeUnit.SECONDS)
        .addInterceptor(::blockMessageIntercept)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Accept", ACCEPT)
        .add("Accept-Language", ACCEPT_LANGUAGE)
        .add("Referer", baseUrl)

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int): Request {
        val listPath = if (page == 1) "" else "/mais-visualizados/page/${page - 1}"
        val newHeaders = headersBuilder()
            .set("Referer", "$baseUrl/mangas$listPath")
            .build()

        val pageStr = if (page != 1) "/page/$page" else ""
        return GET("$baseUrl/mangas/mais-visualizados$pageStr", newHeaders)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val results = super.popularMangaParse(response)

        if (results.mangas.isEmpty()) {
            throw Exception(BLOCK_MESSAGE)
        }

        return results
    }

    override fun popularMangaSelector() = "div#dados div.manga-block div.manga-block-left a"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        val thumbnailEl = element.selectFirst("img")!!
        val thumbnailAttr = if (thumbnailEl.hasAttr("data-path")) "data-path" else "src"

        title = element.attr("title").withoutLanguage()
        thumbnail_url = thumbnailEl.attr(thumbnailAttr).toLargeUrl()
        setUrlWithoutDomain(element.attr("href"))
    }

    override fun popularMangaNextPageSelector() = "div.wp-pagenavi:has(a.nextpostslink)"

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request {
        val listPath = if (page == 1) "" else "/lancamentos/page/${page - 1}"
        val newHeaders = headersBuilder()
            .set("Referer", baseUrl + listPath)
            .build()

        val pageStr = if (page != 1) "/page/$page" else ""
        return GET("$baseUrl/lancamentos$pageStr", newHeaders)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val results = super.latestUpdatesParse(response)

        if (results.mangas.isEmpty()) {
            throw Exception(BLOCK_MESSAGE)
        }

        return results
    }

    override fun latestUpdatesSelector() = "div#dados div.w-row div.column-img a"

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // =============================== Search ===============================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/find/$query", headers)
    }

    override fun searchMangaSelector() = "table.table-search > tbody > tr > td:eq(0) > a"

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector(): String? = null

    // =========================== Manga Details ============================
    /**
     * The site wrongly return 404 for some titles, even if they are present.
     * In those cases, the extension will parse the response normally.
     */
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return client.newCall(mangaDetailsRequest(manga))
            .asObservableIgnoreCode(404)
            .map { response ->
                mangaDetailsParse(response).apply { initialized = true }
            }
    }

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        val infoElement = document.selectFirst("div.box-content div.w-row div.w-col:eq(1) article")
            ?: throw Exception(BLOCK_MESSAGE)

        val textElement = infoElement.selectFirst("div.text")!!

        author = textElement.selectFirst("li div:contains(Autor:)").textWithoutLabel()
        artist = textElement.selectFirst("li div:contains(Arte:)").textWithoutLabel()
        genre = infoElement.select("h3.subtitle + div.tags a").eachText().joinToString()
        status = infoElement.selectFirst("li div:contains(Status:)")?.ownText().toStatus()
        thumbnail_url = document.selectFirst("div.box-content div.w-row div.w-col:eq(0) div.widget img")!!
            .attr("src")

        description = textElement.selectFirst("div.paragraph")?.text()
            ?.substringBefore("Relacionados:")
    }

    // ============================== Chapters ==============================
    /**
     * The site wrongly return 404 for some titles, even if they are present.
     * In those cases, the extension will parse the response normally.
     */
    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return if (manga.status != SManga.LICENSED) {
            client.newCall(chapterListRequest(manga))
                .asObservableIgnoreCode(404)
                .map(::chapterListParse)
        } else {
            Observable.error(Exception("Licensed - No chapters to show"))
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = super.chapterListParse(response)

        if (chapters.isEmpty()) {
            throw Exception(BLOCK_MESSAGE)
        }

        return chapters
    }

    override fun chapterListSelector(): String =
        "article section.clearfix div.chapters div.cap div.card.pop"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        val poptitle = element.selectFirst("div.pop-title")!!
        name = poptitle.text().withoutLanguage()
        scanlator = element.selectFirst("div.pop-content small strong")?.text().orEmpty()
        date_upload = element.selectFirst("small.clearfix")?.text()
            ?.substringAfter("Adicionado em ")
            .toDate()
        chapter_number = poptitle.selectFirst("span.btn-caps")?.text()
            ?.toFloatOrNull() ?: 1f
        setUrlWithoutDomain(element.selectFirst("div.tags a")!!.attr("href"))
        scanlator!!.split("/").let { scanlators ->
            if (scanlators.count() >= 5) {
                scanlator = scanlators.first() + " e mais " + (scanlators.count() - 1)
            }
        }
    }

    // =============================== Pages ================================
    /**
     * The site wrongly return 404 for some chapters, even if they are present.
     * In those cases, the extension will parse the response normally.
     */
    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        return client.newCall(pageListRequest(chapter))
            .asObservableIgnoreCode(404)
            .map(::pageListParse)
    }

    override fun pageListRequest(chapter: SChapter): Request {
        // Just to prevent the detection of the crawler.
        val newHeader = headersBuilder()
            .set("Referer", "$baseUrl${chapter.url}".substringBeforeLast("/"))
            .build()

        return GET(baseUrl + chapter.url, newHeader)
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div#slider a img")
            .mapIndexed { i, el -> Page(i, document.location(), el.attr("src")) }
    }

    // =============================== Images ===============================
    override fun imageUrlParse(document: Document) = ""

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .set("Accept", ACCEPT_IMAGE)
            .set("Referer", "$baseUrl/")
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    // ============================= Utilities ==============================
    private fun blockMessageIntercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())

        if (response.code == 403 || response.code == 1020) {
            response.close()
            throw Exception(BLOCK_MESSAGE)
        }

        return response
    }

    private fun Call.asObservableIgnoreCode(code: Int): Observable<Response> {
        return asObservable().doOnNext { response ->
            if (!response.isSuccessful && response.code != code) {
                response.close()
                throw Exception("HTTP error ${response.code}")
            }
        }
    }

    private fun String?.toDate(): Long {
        return runCatching {
            DATE_FORMAT.parse(this.orEmpty())?.time
        }.getOrNull() ?: 0L
    }

    private fun String?.toStatus() = when (this) {
        "Ativo" -> SManga.ONGOING
        "Completo" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    private fun String.withoutLanguage(): String = replace(LANG_REGEX, "")

    private fun String.toLargeUrl(): String = replace(IMAGE_REGEX, "_full.")

    private fun Element?.textWithoutLabel() = this?.text().orEmpty().substringAfter(":").trim()

    companion object {
        private const val ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9," +
            "image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9"
        private const val ACCEPT_IMAGE = "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8"
        private const val ACCEPT_LANGUAGE = "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7,es;q=0.6,gl;q=0.5"

        private val LANG_REGEX = "( )?\\((PT-)?BR\\)".toRegex()
        private val IMAGE_REGEX = "_(small|medium|xmedium)\\.".toRegex()

        private const val BLOCK_MESSAGE = "O site está bloqueando o Tachiyomi. " +
            "Migre para outra fonte caso o problema persistir."

        private val DATE_FORMAT by lazy {
            SimpleDateFormat("MMM dd, yyyy", Locale.ENGLISH)
        }
    }
}
