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


    val filterList: FilterList
        get() = FilterList(
            GenresFilter(),
            StatusFilter(),
        )

    data class FilterSearchParams(
        val genres: String = "",
        val status: String = "",
    )

    internal fun getSearchParameters(filters: FilterList): FilterSearchParams {
        return FilterSearchParams(
            filters.asQueryPart<GenresFilter>(),
            filters.asQueryPart<StatusFilter>(),
        )
    }

    private object CNFiltersData {

        val status = arrayOf(
            Pair("All Novels", ""),
            Pair("Completed", "/completed"),
        )

        val genres = arrayOf(
            Pair("Hot Novels", "top-hot"),
            Pair("Completed Novels", "completed"),
            Pair("Action", "action"),
            Pair("Adult", "adult"),
            Pair("Adventure", "adventure"),
            Pair("Anime", "anime"),
            Pair("Arts", "arts"),
            Pair("Comedy", "comedy"),
            Pair("Drama", "drama"),
            Pair("Eastern", "eastern"),
            Pair("Ecchi", "ecchi"),
            Pair("Fan-fiction", "fan-fiction"),
            Pair("Fantasy", "fantasy"),
            Pair("Game", "game"),
            Pair("Gender bender", "gender-bender"),
            Pair("Harem", "harem"),
            Pair("Historical", "historical"),
            Pair("Horror", "horror"),
            Pair("Isekai", "isekai"),
            Pair("Josei", "josei"),
            Pair("Magic", "magic"),
            Pair("Magical realism", "magical-realism"),
            Pair("Manhua", "manhua"),
            Pair("Martial arts", "martial-arts"),
            Pair("Mature", "mature"),
            Pair("Mecha", "mecha"),
            Pair("Military", "military"),
            Pair("Modern life", "modern-life"),
            Pair("Movies", "movies"),
            Pair("Mystery", "mystery"),
            Pair("Psychological", "psychological"),
            Pair("Realistic fiction", "realistic-fiction"),
            Pair("Reincarnation", "reincarnation"),
            Pair("Romance", "romance"),
            Pair("School life", "school-life"),
            Pair("Sci-fi", "sci-fi"),
            Pair("Seinen", "seinen"),
            Pair("Shoujo", "shoujo"),
            Pair("Shoujo ai", "shoujo-ai"),
            Pair("Shounen", "shounen"),
            Pair("Shounen ai", "shounen-ai"),
            Pair("Slice of life", "slice-of-life"),
            Pair("Smut", "smut"),
            Pair("Sports", "sports"),
            Pair("Supernatural", "supernatural"),
            Pair("System", "system"),
            Pair("Tragedy", "tragedy"),
            Pair("Urban life", "urban-life"),
            Pair("Video games", "video-games"),
            Pair("War", "war"),
            Pair("Wuxia", "wuxia"),
            Pair("Xianxia", "xianxia"),
            Pair("Xuanhuan", "xuanhuan")
        )
    }
}
