package com.broxa.screentime

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar

object Usage {

    const val DB = "https://broxa-games-default-rtdb.firebaseio.com"
    const val GAME_URL = "https://sasukeuchihakzl-oss.github.io/broxa-games/"

    private fun httpGet(path: String): String? {
        return try {
            val conn = URL("$DB/$path").openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            val txt = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            txt
        } catch (e: Exception) {
            null
        }
    }

    /** Nomes das missões atrasadas AGORA (pendentes, com horário já passado hoje). */
    fun overdueMissions(name: String): List<String> {
        if (name.isBlank()) return emptyList()
        val raw = httpGet("forge_players/$name.json") ?: return emptyList()
        return try {
            val obj = JSONObject(raw)
            val arr = obj.optJSONArray("todayMissions") ?: return emptyList()
            val now = Calendar.getInstance()
            val nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
            val out = ArrayList<String>()
            for (i in 0 until arr.length()) {
                val m = arr.optJSONObject(i) ?: continue
                if (m.optBoolean("d", false)) continue          // já feita
                val t = m.optString("t", "")
                if (t.isBlank() || !t.contains(":")) continue    // sem horário
                val parts = t.split(":")
                val tm = (parts[0].toIntOrNull() ?: 0) * 60 + (parts.getOrNull(1)?.toIntOrNull() ?: 0)
                if (nowMin > tm) out.add(m.optString("n", "missão"))
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Checa se há versão mais nova publicada (forge_appmeta = {code, url}). */
    fun fetchAppMeta(): JSONObject? {
        val raw = httpGet("forge_appmeta.json") ?: return null
        return try { JSONObject(raw) } catch (e: Exception) { null }
    }
}
