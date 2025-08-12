package com.example.sampleiwatts.receivers;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.example.sampleiwatts.managers.DataProcessingManager;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d(TAG, "Device boot completed - starting I-WATTS processing");

            DataProcessingManager processingManager = new DataProcessingManager(context);
            processingManager.initializeAutoProcessing();

            Log.d(TAG, "I-WATTS auto-processing started after boot");
        }
    }
}