package com.moniptv.app

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class SetupActivity : AppCompatActivity() {

    private var type = "xtream"
    private var liveFormat = "ts"

    private lateinit var typeXtream: Button
    private lateinit var typeM3u: Button
    private lateinit var fmtTs: Button
    private lateinit var fmtHls: Button
    private lateinit var groupXtream: View
    private lateinit var groupM3u: View
    private lateinit var server: EditText
    private lateinit var username: EditText
    private lateinit var password: EditText
    private lateinit var m3uUrl: EditText
    private lateinit var epgUrl: EditText
    private lateinit var status: TextView
    private lateinit var loading: ProgressBar
    private lateinit var save: Button
    private lateinit var reload: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        setContentView(R.layout.activity_setup)

        typeXtream = findViewById(R.id.typeXtream)
        typeM3u = findViewById(R.id.typeM3u)
        fmtTs = findViewById(R.id.fmtTs)
        fmtHls = findViewById(R.id.fmtHls)
        groupXtream = findViewById(R.id.groupXtream)
        groupM3u = findViewById(R.id.groupM3u)
        server = findViewById(R.id.server)
        username = findViewById(R.id.username)
        password = findViewById(R.id.password)
        m3uUrl = findViewById(R.id.m3uUrl)
        epgUrl = findViewById(R.id.epgUrl)
        status = findViewById(R.id.status)
        loading = findViewById(R.id.loading)
        save = findViewById(R.id.save)
        reload = findViewById(R.id.reload)

        Prefs.source?.let { cfg ->
            type = cfg.type
            liveFormat = cfg.liveFormat
            server.setText(cfg.server)
            username.setText(cfg.username)
            password.setText(cfg.password)
            m3uUrl.setText(cfg.m3uUrl)
            epgUrl.setText(cfg.epgUrl)
        }
        reload.visibility = if (Prefs.source?.isValid == true) View.VISIBLE else View.GONE

        typeXtream.setOnClickListener { type = "xtream"; refreshToggles() }
        typeM3u.setOnClickListener { type = "m3u"; refreshToggles() }
        fmtTs.setOnClickListener { liveFormat = "ts"; refreshToggles() }
        fmtHls.setOnClickListener { liveFormat = "m3u8"; refreshToggles() }
        save.setOnClickListener { saveAndConnect(force = true) }
        reload.setOnClickListener { saveAndConnect(force = true) }

        refreshToggles()
        typeXtream.requestFocus()
    }

    private fun refreshToggles() {
        typeXtream.isActivated = type == "xtream"
        typeM3u.isActivated = type == "m3u"
        fmtTs.isActivated = liveFormat == "ts"
        fmtHls.isActivated = liveFormat == "m3u8"
        groupXtream.visibility = if (type == "xtream") View.VISIBLE else View.GONE
        groupM3u.visibility = if (type == "m3u") View.VISIBLE else View.GONE
    }

    private fun currentConfig() = SourceConfig(
        type = type,
        server = server.text.toString().trim(),
        username = username.text.toString().trim(),
        password = password.text.toString(),
        m3uUrl = m3uUrl.text.toString().trim(),
        epgUrl = epgUrl.text.toString().trim(),
        liveFormat = liveFormat
    )

    private fun saveAndConnect(force: Boolean) {
        val cfg = currentConfig()
        if (!cfg.isValid) {
            status.text = if (type == "xtream") "Remplis l'adresse du serveur, l'identifiant et le mot de passe."
            else "Colle le lien de ta liste M3U."
            return
        }
        save.isEnabled = false
        reload.isEnabled = false
        loading.visibility = View.VISIBLE
        status.text = "Connexion en cours…"
        lifecycleScope.launch {
            try {
                Repo.reset()
                Repo.load(cfg, force)
                Prefs.source = cfg
                AppScope.launch { Repo.loadEpg(cfg) }
                status.text = "✅ Connecté : ${Repo.channels.size} chaînes, ${Repo.movies.size} films, ${Repo.series.size} séries."
                Ui.toast(this@SetupActivity, "C'est prêt !")
                finish()
            } catch (e: Throwable) {
                status.text = "❌ Échec : ${e.message ?: e.javaClass.simpleName}"
            } finally {
                save.isEnabled = true
                reload.isEnabled = true
                loading.visibility = View.GONE
            }
        }
    }
}
