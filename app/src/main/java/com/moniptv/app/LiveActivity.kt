package com.moniptv.app

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class LiveActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CATEGORY = "category"
    }

    private lateinit var categoriesView: RecyclerView
    private lateinit var channelsView: RecyclerView
    private lateinit var guideView: RecyclerView
    private lateinit var catAdapter: CategoryAdapter
    private lateinit var chAdapter: ChannelAdapter
    private val guideAdapter = GuideAdapter()

    private lateinit var infoLogo: ImageView
    private lateinit var infoName: TextView
    private lateinit var infoNowLabel: TextView
    private lateinit var infoNowTitle: TextView
    private lateinit var infoNowTime: TextView
    private lateinit var infoProgress: ProgressBar
    private lateinit var infoDesc: TextView
    private lateinit var clock: Clock

    private var currentCategory = Repo.CAT_ALL
    private var currentChannel: Channel? = null
    private val handler = Handler(Looper.getMainLooper())
    private var pendingCategory: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        setContentView(R.layout.activity_live)
        if (!Repo.isLoaded) { finish(); return }

        categoriesView = findViewById(R.id.categories)
        channelsView = findViewById(R.id.channels)
        guideView = findViewById(R.id.guide)
        infoLogo = findViewById(R.id.infoLogo)
        infoName = findViewById(R.id.infoName)
        infoNowLabel = findViewById(R.id.infoNowLabel)
        infoNowTitle = findViewById(R.id.infoNowTitle)
        infoNowTime = findViewById(R.id.infoNowTime)
        infoProgress = findViewById(R.id.infoProgress)
        infoDesc = findViewById(R.id.infoDesc)
        clock = Clock(findViewById(R.id.clock))

        catAdapter = CategoryAdapter(
            onFocus = { c -> scheduleCategory(c.id) },
            onClick = { c -> showCategory(c.id); focusChannel(0) }
        )
        chAdapter = ChannelAdapter(
            onFocus = { ch -> currentChannel = ch; updateInfo(ch) },
            onClick = { ch, _ -> play(ch) },
            onToggleFav = { ch -> toggleFavorite(ch) }
        )

        categoriesView.layoutManager = LinearLayoutManager(this)
        categoriesView.adapter = catAdapter
        channelsView.layoutManager = LinearLayoutManager(this)
        channelsView.adapter = chAdapter
        channelsView.itemAnimator = null
        guideView.layoutManager = LinearLayoutManager(this)
        guideView.adapter = guideAdapter
        guideView.isFocusable = false
        guideView.descendantFocusability = RecyclerView.FOCUS_BLOCK_DESCENDANTS

        catAdapter.submit(buildCategories())

        currentCategory = intent.getStringExtra(EXTRA_CATEGORY) ?: Repo.CAT_ALL
        showCategory(currentCategory)

        // Positionne le focus sur la dernière chaîne regardée si elle est dans la liste
        val lastKey = Prefs.lastChannelKey
        val idx = chAdapter.items.indexOfFirst { it.key == lastKey }.coerceAtLeast(0)
        if (chAdapter.items.isEmpty()) focusCategory() else focusChannel(idx)

        // Quand le guide des programmes arrive, on rafraîchit l'affichage
        lifecycleScope.launch {
            Repo.epgVersion.drop(1).collect {
                chAdapter.refresh()
                currentChannel?.let { updateInfo(it) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        clock.start()
        if (::chAdapter.isInitialized) {
            if (currentCategory == Repo.CAT_FAV) {
                chAdapter.submit(Repo.channelsIn(Repo.CAT_FAV))
            } else {
                chAdapter.refresh()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        clock.stop()
    }

    private fun buildCategories(): List<Category> {
        val favCount = Repo.channelsIn(Repo.CAT_FAV).size
        val list = mutableListOf(
            Category(Repo.CAT_FAV, "⭐ Favoris ($favCount)"),
            Category(Repo.CAT_ALL, "Toutes les chaînes (${Repo.channels.size})")
        )
        list.addAll(Repo.liveCategories)
        return list
    }

    /** Petite attente avant de changer de liste, pour que le défilement des catégories reste fluide. */
    private fun scheduleCategory(id: String) {
        pendingCategory?.let { handler.removeCallbacks(it) }
        val r = Runnable { if (id != currentCategory) showCategory(id) }
        pendingCategory = r
        handler.postDelayed(r, 180)
    }

    private fun showCategory(id: String) {
        currentCategory = id
        catAdapter.selectedId = id
        val list = Repo.channelsIn(id)
        chAdapter.submit(list)
        channelsView.scrollToPosition(0)
        list.firstOrNull()?.let { updateInfo(it) } ?: clearInfo(
            if (id == Repo.CAT_FAV) "Aucun favori pour l'instant.\nSur une chaîne, fais un appui long sur OK pour l'ajouter."
            else "Aucune chaîne dans cette catégorie."
        )
    }

    private fun focusChannel(index: Int) {
        if (chAdapter.itemCount == 0) return
        val i = index.coerceIn(0, chAdapter.itemCount - 1)
        channelsView.scrollToPosition(i)
        channelsView.post {
            val vh = channelsView.findViewHolderForAdapterPosition(i)
            if (vh != null) vh.itemView.requestFocus() else channelsView.requestFocus()
        }
    }

    private fun focusCategory() {
        val i = catAdapter.indexOf(currentCategory).coerceAtLeast(0)
        categoriesView.scrollToPosition(i)
        categoriesView.post {
            categoriesView.findViewHolderForAdapterPosition(i)?.itemView?.requestFocus()
        }
    }

    /** Gauche/droite : on passe directement de la catégorie choisie à ses chaînes, et inversement. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val focused = currentFocus
            if (focused != null) {
                if (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT && isInside(focused, channelsView)) {
                    focusCategory(); return true
                }
                if (event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && isInside(focused, categoriesView)) {
                    pendingCategory?.let { handler.removeCallbacks(it); it.run() }
                    val idx = chAdapter.items.indexOfFirst { it.key == currentChannel?.key }.coerceAtLeast(0)
                    focusChannel(idx); return true
                }
                if (event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && isInside(focused, channelsView)) {
                    return true // pas de focus dans le panneau d'infos
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun isInside(view: View, parent: View): Boolean {
        var v: View? = view
        while (v != null) {
            if (v === parent) return true
            v = v.parent as? View
        }
        return false
    }

    private fun updateInfo(ch: Channel) {
        infoName.text = ch.name
        if (ch.logo.isNullOrBlank()) infoLogo.setImageDrawable(null) else infoLogo.load(ch.logo)
        val list = Epg.programmesFor(ch)
        val (now, _) = Epg.nowAndNext(ch)
        if (now != null) {
            infoNowLabel.visibility = View.VISIBLE
            infoNowTitle.text = now.title
            infoNowTime.text = Ui.range(now)
            infoProgress.visibility = View.VISIBLE
            infoProgress.progress = now.progress()
            infoDesc.text = now.desc ?: ""
        } else {
            infoNowLabel.visibility = View.GONE
            infoNowTitle.text = ""
            infoNowTime.text = if (Repo.epgStatus.isNotEmpty()) Repo.epgStatus else "Pas d'informations de programme"
            infoProgress.visibility = View.GONE
            infoDesc.text = ""
        }
        val nowMs = System.currentTimeMillis()
        guideAdapter.submit(list.filter { it.end > nowMs }.take(12))
    }

    private fun clearInfo(message: String) {
        currentChannel = null
        infoName.text = ""
        infoLogo.setImageDrawable(null)
        infoNowLabel.visibility = View.GONE
        infoNowTitle.text = message
        infoNowTime.text = ""
        infoProgress.visibility = View.GONE
        infoDesc.text = ""
        guideAdapter.submit(emptyList())
    }

    private fun toggleFavorite(ch: Channel) {
        val fav = Prefs.toggleFavorite(ch.key)
        Ui.toast(this, if (fav) "⭐ ${ch.name} ajoutée aux favoris" else "${ch.name} retirée des favoris")
        catAdapter.submit(buildCategories())
        if (currentCategory == Repo.CAT_FAV) {
            val pos = chAdapter.items.indexOfFirst { it.key == ch.key }
            chAdapter.submit(Repo.channelsIn(Repo.CAT_FAV))
            if (chAdapter.itemCount == 0) {
                clearInfo("Aucun favori pour l'instant.")
                focusCategory()
            } else {
                focusChannel(pos)
            }
        } else {
            chAdapter.refresh()
        }
    }

    private fun play(ch: Channel) {
        Prefs.lastChannelKey = ch.key
        startActivity(
            Intent(this, PlayerActivity::class.java)
                .putExtra(PlayerActivity.EXTRA_MODE, PlayerActivity.MODE_LIVE)
                .putExtra(PlayerActivity.EXTRA_CATEGORY, currentCategory)
                .putExtra(PlayerActivity.EXTRA_KEY, ch.key)
        )
    }
}
