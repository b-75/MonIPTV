package com.moniptv.app

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/** Client pour les serveurs au format "Xtream Codes" (identifiant + mot de passe). */
class XtreamApi(private val cfg: SourceConfig) {

    private val base: String = cfg.server.trim().trimEnd('/').let {
        if (it.startsWith("http://") || it.startsWith("https://")) it else "http://$it"
    }
    private val user = cfg.username.trim()
    private val pass = cfg.password.trim()

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun api(action: String?, extra: String = ""): String {
        val a = if (action != null) "&action=$action" else ""
        return Net.getText("$base/player_api.php?username=${enc(user)}&password=${enc(pass)}$a$extra")
    }

    private fun array(action: String): JSONArray {
        val txt = api(action).trim()
        return if (txt.startsWith("[")) JSONArray(txt) else JSONArray()
    }

    /** Vérifie les identifiants. Lève une exception avec un message clair si ça ne va pas. */
    fun checkLogin() {
        val txt = api(null).trim()
        if (!txt.startsWith("{")) throw IllegalStateException("Réponse inattendue du serveur")
        val info = JSONObject(txt).optJSONObject("user_info")
            ?: throw IllegalStateException("Identifiants refusés")
        if (info.optInt("auth", 1) == 0) throw IllegalStateException("Identifiant ou mot de passe incorrect")
        val status = info.optString("status", "Active")
        if (!status.equals("Active", ignoreCase = true)) {
            throw IllegalStateException("Compte non actif ($status)")
        }
    }

    private fun categories(action: String): List<Category> {
        val arr = array(action)
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            Category((o.str("category_id") ?: ""), o.optString("category_name").trim())
        }
    }

    fun liveCategories() = categories("get_live_categories")
    fun vodCategories() = categories("get_vod_categories")
    fun seriesCategories() = categories("get_series_categories")

    fun liveStreams(): List<Channel> {
        val arr = array("get_live_streams")
        val ext = if (cfg.liveFormat == "m3u8") "m3u8" else "ts"
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.str("stream_id") ?: return@mapNotNull null
            Channel(
                key = "x:$id",
                name = o.optString("name").trim(),
                logo = o.str("stream_icon"),
                categoryId = (o.str("category_id") ?: ""),
                url = "$base/live/${enc(user)}/${enc(pass)}/$id.$ext",
                epgId = o.str("epg_channel_id"),
                number = o.optInt("num", i + 1)
            )
        }
    }

    fun vodStreams(): List<Movie> {
        val arr = array("get_vod_streams")
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.str("stream_id") ?: return@mapNotNull null
            val ext = o.str("container_extension") ?: "mp4"
            Movie(
                key = "xm:$id",
                name = o.optString("name").trim(),
                poster = o.str("stream_icon"),
                categoryId = (o.str("category_id") ?: ""),
                url = "$base/movie/${enc(user)}/${enc(pass)}/$id.$ext",
                rating = o.str("rating")?.takeIf { it != "0" && it != "0.0" }
            )
        }
    }

    fun series(): List<Series> {
        val arr = array("get_series")
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.str("series_id") ?: return@mapNotNull null
            Series(
                id = id,
                name = o.optString("name").trim(),
                cover = o.str("cover"),
                categoryId = (o.str("category_id") ?: ""),
                plot = o.str("plot")
            )
        }
    }

    fun episodes(seriesId: String): List<Episode> {
        val txt = api("get_series_info", "&series_id=${enc(seriesId)}").trim()
        if (!txt.startsWith("{")) return emptyList()
        val root = JSONObject(txt)
        val result = mutableListOf<Episode>()
        // "episodes" peut être un objet {"1":[...],"2":[...]} ou parfois un tableau de tableaux
        val eps = root.opt("episodes")
        val seasonArrays = mutableListOf<JSONArray>()
        when (eps) {
            is JSONObject -> eps.keys().forEach { k -> eps.optJSONArray(k)?.let { seasonArrays.add(it) } }
            is JSONArray -> for (i in 0 until eps.length()) eps.optJSONArray(i)?.let { seasonArrays.add(it) }
        }
        for (arr in seasonArrays) {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.str("id") ?: continue
                val ext = o.str("container_extension") ?: "mp4"
                val info = o.optJSONObject("info")
                result.add(
                    Episode(
                        key = "xe:$id",
                        title = o.optString("title").trim(),
                        season = o.optString("season").toIntOrNull() ?: 1,
                        number = o.optString("episode_num").toIntOrNull() ?: (i + 1),
                        url = "$base/series/${enc(user)}/${enc(pass)}/$id.$ext",
                        plot = info?.str("plot"),
                        image = info?.str("movie_image")
                    )
                )
            }
        }
        return result.sortedWith(compareBy({ it.season }, { it.number }))
    }

    fun epgUrl(): String = "$base/xmltv.php?username=${enc(user)}&password=${enc(pass)}"
}

/** Lit un champ texte JSON en ignorant les valeurs vides ou "null". */
internal fun JSONObject.str(name: String): String? {
    if (!has(name) || isNull(name)) return null
    val v = optString(name).trim()
    return if (v.isEmpty() || v == "null") null else v
}
