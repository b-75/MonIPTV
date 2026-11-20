package com.moniptv.app

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

/** Lit une liste de lecture M3U / M3U8 (format #EXTINF). */
object M3uParser {

    class Result(
        val liveCategories: List<Category>,
        val channels: List<Channel>,
        val vodCategories: List<Category>,
        val movies: List<Movie>,
        val epgUrl: String?
    )

    private val attrRegex = Regex("""([\w-]+)="([^"]*)"""")
    private val vodExtensions = listOf(".mp4", ".mkv", ".avi", ".mov", ".wmv", ".m4v")

    fun parse(input: InputStream): Result {
        val reader = BufferedReader(InputStreamReader(input, Charsets.UTF_8), 64 * 1024)
        val channels = mutableListOf<Channel>()
        val movies = mutableListOf<Movie>()
        val liveGroups = LinkedHashSet<String>()
        val vodGroups = LinkedHashSet<String>()
        var epgUrl: String? = null

        var pendingName: String? = null
        var pendingAttrs: Map<String, String> = emptyMap()
        var pendingGroup: String? = null
        var index = 0

        reader.useLines { lines ->
            for (rawLine in lines) {
                val line = rawLine.trim()
                if (line.isEmpty()) continue
                when {
                    line.startsWith("#EXTM3U") -> {
                        val attrs = attributes(line)
                        epgUrl = attrs["url-tvg"] ?: attrs["x-tvg-url"]
                        epgUrl = epgUrl?.split(",")?.firstOrNull()?.trim()?.ifBlank { null }
                    }
                    line.startsWith("#EXTINF") -> {
                        pendingAttrs = attributes(line)
                        // Le nom est après la première virgule hors guillemets
                        pendingName = nameAfterComma(line)
                        pendingGroup = pendingAttrs["group-title"]
                    }
                    line.startsWith("#EXTGRP:") -> pendingGroup = line.substringAfter(":").trim()
                    line.startsWith("#") -> Unit
                    else -> {
                        val url = line
                        val name = (pendingName ?: pendingAttrs["tvg-name"] ?: "Chaîne ${index + 1}").trim()
                        val group = pendingGroup?.trim()?.ifBlank { null } ?: "Autres"
                        val lower = url.lowercase().substringBefore('?')
                        val isVod = lower.contains("/movie/") || lower.contains("/series/") ||
                            vodExtensions.any { lower.endsWith(it) }
                        if (isVod) {
                            vodGroups.add(group)
                            movies.add(
                                Movie(
                                    key = "m:${url.hashCode()}",
                                    name = name,
                                    poster = pendingAttrs["tvg-logo"]?.ifBlank { null },
                                    categoryId = group,
                                    url = url,
                                    rating = null
                                )
                            )
                        } else {
                            liveGroups.add(group)
                            channels.add(
                                Channel(
                                    key = "c:${url.hashCode()}:${name.hashCode()}",
                                    name = name,
                                    logo = pendingAttrs["tvg-logo"]?.ifBlank { null },
                                    categoryId = group,
                                    url = url,
                                    epgId = pendingAttrs["tvg-id"]?.ifBlank { null },
                                    number = pendingAttrs["tvg-chno"]?.toIntOrNull() ?: (channels.size + 1)
                                )
                            )
                        }
                        index++
                        pendingName = null
                        pendingAttrs = emptyMap()
                        pendingGroup = null
                    }
                }
            }
        }

        return Result(
            liveCategories = liveGroups.map { Category(it, it) },
            channels = channels,
            vodCategories = vodGroups.map { Category(it, it) },
            movies = movies,
            epgUrl = epgUrl
        )
    }

    private fun attributes(line: String): Map<String, String> =
        attrRegex.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }

    private fun nameAfterComma(line: String): String? {
        var inQuotes = false
        for (i in line.indices) {
            val c = line[i]
            if (c == '"') inQuotes = !inQuotes
            else if (c == ',' && !inQuotes) return line.substring(i + 1).trim().ifBlank { null }
        }
        return null
    }
}
