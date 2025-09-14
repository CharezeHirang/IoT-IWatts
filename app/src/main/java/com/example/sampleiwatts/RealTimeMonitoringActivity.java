package com.example.sampleiwatts;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.example.sampleiwatts.processors.RealTimeDataProcessor;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class RealTimeMonitoringActivity extends AppCompatActivity {
    private static final String TAG = "RealTimeMonitoring";
    private static final long REFRESH_INTERVAL = 3 * 60 * 1000; // 3 minutes in milliseconds

    // UI Components - Overall Summary
    private TextView todaysTotalValue;
    private TextView todaysTotalPercentage;
    private TextView peakUsageValue;
    private TextView peakUsageTime;
    private TextView estimatedCostValue;
    private TextView estimatedCostDetails;

    // UI Components - Area 1
    private FrameLayout area1ChartContainer;
    private LineChart area1Chart;
    private TextView area1TotalConsumption;
    private TextView area1EstimatedCost;
    private TextView area1PeakConsumption;
    private TextView area1SharePercentage;

    // UI Components - Area 2
    private FrameLayout area2ChartContainer;
    private LineChart area2Chart;
    private TextView area2TotalConsumption;
    private TextView area2EstimatedCost;
    private TextView area2PeakConsumption;
    private TextView area2SharePercentage;

    // UI Components - Area 3
    private FrameLayout area3ChartContainer;
    private LineChart area3Chart;
    private TextView area3TotalConsumption;
    private TextView area3EstimatedCost;
    private TextView area3PeakConsumption;
    private TextView area3SharePercentage;

    // Data processor and refresh handler
    private RealTimeDataProcessor dataProcessor;
    private Handler refreshHandler;
    private Runnable refreshRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_real_time_monitoring);

        // Get the button layout
        LinearLayout buttonLayout = findViewById(R.id.button);

        // Set up buttons using the utility class
        ButtonNavigator.setupButtons(this, buttonLayout);
        Log.d(TAG, "RealTimeMonitoringActivity created");

        initializeViews();
        setupRefreshTimer();

        // Initialize data processor - UPDATED: No context parameter needed
        dataProcessor = new RealTimeDataProcessor();

        // Load initial data
        loadRealTimeData();
    }



    /**
     * Initialize all UI components
     */
    private void initializeViews() {
        // Overall summary views
        todaysTotalValue = findViewById(R.id.todays_total_value);
        todaysTotalPercentage = findViewById(R.id.todays_total_percentage);
        peakUsageValue = findViewById(R.id.peak_usage_value);
        peakUsageTime = findViewById(R.id.peak_usage_time);
        estimatedCostValue = findViewById(R.id.estimated_cost_value);
        estimatedCostDetails = findViewById(R.id.estimated_cost_details);

        // Area 1 views
        area1ChartContainer = findViewById(R.id.area1_chart_container);
        area1TotalConsumption = findViewById(R.id.area1_total_consumption);
        area1EstimatedCost = findViewById(R.id.area1_estimated_cost);
        area1PeakConsumption = findViewById(R.id.area1_peak_consumption);
        area1SharePercentage = findViewById(R.id.area1_share_percentage);

        // Area 2 views
        area2ChartContainer = findViewById(R.id.area2_chart_container);
        area2TotalConsumption = findViewById(R.id.area2_total_consumption);
        area2EstimatedCost = findViewById(R.id.area2_estimated_cost);
        area2PeakConsumption = findViewById(R.id.area2_peak_consumption);
        area2SharePercentage = findViewById(R.id.area2_share_percentage);

        // Area 3 views
        area3ChartContainer = findViewById(R.id.area3_chart_container);
        area3TotalConsumption = findViewById(R.id.area3_total_consumption);
        area3EstimatedCost = findViewById(R.id.area3_estimated_cost);
        area3PeakConsumption = findViewById(R.id.area3_peak_consumption);
        area3SharePercentage = findViewById(R.id.area3_share_percentage);

        // Setup chart containers
        setupChartContainers();

        Log.d(TAG, "All views initialized successfully");
    }

    /**
     * Setup chart containers with LineChart instances
     */
    private void setupChartContainers() {
        try {
            // Create LineChart for Area 1
            if (area1ChartContainer != null) {
                area1Chart = new LineChart(this);
                area1ChartContainer.removeAllViews();
                area1ChartContainer.addView(area1Chart);
            }

            // Create LineChart for Area 2
            if (area2ChartContainer != null) {
                area2Chart = new LineChart(this);
                area2ChartContainer.removeAllViews();
                area2ChartContainer.addView(area2Chart);
            }

            // Create LineChart for Area 3
            if (area3ChartContainer != null) {
                area3Chart = new LineChart(this);
                area3ChartContainer.removeAllViews();
                area3ChartContainer.addView(area3Chart);
            }

            Log.d(TAG, "Chart containers setup successfully");

        } catch (Exception e) {
            Log.e(TAG, "Error setting up chart containers: " + e.getMessage(), e);
        }
    }

    /**
     * Setup automatic refresh timer
     */
    private void setupRefreshTimer() {
        refreshHandler = new Handler();
        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Auto-refreshing real-time data...");
                loadRealTimeData();
                // Schedule next refresh
                refreshHandler.postDelayed(this, REFRESH_INTERVAL);
            }
        };
    }

    /**
     * Load real-time data from processor - UPDATED for new logs-based processor
     */
    private void loadRealTimeData() {
        Log.d(TAG, "Loading real-time data...");



        // UPDATED: Use new method signature with date parameter
        String todayDate = getCurrentDate();
        dataProcessor.processRealTimeData(todayDate, new RealTimeDataProcessor.DataProcessingCallback() {
            @Override
            public void onDataProcessed(RealTimeDataProcessor.RealTimeData realTimeData) {



                runOnUiThread(() -> {
                    // 🆕 VALIDATE DATA BEFORE UPDATING UI:
                    if (validateRealTimeData(realTimeData)) {
                        updateUI(realTimeData);
                        Log.d(TAG, "✅ UI updated successfully with valid data");
                    } else {
                        Log.w(TAG, "⚠️ Invalid data received, not updating UI");
                        Toast.makeText(RealTimeMonitoringActivity.this,
                                "Data incomplete - please wait and try again", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            private boolean validateRealTimeData(RealTimeDataProcessor.RealTimeData data) {
                if (data == null) {
                    Log.e(TAG, "❌ Data is null");
                    return false;
                }

                if (data.totalConsumption < 0) {
                    Log.e(TAG, "❌ Negative total consumption: " + data.totalConsumption);
                    return false;
                }

                if (data.hourlyData == null || data.hourlyData.isEmpty()) {
                    Log.e(TAG, "❌ No hourly data available");
                    return false;
                }

                if (data.area1Data == null || data.area2Data == null || data.area3Data == null) {
                    Log.e(TAG, "❌ Missing area data");
                    return false;
                }

                Log.d(TAG, "✅ Data validation passed");
                return true;
            }

            @Override
            public void onError(String error) {

                runOnUiThread(() -> {
                    Log.e(TAG, "Error loading real-time data: " + error);
                    Toast.makeText(RealTimeMonitoringActivity.this,
                            "Failed to load data: " + error, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onSettingsLoaded(double electricityRate, double voltageReference) {
                Log.d(TAG, "Settings loaded - Rate: " + electricityRate + "/kWh, Voltage: " + voltageReference + "V");
            }
        });
    }

    /**
     * Helper method to get current date in Philippine timezone
     */
    private String getCurrentDate() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        dateFormat.setTimeZone(TimeZone.getTimeZone("Asia/Manila"));
        return dateFormat.format(new Date());
    }

    /**
     * Update UI with real-time data
     */
    private void updateUI(RealTimeDataProcessor.RealTimeData realTimeData) {
        try {
            // Update overall summary
            updateOverallSummary(realTimeData);

            // Update area data
            updateAreaData(realTimeData);

            // Update charts
            updateCharts(realTimeData);

        } catch (Exception e) {
            Log.e(TAG, "Error updating UI: " + e.getMessage(), e);
        }
    }

    /**
     * Update overall summary section
     */
    private void updateOverallSummary(RealTimeDataProcessor.RealTimeData realTimeData) {
        if (todaysTotalValue != null) {
            todaysTotalValue.setText(String.format(Locale.getDefault(), "%.2f kWh", realTimeData.totalConsumption));
        }

        if (peakUsageValue != null) {
            peakUsageValue.setText(String.format(Locale.getDefault(), "%.0f W", realTimeData.peakWatts));
        }

        if (peakUsageTime != null) {
            peakUsageTime.setText(realTimeData.peakTime != null ? realTimeData.peakTime : "--:--");
        }

        if (estimatedCostValue != null) {
            estimatedCostValue.setText(String.format(Locale.getDefault(), "₱%.2f", realTimeData.totalCost));
        }

        // Calculate percentage change (placeholder logic - you can implement based on your needs)
        if (todaysTotalPercentage != null) {
            todaysTotalPercentage.setText("-- %");
        }

        if (estimatedCostDetails != null) {
            estimatedCostDetails.setText("Today's estimated cost");
        }
    }

    /**
     * Update area-specific data
     */
    private void updateAreaData(RealTimeDataProcessor.RealTimeData realTimeData) {
        // Area 1
        updateSingleAreaData(realTimeData.area1Data,
                area1TotalConsumption, area1EstimatedCost,
                area1PeakConsumption, area1SharePercentage);

        // Area 2
        updateSingleAreaData(realTimeData.area2Data,
                area2TotalConsumption, area2EstimatedCost,
                area2PeakConsumption, area2SharePercentage);

        // Area 3
        updateSingleAreaData(realTimeData.area3Data,
                area3TotalConsumption, area3EstimatedCost,
                area3PeakConsumption, area3SharePercentage);
    }

    /**
     * Update single area data
     */
    private void updateSingleAreaData(RealTimeDataProcessor.AreaData areaData,
                                      TextView consumptionView, TextView costView,
                                      TextView peakView, TextView shareView) {
        if (consumptionView != null) {
            consumptionView.setText(String.format(Locale.getDefault(), "%.3f kWh", areaData.consumption));
        }

        if (costView != null) {
            costView.setText(String.format(Locale.getDefault(), "₱%.2f", areaData.cost));
        }

        if (peakView != null) {
            peakView.setText(String.format(Locale.getDefault(), "%.0f W", areaData.peakWatts));
        }

        if (shareView != null) {
            shareView.setText(String.format(Locale.getDefault(), "%.1f%%", areaData.sharePercentage));
        }
    }

    /**
     * Update charts with hourly data
     */
    private void updateCharts(RealTimeDataProcessor.RealTimeData realTimeData) {
        try {
            // Fetch area names from database and then update charts
            DatabaseReference systemSettingsRef = FirebaseDatabase.getInstance()
                    .getReference("system_settings");

            systemSettingsRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    // Get area names from database
                    String area1Name = snapshot.child("area1_name").getValue(String.class);
                    String area2Name = snapshot.child("area2_name").getValue(String.class);
                    String area3Name = snapshot.child("area3_name").getValue(String.class);

                    // Use defaults if not found
                    if (area1Name == null) area1Name = "Area 1";
                    if (area2Name == null) area2Name = "Area 2";
                    if (area3Name == null) area3Name = "Area 3";

                    // Create hourly labels
                    List<String> hourLabels = new ArrayList<>();
                    for (int i = 0; i < 24; i++) {
                        hourLabels.add(String.format("%02d:00", i));
                    }

                    // CORRECTED: Pass realTimeData to setupAreaChart for percentage calculations
                    if (area1Chart != null) {
                        setupAreaChart(area1Chart, realTimeData.hourlyData, hourLabels,
                                area1Name, getResources().getColor(R.color.brown), realTimeData);
                    }

                    if (area2Chart != null) {
                        setupAreaChart(area2Chart, realTimeData.hourlyData, hourLabels,
                                area2Name, getResources().getColor(R.color.brown), realTimeData);
                    }

                    if (area3Chart != null) {
                        setupAreaChart(area3Chart, realTimeData.hourlyData, hourLabels,
                                area3Name, getResources().getColor(R.color.brown), realTimeData);
                    }

                    // Update area labels in UI
                    updateAreaLabels(area1Name, area2Name, area3Name);

                    Log.d(TAG, "Charts updated with area distribution - Area1: " +
                            String.format("%.1f%%", realTimeData.area1Data.sharePercentage) +
                            ", Area2: " + String.format("%.1f%%", realTimeData.area2Data.sharePercentage) +
                            ", Area3: " + String.format("%.1f%%", realTimeData.area3Data.sharePercentage));
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.e(TAG, "Error fetching area names: " + error.getMessage());
                    // Use defaults if database fetch fails
                    updateChartsWithDefaults(realTimeData);
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Error updating charts: " + e.getMessage(), e);
        }
    }

    private void updateAreaLabels(String area1Name, String area2Name, String area3Name) {
        runOnUiThread(() -> {
            TextView area1Label = findViewById(R.id.area1_label);
            TextView area2Label = findViewById(R.id.area2_label);
            TextView area3Label = findViewById(R.id.area3_label);

            if (area1Label != null) area1Label.setText(area1Name);
            if (area2Label != null) area2Label.setText(area2Name);
            if (area3Label != null) area3Label.setText(area3Name);
        });
    }



    private void createEmptyChart(LineChart chart, String chartName, int color) {
        try {
            List<Entry> emptyEntries = new ArrayList<>();
            List<String> emptyLabels = new ArrayList<>();

            for (int i = 0; i < 24; i += 6) {
                emptyEntries.add(new Entry(i, 0f));
                emptyLabels.add(String.format("%02d:00", i));
            }

            LineDataSet dataSet = new LineDataSet(emptyEntries, chartName + " (No Data)");
            dataSet.setColor(Color.GRAY);
            dataSet.setLineWidth(1f);
            dataSet.setDrawCircles(false);
            dataSet.setDrawFilled(false);

            LineData lineData = new LineData(dataSet);
            chart.setData(lineData);

            configureDashboardStyleChart(chart, emptyLabels);

        } catch (Exception e) {
            Log.e(TAG, "Error creating empty chart: " + e.getMessage());
        }
    }

    private void updateChartsWithDefaults(RealTimeDataProcessor.RealTimeData realTimeData) {
        List<String> hourLabels = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            hourLabels.add(String.format("%02d:00", i));
        }

        if (area1Chart != null) {
            setupAreaChart(area1Chart, realTimeData.hourlyData, hourLabels,
                    "Area 1", getResources().getColor(R.color.brown), realTimeData);
        }

        if (area2Chart != null) {
            setupAreaChart(area2Chart, realTimeData.hourlyData, hourLabels,
                    "Area 2", getResources().getColor(R.color.brown), realTimeData);
        }

        if (area3Chart != null) {
            setupAreaChart(area3Chart, realTimeData.hourlyData, hourLabels,
                    "Area 3", getResources().getColor(R.color.brown), realTimeData);
        }
    }


    /**
     * Setup individual area chart
     */
    private void setupAreaChart(LineChart chart, List<RealTimeDataProcessor.HourlyData> hourlyData,
                                List<String> labels, String chartName, int color,
                                RealTimeDataProcessor.RealTimeData realTimeData) {
        if (chart == null) {
            Log.w(TAG, "Chart is null for " + chartName);
            return;
        }

        try {
            List<Entry> entries = new ArrayList<>();
            List<String> usedLabels = new ArrayList<>();

            Log.d(TAG, "Setting up " + chartName + " with " + hourlyData.size() + " hourly data points");

            // FIXED: For area charts, we need to use a different approach since HourlyData only has total consumption
            // We'll create a proportional distribution based on area data percentages

            double area1Percentage = 0.0;
            double area2Percentage = 0.0;
            double area3Percentage = 0.0;

            // Get area percentages from the real-time data
            if (realTimeData != null) {
                double totalAreaConsumption = realTimeData.area1Data.consumption +
                        realTimeData.area2Data.consumption +
                        realTimeData.area3Data.consumption;

                if (totalAreaConsumption > 0) {
                    area1Percentage = realTimeData.area1Data.consumption / totalAreaConsumption;
                    area2Percentage = realTimeData.area2Data.consumption / totalAreaConsumption;
                    area3Percentage = realTimeData.area3Data.consumption / totalAreaConsumption;
                } else {
                    // Default equal distribution if no data
                    area1Percentage = area2Percentage = area3Percentage = 1.0 / 3.0;
                }
            }

            // Process all 24 hours
            for (int hour = 0; hour < 24; hour++) {
                float value = 0f;

                // Find matching hourly data
                for (RealTimeDataProcessor.HourlyData data : hourlyData) {
                    if (data.hour.equals(String.format("%02d", hour))) {
                        // CORRECTED: Use total consumption and distribute by area percentage
                        if (chartName.contains("Area 1") || chartName.contains("Bedroom")) {
                            value = (float) (data.consumption * area1Percentage);
                        } else if (chartName.contains("Area 2") || chartName.contains("Living")) {
                            value = (float) (data.consumption * area2Percentage);
                        } else if (chartName.contains("Area 3") || chartName.contains("Kitchen")) {
                            value = (float) (data.consumption * area3Percentage);
                        } else {
                            // For total consumption chart
                            value = (float) data.consumption;
                        }
                        break;
                    }
                }

                entries.add(new Entry(hour, Math.max(0f, value)));
                usedLabels.add(String.format("%02d:00", hour));

                if (value > 0) {
                    Log.d(TAG, chartName + " - Hour " + hour + ": " + value + " kWh");
                }
            }

            if (entries.isEmpty()) {
                Log.w(TAG, "No entries created for " + chartName + ", creating default entries");
                for (int i = 0; i < 24; i += 4) {
                    entries.add(new Entry(i, 0f));
                    usedLabels.add(String.format("%02d:00", i));
                }
            }

            // Create dataset with dashboard-style formatting
            LineDataSet dataSet = new LineDataSet(entries, chartName);
            dataSet.setColor(color);
            dataSet.setCircleColor(color);
            dataSet.setLineWidth(2f);
            dataSet.setCircleRadius(4f);
            dataSet.setDrawCircleHole(false);
            dataSet.setDrawValues(false);
            dataSet.setDrawFilled(true);
            dataSet.setFillColor(color);
            dataSet.setFillAlpha(50);

            // CORRECTED: Use only available methods
            dataSet.setMode(LineDataSet.Mode.LINEAR);

            LineData lineData = new LineData(dataSet);
            chart.setData(lineData);

            configureDashboardStyleChart(chart, usedLabels);

            Log.d(TAG, chartName + " updated successfully with " + entries.size() + " entries");

        } catch (Exception e) {
            Log.e(TAG, "Error setting up area chart " + chartName + ": " + e.getMessage(), e);
            createEmptyChart(chart, chartName, color);
        }
    }


    // New method to apply dashboard-style formatting
    private void configureDashboardStyleChart(LineChart chart, List<String> labels) {
        if (chart == null) return;

        try {
            chart.getDescription().setEnabled(false);
            chart.setTouchEnabled(true);
            chart.setDragEnabled(true);
            chart.setScaleEnabled(false);
            chart.setPinchZoom(false);
            chart.setDrawGridBackground(false);
            chart.setDrawBorders(false);

            chart.getAxisRight().setEnabled(false);

            XAxis xAxis = chart.getXAxis();
            xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);

            if (labels != null && !labels.isEmpty()) {
                List<String> reducedLabels = new ArrayList<>();
                for (int i = 0; i < 24; i += 4) {
                    if (i < labels.size()) {
                        reducedLabels.add(labels.get(i));
                    } else {
                        reducedLabels.add(String.format("%02d:00", i));
                    }
                }
                xAxis.setValueFormatter(new IndexAxisValueFormatter(reducedLabels));
                xAxis.setLabelCount(reducedLabels.size(), true);
                xAxis.setGranularity(4f);
            }

            xAxis.setTextColor(getResources().getColor(R.color.brown));
            xAxis.setTextSize(10f);
            xAxis.setDrawGridLines(true);
            xAxis.setGridColor(getResources().getColor(R.color.brown));
            xAxis.setDrawLabels(true);

            YAxis leftAxis = chart.getAxisLeft();
            leftAxis.setTextColor(getResources().getColor(R.color.brown));
            leftAxis.setTextSize(10f);
            leftAxis.setGridColor(getResources().getColor(R.color.brown));
            leftAxis.setDrawGridLines(true);
            leftAxis.setAxisMinimum(0f);
            leftAxis.setGranularityEnabled(true);
            leftAxis.setGranularity(0.1f);

            chart.setExtraBottomOffset(10f);

            Legend legend = chart.getLegend();
            legend.setVerticalAlignment(Legend.LegendVerticalAlignment.TOP);
            legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.LEFT);
            legend.setOrientation(Legend.LegendOrientation.HORIZONTAL);
            legend.setForm(Legend.LegendForm.LINE);
            legend.setTextColor(getResources().getColor(R.color.brown));
            legend.setEnabled(true);

            chart.notifyDataSetChanged();
            chart.invalidate();

        } catch (Exception e) {
            Log.e(TAG, "Error configuring dashboard-style chart: " + e.getMessage());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Start refresh timer
        if (refreshHandler != null && refreshRunnable != null) {
            refreshHandler.post(refreshRunnable);
        }
        // Load fresh data
        loadRealTimeData();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Stop refresh timer
        if (refreshHandler != null && refreshRunnable != null) {
            refreshHandler.removeCallbacks(refreshRunnable);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up
        if (refreshHandler != null && refreshRunnable != null) {
            refreshHandler.removeCallbacks(refreshRunnable);
        }
    }
}