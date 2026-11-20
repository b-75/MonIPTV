package com.moniptv.app

data class Category(
    val id: String,
    val name: String
)

data class Channel(
    val key: String,        // identifiant unique (sert pour les favoris)
    val name: String,
    val logo: String?,
    val categoryId: String,
    val url: String,
    val epgId: String?,
    val number: Int
)

data class Movie(
    val key: String,
    val name: String,
    val poster: String?,
    val categoryId: String,
    val url: String,
    val rating: String?
)

data class Series(
    val id: String,
    val name: String,
    val cover: String?,
    val categoryId: String,
    val plot: String?
)

data class Episode(
    val key: String,
    val title: String,
    val season: Int,
    val number: Int,
    val url: String,
    val plot: String?,
    val image: String?
)

data class Programme(
    val start: Long,        // millisecondes
    val end: Long,
    val title: String,
    val desc: String?
) {
    fun isNow(now: Long = System.currentTimeMillis()) = now in start until end
    fun progress(now: Long = System.currentTimeMillis()): Int {
        val total = (end - start).coerceAtLeast(1)
        return (((now - start) * 100) / total).toInt().coerceIn(0, 100)
    }
}

/** Configuration de la source IPTV saisie par l'utilisateur. */
data class SourceConfig(
    val type: String,           // "xtream" ou "m3u"
    val server: String = "",
    val username: String = "",
    val password: String = "",
    val m3uUrl: String = "",
    val epgUrl: String = "",
    val liveFormat: String = "ts"  // "ts" ou "m3u8" (Xtream)
) {
    val isValid: Boolean
        get() = when (type) {
            "xtream" -> server.isNotBlank() && username.isNotBlank() && password.isNotBlank()
            "m3u" -> m3uUrl.isNotBlank()
            else -> false
        }
}
