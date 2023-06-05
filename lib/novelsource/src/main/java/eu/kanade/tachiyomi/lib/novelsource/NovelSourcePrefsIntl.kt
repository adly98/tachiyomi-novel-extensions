package eu.kanade.tachiyomi.lib.novelsource

class NovelSourcePrefsIntl(lang: String) {

    val availableLang = if (lang in AVAILABLE_LANGS) lang else ENGLISH

    val prefCenteredTitle: String = when (availableLang) {
        BRAZILIAN_PORTUGUESE -> "Centralizar texto"
        else -> "Center text"
    }

    val prefFontSizeTitle: String = when (availableLang) {
        BRAZILIAN_PORTUGUESE -> "Tamanho da fonte"
        else -> "Font size"
    }

    val prefMarginTitle: String = when (availableLang) {
        BRAZILIAN_PORTUGUESE -> "Largura da margem"
        else -> "Margin width"
    }

    val prefHeightTitle: String = when (availableLang) {
        BRAZILIAN_PORTUGUESE -> "Altura das páginas"
        else -> "Page height"
    }

    val prefWidthTitle: String = when (availableLang) {
        BRAZILIAN_PORTUGUESE -> "Largura das páginas"
        else -> "Page width"
    }

    val prefThemeTitle: String = when (availableLang) {
        BRAZILIAN_PORTUGUESE -> "Tema preferido"
        else -> "Preferred theme"
    }

    val prefThemeEntries = when (availableLang) {
        BRAZILIAN_PORTUGUESE -> arrayOf("Preto", "Escuro", "Claro")
        else -> arrayOf("Black", "Dark", "White")
    }

    val prefResetTitle: String = when (availableLang) {
        BRAZILIAN_PORTUGUESE -> "Resetar preferências para o padrão"
        else -> "Reset preferences to default"
    }

    val reopenPreferencesText: String = when (availableLang) {
        BRAZILIAN_PORTUGUESE -> "Reabra as configurações da extensão."
        else -> "Re-open the extension preferences"
    }

    companion object {
        const val BRAZILIAN_PORTUGUESE = "pt-BR"
        const val ENGLISH = "en"

        val AVAILABLE_LANGS = arrayOf(BRAZILIAN_PORTUGUESE, ENGLISH)
    }
}
