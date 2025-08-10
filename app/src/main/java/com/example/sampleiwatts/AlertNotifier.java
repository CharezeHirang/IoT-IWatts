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

    static void ensureChannel(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID,
                    "Alerts",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            ch.setDescription("Notifications for energy alerts");
            NotificationManager nm = ctx.getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    static void notifyIfAllowed(Context ctx, AlertSettings s, String title, String body) {
        if (!s.pushEnabled || !canPostNotifications(ctx)) return;
        ensureChannel(ctx);
        try {
            NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT);
            NotificationManagerCompat.from(ctx).notify(ID_GENERAL, b.build());

            // ADDED: log “Settings Updated” (or whatever title/body you pass)
            NotificationLogger.log(ctx, title, body, System.currentTimeMillis(), "System");
        } catch (SecurityException ignored) { /* user denied notifications */ }
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

                // ADDED: log the alert with a category for filtering in your NotificationActivity
                String category = pickCategory(power, budget, voltage);
                NotificationLogger.log(ctx, "Energy Alert", text, System.currentTimeMillis(), category);
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
