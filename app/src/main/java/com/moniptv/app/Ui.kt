package com.moniptv.app

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Ui {
    private val hourFmt = SimpleDateFormat("HH:mm", Locale.FRANCE)

    fun hour(ms: Long): String = hourFmt.format(Date(ms))

    fun range(p: Programme): String = "${hour(p.start)} – ${hour(p.end)}"

    /** Petit effet de zoom quand un élément reçoit le focus de la télécommande. */
    fun zoomOnFocus(view: View, scale: Float = 1.06f) {
        view.setOnFocusChangeListener { v, hasFocus ->
            val s = if (hasFocus) scale else 1f
            v.animate().scaleX(s).scaleY(s).setDuration(120).start()
        }
    }

    fun toast(activity: Activity, msg: String) {
        Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
    }
}

/** Horloge qui se met à jour toute seule tant que l'écran est affiché. */
class Clock(private val view: TextView?) {
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            view?.text = Ui.hour(System.currentTimeMillis())
            handler.postDelayed(this, 15_000)
        }
    }

    fun start() { handler.removeCallbacks(tick); tick.run() }
    fun stop() = handler.removeCallbacks(tick)
}
