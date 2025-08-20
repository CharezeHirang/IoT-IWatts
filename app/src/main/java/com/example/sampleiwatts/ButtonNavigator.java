package com.example.sampleiwatts;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.example.sampleiwatts.CostEstimationActivity;
import com.example.sampleiwatts.DashboardActivity;
import com.example.sampleiwatts.HistoricalDataActivity;
import com.example.sampleiwatts.R;
import com.example.sampleiwatts.RealTimeMonitoringActivity;
import com.example.sampleiwatts.SettingsActivity;

public class ButtonNavigator {

    // Method to initialize buttons and handle navigation and emphasis
    public static void setupButtons(final Context context, LinearLayout buttonLayout) {
        // Find all ImageViews by ID
        final ImageView historicalAnalysis = buttonLayout.findViewById(R.id.img_historical_analysis);
        final ImageView realTimeMonitoring = buttonLayout.findViewById(R.id.img_realtime_monitoring);
        final ImageView homepage = buttonLayout.findViewById(R.id.img_homepage);
        final ImageView costEstimation = buttonLayout.findViewById(R.id.img_cost_estimation);
        final ImageView settings = buttonLayout.findViewById(R.id.img_settings);

        // Set click listeners for each button
        historicalAnalysis.setOnClickListener(v -> navigateToActivity(context, HistoricalDataActivity.class, historicalAnalysis, realTimeMonitoring, homepage, costEstimation, settings));
        realTimeMonitoring.setOnClickListener(v -> navigateToActivity(context, RealTimeMonitoringActivity.class, realTimeMonitoring, historicalAnalysis, homepage, costEstimation, settings));
        homepage.setOnClickListener(v -> navigateToActivity(context, DashboardActivity.class, homepage, historicalAnalysis, realTimeMonitoring, costEstimation, settings));
        costEstimation.setOnClickListener(v -> navigateToActivity(context, CostEstimationActivity.class, costEstimation, historicalAnalysis, realTimeMonitoring, homepage, settings));
        settings.setOnClickListener(v -> navigateToActivity(context, SettingsActivity.class, settings, historicalAnalysis, realTimeMonitoring, homepage, costEstimation));
    }

    // Method to navigate and emphasize the selected button
    private static void navigateToActivity(Context context, Class<?> activityClass, ImageView selectedButton, ImageView... otherButtons) {
        // Start the activity
        Intent intent = new Intent(context, activityClass);
        context.startActivity(intent);

        // Emphasize the selected button and reset others
        emphasizeButton(selectedButton, otherButtons);
    }

    // Method to emphasize the selected button
    private static void emphasizeButton(ImageView selectedButton, ImageView... otherButtons) {
        // Reset all buttons to normal state
        for (ImageView button : otherButtons) {
            button.setAlpha(0.5f);  // Dim the other buttons
        }

        // Highlight the selected button
        selectedButton.setAlpha(1f);  // Make the selected button fully opaque
    }
}
