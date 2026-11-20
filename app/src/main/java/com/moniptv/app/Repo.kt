package com.moniptv.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Toutes les données de la source, chargées une fois puis gardées en mémoire. */
object Repo {

    const val CAT_ALL = "__all__"
    const val CAT_FAV = "__fav__"

    var liveCategories: List<Category> = emptyList()
        private set
    var channels: List<Channel> = emptyList()
        private set
    var vodCategories: List<Category> = emptyList()
        private set
    var movies: List<Movie> = emptyList()
        private set
    var seriesCategories: List<Category> = emptyList()
        private set
    var series: List<Series> = emptyList()
        private set

    private var loadedFor: SourceConfig? = null
    private val mutex = Mutex()

    /** Incrémenté quand le guide des programmes est prêt (les écrans s'y abonnent). */
    private val _epgVersion = MutableStateFlow(0)
    val epgVersion: StateFlow<Int> = _epgVersion

    @Volatile
    var epgStatus: String = ""
        private set

    val isLoaded: Boolean get() = loadedFor != null

    fun reset() {
        loadedFor = null
        liveCategories = emptyList(); channels = emptyList()
        vodCategories = emptyList(); movies = emptyList()
        seriesCategories = emptyList(); series = emptyList()
        Epg.clear()
        epgStatus = ""
    }

    /** Charge les listes (chaînes, films, séries). Lève une exception en cas d'échec. */
    suspend fun load(cfg: SourceConfig, force: Boolean = false): Unit = mutex.withLock {
        if (!force && loadedFor == cfg) return@withLock
        withContext(Dispatchers.IO) {
            if (cfg.type == "xtream") {
                val api = XtreamApi(cfg)
                api.checkLogin()
                val lc = api.liveCategories()
                val ch = api.liveStreams()
                val vc = runCatching { api.vodCategories() }.getOrDefault(emptyList())
                val mv = runCatching { api.vodStreams() }.getOrDefault(emptyList())
                val sc = runCatching { api.seriesCategories() }.getOrDefault(emptyList())
                val se = runCatching { api.series() }.getOrDefault(emptyList())
                liveCategories = withOrphans(lc, ch.map { it.categoryId })
                channels = ch
                vodCategories = withOrphans(vc, mv.map { it.categoryId })
                movies = mv
                seriesCategories = withOrphans(sc, se.map { it.categoryId })
                series = se
            } else {
                val res = Net.open(cfg.m3uUrl.trim()).use { M3uParser.parse(it) }
                if (res.channels.isEmpty() && res.movies.isEmpty()) {
                    throw IllegalStateException("La liste est vide ou n'est pas au format M3U")
                }
                liveCategories = res.liveCategories
                channels = res.channels
                vodCategories = res.vodCategories
                movies = res.movies
                seriesCategories = emptyList()
                series = emptyList()
                m3uEpgUrl = res.epgUrl
            }
            loadedFor = cfg
        }
    }

    private var m3uEpgUrl: String? = null

    /** Ajoute une catégorie "Autres" pour les éléments rattachés à une catégorie inconnue. */
    private fun withOrphans(cats: List<Category>, used: List<String>): List<Category> {
        val known = cats.map { it.id }.toHashSet()
        val missing = used.filter { it !in known }.distinct()
        return if (missing.isEmpty()) cats else cats + missing.map { Category(it, if (it.isBlank()) "Autres" else "Catégorie $it") }
    }

    /** Charge le guide des programmes en arrière-plan. N'échoue jamais (le guide est facultatif). */
    suspend fun loadEpg(cfg: SourceConfig): Unit = withContext(Dispatchers.IO) {
        val custom = cfg.epgUrl.trim()
        val url: String? = when {
            custom.isNotEmpty() -> custom
            cfg.type == "xtream" -> XtreamApi(cfg).epgUrl()
            else -> m3uEpgUrl
        }
        try {
            if (url.isNullOrBlank()) {
                epgStatus = "Aucun guide des programmes configuré"
            } else {
                epgStatus = "Chargement du guide…"
                Net.open(url).use { Epg.load(it, channels) }
                epgStatus = if (Epg.isLoaded) "" else "Guide reçu, mais aucune chaîne correspondante"
            }
        } catch (e: Throwable) {
            epgStatus = "Guide indisponible"
        } finally {
            _epgVersion.value = _epgVersion.value + 1
        }
    }

    // ---------- Filtres ----------

    fun channelsIn(categoryId: String): List<Channel> = when (categoryId) {
        CAT_ALL -> channels
        CAT_FAV -> {
            val fav = Prefs.favorites()
            channels.filter { it.key in fav }
        }
        else -> channels.filter { it.categoryId == categoryId }
    }

    fun moviesIn(categoryId: String): List<Movie> =
        if (categoryId == CAT_ALL) movies else movies.filter { it.categoryId == categoryId }

    fun seriesIn(categoryId: String): List<Series> =
        if (categoryId == CAT_ALL) series else series.filter { it.categoryId == categoryId }

    fun findSeries(id: String): Series? = series.firstOrNull { it.id == id }

    fun findChannel(key: String): Channel? = channels.firstOrNull { it.key == key }
}
