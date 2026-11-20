package com.moniptv.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Fiche d'une série : saisons et épisodes. */
class SeriesActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ID = "series_id"
    }

    private var episodes: List<Episode> = emptyList()
    private var currentSeason = 0
    private lateinit var seasonAdapter: RowAdapter
    private lateinit var episodeAdapter: RowAdapter
    private lateinit var seasonsView: RecyclerView
    private lateinit var episodesView: RecyclerView
    private var seriesName = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        setContentView(R.layout.activity_series)

        val id = intent.getStringExtra(EXTRA_ID)
        val series = id?.let { Repo.findSeries(it) }
        val cfg = Prefs.source
        if (series == null || cfg == null) { finish(); return }
        seriesName = series.name

        findViewById<TextView>(R.id.name).text = series.name
        findViewById<TextView>(R.id.plot).text = series.plot ?: ""
        val cover = findViewById<ImageView>(R.id.cover)
        if (!series.cover.isNullOrBlank()) cover.load(series.cover)

        seasonsView = findViewById(R.id.seasons)
        episodesView = findViewById(R.id.episodes)
        val loading = findViewById<ProgressBar>(R.id.loading)
        val empty = findViewById<TextView>(R.id.empty)

        seasonAdapter = RowAdapter(
            onFocus = { r -> showSeason(r.id.toInt()) },
            onClick = { r -> showSeason(r.id.toInt()); focusFirstEpisode() }
        )
        episodeAdapter = RowAdapter(onClick = { r -> play(r.id) })
        seasonsView.layoutManager = LinearLayoutManager(this)
        seasonsView.adapter = seasonAdapter
        episodesView.layoutManager = LinearLayoutManager(this)
        episodesView.adapter = episodeAdapter

        loading.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                episodes = withContext(Dispatchers.IO) { XtreamApi(cfg).episodes(series.id) }
            } catch (e: Throwable) {
                episodes = emptyList()
            }
            loading.visibility = View.GONE
            if (episodes.isEmpty()) {
                empty.text = "Aucun épisode trouvé."
                empty.visibility = View.VISIBLE
                return@launch
            }
            val seasons = episodes.map { it.season }.distinct().sorted()
            seasonAdapter.submit(seasons.map { RowItem(it.toString(), "Saison $it", "${episodes.count { e -> e.season == it }} épisodes") })
            showSeason(seasons.first())
            seasonsView.post { seasonsView.getChildAt(0)?.requestFocus() }
        }
    }

    override fun onResume() {
        super.onResume()
        if (currentSeason != 0) refreshEpisodes()
    }

    private fun showSeason(season: Int) {
        if (season == currentSeason) return
        currentSeason = season
        seasonAdapter.selectedId = season.toString()
        refreshEpisodes()
        episodesView.scrollToPosition(0)
    }

    private fun refreshEpisodes() {
        episodeAdapter.submit(
            episodes.filter { it.season == currentSeason }.map { e ->
                val resume = if (Prefs.getPosition(e.key) > 0) "▶ Reprendre  •  " else ""
                RowItem(e.key, "${e.number}. ${e.title}", resume + (e.plot ?: ""))
            }
        )
    }

    private fun focusFirstEpisode() {
        episodesView.post { episodesView.getChildAt(0)?.requestFocus() }
    }

    private fun play(key: String) {
        val e = episodes.firstOrNull { it.key == key } ?: return
        startActivity(
            Intent(this, PlayerActivity::class.java)
                .putExtra(PlayerActivity.EXTRA_MODE, PlayerActivity.MODE_VOD)
                .putExtra(PlayerActivity.EXTRA_KEY, e.key)
                .putExtra(PlayerActivity.EXTRA_URL, e.url)
                .putExtra(PlayerActivity.EXTRA_TITLE, "$seriesName – S${e.season}E${e.number}")
        )
    }
}
