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
    private lateinit var alertStatus: TextView
    private lateinit var result: TextView
    private lateinit var btnPerm: Button
    private lateinit var btnSync: Button
    private lateinit var btnNotif: Button
    private lateinit var btnOverlay: Button
    private lateinit var btnUpdate: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        spinner = findViewById(R.id.spinnerName)
        status = findViewById(R.id.txtStatus)
        alertStatus = findViewById(R.id.txtAlertStatus)
        result = findViewById(R.id.txtResult)
        btnPerm = findViewById(R.id.btnPerm)
        btnSync = findViewById(R.id.btnSync)
        btnNotif = findViewById(R.id.btnNotif)
        btnOverlay = findViewById(R.id.btnOverlay)
        btnUpdate = findViewById(R.id.btnUpdate)

        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, players)
        val prefs = getSharedPreferences("broxa", Context.MODE_PRIVATE)
        val saved = prefs.getString("name", players[0])
        val idx = players.indexOf(saved)
        if (idx >= 0) spinner.setSelection(idx)

        btnPerm.setOnClickListener { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        btnSync.setOnClickListener { saveNameAndSync() }
        btnNotif.setOnClickListener { requestNotif() }
        btnOverlay.setOnClickListener { requestOverlay() }
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

    private fun saveName(): String {
        val name = players[spinner.selectedItemPosition]
        getSharedPreferences("broxa", Context.MODE_PRIVATE).edit().putString("name", name).apply()
        return name
    }

    private fun hasNotifPerm(): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun hasOverlay(): Boolean =
        Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)

    private fun refreshStatus() {
        if (Usage.hasUsageAccess(this)) {
            status.text = "✅ Acesso ao uso concedido."
            btnPerm.text = "✓ Acesso ao uso concedido"
        } else {
            status.text = "⚠️ Toque no botão 1, ache \"Broxa Tempo de Tela\" e ative."
            btnPerm.text = "1. Conceder acesso ao uso"
        }
        val n = if (hasNotifPerm()) "✓ notificações" else "✗ notificações"
        val o = if (hasOverlay()) "✓ abrir sozinho" else "✗ abrir sozinho"
        alertStatus.text = "A cada 15 min checo missões atrasadas. Status: $n · $o"
        btnNotif.text = if (hasNotifPerm()) "✓ Notificações OK" else "Permitir notificações"
        btnOverlay.text = if (hasOverlay()) "✓ Abrir o jogo sozinho OK" else "Permitir abrir o jogo sozinho"
    }

    private fun requestNotif() {
        if (Build.VERSION.SDK_INT >= 33 && !hasNotifPerm()) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        } else {
            result.text = "Notificações já estão liberadas."
        }
    }

    private fun requestOverlay() {
        if (!hasOverlay()) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        } else {
            result.text = "Permissão de sobreposição já está ligada."
        }
    }

    private fun saveNameAndSync() {
        val name = saveName()
        if (!Usage.hasUsageAccess(this)) {
            result.text = "Conceda a permissão primeiro (botão 1)."
            return
        }
        result.text = "Lendo e enviando…"
        Thread {
            val apps = Usage.readToday(this)
            val ok = Usage.push(name, apps)
            val txt = StringBuilder()
            if (ok) txt.append("✅ Enviado como \"$name\"!\n\n") else txt.append("❌ Falha ao enviar. Veja sua internet.\n\n")
            if (apps.isEmpty()) txt.append("(Nenhum app com 1+ min hoje.)")
            else for ((k, v) in apps) txt.append("$k: ${fmt(v)}\n")
            runOnUiThread { result.text = txt.toString() }
        }.start()
    }

    private fun fmt(m: Int): String =
        if (m < 60) "${m}min" else "${m / 60}h ${m % 60}min"

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
