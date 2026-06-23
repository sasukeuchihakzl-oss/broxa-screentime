package com.broxa.screentime

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object Usage {

    const val DB = "https://broxa-games-default-rtdb.firebaseio.com"
    const val GAME_URL = "https://sasukeuchihakzl-oss.github.io/broxa-games/"

    fun hasUsageAccess(ctx: Context): Boolean {
        val appOps = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            ctx.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(System.currentTimeMillis())

    /** Lê o tempo de uso (minutos) de cada app HOJE, do maior pro menor (top 14). */
    fun readToday(ctx: Context): LinkedHashMap<String, Int> {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        val end = System.currentTimeMillis()

        val pm = ctx.packageManager
        val raw = HashMap<String, Int>()
        val stats = usm.queryAndAggregateUsageStats(start, end)
        for ((pkg, us) in stats) {
            val mins = (us.totalTimeInForeground / 60000L).toInt()
            if (mins < 1) continue
            if (pkg == ctx.packageName) continue
            val label = try {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                pkg
            }
            raw[label] = (raw[label] ?: 0) + mins
        }
        val sorted = raw.entries.sortedByDescending { it.value }.take(14)
        val out = LinkedHashMap<String, Int>()
        for (e in sorted) out[e.key] = e.value
        return out
    }

    /** Envia pro Firebase: forge_screentime/<nome> = { apps:{...}, date, at }. */
    fun push(name: String, apps: Map<String, Int>): Boolean {
        if (name.isBlank()) return false
        return try {
            val body = JSONObject()
            val a = JSONObject()
            for ((k, v) in apps) a.put(k, v)
            body.put("apps", a)
            body.put("date", today())
            body.put("at", System.currentTimeMillis())

            val url = URL("$DB/forge_screentime/$name.json")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "PUT"
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        } catch (e: Exception) {
            false
        }
    }

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
