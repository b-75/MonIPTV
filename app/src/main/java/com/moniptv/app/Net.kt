package com.moniptv.app

import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

object Net {
    const val USER_AGENT = "MonIPTV/1.0 (Android TV)"

    /** Ouvre une URL en suivant les redirections (y compris http <-> https). */
    @Throws(IOException::class)
    fun open(url: String, timeoutMs: Int = 20_000): InputStream {
        var current = url
        repeat(6) {
            val conn = URL(current).openConnection() as HttpURLConnection
            conn.connectTimeout = timeoutMs
            conn.readTimeout = 60_000
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept-Encoding", "gzip")
            val code = conn.responseCode
            if (code in 300..399) {
                val loc = conn.getHeaderField("Location") ?: throw IOException("Redirection sans adresse")
                current = URL(URL(current), loc).toString()
                conn.disconnect()
                return@repeat
            }
            if (code !in 200..299) {
                conn.disconnect()
                throw IOException("Erreur serveur HTTP $code")
            }
            val raw = BufferedInputStream(conn.inputStream, 64 * 1024)
            return maybeGunzip(raw)
        }
        throw IOException("Trop de redirections")
    }

    /** Décompresse automatiquement si le contenu est en gzip (ex. guides .xml.gz). */
    private fun maybeGunzip(input: BufferedInputStream): InputStream {
        input.mark(4)
        val b1 = input.read()
        val b2 = input.read()
        input.reset()
        return if (b1 == 0x1f && b2 == 0x8b) BufferedInputStream(GZIPInputStream(input, 64 * 1024)) else input
    }

    fun getText(url: String): String = open(url).use { it.readBytes().toString(Charsets.UTF_8) }
}
