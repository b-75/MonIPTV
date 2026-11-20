package com.moniptv.app

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** Catalogue de films ou de séries : catégories à gauche, affiches à droite. */
class VodActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_MOVIES = "movies"
        const val MODE_SERIES = "series"
    }

    private var mode = MODE_MOVIES
    private lateinit var catAdapter: CategoryAdapter
    private lateinit var posterAdapter: PosterAdapter
    private lateinit var grid: RecyclerView
    private lateinit var categoriesView: RecyclerView
    private lateinit var count: TextView
    private lateinit var empty: TextView
    private lateinit var clock: Clock
    private var currentCategory = Repo.CAT_ALL
    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        setContentView(R.layout.activity_vod)
        if (!Repo.isLoaded) { finish(); return }

        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_MOVIES
        findViewById<TextView>(R.id.title).text =
            getString(if (mode == MODE_SERIES) R.string.menu_series else R.string.menu_movies)
        count = findViewById(R.id.count)
        empty = findViewById(R.id.empty)
        clock = Clock(findViewById(R.id.clock))
        grid = findViewById(R.id.grid)
        categoriesView = findViewById(R.id.categories)

        catAdapter = CategoryAdapter(
            onFocus = { c -> schedule(c.id) },
            onClick = { c -> show(c.id); grid.post { grid.getChildAt(0)?.requestFocus() } }
        )
        posterAdapter = PosterAdapter { item -> openItem(item) }

        categoriesView.layoutManager = LinearLayoutManager(this)
        categoriesView.adapter = catAdapter
        grid.layoutManager = GridLayoutManager(this, 5)
        grid.adapter = posterAdapter

        val cats = if (mode == MODE_SERIES) Repo.seriesCategories else Repo.vodCategories
        val total = if (mode == MODE_SERIES) Repo.series.size else Repo.movies.size
        catAdapter.submit(listOf(Category(Repo.CAT_ALL, "Tout ($total)")) + cats)
        show(Repo.CAT_ALL)
        categoriesView.post { categoriesView.getChildAt(0)?.requestFocus() }

        if (total == 0) {
            empty.text = if (mode == MODE_SERIES && Prefs.source?.type == "m3u")
                "Les séries sont disponibles avec une connexion par identifiants (Xtream).\nLes séries de ta liste M3U apparaissent dans « Films »."
            else "Rien à afficher ici pour l'instant."
            empty.visibility = View.VISIBLE
        }
    }

    override fun onResume() {
        super.onResume()
        clock.start()
    }

    override fun onPause() {
        super.onPause()
        clock.stop()
    }

    private fun schedule(id: String) {
        pending?.let { handler.removeCallbacks(it) }
        val r = Runnable { if (id != currentCategory) show(id) }
        pending = r
        handler.postDelayed(r, 200)
    }

    private fun show(id: String) {
        currentCategory = id
        catAdapter.selectedId = id
        val items: List<PosterItem> = if (mode == MODE_SERIES) {
            Repo.seriesIn(id).map { PosterItem(it.id, it.name, it.cover) }
        } else {
            Repo.moviesIn(id).map { m ->
                val resume = Prefs.getPosition(m.key) > 0
                PosterItem(m.key, m.name, m.poster, badge = if (resume) "Reprendre" else m.rating?.let { r -> "★ $r" })
            }
        }
        posterAdapter.submit(items)
        grid.scrollToPosition(0)
        count.text = "${items.size} ${if (mode == MODE_SERIES) "séries" else "films"}"
    }

    private fun openItem(item: PosterItem) {
        if (mode == MODE_SERIES) {
            startActivity(Intent(this, SeriesActivity::class.java).putExtra(SeriesActivity.EXTRA_ID, item.id))
        } else {
            val m = Repo.movies.firstOrNull { it.key == item.id } ?: return
            startActivity(
                Intent(this, PlayerActivity::class.java)
                    .putExtra(PlayerActivity.EXTRA_MODE, PlayerActivity.MODE_VOD)
                    .putExtra(PlayerActivity.EXTRA_KEY, m.key)
                    .putExtra(PlayerActivity.EXTRA_URL, m.url)
                    .putExtra(PlayerActivity.EXTRA_TITLE, m.name)
            )
        }
    }
}
