package com.moniptv.app

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import coil.dispose
import coil.load

private fun ImageView.loadOrClear(url: String?) {
    if (url.isNullOrBlank()) {
        dispose()
        setImageDrawable(null)
    } else {
        load(url) { crossfade(false) }
    }
}

private fun isMenuKey(keyCode: Int) =
    keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_BOOKMARK ||
        keyCode == KeyEvent.KEYCODE_PROG_YELLOW

// ------------------------------------------------------------------ Catégories

class CategoryAdapter(
    private val onFocus: (Category) -> Unit,
    private val onClick: (Category) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.VH>() {

    private var items: List<Category> = emptyList()
    var selectedId: String? = null
        @SuppressLint("NotifyDataSetChanged")
        set(value) { field = value; notifyDataSetChanged() }

    init { setHasStableIds(true) }

    override fun getItemId(position: Int): Long = (items[position].id.hashCode().toLong() shl 20) xor position.toLong()

    class VH(val text: TextView) : RecyclerView.ViewHolder(text)

    @SuppressLint("NotifyDataSetChanged")
    fun submit(list: List<Category>) { items = list; notifyDataSetChanged() }

    fun indexOf(id: String?) = items.indexOfFirst { it.id == id }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_category, parent, false) as TextView)

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val c = items[position]
        holder.text.text = c.name
        holder.text.isActivated = c.id == selectedId
        holder.text.setOnFocusChangeListener { _, has -> if (has) onFocus(c) }
        holder.text.setOnClickListener { onClick(c) }
    }
}

// ------------------------------------------------------------------ Chaînes

class ChannelAdapter(
    private val onFocus: (Channel) -> Unit,
    private val onClick: (Channel, List<Channel>) -> Unit,
    private val onToggleFav: (Channel) -> Unit
) : RecyclerView.Adapter<ChannelAdapter.VH>() {

    var items: List<Channel> = emptyList()
        private set
    private var favorites: Set<String> = emptySet()

    // Identifiants stables : le focus de la télécommande ne saute pas quand la liste se rafraîchit
    init { setHasStableIds(true) }

    override fun getItemId(position: Int): Long = (items[position].key.hashCode().toLong() shl 20) xor position.toLong()

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val number: TextView = v.findViewById(R.id.number)
        val logo: ImageView = v.findViewById(R.id.logo)
        val name: TextView = v.findViewById(R.id.name)
        val now: TextView = v.findViewById(R.id.now)
        val progress: ProgressBar = v.findViewById(R.id.progress)
        val star: TextView = v.findViewById(R.id.star)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun submit(list: List<Channel>) {
        items = list
        favorites = Prefs.favorites()
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun refresh() {
        favorites = Prefs.favorites()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_channel, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val ch = items[position]
        h.number.text = ch.number.toString()
        h.name.text = ch.name
        h.logo.loadOrClear(ch.logo)
        val (now, _) = Epg.nowAndNext(ch)
        if (now != null) {
            h.now.text = now.title
            h.now.visibility = View.VISIBLE
            h.progress.progress = now.progress()
            h.progress.visibility = View.VISIBLE
        } else {
            h.now.visibility = View.GONE
            h.progress.visibility = View.GONE
        }
        h.star.visibility = if (ch.key in favorites) View.VISIBLE else View.GONE
        h.itemView.setOnFocusChangeListener { _, has -> if (has) onFocus(ch) }
        h.itemView.setOnClickListener { onClick(ch, items) }
        h.itemView.setOnLongClickListener { onToggleFav(ch); true }
        h.itemView.setOnKeyListener { _, keyCode, event ->
            if (isMenuKey(keyCode)) {
                if (event.action == KeyEvent.ACTION_UP) onToggleFav(ch)
                true
            } else false
        }
    }
}

// ------------------------------------------------------------------ Affiches (films / séries)

data class PosterItem(
    val id: String,
    val title: String,
    val image: String?,
    val badge: String? = null
)

class PosterAdapter(
    private val onClick: (PosterItem) -> Unit
) : RecyclerView.Adapter<PosterAdapter.VH>() {

    private var items: List<PosterItem> = emptyList()

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val image: ImageView = v.findViewById(R.id.image)
        val placeholder: TextView = v.findViewById(R.id.placeholder)
        val title: TextView = v.findViewById(R.id.title)
        val badge: TextView = v.findViewById(R.id.badge)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun submit(list: List<PosterItem>) { items = list; notifyDataSetChanged() }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_poster, parent, false)
        Ui.zoomOnFocus(v, 1.08f)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val item = items[position]
        h.title.text = item.title
        h.placeholder.text = item.title
        h.image.loadOrClear(item.image)
        if (item.badge != null) {
            h.badge.text = item.badge
            h.badge.visibility = View.VISIBLE
        } else {
            h.badge.visibility = View.GONE
        }
        h.itemView.setOnClickListener { onClick(item) }
    }
}

// ------------------------------------------------------------------ Lignes simples (saisons, épisodes)

data class RowItem(val id: String, val title: String, val subtitle: String? = null)

class RowAdapter(
    private val onFocus: (RowItem) -> Unit = {},
    private val onClick: (RowItem) -> Unit
) : RecyclerView.Adapter<RowAdapter.VH>() {

    private var items: List<RowItem> = emptyList()
    var selectedId: String? = null
        @SuppressLint("NotifyDataSetChanged")
        set(value) { field = value; notifyDataSetChanged() }

    init { setHasStableIds(true) }

    override fun getItemId(position: Int): Long = (items[position].id.hashCode().toLong() shl 20) xor position.toLong()

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.title)
        val subtitle: TextView = v.findViewById(R.id.subtitle)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun submit(list: List<RowItem>) { items = list; notifyDataSetChanged() }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_row, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val r = items[position]
        h.title.text = r.title
        h.subtitle.text = r.subtitle ?: ""
        h.subtitle.visibility = if (r.subtitle.isNullOrBlank()) View.GONE else View.VISIBLE
        h.itemView.isActivated = r.id == selectedId
        h.itemView.setOnFocusChangeListener { _, has -> if (has) onFocus(r) }
        h.itemView.setOnClickListener { onClick(r) }
    }
}

// ------------------------------------------------------------------ Guide d'une chaîne

class GuideAdapter : RecyclerView.Adapter<GuideAdapter.VH>() {

    private var items: List<Programme> = emptyList()

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val time: TextView = v.findViewById(R.id.time)
        val title: TextView = v.findViewById(R.id.title)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun submit(list: List<Programme>) { items = list; notifyDataSetChanged() }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_guide, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val p = items[position]
        h.time.text = Ui.hour(p.start)
        h.title.text = p.title
        val ctx = h.itemView.context
        if (p.isNow()) {
            h.title.setTypeface(null, Typeface.BOLD)
            h.title.setTextColor(ContextCompat.getColor(ctx, R.color.text))
        } else {
            h.title.setTypeface(null, Typeface.NORMAL)
            h.title.setTextColor(ContextCompat.getColor(ctx, R.color.text_dim))
        }
    }
}
