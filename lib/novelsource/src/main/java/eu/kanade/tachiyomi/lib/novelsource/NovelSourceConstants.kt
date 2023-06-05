package eu.kanade.tachiyomi.lib.novelsource

import org.claudemirovsky.noveltomanga.DefaultThemes

const val NOVELTOMANGA_HOST = "noveltomanga"

const val PREF_CENTERED_KEY = "centered_pref"
const val PREF_CENTERED_DEFAULT = false

const val PREF_FONTSIZE_KEY = "fontsize_pref"
const val PREF_FONTSIZE_DEFAULT = 30
const val PREF_FONTSIZE_MIN = 10
const val PREF_FONTSIZE_MAX = 100

const val PREF_MARGIN_KEY = "margin_pref"
const val PREF_MARGIN_DEFAULT = 30
const val PREF_MARGIN_MIN = 20
const val PREF_MARGIN_MAX = 500

const val PREF_HEIGHT_KEY = "pageheight_pref"
const val PREF_HEIGHT_DEFAULT = 1536

const val PREF_WIDTH_KEY = "pagewidth_pref"
const val PREF_WIDTH_DEFAULT = 1080

const val PREF_PAGESIZE_MIN = 240
const val PREF_PAGESIZE_MAX = 4096

const val PREF_RESET_KEY = "reset_pref"
const val PREF_RESET_DEFAULT = false

const val PREF_THEME_KEY = "theme_pref"
const val PREF_THEME_DEFAULT = "DARK"
val PREF_THEME_ENTRIES = arrayOf("Preto", "Escuro", "Claro")
val PREF_THEME_VALUES = enumValues<DefaultThemes>()
    .map { it.name }
    .toTypedArray()
