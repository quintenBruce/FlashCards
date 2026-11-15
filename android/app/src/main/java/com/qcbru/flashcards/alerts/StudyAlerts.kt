package com.qcbru.flashcards.alerts

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.qcbru.flashcards.*
import com.qcbru.flashcards.storage.StoredDocument
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import java.time.*

object StudyAlerts {
    private const val CHANNEL = "study"
    private fun repository(context: Context) = (context.applicationContext as FlashApplication).repository
    private fun alarm(context: Context, uid: String, reminderId: String, occurrence: String, scheduledAt: Long, triggerAt: Long): PendingIntent {
        val intent = Intent(context,StudyAlarmReceiver::class.java).apply {
            data = Uri.parse("flashcards://alarm/$uid/$occurrence")
            putExtra("uid",uid); putExtra("reminderId",reminderId); putExtra("occurrenceId",occurrence)
            putExtra("scheduledAt",scheduledAt); putExtra("triggerAt",triggerAt)
        }
        return PendingIntent.getBroadcast(context,0,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    private fun schedule(context: Context, uid: String, reminderId: String, occurrence: String, scheduledAt: Long, triggerAt: Long) {
        context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
            maxOf(System.currentTimeMillis()+1000,triggerAt),alarm(context,uid,reminderId,occurrence,scheduledAt,triggerAt))
        val version = repository(context).dao.get(uid,"reminders",reminderId)?.let { Codec.decodeFromString<StudyReminder>(it.body).version } ?: 0
        repository(context).dao.put(StoredDocument(uid,"alarms",occurrence,mapOf("reminderId" to reminderId,"scheduledAt" to scheduledAt,"triggerAt" to triggerAt,"version" to version).toJson().toString()))
    }
    fun cancelAll(context: Context, uid: String) {
        val repo = repository(context)
        for (row in repo.dao.list(uid,"alarms")) {
            val d = Codec.parseToJsonElement(row.body).toCloud() as Map<*,*>
            context.getSystemService(AlarmManager::class.java).cancel(alarm(context,uid,d["reminderId"] as String,row.id,(d["scheduledAt"] as Number).toLong(),(d["triggerAt"] as Number).toLong()))
        }
        repo.dao.clearKind(uid,"alarms")
    }
    fun reschedule(context: Context, uid: String) {
        val repo = repository(context); val now = System.currentTimeMillis()
        val snoozes = repo.dao.list(uid,"local_snoozes")
        val previous = repo.dao.list(uid,"alarms")
        cancelAll(context,uid)
        for (row in repo.dao.list(uid,"reminders")) {
            val r = Codec.decodeFromString<StudyReminder>(row.body)
            if (!canDeliverReminder(r)) continue
            val at = r.nextAt?.takeIf { it > now - 3600000 && repo.dao.get(uid,"delivered","${r.id}_$it") == null }
                ?: ReminderClock.next(r.schedule,now) ?: continue
            schedule(context,uid,r.id,"${r.id}_$at",at,at)
        }
        // Preserve a due local fallback after the dispatcher advances nextAt. Manual
        // edits/deletion change the reminder version and invalidate the old alarm.
        for (row in previous) {
            val d = Codec.parseToJsonElement(row.body).toCloud() as Map<*,*>
            val r = repo.dao.get(uid,"reminders",d["reminderId"] as String)?.let { Codec.decodeFromString<StudyReminder>(it.body) } ?: continue
            val at = (d["triggerAt"] as Number).toLong()
            if (canDeliverReminder(r) && at <= now && at > now-3600000 && (d["version"] as? Number)?.toInt() == r.version && repo.dao.get(uid,"delivered",row.id) == null) {
                schedule(context,uid,r.id,row.id,(d["scheduledAt"] as Number).toLong(),at)
            }
        }
        for (row in snoozes) {
            val d = Codec.parseToJsonElement(row.body).toCloud() as Map<*,*>
            val reminder = repo.dao.get(uid,"reminders",d["reminderId"] as String)?.let { Codec.decodeFromString<StudyReminder>(it.body) }
            if (!canDeliverReminder(reminder, (d["version"] as? Number)?.toInt())) continue
            val at = (d["at"] as Number).toLong()
            if (at > now - 3600000 && repo.dao.get(uid,"delivered",row.id) == null) schedule(context,uid,d["reminderId"] as String,row.id,(d["scheduledAt"] as Number).toLong(),at)
        }
    }
    @Synchronized fun show(context: Context, uid: String, reminderId: String, occurrence: String, scheduledAt: Long) {
        val repo = repository(context)
        if (repo.uid != uid || repo.dao.get(uid,"delivered",occurrence) != null) return
        val r = repo.dao.get(uid,"reminders",reminderId)?.let { Codec.decodeFromString<StudyReminder>(it.body) } ?: return
        if (!canDeliverReminder(r)) return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            repo.dao.put(StoredDocument(uid,"delivered",occurrence,"{\"reason\":\"permission_denied\"}"))
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL,"Study reminders",NotificationManager.IMPORTANCE_DEFAULT))
        val open = Intent(context,MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            data = Uri.parse("flashcards://study/$uid/$reminderId/$occurrence")
            putExtra("reminderId",reminderId); putExtra("uid",uid)
        }
        val study = PendingIntent.getActivity(context,0,open,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val later = PendingIntent.getBroadcast(context,0,Intent(context,SnoozeReceiver::class.java).apply {
            data = Uri.parse("flashcards://snooze/$uid/$occurrence")
            putExtra("uid",uid); putExtra("reminderId",reminderId); putExtra("occurrenceId",occurrence); putExtra("scheduledAt",scheduledAt)
        },PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        // Durable deduplication before notification posting; server/local share the same key.
        repo.dao.put(StoredDocument(uid,"delivered",occurrence,"{}"))
        manager.notify(occurrence.hashCode(),NotificationCompat.Builder(context,CHANNEL).setSmallIcon(com.qcbru.flashcards.R.drawable.ic_cards)
            .setContentTitle(r.title).setContentText("Ready for a review?").setContentIntent(study).setAutoCancel(true)
            .addAction(0,"Study now",study).addAction(0,"Snooze 15 min",later).build())
    }
    fun snooze(context: Context, uid: String, reminderId: String, occurrence: String, scheduledAt: Long) {
        val repo = repository(context); if (repo.uid != uid) return
        val reminder = repo.dao.get(uid,"reminders",reminderId)?.let { Codec.decodeFromString<StudyReminder>(it.body) } ?: return
        if (!canDeliverReminder(reminder)) return
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(occurrence.toByteArray()).take(16).joinToString(""){"%02x".format(it)}
        val requestId = "snooze_$digest"; val at = System.currentTimeMillis()+15*60000
        if (repo.dao.get(uid,"local_snoozes","snooze_$requestId") != null) return
        repo.dao.put(StoredDocument(uid,"local_snoozes","snooze_$requestId",mapOf("reminderId" to reminderId,"scheduledAt" to scheduledAt,"at" to at,"version" to reminder.version).toJson().toString()))
        repo.dao.put(StoredDocument(uid,"reminder_pending",requestId,mapOf("id" to reminderId,"requestId" to requestId,"action" to "snooze","scheduledAt" to scheduledAt,"snoozeAt" to at).toJson().toString()))
        context.getSystemService(NotificationManager::class.java).cancel(occurrence.hashCode())
        schedule(context,uid,reminderId,"snooze_$requestId",scheduledAt,at)
        SyncWorker.enqueue(context)
    }
}
abstract class AsyncReceiver : BroadcastReceiver() {
    final override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch { try { handle(context,intent) } finally { pending.finish() } }
    }
    abstract fun handle(context: Context, intent: Intent)
}
class StudyAlarmReceiver : AsyncReceiver() {
    override fun handle(context: Context, intent: Intent) {
        val uid = intent.getStringExtra("uid") ?: return
        StudyAlerts.show(context,uid,intent.getStringExtra("reminderId") ?: return,intent.getStringExtra("occurrenceId") ?: return,intent.getLongExtra("scheduledAt",0))
        StudyAlerts.reschedule(context,uid)
    }
}
class SnoozeReceiver : AsyncReceiver() {
    override fun handle(context: Context, intent: Intent) {
        StudyAlerts.snooze(context,intent.getStringExtra("uid") ?: return,intent.getStringExtra("reminderId") ?: return,
            intent.getStringExtra("occurrenceId") ?: return,intent.getLongExtra("scheduledAt",0))
    }
}
class RescheduleReceiver : AsyncReceiver() {
    override fun handle(context: Context, intent: Intent) {
        val repo = (context.applicationContext as FlashApplication).repository
        repo.uid?.let { StudyAlerts.reschedule(context,it); SyncWorker.enqueue(context) }
    }
}
class FlashMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        CoroutineScope(Dispatchers.IO).launch {
            val data = message.data
            StudyAlerts.show(this@FlashMessagingService,data["uid"] ?: return@launch,data["reminderId"] ?: return@launch,
                data["occurrenceId"] ?: return@launch,data["scheduledAt"]?.toLongOrNull() ?: return@launch)
            StudyAlerts.reschedule(this@FlashMessagingService,data["uid"] ?: return@launch)
        }
    }
    override fun onNewToken(token: String) { SyncWorker.enqueue(this) }
}
