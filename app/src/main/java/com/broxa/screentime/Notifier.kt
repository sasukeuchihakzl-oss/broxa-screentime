package com.broxa.screentime

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object Notifier {

    private const val CHANNEL = "broxa_missoes"
    private const val NOTIF_ID = 4201

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL,
                "Missões",
                NotificationManager.IMPORTANCE_HIGH
            )
            ch.description = "Lembretes de missões atrasadas"
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(ch)
        }
    }

    /** Notificação de missões atrasadas; ao tocar, abre o jogo. */
    fun notifyOverdue(ctx: Context, missions: List<String>) {
        ensureChannel(ctx)
        val titulo = if (missions.size == 1) "⚠️ Missão atrasada!" else "⚠️ ${missions.size} missões atrasadas!"
        val corpo = missions.take(4).joinToString(", ") + " — abra o Broxa e cumpra!"

        val open = Intent(Intent.ACTION_VIEW, Uri.parse(Usage.GAME_URL))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val flags = if (Build.VERSION.SDK_INT >= 23)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        else PendingIntent.FLAG_UPDATE_CURRENT
        val pi = PendingIntent.getActivity(ctx, 0, open, flags)

        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(titulo)
            .setContentText(corpo)
            .setStyle(NotificationCompat.BigTextStyle().bigText(corpo))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(NOTIF_ID, n)
        } catch (e: SecurityException) { /* sem permissão de notificação */ }
    }

    /** Se tiver permissão de sobreposição, abre o jogo automaticamente. */
    fun maybeOpenGame(ctx: Context) {
        val canOverlay = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(ctx)
        if (!canOverlay) return
        try {
            val open = Intent(Intent.ACTION_VIEW, Uri.parse(Usage.GAME_URL))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(open)
        } catch (e: Exception) { /* alguns aparelhos bloqueiam */ }
    }
}
