package com.example.sampleiwatts;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public class AlertNotifier {
    static final String CHANNEL_ID = "alert_settings_channel";
    private static final int ID_GENERAL = 1001;
    private static final int ID_THRESHOLD = 1002;

    private static boolean canPostNotifications(Context ctx) {
        if (Build.VERSION.SDK_INT < 33) return true; // no runtime perm before Tiramisu
        boolean granted = ActivityCompat.checkSelfPermission(
                ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        return granted && NotificationManagerCompat.from(ctx).areNotificationsEnabled();
    }

    public static void ensureChannel(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.NotificationChannel channel = new android.app.NotificationChannel(
                    CHANNEL_ID,
                    "Alert Notifications",
                    android.app.NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications for watts and budget alerts.");
            android.app.NotificationManager nm = ctx.getSystemService(android.app.NotificationManager.class);
            nm.createNotificationChannel(channel);
        }
    }

    public static void notifyIfAllowed(Context ctx, AlertSettings settings, String title, String message) {
        if (!settings.pushEnabled) return;

        android.app.NotificationManager nm = (android.app.NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        android.app.Notification notification = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(true)
                .build();
        nm.notify((int) System.currentTimeMillis(), notification);
    }

    static void evaluateReadingAndNotify(Context ctx, AlertSettings s,
                                         double currentWatts, double projectedBillPhp,
                                         boolean voltageSpikedRecently) {
        if (!s.pushEnabled || !canPostNotifications(ctx)) return;

        StringBuilder alert = new StringBuilder();

        boolean power = currentWatts > s.powerThresholdWatts;
        boolean budget = projectedBillPhp > s.budgetPhp;
        boolean voltage = s.alertVoltageFluctuations && voltageSpikedRecently;

        if (power) {
            alert.append("Power exceeded ").append(s.powerThresholdWatts.intValue()).append(" W. ");
        }
        if (budget) {
            alert.append("Projected bill over ₱").append(Math.round(s.budgetPhp)).append(". ");
        }
        if (voltage) {
            alert.append("Voltage fluctuation detected. ");
        }

        if (alert.length() > 0) {
            ensureChannel(ctx);
            String text = alert.toString().trim();
            try {
                NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.ic_dialog_alert)
                        .setContentTitle("Energy Alert")
                        .setContentText(text)
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                        .setAutoCancel(true)
                        .setPriority(NotificationCompat.PRIORITY_HIGH);
                NotificationManagerCompat.from(ctx).notify(ID_THRESHOLD, b.build());
            } catch (SecurityException ignored) {}
        }
    }

    // ADDED: simple category picker (matches your filter chips)
    private static String pickCategory(boolean power, boolean budget, boolean voltage) {
        if (voltage) return "Voltage";
        if (power) return "Power usage";
        if (budget) return "Budget";
        return "Other";
    }
}
