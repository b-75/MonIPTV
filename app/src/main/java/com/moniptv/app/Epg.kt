package com.moniptv.app

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Guide des programmes (format XMLTV). */
object Epg {

    @Volatile private var byXmlId: Map<String, List<Programme>> = emptyMap()
    @Volatile private var nameIndex: Map<String, String> = emptyMap()

    val isLoaded: Boolean get() = byXmlId.isNotEmpty()

    fun clear() {
        byXmlId = emptyMap()
        nameIndex = emptyMap()
    }

    /** Programmes d'une chaîne (triés), ou liste vide si inconnue. */
    fun programmesFor(channel: Channel): List<Programme> {
        val map = byXmlId
        if (map.isEmpty()) return emptyList()
        channel.epgId?.lowercase()?.let { id -> map[id]?.let { return it } }
        val xmlId = nameIndex[normalize(channel.name)] ?: return emptyList()
        return map[xmlId] ?: emptyList()
    }

    fun nowAndNext(channel: Channel): Pair<Programme?, Programme?> {
        val list = programmesFor(channel)
        val now = System.currentTimeMillis()
        val idx = list.indexOfFirst { it.isNow(now) }
        if (idx < 0) {
            return null to list.firstOrNull { it.start > now }
        }
        return list[idx] to list.getOrNull(idx + 1)
    }

    fun normalize(name: String): String {
        var n = name.lowercase(Locale.ROOT).trim()
        n = n.replace(Regex("""^[a-z]{2,3}\s*[:|\-]\s*"""), "")           // "FR: ", "FR | "
        n = n.replace(Regex("""\b(fhd|uhd|hd|sd|4k|hevc|h265|backup)\b"""), "")
        n = n.replace(Regex("""[^\p{L}\p{N}]"""), "")
        return n
    }

    /**
     * Lit un flux XMLTV en ne gardant que les chaînes présentes dans la liste
     * et les programmes entre -2h et +36h (pour ménager la mémoire de la box).
     */
    fun load(input: InputStream, channels: List<Channel>) {
        val wantedIds = channels.mapNotNull { it.epgId?.lowercase() }.toHashSet()
        val wantedNames = channels.map { normalize(it.name) }.toHashSet()

        val keepXmlIds = HashSet<String>()
        val names = HashMap<String, String>()
        val result = HashMap<String, MutableList<Programme>>()

        val now = System.currentTimeMillis()
        val minTime = now - 2 * 3600_000L
        val maxTime = now + 36 * 3600_000L

        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)

        var currentChannelId: String? = null
        var progChannel: String? = null
        var progStart = 0L
        var progEnd = 0L
        var progTitle: String? = null
        var progDesc: String? = null
        var inProgramme = false

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "channel" -> {
                        currentChannelId = parser.getAttributeValue(null, "id")?.lowercase()
                        val cid = currentChannelId
                        if (cid != null && cid in wantedIds) keepXmlIds.add(cid)
                    }
                    "display-name" -> {
                        val id = currentChannelId
                        if (id != null && !inProgramme) {
                            val n = normalize(safeText(parser))
                            if (n.isNotEmpty() && n in wantedNames) {
                                keepXmlIds.add(id)
                                names.putIfAbsent(n, id)
                            }
                        }
                    }
                    "programme" -> {
                        inProgramme = true
                        progChannel = parser.getAttributeValue(null, "channel")?.lowercase()
                        progStart = parseTime(parser.getAttributeValue(null, "start"))
                        progEnd = parseTime(parser.getAttributeValue(null, "stop"))
                        progTitle = null
                        progDesc = null
                    }
                    "title" -> {
                        if (inProgramme && progTitle == null) progTitle = safeText(parser)
                    }
                    "desc" -> {
                        if (inProgramme && progDesc == null) progDesc = safeText(parser)
                    }
                }
            } else if (event == XmlPullParser.END_TAG) {
                when (parser.name) {
                    "channel" -> currentChannelId = null
                    "programme" -> {
                        inProgramme = false
                        val ch = progChannel
                        if (ch != null && ch in keepXmlIds && progStart > 0 && progEnd > progStart &&
                            progEnd > minTime && progStart < maxTime
                        ) {
                            result.getOrPut(ch) { mutableListOf() }
                                .add(Programme(progStart, progEnd, progTitle ?: "Sans titre", progDesc))
                        }
                    }
                }
            }
            event = parser.next()
        }

        result.values.forEach { it.sortBy { p -> p.start } }
        byXmlId = result
        nameIndex = names
    }

    private fun safeText(parser: XmlPullParser): String =
        try { parser.nextText()?.trim() ?: "" } catch (e: Exception) { "" }

    private val fmtWithZone = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
    private val fmtNoZone = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private fun parseTime(value: String?): Long {
        if (value.isNullOrBlank()) return 0L
        val v = value.trim()
        return try {
            if (v.length > 14) fmtWithZone.parse(v)?.time ?: 0L
            else fmtNoZone.parse(v.take(14))?.time ?: 0L
        } catch (e: Exception) {
            try { fmtNoZone.parse(v.take(14))?.time ?: 0L } catch (e2: Exception) { 0L }
        }
    }
}
