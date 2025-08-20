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
import androidx.appcompat.app.AppCompatActivity;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.example.sampleiwatts.processors.RealTimeDataProcessor;
import java.util.ArrayList;
import java.util.List;

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

    // Navigation
    private ImageView navBarChart;
    private ImageView navHistory;
    private ImageView navHome;
    private ImageView navTrends;
    private ImageView navSettings;

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

        // Initialize data processor
        dataProcessor = new RealTimeDataProcessor(this);

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

        // Create LineChart instances and add them to containers
        setupChartContainers();



        Log.d(TAG, "Views initialized successfully");
    }

    /**
     * Setup LineChart instances in FrameLayout containers
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
     * Load real-time data from processor
     */
    private void loadRealTimeData() {
        Log.d(TAG, "Loading real-time data...");

        dataProcessor.processTodaysData(new RealTimeDataProcessor.DataProcessingCallback() {
            @Override
            public void onDataProcessed(RealTimeDataProcessor.RealTimeData realTimeData) {
                runOnUiThread(() -> {
                    updateUI(realTimeData);
                    Log.d(TAG, "UI updated successfully");
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(RealTimeMonitoringActivity.this,
                            "Error loading data: " + error, Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Data processing error: " + error);
                });
            }
        });
    }

    /**
     * Update UI with processed data
     */
    private void updateUI(RealTimeDataProcessor.RealTimeData data) {
        try {
            // Update overall summary
            updateOverallSummary(data);

            // Update area data
            updateAreaData(data.area1Data, area1Chart, area1TotalConsumption,
                    area1EstimatedCost, area1PeakConsumption, area1SharePercentage,
                    Color.parseColor("#FFD700")); // Gold color for area 1

            updateAreaData(data.area2Data, area2Chart, area2TotalConsumption,
                    area2EstimatedCost, area2PeakConsumption, area2SharePercentage,
                    Color.parseColor("#FF6B6B")); // Red color for area 2

            updateAreaData(data.area3Data, area3Chart, area3TotalConsumption,
                    area3EstimatedCost, area3PeakConsumption, area3SharePercentage,
                    Color.parseColor("#4ECDC4")); // Teal color for area 3

            Log.d(TAG, "All UI components updated successfully");

        } catch (Exception e) {
            Log.e(TAG, "Error updating UI: " + e.getMessage(), e);
        }
    }

    /**
     * Update overall summary section
     */
    private void updateOverallSummary(RealTimeDataProcessor.RealTimeData data) {
        todaysTotalValue.setText(String.format("%.2f kWh", data.totalKwhToday));
        todaysTotalPercentage.setText("Updated: " + data.lastUpdateTime);
        peakUsageValue.setText(String.format("%.0f W", data.peakUsageValue));
        peakUsageTime.setText(data.peakUsageTime.isEmpty() ? "--:--" : data.peakUsageTime);
        estimatedCostValue.setText(String.format("₱%.2f", data.estimatedCostToday));
        estimatedCostDetails.setText(String.format("Rate: ₱%.2f/kWh", data.electricityRate));
    }

    /**
     * Update area-specific data and chart
     */
    private void updateAreaData(RealTimeDataProcessor.AreaData areaData, LineChart chart,
                                TextView totalConsumption, TextView estimatedCost,
                                TextView peakConsumption, TextView sharePercentage, int color) {

        // Update text views
        totalConsumption.setText(String.format("%.2f kWh", areaData.totalConsumption));
        estimatedCost.setText(String.format("₱%.2f", areaData.estimatedCost));
        peakConsumption.setText(String.format("%.0f W at %s",
                areaData.peakConsumption,
                areaData.peakTime.isEmpty() ? "--:--" : areaData.peakTime));
        sharePercentage.setText(String.format("%.1f%%", areaData.sharePercentage));

        // Update chart
        setupLineChart(chart, areaData.hourlyData, color, areaData.name);
    }

    /**
     * Setup line chart for area consumption
     */
    private void setupLineChart(LineChart chart, List<Double> hourlyData, int color, String areaName) {
        if (hourlyData == null || hourlyData.isEmpty()) {
            chart.clear();
            return;
        }

        // Prepare data entries
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < hourlyData.size(); i++) {
            entries.add(new Entry(i, hourlyData.get(i).floatValue()));
        }

        // Create dataset
        LineDataSet dataSet = new LineDataSet(entries, areaName + " Usage");
        dataSet.setColor(color);
        dataSet.setCircleColor(color);
        dataSet.setLineWidth(2f);
        dataSet.setCircleRadius(3f);
        dataSet.setDrawCircleHole(false);
        dataSet.setValueTextSize(0f); // Hide values on points
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(color);
        dataSet.setFillAlpha(50);

        // Create line data
        LineData lineData = new LineData(dataSet);
        chart.setData(lineData);

        // Configure chart appearance
        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(false);
        chart.setPinchZoom(false);
        chart.setDrawGridBackground(false);
        chart.setDrawBorders(false);
        chart.getLegend().setEnabled(false);

        // Configure X-axis (hours)
        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(true);
        xAxis.setGridColor(Color.LTGRAY);
        xAxis.setTextColor(Color.GRAY);
        xAxis.setTextSize(8f);
        xAxis.setLabelCount(6); // Show every 4th hour (0, 4, 8, 12, 16, 20)

        // Set hour labels
        String[] hourLabels = new String[24];
        for (int i = 0; i < 24; i++) {
            hourLabels[i] = String.format("%02d:00", i);
        }
        xAxis.setValueFormatter(new IndexAxisValueFormatter(hourLabels));

        // Configure Y-axis (left)
        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(Color.LTGRAY);
        leftAxis.setTextColor(Color.GRAY);
        leftAxis.setTextSize(8f);
        leftAxis.setAxisMinimum(0f);

        // Disable right Y-axis
        chart.getAxisRight().setEnabled(false);

        // Refresh chart
        chart.invalidate();

        Log.d(TAG, "Chart setup completed for " + areaName);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "Activity resumed, starting refresh timer");

        // Start refresh timer
        refreshHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL);

        // Load data immediately
        loadRealTimeData();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "Activity paused, stopping refresh timer");

        // Stop refresh timer to save battery
        if (refreshHandler != null && refreshRunnable != null) {
            refreshHandler.removeCallbacks(refreshRunnable);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Activity destroyed");

        // Clean up resources
        if (refreshHandler != null && refreshRunnable != null) {
            refreshHandler.removeCallbacks(refreshRunnable);
        }
    }

    /**
     * Manual refresh method (can be called by swipe-to-refresh or button)
     */
    public void refreshData(View view) {
        Log.d(TAG, "Manual refresh triggered");
        loadRealTimeData();
    }
}