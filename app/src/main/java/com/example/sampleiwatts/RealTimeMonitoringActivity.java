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
        new Handler().postDelayed(() -> {
            Log.d(TAG, "🔄 Starting delayed real-time data load...");
            loadRealTimeData();
        }, 5000); // 5 second delay to let processing complete
    }

    private void showProcessingStatus(boolean isProcessing) {
        runOnUiThread(() -> {
            if (isProcessing) {
                Toast.makeText(this, "Processing data...", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "📊 Data processing in progress...");
            } else {
                Log.d(TAG, "✅ Data processing completed");
            }
        });
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

        // 🆕 SHOW PROCESSING STATUS:
        showProcessingStatus(true);

        // UPDATED: Use new method signature with date parameter
        String todayDate = getCurrentDate();
        dataProcessor.processRealTimeData(todayDate, new RealTimeDataProcessor.DataProcessingCallback() {
            @Override
            public void onDataProcessed(RealTimeDataProcessor.RealTimeData realTimeData) {
                // 🆕 HIDE PROCESSING STATUS:
                showProcessingStatus(false);

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
                showProcessingStatus(false);
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
            // Create hourly labels
            List<String> hourLabels = new ArrayList<>();
            for (int i = 0; i < 24; i++) {
                hourLabels.add(String.format("%02d:00", i));
            }

            // Update charts for each area
            if (area1Chart != null) {
                setupAreaChart(area1Chart, realTimeData.hourlyData, hourLabels, "Area 1", Color.rgb(255, 193, 7));
            }

            if (area2Chart != null) {
                setupAreaChart(area2Chart, realTimeData.hourlyData, hourLabels, "Area 2", Color.rgb(220, 53, 69));
            }

            if (area3Chart != null) {
                setupAreaChart(area3Chart, realTimeData.hourlyData, hourLabels, "Area 3", Color.rgb(40, 167, 69));
            }

        } catch (Exception e) {
            Log.e(TAG, "Error updating charts: " + e.getMessage(), e);
        }
    }

    /**
     * Setup individual area chart
     */
    private void setupAreaChart(LineChart chart, List<RealTimeDataProcessor.HourlyData> hourlyData,
                                List<String> labels, String chartName, int color) {
        try {
            List<Entry> entries = new ArrayList<>();

            // Create entries from hourly data
            for (int i = 0; i < 24; i++) {
                float value = 0f;

                // Find matching hourly data
                for (RealTimeDataProcessor.HourlyData data : hourlyData) {
                    if (data.hour.equals(String.format("%02d", i))) {
                        value = (float) data.consumption;
                        break;
                    }
                }

                entries.add(new Entry(i, value));
            }

            // Create dataset
            LineDataSet dataSet = new LineDataSet(entries, chartName);
            dataSet.setColor(color);
            dataSet.setCircleColor(color);
            dataSet.setLineWidth(2f);
            dataSet.setCircleRadius(3f);
            dataSet.setDrawCircleHole(false);
            dataSet.setDrawValues(false);
            dataSet.setDrawFilled(true);
            dataSet.setFillColor(color);
            dataSet.setFillAlpha(50);

            // Setup chart
            LineData lineData = new LineData(dataSet);
            chart.setData(lineData);

            // Customize chart appearance
            chart.getDescription().setEnabled(false);
            chart.setTouchEnabled(false);
            chart.setDragEnabled(false);
            chart.setScaleEnabled(false);
            chart.setPinchZoom(false);
            chart.setDrawGridBackground(false);

            // X-axis
            XAxis xAxis = chart.getXAxis();
            xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
            xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
            xAxis.setGranularity(1f);
            xAxis.setGranularityEnabled(true);
            xAxis.setTextColor(Color.GRAY);
            xAxis.setTextSize(8f);

            // Y-axis
            YAxis leftAxis = chart.getAxisLeft();
            leftAxis.setTextColor(Color.GRAY);
            leftAxis.setTextSize(8f);
            leftAxis.setAxisMinimum(0f);

            YAxis rightAxis = chart.getAxisRight();
            rightAxis.setEnabled(false);

            // Legend
            chart.getLegend().setEnabled(false);

            // Refresh chart
            chart.invalidate();

        } catch (Exception e) {
            Log.e(TAG, "Error setting up area chart: " + e.getMessage(), e);
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