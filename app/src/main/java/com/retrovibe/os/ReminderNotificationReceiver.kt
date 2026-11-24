package com.retrovibe.os

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.time.LocalDate
import java.time.ZoneId

private const val CHANNEL_ID = "retrovibe_reminders"

class ReminderNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "RetroVibe Reminder"
        val message = intent.getStringExtra("message") ?: "You have a scheduled reminder."
        val notifId = intent.getIntExtra("notif_id", (System.currentTimeMillis() and 0x7FFFFFFF).toInt())
        postReminderNotificationNow(context, notifId, title, message)
    }
}

fun postReminderNotificationNow(context: Context, notifId: Int, title: String, message: String) {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val ch = NotificationChannel(CHANNEL_ID, "RetroVibe Reminders", NotificationManager.IMPORTANCE_HIGH)
        ch.description = "Scheduled RetroVibe OS reminders"
        nm.createNotificationChannel(ch)
    }
    val openIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val pi = PendingIntent.getActivity(
        context,
        notifId,
        openIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val notif = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(title)
        .setContentText(message)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .setContentIntent(pi)
        .build()
    try {
        nm.notify(notifId, notif)
    } catch (_: SecurityException) {
    }
}

fun scheduleReminderNotification(context: Context, reminderId: String, title: String, message: String, dueDateIso: String) {
    val notifId = reminderId.hashCode() and 0x7FFFFFFF
    val parsed = try { LocalDate.parse(dueDateIso) } catch (_: Throwable) { LocalDate.now() }
    val today = LocalDate.now()
    if (!parsed.isAfter(today)) {
        postReminderNotificationNow(context, notifId, title, message)
        return
    }
    val triggerMs = parsed.atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val intent = Intent(context, ReminderNotificationReceiver::class.java).apply {
        putExtra("title", title)
        putExtra("message", message)
        putExtra("notif_id", notifId)
    }
    val pi = PendingIntent.getBroadcast(
        context,
        notifId,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
    am.set(AlarmManager.RTC_WAKEUP, triggerMs, pi)
}
