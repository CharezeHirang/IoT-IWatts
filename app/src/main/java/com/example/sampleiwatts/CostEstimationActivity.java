package com.example.sampleiwatts;

import android.os.Bundle;
import android.widget.LinearLayout;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class CostEstimationActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_cost_estimation);
        // Get the button layout
        LinearLayout buttonLayout = findViewById(R.id.button);

        // Set up buttons using the utility class
        ButtonNavigator.setupButtons(this, buttonLayout);
    }
}