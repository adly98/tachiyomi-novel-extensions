package eu.kanade.tachiyomi.extension.en.wuxiap

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

object WuxiapFilters {

    open class QueryPartFilter(
        displayName: String,
        val vals: Array<Pair<String, String>>,
    ) : Filter.Select<String>(
        displayName,
        vals.map { it.first }.toTypedArray(),
    ) {

        fun toQueryPart() = vals[state].second
    }

    private inline fun <reified R> FilterList.asQueryPart(): String {
        return this.filterIsInstance<R>().joinToString("") {
            (it as QueryPartFilter).toQueryPart()
        }
    }

    class StatusFilter : QueryPartFilter("Status", CNFiltersData.status)
    class GenresFilter : QueryPartFilter("Genre", CNFiltersData.genres)
    class SortFilter : QueryPartFilter("Sort", CNFiltersData.sort)


    val filterList: FilterList
        get() = FilterList(
            GenresFilter(),
            StatusFilter(),
            SortFilter(),
        )

    data class FilterSearchParams(
        val genres: String = "",
        val status: String = "",
        val sort: String = "",
    )

    internal fun getSearchParameters(filters: FilterList): FilterSearchParams {
        return FilterSearchParams(
            filters.asQueryPart<GenresFilter>(),
            filters.asQueryPart<StatusFilter>(),
            filters.asQueryPart<SortFilter>(),
        )
    }

    private object CNFiltersData {

        val genres = arrayOf(
            Pair("All", "all"),
            Pair("Action", "action"),
            Pair("Adult", "adult"),
            Pair("Adventure", "adventure"),
            Pair("Comedy", "comedy"),
            Pair("Contemporary Romance", "contemporary-romance"),
            Pair("Drama", "drama"),
            Pair("Eastern Fantasy", "eastern-fantasy"),
            Pair("Ecchi", "ecchi"),
            Pair("Erciyuan", "erciyuan"),
            Pair("Faloo", "faloo"),
            Pair("Fan-fiction", "fan-fiction"),
            Pair("Fantasy", "fantasy"),
            Pair("Fantasy Romance", "fantasy-romance"),
            Pair("Game", "game"),
            Pair("Gender bender", "gender-bender"),
            Pair("Harem", "harem"),
            Pair("Hentai", "hentai"),
            Pair("Historical", "historical"),
            Pair("Horror", "horror"),
            Pair("Isekai", "isekai"),
            Pair("Josei", "josei"),
            Pair("Magical realism", "magical-realism"),
            Pair("Magic", "magic"),
            Pair("Martial arts", "martial-arts"),
            Pair("Mature", "mature"),
            Pair("Mecha", "mecha"),
            Pair("Modern life", "modern-life"),
            Pair("Movies", "movies"),
            Pair("Military", "military"),
            Pair("Mystery", "mystery"),
            Pair("Official Circles", "official_circles"),
            Pair("Psychological", "psychological"),
            Pair("Realistic fiction", "realistic-fiction"),
            Pair("Reincarnation", "reincarnation"),
            Pair("Romance", "romance"),
            Pair("School life", "school-life"),
            Pair("Sci-fi", "sci-fi"),
            Pair("Science Fiction", "science_fiction"),
            Pair("Suspense Thriller", "suspense_thriller"),
            Pair("Seinen", "seinen"),
            Pair("Shoujo", "shoujo"),
            Pair("Shoujo ai", "shoujo-ai"),
            Pair("Shounen", "shounen"),
            Pair("Shounen ai", "shounen-ai"),
            Pair("Slice of life", "slice-of-life"),
            Pair("Smut", "smut"),
            Pair("Sports", "sports"),
            Pair("Supernatural", "supernatural"),
            Pair("Tragedy", "tragedy"),
            Pair("Two-Dimensional", "two-dimensional"),
            Pair("Travel Through Time", "travel_through_time"),
            Pair("Urban", "urban"),
            Pair("Urban Life", "urban-life"),
            Pair("Video games", "video-games"),
            Pair("Virtual Reality", "virtual-reality"),
            Pair("Wuxia", "wuxia"),
            Pair("Wuxia Xianxia", "wuxia_xianxia"),
            Pair("Xianxia", "xianxia"),
            Pair("Xuanhuan", "xuanhuan"),
            Pair("Chinese", "chinese"),
            Pair("Korean", "korean"),
            Pair("Japanese", "japanese"),
        )

        val status = arrayOf(
            Pair("All", "all"),
            Pair("Completed", "Completed"),
            Pair("Ongoing", "Ongoing"),
        )

        val sort = arrayOf(
            Pair("Popular", "onclick"),
            Pair("New", "newstime"),
            Pair("Updates", "lastdotime"),
        )
    }
}
