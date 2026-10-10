package com.rahmatsobrian.miraicode

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat

/**
 * Foreground service yang hidup selama AI bekerja. Tujuannya agar sistem (terutama ROM agresif)
 * tidak mematikan proses saat app di latar belakang / layar mati, yang membuat request AI terputus.
 */
class KeepAliveService : Service() {
    private var wl: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CH, "AI berjalan di latar belakang", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(this, CH)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("MiraiCode sedang bekerja")
            .setContentText("AI sedang memproses permintaanmu…")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW).build()
        val ok = runCatching {
            if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else startForeground(ID, n)
        }.isSuccess
        if (!ok) { stopSelf(); return START_NOT_STICKY }
        if (wl == null) wl = runCatching {
            getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MiraiCode:ai")
                .apply { setReferenceCounted(false); acquire(30 * 60 * 1000L) }   // batas 30 menit sebagai pengaman
        }.getOrNull()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        runCatching { wl?.release() }; wl = null
        super.onDestroy()
    }

    companion object { const val CH = "mirai_keepalive"; const val ID = 4201 }
}
