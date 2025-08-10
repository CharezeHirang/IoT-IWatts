package com.example.sampleiwatts;

import android.content.Context;
import android.content.SharedPreferences;

public class AlertSettingsStore {
    private static final String PREFS = "alert_settings_prefs";
    private static final String K_WATTS = "power_threshold_watts";
    private static final String K_BUDGET = "budget_php";
    private static final String K_VOLTAGE = "alert_voltage";
    private static final String K_SYSTEM = "alert_system";
    private static final String K_PUSH = "push_enabled";

    private final SharedPreferences sp;

    AlertSettingsStore(Context ctx) {
        sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    void save(AlertSettings s) {
        sp.edit()
                .putFloat(K_WATTS, s.powerThresholdWatts.floatValue())
                .putFloat(K_BUDGET, s.budgetPhp.floatValue())
                .putBoolean(K_VOLTAGE, s.alertVoltageFluctuations)
                .putBoolean(K_SYSTEM, s.alertSystemUpdates)
                .putBoolean(K_PUSH, s.pushEnabled)
                .apply();
    }

    AlertSettings load() {
        if (!sp.contains(K_WATTS)) {
            // defaults
            return new AlertSettings(0d, 0d, false, false, false);
        }
        double watts = sp.getFloat(K_WATTS, 0f);
        double budget = sp.getFloat(K_BUDGET, 0f);
        boolean voltage = sp.getBoolean(K_VOLTAGE, false);
        boolean system = sp.getBoolean(K_SYSTEM, false);
        boolean push = sp.getBoolean(K_PUSH, false);
        return new AlertSettings(watts, budget, voltage, system, push);
    }
}
