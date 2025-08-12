package com.example.sampleiwatts.managers;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import com.example.sampleiwatts.services.DataProcessingService;

public class DataProcessingManager {
    private static final String TAG = "DataProcessingManager";
    private static final String PREFS_NAME = "iwatts_processing_prefs";
    private static final String KEY_AUTO_PROCESSING_ENABLED = "auto_processing_enabled";

    private Context context;
    private SharedPreferences prefs;

    public DataProcessingManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Start automated processing
     */
    public void startAutomatedProcessing() {
        Log.d(TAG, "Starting automated data processing service");

        Intent serviceIntent = new Intent(context, DataProcessingService.class);
        context.startService(serviceIntent);

        prefs.edit().putBoolean(KEY_AUTO_PROCESSING_ENABLED, true).apply();
    }

    /**
     * Stop automated processing
     */
    public void stopAutomatedProcessing() {
        Log.d(TAG, "Stopping automated data processing service");

        Intent serviceIntent = new Intent(context, DataProcessingService.class);
        context.stopService(serviceIntent);

        prefs.edit().putBoolean(KEY_AUTO_PROCESSING_ENABLED, false).apply();
    }

    /**
     * Check if auto processing is enabled
     */
    public boolean isAutoProcessingEnabled() {
        return prefs.getBoolean(KEY_AUTO_PROCESSING_ENABLED, true);
    }

    /**
     * Initialize on app start
     */
    public void initializeAutoProcessing() {
        if (isAutoProcessingEnabled()) {
            startAutomatedProcessing();
            Log.d(TAG, "Auto processing initialized");
        }
    }
}
