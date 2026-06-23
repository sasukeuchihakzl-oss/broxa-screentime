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
}
