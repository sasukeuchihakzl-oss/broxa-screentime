package com.broxa.screentime

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private val players = listOf("Rodrigo", "Mizuki", "Craudo", "Ana")

    private lateinit var spinner: Spinner
    private lateinit var status: TextView
    private lateinit var result: TextView
    private lateinit var btnPerm: Button
    private lateinit var btnSync: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        spinner = findViewById(R.id.spinnerName)
        status = findViewById(R.id.txtStatus)
        result = findViewById(R.id.txtResult)
        btnPerm = findViewById(R.id.btnPerm)
        btnSync = findViewById(R.id.btnSync)

        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, players)
        val prefs = getSharedPreferences("broxa", Context.MODE_PRIVATE)
        val saved = prefs.getString("name", players[0])
        val idx = players.indexOf(saved)
        if (idx >= 0) spinner.setSelection(idx)

        btnPerm.setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        btnSync.setOnClickListener { saveNameAndSync() }

        scheduleBackground()
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

    private fun refreshStatus() {
        if (Usage.hasUsageAccess(this)) {
            status.text = "✅ Permissão concedida. Toque em \"Sincronizar agora\" (e fica automático em segundo plano)."
            btnPerm.text = "✓ Acesso ao uso concedido"
        } else {
            status.text = "⚠️ Falta a permissão. Toque no botão 1, ache \"Broxa Tempo de Tela\" na lista e ative."
            btnPerm.text = "1. Conceder acesso ao uso"
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
            if (ok) txt.append("✅ Enviado pro Broxa como \"$name\"!\n\n") else txt.append("❌ Falha ao enviar. Veja sua internet.\n\n")
            if (apps.isEmpty()) txt.append("(Nenhum app com 1+ min hoje.)")
            else for ((k, v) in apps) txt.append("$k: ${fmt(v)}\n")
            runOnUiThread { result.text = txt.toString() }
        }.start()
    }

    private fun fmt(m: Int): String =
        if (m < 60) "${m}min" else "${m / 60}h ${m % 60}min"

    private fun scheduleBackground() {
        val req = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "broxa-screen-sync",
            ExistingPeriodicWorkPolicy.UPDATE,
            req
        )
    }
}
