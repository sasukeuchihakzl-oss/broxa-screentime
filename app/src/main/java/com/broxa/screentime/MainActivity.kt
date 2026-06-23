package com.broxa.screentime

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private val players = listOf("Rodrigo", "Mizuki", "Craudo", "Ana")

    private lateinit var spinner: Spinner
    private lateinit var status: TextView
    private lateinit var btnAtivar: Button
    private lateinit var btnUpdate: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        spinner = findViewById(R.id.spinnerName)
        status = findViewById(R.id.txtStatus)
        btnAtivar = findViewById(R.id.btnAtivar)
        btnUpdate = findViewById(R.id.btnUpdate)

        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, players)
        val prefs = getSharedPreferences("broxa", Context.MODE_PRIVATE)
        val idx = players.indexOf(prefs.getString("name", players[0]))
        if (idx >= 0) spinner.setSelection(idx)

        btnAtivar.setOnClickListener { ativar() }
        btnUpdate.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(pendingUpdateUrl ?: Usage.GAME_URL)))
        }

        Notifier.ensureChannel(this)
        scheduleBackground()
        checkForUpdate()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun hasNotif(): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun hasOverlay(): Boolean =
        Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)

    private fun refreshStatus() {
        val n = if (hasNotif()) "✅" else "❌"
        val o = if (hasOverlay()) "✅" else "❌"
        if (hasNotif() && hasOverlay()) {
            status.text = "✅ Tudo pronto! Vou te avisar (e abrir o jogo) quando atrasar uma missão."
            btnAtivar.text = "✓ ALERTAS ATIVOS"
        } else {
            status.text = "Permissões: $n notificação · $o abrir o jogo\nToque em ATIVAR e conceda as duas."
            btnAtivar.text = "ATIVAR ALERTAS"
        }
    }

    /** Salva o nome e pede as permissões necessárias, uma de cada vez. */
    private fun ativar() {
        val name = players[spinner.selectedItemPosition]
        getSharedPreferences("broxa", Context.MODE_PRIVATE).edit().putString("name", name).apply()

        if (Build.VERSION.SDK_INT >= 33 && !hasNotif()) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            return
        }
        if (!hasOverlay()) {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
            return
        }
        status.text = "✅ Pronto, $name! Alertas ativados."
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // depois da notificação, segue pedindo a sobreposição
        if (!hasOverlay()) {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        } else {
            refreshStatus()
        }
    }

    private fun scheduleBackground() {
        val req = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "broxa-sync",
            ExistingPeriodicWorkPolicy.UPDATE,
            req
        )
    }

    private var pendingUpdateUrl: String? = null
    private fun checkForUpdate() {
        Thread {
            val meta = Usage.fetchAppMeta() ?: return@Thread
            val latest = meta.optInt("code", 0)
            val url = meta.optString("url", "")
            val mine = try {
                if (Build.VERSION.SDK_INT >= 28)
                    packageManager.getPackageInfo(packageName, 0).longVersionCode.toInt()
                else
                    @Suppress("DEPRECATION") packageManager.getPackageInfo(packageName, 0).versionCode
            } catch (e: Exception) { 0 }
            if (latest > mine && url.isNotBlank()) {
                pendingUpdateUrl = url
                runOnUiThread { btnUpdate.visibility = android.view.View.VISIBLE }
            }
        }.start()
    }
}
