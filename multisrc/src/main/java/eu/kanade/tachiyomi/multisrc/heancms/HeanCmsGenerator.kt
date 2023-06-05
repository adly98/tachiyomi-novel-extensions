package eu.kanade.tachiyomi.multisrc.heancms

import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class HeanCmsGenerator : ThemeSourceGenerator {

    override val themePkg = "heancms"

    override val themeClass = "HeanCms"

    override val baseVersionCode: Int = 1

    override val sources = listOf(
        SingleLang("Reaper Scans (Novels)", "https://reaperscans.net", "pt-BR", className = "ReaperScansNovels"),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            HeanCmsGenerator().createAll()
        }
    }
}
