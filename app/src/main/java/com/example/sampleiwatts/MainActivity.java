package com.example.sampleiwatts;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import com.example.sampleiwatts.managers.DataProcessingManager;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private DataProcessingManager processingManager;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        IWattsApplication app = (IWattsApplication) getApplication();
        processingManager = app.getProcessingManager();

        Log.d(TAG, "MainActivity created - automated processing is " +
                (processingManager.isAutoProcessingEnabled() ? "ACTIVE" : "STOPPED"));

        Button button1 = findViewById(R.id.button1);
        button1.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, RealTimeMonitoringActivity.class);
            startActivity(intent);
            finish();
        });

        Button button2 = findViewById(R.id.button2);
        button2.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
            finish();
        });

    }
}