package com.example.sampleiwatts;


import android.app.Application;
import android.util.Log;
import com.example.sampleiwatts.managers.DataProcessingManager;
import com.google.firebase.FirebaseApp;
import com.google.firebase.database.FirebaseDatabase;

public class IWattsApplication extends Application {
    private static final String TAG = "IWattsApplication";
    private DataProcessingManager processingManager;

    @Override
    public void onCreate() {
        super.onCreate();

        Log.d(TAG, "I-WATTS Application starting");

        // Initialize Firebase
        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this);
        }

        // Enable Firebase offline persistence
        try {
            FirebaseDatabase.getInstance().setPersistenceEnabled(true);
        } catch (Exception e) {
            Log.w(TAG, "Firebase persistence already enabled");
        }

        // Initialize processing manager (no background processing)
        processingManager = new DataProcessingManager(this);
        processingManager.initializeAppProcessing();

        Log.d(TAG, "I-WATTS Application initialized successfully");
    }

    public DataProcessingManager getProcessingManager() {
        return processingManager;
    }
}