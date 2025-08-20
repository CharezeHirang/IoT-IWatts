package com.example.sampleiwatts;

import android.os.Bundle;
import android.util.Log;
import android.widget.LinearLayout;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.sampleiwatts.managers.DataProcessingManager;

public class DashboardActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private DataProcessingManager processingManager;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_dashboard);
        // Get the button layout
        LinearLayout buttonLayout = findViewById(R.id.button);

        // Set up buttons using the utility class
        ButtonNavigator.setupButtons(this, buttonLayout);

        IWattsApplication app = (IWattsApplication) getApplication();
        processingManager = app.getProcessingManager();

        Log.d(TAG, "MainActivity created");

    }
    @Override
    protected void onResume() {
        super.onResume();

        // Process data when app becomes active
        Log.d(TAG, "MainActivity resumed - processing data");

        DataProcessingManager manager = ((IWattsApplication) getApplication()).getProcessingManager();
        manager.processDataInForeground();
    }
}