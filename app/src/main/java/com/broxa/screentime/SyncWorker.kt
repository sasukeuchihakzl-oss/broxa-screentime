package com.broxa.screentime

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * Roda a cada ~15 min em segundo plano:
 *  - checa missões atrasadas no Firebase → notifica e (se permitido) abre o jogo.
 */
class SyncWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        val ctx = applicationContext
        val prefs = ctx.getSharedPreferences("broxa", Context.MODE_PRIVATE)
        val name = prefs.getString("name", "") ?: ""
        if (name.isBlank()) return Result.success()

        val overdue = Usage.overdueMissions(name)
        if (overdue.isNotEmpty()) {
            Notifier.notifyOverdue(ctx, overdue)
            Notifier.maybeOpenGame(ctx)
        }
        return Result.success()
    }
}
