package com.example.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ClassReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val subject = intent.getStringExtra("EXTRA_SUBJECT") ?: "Class"
        val time = intent.getStringExtra("EXTRA_TIME") ?: ""
        val room = intent.getStringExtra("EXTRA_ROOM") ?: ""
        val id = intent.getIntExtra("EXTRA_ID", 1001)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date())
                val exception = AppDatabase.getInstance(context).holidayExceptionDao().getExceptionForDate(todayStr)
                if (exception != null && exception.isFullDay) {
                    // Holiday today: suppress class notification
                    return@launch
                }
                NotificationHelper.showClassNotification(
                    context = context,
                    notificationId = id,
                    subject = subject,
                    time = time,
                    room = room
                )
            } catch (_: Exception) {
                // Fallback to showing notification on error
                NotificationHelper.showClassNotification(
                    context = context,
                    notificationId = id,
                    subject = subject,
                    time = time,
                    room = room
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}

object NotificationHelper {
    const val CHANNEL_ID_CLASSES = "channel_class_reminders"
    const val CHANNEL_ID_TASKS = "channel_task_deadlines"

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val classChannel = NotificationChannel(
                CHANNEL_ID_CLASSES,
                "Class Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders for upcoming classes"
                enableVibration(true)
            }

            val taskChannel = NotificationChannel(
                CHANNEL_ID_TASKS,
                "Assignment & Exam Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Reminders for assignment submissions and exam dates"
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(classChannel)
            notificationManager?.createNotificationChannel(taskChannel)
        }
    }

    fun showClassNotification(
        context: Context,
        notificationId: Int,
        subject: String,
        time: String,
        room: String
    ) {
        try {
            val contentIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val roomText = if (room.isNotBlank()) " in Room $room" else ""
            val body = if (time.isNotBlank()) "Class starts at $time$roomText" else "Your upcoming class is starting soon$roomText"

            val notification = NotificationCompat.Builder(context, CHANNEL_ID_CLASSES)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Upcoming Class: $subject")
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {
            // Permission denied or missing, safely ignored
        } catch (_: Exception) {
            // Defensive against OEM quirks
        }
    }

    fun scheduleClassReminder(
        context: Context,
        scheduleId: Long,
        dayOfWeek: Int,
        startTime: String,
        subject: String,
        room: String,
        minutesBefore: Int = 10
    ) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val parts = startTime.split(":")
            if (parts.size < 2) return
            val hour = parts[0].toIntOrNull() ?: return
            val minute = parts[1].toIntOrNull() ?: return

            val intent = Intent(context, ClassReminderReceiver::class.java).apply {
                putExtra("EXTRA_SUBJECT", subject)
                putExtra("EXTRA_TIME", startTime)
                putExtra("EXTRA_ROOM", room)
                putExtra("EXTRA_ID", scheduleId.toInt())
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                scheduleId.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val cal = Calendar.getInstance().apply {
                val targetDay = when (dayOfWeek) {
                    1 -> Calendar.MONDAY
                    2 -> Calendar.TUESDAY
                    3 -> Calendar.WEDNESDAY
                    4 -> Calendar.THURSDAY
                    5 -> Calendar.FRIDAY
                    6 -> Calendar.SATURDAY
                    7 -> Calendar.SUNDAY
                    else -> Calendar.MONDAY
                }
                set(Calendar.DAY_OF_WEEK, targetDay)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                add(Calendar.MINUTE, -minutesBefore)

                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.WEEK_OF_YEAR, 1)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent)
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent)
            }
        } catch (_: Exception) {
            // Safe fallback if exact alarm permission is restricted
        }
    }

    fun cancelClassReminder(context: Context, scheduleId: Long) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, ClassReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                scheduleId.toInt(),
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
            }
        } catch (_: Exception) {
            // Defensive
        }
    }
}
