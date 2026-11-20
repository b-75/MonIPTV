package com.moniptv.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar

/** Tâches qui doivent continuer même quand on change d'écran (ex. chargement du guide). */
object AppScope : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.Main)

class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private lateinit var greeting: TextView
    private lateinit var loading: ProgressBar
    private lateinit var clock: Clock
    private var loadingNow = false

    private data class Tile(val view: View, val icon: String, val label: String)

    private lateinit var tileLive: View
    private lateinit var tileMovies: View
    private lateinit var tileSeries: View
    private lateinit var tileFav: View
    private lateinit var tileSettings: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        setContentView(R.layout.activity_main)

        status = findViewById(R.id.status)
        greeting = findViewById(R.id.greeting)
        loading = findViewById(R.id.loading)
        clock = Clock(findViewById(R.id.clock))

        tileLive = findViewById(R.id.tileLive)
        tileMovies = findViewById(R.id.tileMovies)
        tileSeries = findViewById(R.id.tileSeries)
        tileFav = findViewById(R.id.tileFav)
        tileSettings = findViewById(R.id.tileSettings)

        // ====== PERSONNALISATION : icônes et noms des tuiles de l'accueil ======
        listOf(
            Tile(tileLive, "📺", getString(R.string.menu_live)),
            Tile(tileMovies, "🎬", getString(R.string.menu_movies)),
            Tile(tileSeries, "🍿", getString(R.string.menu_series)),
            Tile(tileFav, "⭐", getString(R.string.menu_favorites)),
            Tile(tileSettings, "⚙️", getString(R.string.menu_settings))
        ).forEach { t ->
            t.view.findViewById<TextView>(R.id.tileIcon).text = t.icon
            t.view.findViewById<TextView>(R.id.tileLabel).text = t.label
            Ui.zoomOnFocus(t.view, 1.08f)
        }

        tileLive.setOnClickListener { openIfReady(Intent(this, LiveActivity::class.java)) }
        tileFav.setOnClickListener {
            openIfReady(Intent(this, LiveActivity::class.java).putExtra(LiveActivity.EXTRA_CATEGORY, Repo.CAT_FAV))
        }
        tileMovies.setOnClickListener {
            openIfReady(Intent(this, VodActivity::class.java).putExtra(VodActivity.EXTRA_MODE, VodActivity.MODE_MOVIES))
        }
        tileSeries.setOnClickListener {
            openIfReady(Intent(this, VodActivity::class.java).putExtra(VodActivity.EXTRA_MODE, VodActivity.MODE_SERIES))
        }
        tileSettings.setOnClickListener { startActivity(Intent(this, SetupActivity::class.java)) }

        tileLive.requestFocus()

        if (Prefs.source?.isValid != true) {
            startActivity(Intent(this, SetupActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        clock.start()
        greeting.text = hello()
        val cfg = Prefs.source
        if (cfg == null || !cfg.isValid) {
            status.text = "Commence par configurer ta source dans « Réglages »."
            updateCounts()
            return
        }
        if (Repo.isLoaded) {
            updateCounts()
            status.text = Repo.epgStatus
        } else {
            loadAll(cfg)
        }
    }

    override fun onPause() {
        super.onPause()
        clock.stop()
    }

    private fun loadAll(cfg: SourceConfig) {
        if (loadingNow) return
        loadingNow = true
        loading.visibility = View.VISIBLE
        status.text = "Connexion au serveur…"
        lifecycleScope.launch {
            try {
                Repo.load(cfg)
                status.text = ""
                updateCounts()
                // Le guide se charge en arrière-plan, l'appli reste utilisable
                AppScope.launch { Repo.loadEpg(cfg) }
            } catch (e: Throwable) {
                status.text = "Impossible de charger les listes : ${e.message ?: e.javaClass.simpleName}\n" +
                    "Vérifie tes informations dans « Réglages »."
                tileSettings.requestFocus()
            } finally {
                loading.visibility = View.GONE
                loadingNow = false
            }
        }
    }

    private fun updateCounts() {
        fun count(v: View, n: Int, word: String) {
            v.findViewById<TextView>(R.id.tileCount).text = if (Repo.isLoaded) "$n $word" else ""
        }
        count(tileLive, Repo.channels.size, "chaînes")
        count(tileMovies, Repo.movies.size, "films")
        count(tileSeries, Repo.series.size, "séries")
        count(tileFav, Repo.channelsIn(Repo.CAT_FAV).size, "favoris")
        tileSettings.findViewById<TextView>(R.id.tileCount).text = ""
    }

    private fun openIfReady(intent: Intent) {
        if (!Repo.isLoaded) {
            Ui.toast(this, if (loadingNow) "Chargement en cours, patiente un instant…" else "Configure d'abord ta source")
            return
        }
        startActivity(intent)
    }

    private fun hello(): String {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (h) {
            in 5..11 -> "Bonjour 👋  Qu'est-ce qu'on regarde ?"
            in 12..17 -> "Bon après-midi 👋  Qu'est-ce qu'on regarde ?"
            else -> "Bonsoir 👋  Installe-toi, c'est l'heure de la télé."
        }
    }
}
