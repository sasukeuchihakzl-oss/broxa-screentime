package com.broxa.screentime

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

/** Sincroniza em segundo plano (a cada ~6h) mesmo com o app fechado. */
class SyncWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        val ctx = applicationContext
        val prefs = ctx.getSharedPreferences("broxa", Context.MODE_PRIVATE)
        val name = prefs.getString("name", "") ?: ""
        if (name.isBlank()) return Result.success()
        if (!Usage.hasUsageAccess(ctx)) return Result.success()
        val apps = Usage.readToday(ctx)
        val ok = Usage.push(name, apps)
        return if (ok) Result.success() else Result.retry()
    }
}
