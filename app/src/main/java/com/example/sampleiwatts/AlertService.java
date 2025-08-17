package com.example.sampleiwatts;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;      // ✅ added
import java.util.Locale;
import java.util.Map;          // ✅ added
import java.util.concurrent.ConcurrentHashMap; // ✅ added
import java.util.concurrent.atomic.AtomicLong;

public class AlertService extends Service {

    private static final String ROOT_PATH = "hourly_summaries";
    private static final String FIELD_WATTS = "peak_watts";
    private static final String FIELD_COST  = "total_cost";
    private static final String FIELD_VAVG  = "avg_voltage";
    private static final String FIELD_V     = "voltage";

    private static final double Z_THRESHOLD_WATTS = 3.0;
    private static final double DEFAULT_ALPHA     = 0.10;

    // ---- Budget notification tuning ----
    private static final long   BUDGET_RECALC_COOLDOWN_MS = 30 * 60 * 1000; // 30 min
    private static final double BUDGET_WARN_PCT  = 0.80; // 80%
    private static final double BUDGET_CRIT_PCT  = 1.00; // 100%

    // ---- Firebase refs ----
    private DatabaseReference rootRef;
    private DatabaseReference alertsRef; // ✅ added (/alerts/{uid})

    private AlertSettingsStore store;

    // cache/cooldown
    private final AtomicLong lastBudgetCalc = new AtomicLong(0);

    // simple dedupe to prevent rapid duplicate logs (per type)
    private final Map<String, Long> lastLoggedAt = new ConcurrentHashMap<>(); // ✅ added
    private static final long LOG_DEDUPE_MS = 2 * 60 * 1000; // 2 minutes ✅ added

    @Override
    public void onCreate() {
        super.onCreate();
        store = new AlertSettingsStore(this);

        startForeground(1, new NotificationCompat.Builder(this, AlertNotifier.CHANNEL_ID)
                .setContentTitle("I-WATTS Monitoring")
                .setContentText("Watching power, voltage, and budget…")
                .setSmallIcon(R.drawable.ic_notification)
                .setOngoing(true)
                .build());

        FirebaseDatabase db = FirebaseDatabase.getInstance();
        rootRef   = db.getReference(ROOT_PATH);

        // ✅ set up /alerts/{uid}
        String uid = getUserId(); // TODO: wire to your auth/session
        if (uid == null || uid.isEmpty()) uid = "device"; // fallback if you’re not using auth
        alertsRef = db.getReference("alerts").child(uid); // e.g. alerts/user123

        // Listen for each date node
        rootRef.addChildEventListener(new ChildEventListener() {
            @Override public void onChildAdded(@NonNull DataSnapshot dateSnap, String prev) { attachHourListener(dateSnap.getRef()); }
            @Override public void onChildChanged(@NonNull DataSnapshot dateSnap, String prev) { /* hours handle below */ }
            @Override public void onChildRemoved(@NonNull DataSnapshot snapshot) {}
            @Override public void onChildMoved(@NonNull DataSnapshot snapshot, String prev) {}
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void attachHourListener(DatabaseReference dateRef) {
        dateRef.addChildEventListener(new ChildEventListener() {
            @Override public void onChildAdded(@NonNull DataSnapshot hourSnap, String prev) { evaluateHour(hourSnap); maybeCheckBudget(); }
            @Override public void onChildChanged(@NonNull DataSnapshot hourSnap, String prev) { evaluateHour(hourSnap); maybeCheckBudget(); }
            @Override public void onChildRemoved(@NonNull DataSnapshot snapshot) {}
            @Override public void onChildMoved(@NonNull DataSnapshot snapshot, String prev) {}
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void evaluateHour(DataSnapshot hourSnap) {
        AlertSettings settings = store.load();
        if (settings == null || !settings.pushEnabled) return;

        // Extract context for logging
        String dateKey = hourSnap.getRef().getParent() != null ? hourSnap.getRef().getParent().getKey() : null; // YYYY-MM-DD
        String hourKey = hourSnap.getKey(); // HH
        long   ts      = System.currentTimeMillis();

        // --- POWER (adaptive + user threshold)
        Double watts = safeNum(hourSnap, FIELD_WATTS);
        if (watts != null) {
            double z = Baseline.adapt(this, FIELD_WATTS, watts, DEFAULT_ALPHA);
            if (z > Z_THRESHOLD_WATTS && watts > settings.powerThresholdWatts) {
                String title = "Power Usage Alert";
                String msg = String.format(Locale.getDefault(), "Unusual usage: %.0f W (above baseline)", watts);
                AlertNotifier.notifyIfAllowed(this, settings, title, msg);

                // ✅ Log to Firebase
                logAlert("POWER", title, msg, ts, dateKey, hourKey,
                        mkMap("value", watts, "threshold", settings.powerThresholdWatts, "z", z));
            }
        }

        // --- VOLTAGE (range check)
        Double v = safeNum(hourSnap, FIELD_VAVG);
        if (v == null) v = safeNum(hourSnap, FIELD_V);
        if (v != null && (v < settings.voltageMin || v > settings.voltageMax)) {
            String title = "Voltage Alert";
            String msg = v < settings.voltageMin
                    ? String.format(Locale.getDefault(), "Low voltage: %.0f V (min %.0f V)", v, settings.voltageMin)
                    : String.format(Locale.getDefault(), "High voltage: %.0f V (max %.0f V)", v, settings.voltageMax);
            AlertNotifier.notifyIfAllowed(this, settings, title, msg);

            // ✅ Log to Firebase
            logAlert("VOLTAGE", title, msg, ts, dateKey, hourKey,
                    mkMap("value", v, "min", settings.voltageMin, "max", settings.voltageMax));
        }

        // (Budget handled in maybeCheckBudget with cooldown)
    }

    /** Recalculate & notify budget with cooldown to avoid heavy reads & spam */
    private void maybeCheckBudget() {
        long now = System.currentTimeMillis();
        if (now - lastBudgetCalc.get() < BUDGET_RECALC_COOLDOWN_MS) return;
        lastBudgetCalc.set(now);

        AlertSettings settings = store.load();
        if (settings == null || !settings.pushEnabled || settings.monthlyBudgetPhp <= 0) return;

        String monthPrefix = new SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(new Date());
        rootRef.get().addOnSuccessListener(snap -> {
            double monthTotal = 0.0;
            for (DataSnapshot dateSnap : snap.getChildren()) {
                String dateKey = dateSnap.getKey(); // "YYYY-MM-DD"
                if (dateKey == null || !dateKey.startsWith(monthPrefix)) continue;

                for (DataSnapshot hourSnap : dateSnap.getChildren()) {
                    Double cost = safeNum(hourSnap, FIELD_COST);
                    if (cost != null) monthTotal += cost;
                }
            }

            double pct = monthTotal / settings.monthlyBudgetPhp; // 0..1+
            handleBudgetNotifications(pct, monthTotal, settings);
        });
    }

    /** Sends “80% approaching” and “100% exceeded” once per month state */
    private void handleBudgetNotifications(double pct, double monthTotal, AlertSettings settings) {
        String state = (pct >= BUDGET_CRIT_PCT) ? "CRIT" : (pct >= BUDGET_WARN_PCT) ? "WARN" : "OK";
        String monthKey = new SimpleDateFormat("yyyyMM", Locale.getDefault()).format(new Date());

        SharedPreferences sp = getSharedPreferences("iwatts_budget", MODE_PRIVATE);
        String lastState = sp.getString("state_" + monthKey, "NONE");

        if (!state.equals(lastState)) {
            long ts = System.currentTimeMillis();

            if ("WARN".equals(state)) {
                String title = "Budget Alert";
                String msg = String.format(
                        Locale.getDefault(),
                        "Budget Limit Approaching: You've used %.0f%% of your monthly budget (₱%.2f of ₱%.2f).",
                        pct * 100.0, monthTotal, settings.monthlyBudgetPhp
                );
                AlertNotifier.notifyIfAllowed(this, settings, title, msg);

                // ✅ Log to Firebase
                logAlert("BUDGET_WARN", title, msg, ts, monthKey, null,
                        mkMap("percent", pct, "total", monthTotal, "limit", settings.monthlyBudgetPhp));
            } else if ("CRIT".equals(state)) {
                String title = "Budget Alert";
                String msg = String.format(
                        Locale.getDefault(),
                        "Budget Exceeded: You've used %.0f%% of your monthly budget (₱%.2f of ₱%.2f).",
                        pct * 100.0, monthTotal, settings.monthlyBudgetPhp
                );
                AlertNotifier.notifyIfAllowed(this, settings, title, msg);

                // ✅ Log to Firebase
                logAlert("BUDGET_CRIT", title, msg, ts, monthKey, null,
                        mkMap("percent", pct, "total", monthTotal, "limit", settings.monthlyBudgetPhp));
            }
            sp.edit().putString("state_" + monthKey, state).apply();
        }
    }

    private Double safeNum(DataSnapshot snap, String key) {
        DataSnapshot c = snap.child(key);
        if (!c.exists()) return null;
        Object v = c.getValue();
        if (v instanceof Number) return ((Number) v).doubleValue();
        try { return Double.parseDouble(String.valueOf(v)); } catch (Exception e) { return null; }
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    /** EWMA baseline helper (as before) */
    static class Baseline {
        private static final String PREF = "iwatts_baselines";

        static double adapt(Context ctx, String key, double x, double alpha) {
            SharedPreferences sp = ctx.getSharedPreferences(PREF, MODE_PRIVATE);
            double mean = Double.longBitsToDouble(sp.getLong(key + "_mean", Double.doubleToLongBits(x)));
            double var  = Double.longBitsToDouble(sp.getLong(key + "_var",  Double.doubleToLongBits(1e-6)));

            double prevMean = mean;
            mean = alpha * x + (1 - alpha) * prevMean;
            var  = (1 - alpha) * (var + alpha * Math.pow(x - prevMean, 2));

            double std = Math.max(Math.sqrt(var), 1e-6);
            double z   = (x - mean) / std;

            sp.edit()
                    .putLong(key + "_mean", Double.doubleToLongBits(mean))
                    .putLong(key + "_var",  Double.doubleToLongBits(var))
                    .apply();
            return z;
        }
    }

    // =========================
    // 🔽 Firebase alert logging
    // =========================

    /** Push a single alert record to /alerts/{uid}/YYYY/MM/{autoId} with small dedupe */
    private void logAlert(String type, String title, String message, long ts,
                          String dateOrMonthKey, String hourKey, Map<String, Object> extra) {
        if (alertsRef == null) return;

        // dedupe window per type to reduce spam
        Long lastTs = lastLoggedAt.get(type);
        if (lastTs != null && ts - lastTs < LOG_DEDUPE_MS) return;
        lastLoggedAt.put(type, ts);

        // Path partitioning by year/month for easier querying
        String yyyy = new SimpleDateFormat("yyyy", Locale.getDefault()).format(new Date(ts));
        String MM   = new SimpleDateFormat("MM",   Locale.getDefault()).format(new Date(ts));

        DatabaseReference dest = alertsRef.child(yyyy).child(MM).push();

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", type);           // e.g., POWER, VOLTAGE, BUDGET_WARN, BUDGET_CRIT
        payload.put("title", title);
        payload.put("message", message);
        payload.put("timestamp", ts);
        payload.put("dateKey", dateOrMonthKey); // "YYYY-MM-DD" or "yyyyMM" for budget
        if (hourKey != null) payload.put("hour", hourKey);

        if (extra != null) payload.put("meta", extra);

        dest.setValue(payload);
    }

    /** Helper to build a small map quickly */
    private Map<String, Object> mkMap(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    /** TODO: Replace with your real user id (Firebase Auth) */
    private String getUserId() {
        // If you use FirebaseAuth:
        // FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        // return user != null ? user.getUid() : null;

        // Temporary fallback when not using auth yet:
        return "device-001";
    }
}
