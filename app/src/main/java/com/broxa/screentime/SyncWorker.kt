package com.broxa.screentime

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * Roda a cada ~15 min em segundo plano:
 *  - envia o tempo de tela pro Firebase
 *  - checa missões atrasadas → notifica e (se permitido) abre o jogo
 */
class SyncWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        val ctx = applicationContext
        val prefs = ctx.getSharedPreferences("broxa", Context.MODE_PRIVATE)
        val name = prefs.getString("name", "") ?: ""
        if (name.isBlank()) return Result.success()

        // 1) tempo de tela
        if (Usage.hasUsageAccess(ctx)) {
            val apps = Usage.readToday(ctx)
            Usage.push(name, apps)
        }

        // 2) missões atrasadas → alerta
        val overdue = Usage.overdueMissions(name)
        if (overdue.isNotEmpty()) {
            Notifier.notifyOverdue(ctx, overdue)
            Notifier.maybeOpenGame(ctx)
        }
        return Result.success()
    }
}
