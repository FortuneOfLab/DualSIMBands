package io.github.fortuneoflab.dualsimbands;

import static io.github.fortuneoflab.dualsimbands.Tr.t;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
final class Notices {
    static void show(Context c, String title, String text) {
        NotificationManager n = c.getSystemService(NotificationManager.class);
        n.createNotificationChannel(new NotificationChannel("restore", t("表示名の復元", "Carrier name restore"), NotificationManager.IMPORTANCE_LOW));
        Intent i = new Intent(c, MainActivity.class).putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_LABELS);
        PendingIntent pi = PendingIntent.getActivity(c, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try { n.notify(1, new Notification.Builder(c, "restore").setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title).setContentText(text).setContentIntent(pi).setAutoCancel(true).build()); }
        catch (SecurityException ignored) { /* Notification permission is optional. */ }
    }
}
