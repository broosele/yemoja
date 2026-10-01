package yemoja.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import yemoja.ui.gui.Underway

/*
 * A dive computer read kept alive while the app is in the background.
 *
 * See ../../../../../../../ui/gui/phone/android/doc.md — `AND-3`.
 */

/**
 * ReadingService is a dive computer download Android leaves running when the app is not in front.
 *
 * Android stops an app it cannot see, and a Bluetooth read takes a quarter of an hour, which
 * nobody watches with the screen on. A foreground service is how an app says it is doing something
 * the user asked for: its notification shows how far the read has got, as Home does, with a Cancel.
 * The read itself runs where it always does; this only keeps the app alive around it.
 */
class ReadingService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == CANCEL) {
            underway?.cancel?.invoke()
            return START_NOT_STICKY
        }
        val shown = underway ?: run {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(SHOWN, notificationOf(this, shown), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        return START_NOT_STICKY
    }

    companion object {

        /** The read under way, or absent where none is. Written on the app's main thread. */
        private var underway: Underway? = null

        /**
         * Tells the service of a read: starts it with the first, updates its notification after,
         * and stops it when [read] is absent.
         */
        fun tell(context: Context, read: Underway?) {
            val starting = underway == null
            underway = read
            val service = Intent(context, ReadingService::class.java)
            when {
                read == null -> context.stopService(service)
                starting -> context.startForegroundService(service)
                else -> context.getSystemService(NotificationManager::class.java)
                    .notify(SHOWN, notificationOf(context, read))
            }
        }

        private fun notificationOf(context: Context, read: Underway): Notification {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Dive computer downloads", NotificationManager.IMPORTANCE_LOW),
            )
            val cancel = PendingIntent.getService(
                context,
                0,
                Intent(context, ReadingService::class.java).setAction(CANCEL),
                PendingIntent.FLAG_IMMUTABLE,
            )
            val opening = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE,
            )
            val builder = Notification.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("Reading ${read.name}")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(opening)
                .addAction(Notification.Action.Builder(null, "Cancel", cancel).build())
            if (read.total > 0) {
                builder.setProgress(PERMILLE, (read.done * PERMILLE / read.total).toInt(), false)
                builder.setContentText("${kilobytes(read.done)} of ${kilobytes(read.total)} kB read")
            } else {
                builder.setProgress(0, 0, true)
            }
            return builder.build()
        }

        private fun kilobytes(bytes: Long): Long = (bytes + 999) / 1000

        private const val CHANNEL = "reading"
        private const val SHOWN = 1
        private const val CANCEL = "yemoja.android.CANCEL"

        /** How finely the notification's bar is drawn. */
        private const val PERMILLE = 1000
    }
}
