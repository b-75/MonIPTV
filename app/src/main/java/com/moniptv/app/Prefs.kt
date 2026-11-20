package com.moniptv.app

import android.content.Context
import android.content.SharedPreferences

/** Stockage local : source IPTV, favoris, reprise de lecture. Rien ne quitte la box. */
object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        if (!::sp.isInitialized) {
            sp = context.applicationContext.getSharedPreferences("moniptv", Context.MODE_PRIVATE)
        }
    }

    // ---------- Source ----------
    var source: SourceConfig?
        get() {
            val type = sp.getString("type", null) ?: return null
            return SourceConfig(
                type = type,
                server = sp.getString("server", "") ?: "",
                username = sp.getString("username", "") ?: "",
                password = sp.getString("password", "") ?: "",
                m3uUrl = sp.getString("m3uUrl", "") ?: "",
                epgUrl = sp.getString("epgUrl", "") ?: "",
                liveFormat = sp.getString("liveFormat", "ts") ?: "ts"
            )
        }
        set(value) {
            val e = sp.edit()
            if (value == null) {
                e.remove("type")
            } else {
                e.putString("type", value.type)
                    .putString("server", value.server)
                    .putString("username", value.username)
                    .putString("password", value.password)
                    .putString("m3uUrl", value.m3uUrl)
                    .putString("epgUrl", value.epgUrl)
                    .putString("liveFormat", value.liveFormat)
            }
            e.apply()
        }

    // ---------- Favoris ----------
    fun favorites(): Set<String> = sp.getStringSet("favorites", emptySet())?.toSet() ?: emptySet()

    fun isFavorite(key: String) = favorites().contains(key)

    /** Ajoute ou retire un favori. Renvoie true si la chaîne est maintenant en favori. */
    fun toggleFavorite(key: String): Boolean {
        val set = favorites().toMutableSet()
        val nowFav = if (set.contains(key)) { set.remove(key); false } else { set.add(key); true }
        sp.edit().putStringSet("favorites", set).apply()
        return nowFav
    }

    // ---------- Dernière chaîne regardée ----------
    var lastChannelKey: String?
        get() = sp.getString("lastChannel", null)
        set(v) { sp.edit().putString("lastChannel", v).apply() }

    // ---------- Reprise de lecture (films / épisodes) ----------
    private fun posKey(key: String) = "pos_" + key.hashCode()

    fun savePosition(key: String, positionMs: Long, durationMs: Long) {
        val e = sp.edit()
        // Si on est à moins de 3 min de la fin, on considère que c'est terminé
        if (durationMs > 0 && positionMs > durationMs - 180_000) {
            e.remove(posKey(key))
        } else if (positionMs > 30_000) {
            e.putLong(posKey(key), positionMs)
        }
        e.apply()
    }

    fun getPosition(key: String): Long = sp.getLong(posKey(key), 0L)
}
