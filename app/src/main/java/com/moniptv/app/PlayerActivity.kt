package com.moniptv.app

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil.load

@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MODE = "mode"
        const val EXTRA_KEY = "key"
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_CATEGORY = "category"
        const val MODE_LIVE = "live"
        const val MODE_VOD = "vod"
    }

    private lateinit var playerView: PlayerView
    private lateinit var overlay: View
    private lateinit var message: TextView
    private var player: ExoPlayer? = null

    private var mode = MODE_LIVE
    private var channels: List<Channel> = emptyList()
    private var index = 0

    // Lecture film / épisode
    private var vodKey = ""
    private var vodUrl = ""
    private var vodTitle = ""

    private val handler = Handler(Looper.getMainLooper())
    private val hideOverlay = Runnable { overlay.visibility = View.GONE }
    private val hideMessage = Runnable { message.visibility = View.GONE }
    private var typedNumber = ""
    private val jumpToNumber = Runnable { applyTypedNumber() }
    private var retries = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_player)
        playerView = findViewById(R.id.playerView)
        overlay = findViewById(R.id.overlay)
        message = findViewById(R.id.message)

        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_LIVE
        if (mode == MODE_LIVE) {
            val cat = intent.getStringExtra(EXTRA_CATEGORY) ?: Repo.CAT_ALL
            channels = Repo.channelsIn(cat).ifEmpty { Repo.channels }
            val key = intent.getStringExtra(EXTRA_KEY)
            index = channels.indexOfFirst { it.key == key }.coerceAtLeast(0)
            if (channels.isEmpty()) { finish(); return }
            playerView.useController = false
        } else {
            vodKey = intent.getStringExtra(EXTRA_KEY) ?: ""
            vodUrl = intent.getStringExtra(EXTRA_URL) ?: ""
            vodTitle = intent.getStringExtra(EXTRA_TITLE) ?: ""
            if (vodUrl.isEmpty()) { finish(); return }
            playerView.useController = true
            playerView.controllerShowTimeoutMs = 4000
        }
    }

    override fun onStart() {
        super.onStart()
        if (mode == MODE_LIVE && channels.isEmpty()) return
        initPlayer()
        if (mode == MODE_LIVE) playChannel(index) else playVod()
    }

    override fun onStop() {
        super.onStop()
        savePosition()
        player?.release()
        player = null
    }

    private fun initPlayer() {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(Net.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
        val p = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(http))
            .build()
        p.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    retries = 0
                    message.visibility = View.GONE
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    p.seekToDefaultPosition()
                    p.prepare()
                    return
                }
                // Une coupure réseau ? On réessaie automatiquement 2 fois
                if (retries < 2) {
                    retries++
                    showMessage("Connexion perdue, nouvelle tentative…", 3000)
                    handler.postDelayed({ player?.prepare(); player?.play() }, 2000)
                } else {
                    showMessage(
                        if (mode == MODE_LIVE) "Cette chaîne ne répond pas.\nEssaie une autre chaîne, ou change le format dans Réglages."
                        else "Impossible de lire cette vidéo.", 6000
                    )
                }
            }
        })
        playerView.player = p
        player = p
    }

    private fun mediaItem(url: String): MediaItem {
        val b = MediaItem.Builder().setUri(url)
        if (url.lowercase().contains(".m3u8")) b.setMimeType(MimeTypes.APPLICATION_M3U8)
        return b.build()
    }

    // ------------------------------------------------------------ TV en direct

    private fun playChannel(i: Int) {
        if (channels.isEmpty()) return
        index = ((i % channels.size) + channels.size) % channels.size
        val ch = channels[index]
        Prefs.lastChannelKey = ch.key
        retries = 0
        message.visibility = View.GONE
        player?.apply {
            setMediaItem(mediaItem(ch.url))
            prepare()
            play()
        }
        showOverlay()
    }

    private fun showOverlay() {
        if (mode != MODE_LIVE || channels.isEmpty()) return
        val ch = channels[index]
        findViewById<TextView>(R.id.ovNumber).text = ch.number.toString()
        findViewById<TextView>(R.id.ovName).text =
            if (Prefs.isFavorite(ch.key)) "⭐ ${ch.name}" else ch.name
        val logo = findViewById<ImageView>(R.id.ovLogo)
        if (ch.logo.isNullOrBlank()) logo.setImageDrawable(null) else logo.load(ch.logo)
        findViewById<TextView>(R.id.ovClock).text = Ui.hour(System.currentTimeMillis())
        val (now, next) = Epg.nowAndNext(ch)
        val nowView = findViewById<TextView>(R.id.ovNow)
        val nextView = findViewById<TextView>(R.id.ovNext)
        val progress = findViewById<ProgressBar>(R.id.ovProgress)
        if (now != null) {
            nowView.text = "${Ui.range(now)}   ${now.title}"
            progress.progress = now.progress()
            progress.visibility = View.VISIBLE
        } else {
            nowView.text = ""
            progress.visibility = View.GONE
        }
        nextView.text = next?.let { "Ensuite : ${Ui.hour(it.start)}   ${it.title}" } ?: ""
        overlay.visibility = View.VISIBLE
        handler.removeCallbacks(hideOverlay)
        handler.postDelayed(hideOverlay, 5000)
    }

    private fun applyTypedNumber() {
        val n = typedNumber.toIntOrNull()
        typedNumber = ""
        message.visibility = View.GONE
        if (n == null) return
        // On cherche d'abord dans la liste en cours, puis dans toutes les chaînes
        val i = channels.indexOfFirst { it.number == n }
        if (i >= 0) {
            playChannel(i)
        } else {
            val ch = Repo.channels.firstOrNull { it.number == n }
            if (ch != null) {
                channels = Repo.channels
                playChannel(channels.indexOf(ch))
            } else {
                showMessage("Pas de chaîne n°$n", 2000)
            }
        }
    }

    private fun showMessage(text: String, durationMs: Long) {
        message.text = text
        message.visibility = View.VISIBLE
        handler.removeCallbacks(hideMessage)
        handler.postDelayed(hideMessage, durationMs)
    }

    /** Films : la télécommande pilote la barre de lecture (pause, avance, retour…). */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (mode == MODE_VOD && event.keyCode != KeyEvent.KEYCODE_BACK) {
            if (playerView.dispatchKeyEvent(event)) return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (mode == MODE_LIVE) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP -> {
                    playChannel(index + 1); return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> {
                    playChannel(index - 1); return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_INFO,
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (overlay.visibility == View.VISIBLE && keyCode != KeyEvent.KEYCODE_INFO) {
                        handler.removeCallbacks(hideOverlay)
                        overlay.visibility = View.GONE
                    } else {
                        showOverlay()
                    }
                    return true
                }
                KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_BOOKMARK, KeyEvent.KEYCODE_PROG_YELLOW -> {
                    val ch = channels[index]
                    val fav = Prefs.toggleFavorite(ch.key)
                    Ui.toast(this, if (fav) "⭐ Ajoutée aux favoris" else "Retirée des favoris")
                    showOverlay()
                    return true
                }
            }
            if (keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
                typedNumber += (keyCode - KeyEvent.KEYCODE_0).toString()
                showMessage(typedNumber, 2000)
                handler.removeCallbacks(jumpToNumber)
                handler.postDelayed(jumpToNumber, 1500)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    // ------------------------------------------------------------ Films / épisodes

    private fun playVod() {
        val p = player ?: return
        p.setMediaItem(mediaItem(vodUrl))
        val pos = Prefs.getPosition(vodKey)
        if (pos > 0) {
            p.seekTo(pos)
            val min = pos / 60_000
            showMessage("$vodTitle\nReprise à ${min / 60}h${"%02d".format(min % 60)}", 3000)
        } else {
            showMessage(vodTitle, 2500)
        }
        p.prepare()
        p.play()
    }

    private fun savePosition() {
        if (mode != MODE_VOD) return
        val p = player ?: return
        Prefs.savePosition(vodKey, p.currentPosition, p.duration.coerceAtLeast(0))
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
