package com.example.sampleiwatts;

import android.app.DatePickerDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
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
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class HistoricalDataActivity extends AppCompatActivity {

    private static final String TAG = "HistoricalDataActivity";

    // Philippine timezone - same as real-time monitoring
    private static final TimeZone PHILIPPINE_TIMEZONE = TimeZone.getTimeZone("Asia/Manila");

    // Date selection components
    private EditText startDateEdit;
    private EditText endDateEdit;
    private Calendar startDate;
    private Calendar endDate;

    // Date formatters with Philippine timezone
    private SimpleDateFormat displayFormat;
    private SimpleDateFormat firebaseFormat;

    // Summary components
    private TextView selectedTotalConsumption;
    private TextView selectedDailyAverage;
    private TextView selectedTotalCost;
    private TextView selectedPeakDay;

    // Comparison components
    private TextView previousDateRange;
    private TextView previousTotalConsumption;
    private TextView previousTotalCost;
    private TextView selectedDateRange;
    private TextView selectedDateRangeTotalConsumption;
    private TextView selectedDateRangeTotalCost;

    // Chart components
    private FrameLayout mainChartContainer;
    private LineChart mainChart;

    // Area chart components
    private FrameLayout area1ChartContainer;
    private FrameLayout area2ChartContainer;
    private FrameLayout area3ChartContainer;
    private LineChart area1Chart;
    private LineChart area2Chart;
    private LineChart area3Chart;

    // Area data components
    private TextView area1TotalConsumption;
    private TextView area1EstimatedCost;
    private TextView area1PeakConsumption;
    private TextView area1SharePercentage;

    private TextView area2TotalConsumption;
    private TextView area2EstimatedCost;
    private TextView area2PeakConsumption;
    private TextView area2SharePercentage;

    private TextView area3TotalConsumption;
    private TextView area3EstimatedCost;
    private TextView area3PeakConsumption;
    private TextView area3SharePercentage;

    // Data storage
    private Map<String, List<Map<String, Object>>> historicalData;
    private double electricityRate = 9.85; // Default BATELEC II rate

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_historical_data);

        // Set up bottom navigation
        LinearLayout buttonLayout = findViewById(R.id.button);
        ButtonNavigator.setupButtons(this, buttonLayout);

        initializeViews();
        initializeDateFormatters();
        initializeDates();
        setupDatePickers();
        setupChartContainers();
        loadHistoricalData();

        Log.d(TAG, "HistoricalDataActivity created with Philippine timezone");
    }

    /**
     * Initialize all UI components
     */
    private void initializeViews() {
        // Date selection
        startDateEdit = findViewById(R.id.startdate);
        endDateEdit = findViewById(R.id.enddate);

        // Summary views
        selectedTotalConsumption = findViewById(R.id.selected_total_consumption);
        selectedDailyAverage = findViewById(R.id.selected_daily_average);
        selectedTotalCost = findViewById(R.id.selected_total_cost);
        selectedPeakDay = findViewById(R.id.selected_peak_day);

        // Comparison views
        previousDateRange = findViewById(R.id.previousdaterange);
        previousTotalConsumption = findViewById(R.id.previoustotalconsumption);
        previousTotalCost = findViewById(R.id.previoustotalcost);
        selectedDateRange = findViewById(R.id.selecteddaterange);
        selectedDateRangeTotalConsumption = findViewById(R.id.selecteddaterangetotalconsumption);
        selectedDateRangeTotalCost = findViewById(R.id.selecteddaterangetotalcost);

        // Chart containers
        mainChartContainer = findViewById(R.id.selecteddaterange_chart_container);
        area1ChartContainer = findViewById(R.id.area1_chart_container);
        area2ChartContainer = findViewById(R.id.area2_chart_container);
        area3ChartContainer = findViewById(R.id.area3_chart_container);

        // Area data views
        area1TotalConsumption = findViewById(R.id.area1_total_consumption);
        area1EstimatedCost = findViewById(R.id.area1_estimated_cost);
        area1PeakConsumption = findViewById(R.id.area1_peak_consumption);
        area1SharePercentage = findViewById(R.id.area1_share_percentage);

        area2TotalConsumption = findViewById(R.id.area2_total_consumption);
        area2EstimatedCost = findViewById(R.id.area2_estimated_cost);
        area2PeakConsumption = findViewById(R.id.area2_peak_consumption);
        area2SharePercentage = findViewById(R.id.area2_share_percentage);

        area3TotalConsumption = findViewById(R.id.area3_total_consumption);
        area3EstimatedCost = findViewById(R.id.area3_estimated_cost);
        area3PeakConsumption = findViewById(R.id.area3_peak_consumption);
        area3SharePercentage = findViewById(R.id.area3_share_percentage);

        // Initialize data storage
        historicalData = new HashMap<>();

        Log.d(TAG, "Views initialized successfully");
    }

    /**
     * Initialize date formatters with Philippine timezone
     */
    private void initializeDateFormatters() {
        // Display format for UI (MM/dd/yyyy)
        displayFormat = new SimpleDateFormat("MM/dd/yyyy", Locale.getDefault());
        displayFormat.setTimeZone(PHILIPPINE_TIMEZONE);

        // Firebase format for database queries (yyyy-MM-dd)
        firebaseFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        firebaseFormat.setTimeZone(PHILIPPINE_TIMEZONE);

        Log.d(TAG, "Date formatters initialized with Philippine timezone: " + PHILIPPINE_TIMEZONE.getDisplayName());
    }

    /**
     * Initialize default date range (last 7 days) using Philippine timezone
     */
    private void initializeDates() {
        // Initialize calendars with Philippine timezone
        startDate = Calendar.getInstance(PHILIPPINE_TIMEZONE);
        endDate = Calendar.getInstance(PHILIPPINE_TIMEZONE);

        // Set default range to last 7 days
        startDate.add(Calendar.DAY_OF_MONTH, -7);

        updateDateDisplays();

        Log.d(TAG, "Dates initialized - Start: " + firebaseFormat.format(startDate.getTime()) +
                ", End: " + firebaseFormat.format(endDate.getTime()));
    }

    /**
     * Setup date picker dialogs
     */
    private void setupDatePickers() {
        startDateEdit.setOnClickListener(v -> showDatePicker(true));
        endDateEdit.setOnClickListener(v -> showDatePicker(false));
    }

    /**
     * Show date picker dialog with Philippine timezone consideration
     */
    private void showDatePicker(boolean isStartDate) {
        Calendar calendar = isStartDate ? startDate : endDate;

        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    // Set the date in Philippine timezone
                    calendar.setTimeZone(PHILIPPINE_TIMEZONE);
                    calendar.set(Calendar.YEAR, year);
                    calendar.set(Calendar.MONTH, month);
                    calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    calendar.set(Calendar.HOUR_OF_DAY, 0);
                    calendar.set(Calendar.MINUTE, 0);
                    calendar.set(Calendar.SECOND, 0);
                    calendar.set(Calendar.MILLISECOND, 0);

                    // Validate date range
                    if (startDate.after(endDate)) {
                        Toast.makeText(this, "Start date cannot be after end date", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    updateDateDisplays();
                    loadHistoricalData();

                    Log.d(TAG, "Date selected - " + (isStartDate ? "Start" : "End") +
                            ": " + firebaseFormat.format(calendar.getTime()));
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );

        datePickerDialog.show();
    }

    /**
     * Update date display fields using Philippine timezone
     */
    private void updateDateDisplays() {
        startDateEdit.setText(displayFormat.format(startDate.getTime()));
        endDateEdit.setText(displayFormat.format(endDate.getTime()));

        // Update comparison date ranges
        String selectedRange = displayFormat.format(startDate.getTime()) + " - " + displayFormat.format(endDate.getTime());
        selectedDateRange.setText(selectedRange);

        // Calculate previous period using Philippine timezone
        long daysDiff = (endDate.getTimeInMillis() - startDate.getTimeInMillis()) / (1000 * 60 * 60 * 24);
        Calendar prevStart = Calendar.getInstance(PHILIPPINE_TIMEZONE);
        Calendar prevEnd = Calendar.getInstance(PHILIPPINE_TIMEZONE);

        prevStart.setTime(startDate.getTime());
        prevEnd.setTime(endDate.getTime());
        prevStart.add(Calendar.DAY_OF_MONTH, -(int)daysDiff - 1);
        prevEnd.add(Calendar.DAY_OF_MONTH, -(int)daysDiff - 1);

        String previousRange = displayFormat.format(prevStart.getTime()) + " - " + displayFormat.format(prevEnd.getTime());
        previousDateRange.setText(previousRange);

        Log.d(TAG, "Date displays updated - Selected: " + selectedRange + ", Previous: " + previousRange);
    }

    /**
     * Setup LineChart instances in FrameLayout containers
     */
    private void setupChartContainers() {
        try {
            // Create main consumption chart
            if (mainChartContainer != null) {
                mainChart = new LineChart(this);
                mainChartContainer.removeAllViews();
                mainChartContainer.addView(mainChart);
            }

            // Create area charts
            if (area1ChartContainer != null) {
                area1Chart = new LineChart(this);
                area1ChartContainer.removeAllViews();
                area1ChartContainer.addView(area1Chart);
            }

            if (area2ChartContainer != null) {
                area2Chart = new LineChart(this);
                area2ChartContainer.removeAllViews();
                area2ChartContainer.addView(area2Chart);
            }

            if (area3ChartContainer != null) {
                area3Chart = new LineChart(this);
                area3ChartContainer.removeAllViews();
                area3ChartContainer.addView(area3Chart);
            }

            Log.d(TAG, "Chart containers setup successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error setting up chart containers", e);
        }
    }

    /**
     * FIXED: Load historical data using correct query method
     */
    private void loadHistoricalData() {
        try {
            DatabaseReference summariesRef = FirebaseDatabase.getInstance().getReference("daily_summaries");

            // Format dates for Firebase query using Philippine timezone
            String startDateStr = firebaseFormat.format(startDate.getTime());
            String endDateStr = firebaseFormat.format(endDate.getTime());

            Log.d(TAG, "Loading historical data from " + startDateStr + " to " + endDateStr);

            // FIXED: Query all daily summaries, then filter by date range
            summariesRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    processHistoricalData(dataSnapshot, startDateStr, endDateStr);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.e(TAG, "Failed to load historical data: " + error.getMessage());
                    Toast.makeText(HistoricalDataActivity.this,
                            "Failed to load historical data", Toast.LENGTH_SHORT).show();
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Error loading historical data", e);
        }
    }

    /**
     * FIXED: Process historical data with correct field names and date filtering
     */
    private void processHistoricalData(DataSnapshot dataSnapshot, String startDateStr, String endDateStr) {
        try {
            historicalData.clear();

            double totalConsumption = 0.0;
            double totalCost = 0.0;
            String peakDay = "";
            double peakConsumption = 0.0;
            double maxDailyPeak = 0.0;
            int dayCount = 0;

            // Area totals and peaks
            double area1Total = 0.0, area2Total = 0.0, area3Total = 0.0;
            double area1MaxPeak = 0.0, area2MaxPeak = 0.0, area3MaxPeak = 0.0;

            // Daily data for charts
            List<Double> dailyConsumption = new ArrayList<>();
            List<String> dateLabels = new ArrayList<>();
            Map<String, List<Double>> areaDailyData = new HashMap<>();
            areaDailyData.put("area1", new ArrayList<>());
            areaDailyData.put("area2", new ArrayList<>());
            areaDailyData.put("area3", new ArrayList<>());

            for (DataSnapshot daySnapshot : dataSnapshot.getChildren()) {
                try {
                    String dateKey = daySnapshot.getKey();

                    // FIXED: Filter by date range using string comparison
                    if (dateKey != null && dateKey.compareTo(startDateStr) >= 0 && dateKey.compareTo(endDateStr) <= 0) {

                        Map<String, Object> dayData = (Map<String, Object>) daySnapshot.getValue();
                        if (dayData == null) continue;

                        // FIXED: Use correct field names from your database structure
                        Double dayConsumption = getDoubleValue(dayData.get("total_kwh"));     // NOT total_energy_kwh
                        Double dayCost = getDoubleValue(dayData.get("total_cost"));          // NOT total_cost_php
                        Double dayPeak = getDoubleValue(dayData.get("peak_watts"));

                        if (dayConsumption != null) {
                            totalConsumption += dayConsumption;
                            dailyConsumption.add(dayConsumption);
                            dateLabels.add(dateKey);
                            dayCount++;

                            // Track peak day by consumption
                            if (dayConsumption > peakConsumption) {
                                peakConsumption = dayConsumption;
                                peakDay = dateKey;
                            }
                        }

                        if (dayCost != null) {
                            totalCost += dayCost;
                        }

                        // Track highest daily peak watts
                        if (dayPeak != null && dayPeak > maxDailyPeak) {
                            maxDailyPeak = dayPeak;
                        }

                        // FIXED: Process area data from area_breakdown (not area_data)
                        Map<String, Object> areaBreakdown = (Map<String, Object>) dayData.get("area_breakdown");
                        if (areaBreakdown != null) {
                            // Get area consumption values
                            double area1Kwh = getAreaConsumption(areaBreakdown, "area1");
                            double area2Kwh = getAreaConsumption(areaBreakdown, "area2");
                            double area3Kwh = getAreaConsumption(areaBreakdown, "area3");

                            area1Total += area1Kwh;
                            area2Total += area2Kwh;
                            area3Total += area3Kwh;

                            // FIXED: Calculate proportional peaks for this day
                            if (dayPeak != null && dayConsumption != null && dayConsumption > 0) {
                                double area1Peak = dayPeak * (area1Kwh / dayConsumption);
                                double area2Peak = dayPeak * (area2Kwh / dayConsumption);
                                double area3Peak = dayPeak * (area3Kwh / dayConsumption);

                                // Track maximum peaks across all days
                                area1MaxPeak = Math.max(area1MaxPeak, area1Peak);
                                area2MaxPeak = Math.max(area2MaxPeak, area2Peak);
                                area3MaxPeak = Math.max(area3MaxPeak, area3Peak);
                            }

                            // Process hourly data for charts
                            processAreaDailyData(areaDailyData, "area1", area1Kwh);
                            processAreaDailyData(areaDailyData, "area2", area2Kwh);
                            processAreaDailyData(areaDailyData, "area3", area3Kwh);
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Error processing day data for " + daySnapshot.getKey() + ": " + e.getMessage());
                }
            }

            Log.d(TAG, String.format("Processed %d days of historical data", dayCount));

            // Update UI with processed data
            updateSummaryDisplay(totalConsumption, totalCost, peakDay, dayCount);
            updateAreaDisplays(area1Total, area2Total, area3Total, totalConsumption,
                    area1MaxPeak, area2MaxPeak, area3MaxPeak);
            updateCharts(dailyConsumption, dateLabels, areaDailyData);

            // Load previous period for comparison
            loadPreviousPeriodData();

        } catch (Exception e) {
            Log.e(TAG, "Error processing historical data", e);
        }
    }

    /**
     * FIXED: Get area consumption with correct field names
     */
    private double getAreaConsumption(Map<String, Object> areaBreakdown, String areaKey) {
        try {
            Map<String, Object> areaData = (Map<String, Object>) areaBreakdown.get(areaKey);
            if (areaData != null) {
                // FIXED: Use 'kwh' field (not 'total_energy_kwh')
                Double consumption = getDoubleValue(areaData.get("kwh"));
                if (consumption != null) {
                    return consumption;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error getting area consumption for " + areaKey + ": " + e.getMessage());
        }
        return 0.0;
    }

    /**
     * Process area hourly data for charts
     */
    private void processAreaDailyData(Map<String, List<Double>> areaDailyData, String areaKey, double dailyConsumption) {
        List<Double> dailyList = areaDailyData.get(areaKey);
        if (dailyList != null) {
            dailyList.add(dailyConsumption);  // Simply add the daily consumption
        }
    }

    /**
     * Load previous period data for comparison using Philippine timezone
     */
    private void loadPreviousPeriodData() {
        try {
            long daysDiff = (endDate.getTimeInMillis() - startDate.getTimeInMillis()) / (1000 * 60 * 60 * 24);
            Calendar prevStart = Calendar.getInstance(PHILIPPINE_TIMEZONE);
            Calendar prevEnd = Calendar.getInstance(PHILIPPINE_TIMEZONE);

            prevStart.setTime(startDate.getTime());
            prevEnd.setTime(endDate.getTime());
            prevStart.add(Calendar.DAY_OF_MONTH, -(int)daysDiff - 1);
            prevEnd.add(Calendar.DAY_OF_MONTH, -(int)daysDiff - 1);

            String prevStartStr = firebaseFormat.format(prevStart.getTime());
            String prevEndStr = firebaseFormat.format(prevEnd.getTime());

            Log.d(TAG, "Loading previous period: " + prevStartStr + " to " + prevEndStr);

            DatabaseReference summariesRef = FirebaseDatabase.getInstance().getReference("daily_summaries");
            summariesRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    processPreviousPeriodData(dataSnapshot, prevStartStr, prevEndStr);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.w(TAG, "Failed to load previous period data: " + error.getMessage());
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Error loading previous period data", e);
        }
    }

    /**
     * FIXED: Process previous period data with correct field names
     */
    private void processPreviousPeriodData(DataSnapshot dataSnapshot, String prevStartStr, String prevEndStr) {
        try {
            double prevTotalConsumption = 0.0;
            double prevTotalCost = 0.0;

            for (DataSnapshot daySnapshot : dataSnapshot.getChildren()) {
                String dateKey = daySnapshot.getKey();

                if (dateKey != null && dateKey.compareTo(prevStartStr) >= 0 && dateKey.compareTo(prevEndStr) <= 0) {
                    Map<String, Object> dayData = (Map<String, Object>) daySnapshot.getValue();
                    if (dayData != null) {
                        // FIXED: Use correct field names
                        Double dayConsumption = getDoubleValue(dayData.get("total_kwh"));
                        Double dayCost = getDoubleValue(dayData.get("total_cost"));

                        if (dayConsumption != null) {
                            prevTotalConsumption += dayConsumption;
                        }
                        if (dayCost != null) {
                            prevTotalCost += dayCost;
                        }
                    }
                }
            }

            updateComparisonDisplay(prevTotalConsumption, prevTotalCost);

        } catch (Exception e) {
            Log.e(TAG, "Error processing previous period data", e);
        }
    }

    /**
     * Update summary display with processed data
     */
    private void updateSummaryDisplay(double totalConsumption, double totalCost, String peakDay, int dayCount) {
        try {
            selectedTotalConsumption.setText(String.format(Locale.getDefault(), "%.2f kWh", totalConsumption));
            selectedTotalCost.setText(String.format(Locale.getDefault(), "₱%.2f", totalCost));

            if (dayCount > 0) {
                double dailyAvg = totalConsumption / dayCount;
                selectedDailyAverage.setText(String.format(Locale.getDefault(), "%.2f kWh", dailyAvg));
            } else {
                selectedDailyAverage.setText("0.0 kWh");
            }

            if (!peakDay.isEmpty()) {
                selectedPeakDay.setText(peakDay);
            } else {
                selectedPeakDay.setText("--");
            }

            // Update comparison section (selected period)
            selectedDateRangeTotalConsumption.setText(String.format(Locale.getDefault(), "%.2f kWh", totalConsumption));
            selectedDateRangeTotalCost.setText(String.format(Locale.getDefault(), "₱%.2f", totalCost));

        } catch (Exception e) {
            Log.e(TAG, "Error updating summary display", e);
        }
    }

    /**
     * FIXED: Update area displays with proper peak calculations
     */
    private void updateAreaDisplays(double area1Total, double area2Total, double area3Total,
                                    double grandTotal, double area1Peak, double area2Peak, double area3Peak) {
        try {
            // Calculate percentages
            double area1Percentage = grandTotal > 0 ? (area1Total / grandTotal) * 100 : 0;
            double area2Percentage = grandTotal > 0 ? (area2Total / grandTotal) * 100 : 0;
            double area3Percentage = grandTotal > 0 ? (area3Total / grandTotal) * 100 : 0;

            // Update Area 1
            area1TotalConsumption.setText(String.format(Locale.getDefault(), "%.2f kWh", area1Total));
            area1EstimatedCost.setText(String.format(Locale.getDefault(), "₱%.2f", area1Total * electricityRate));
            area1SharePercentage.setText(String.format(Locale.getDefault(), "%.1f%%", area1Percentage));
            area1PeakConsumption.setText(String.format(Locale.getDefault(), "%.0f W", area1Peak));

            // Update Area 2
            area2TotalConsumption.setText(String.format(Locale.getDefault(), "%.2f kWh", area2Total));
            area2EstimatedCost.setText(String.format(Locale.getDefault(), "₱%.2f", area2Total * electricityRate));
            area2SharePercentage.setText(String.format(Locale.getDefault(), "%.1f%%", area2Percentage));
            area2PeakConsumption.setText(String.format(Locale.getDefault(), "%.0f W", area2Peak));

            // Update Area 3
            area3TotalConsumption.setText(String.format(Locale.getDefault(), "%.2f kWh", area3Total));
            area3EstimatedCost.setText(String.format(Locale.getDefault(), "₱%.2f", area3Total * electricityRate));
            area3SharePercentage.setText(String.format(Locale.getDefault(), "%.1f%%", area3Percentage));
            area3PeakConsumption.setText(String.format(Locale.getDefault(), "%.0f W", area3Peak));

            // Verify peak calculations add up
            double calculatedTotal = area1Peak + area2Peak + area3Peak;
            Log.d(TAG, String.format("Historical area peaks: %.0f + %.0f + %.0f = %.0f W",
                    area1Peak, area2Peak, area3Peak, calculatedTotal));

        } catch (Exception e) {
            Log.e(TAG, "Error updating area displays", e);
        }
    }

    /**
     * Update comparison display with previous period data
     */
    private void updateComparisonDisplay(double prevTotalConsumption, double prevTotalCost) {
        try {
            previousTotalConsumption.setText(String.format(Locale.getDefault(), "%.2f kWh", prevTotalConsumption));
            previousTotalCost.setText(String.format(Locale.getDefault(), "₱%.2f", prevTotalCost));
        } catch (Exception e) {
            Log.e(TAG, "Error updating comparison display", e);
        }
    }

    /**
     * Update all charts with historical data
     */
    private void updateCharts(List<Double> dailyConsumption, List<String> dateLabels,
                              Map<String, List<Double>> areaDailyData) {
        try {
            // Update main consumption chart
            if (mainChart != null && !dailyConsumption.isEmpty()) {
                setupDailyChart(mainChart, dailyConsumption, dateLabels, "Daily Consumption", Color.rgb(134, 59, 23));
            }

            // Update area charts with 24-hour patterns
            if (area1Chart != null) {
                List<Double> area1Data = areaDailyData.get("area1");  // ✅ Renamed
                if (area1Data != null && !area1Data.isEmpty()) {
                    setupAreaDailyChart(area1Chart, area1Data, dateLabels, "Area 1", Color.rgb(255, 193, 7));  // ✅ Renamed method
                }
            }

            if (area2Chart != null) {
                List<Double> area2Data = areaDailyData.get("area2");  // ✅ Renamed
                if (area2Data != null && !area2Data.isEmpty()) {
                    setupAreaDailyChart(area2Chart, area2Data, dateLabels, "Area 2", Color.rgb(220, 53, 69));  // ✅ Renamed method
                }
            }

            if (area3Chart != null) {
                List<Double> area3Data = areaDailyData.get("area3");  // ✅ Renamed
                if (area3Data != null && !area3Data.isEmpty()) {
                    setupAreaDailyChart(area3Chart, area3Data, dateLabels, "Area 3", Color.rgb(40, 167, 69));  // ✅ Renamed method
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error updating charts", e);
        }
    }

    /**
     * Setup daily consumption chart
     */
    private void setupDailyChart(LineChart chart, List<Double> data, List<String> labels, String chartName, int color) {
        try {
            // Reduce data points if too many (show every 2nd or 3rd day if more than 10 days)
            List<Entry> entries = new ArrayList<>();
            List<String> reducedLabels = new ArrayList<>();

            int step = 1;
            if (data.size() > 10) {
                step = Math.max(1, data.size() / 8); // Show maximum 8 points
            }

            int entryIndex = 0;
            for (int i = 0; i < data.size(); i += step) {
                entries.add(new Entry(entryIndex, data.get(i).floatValue()));

                // Format date labels to be shorter (e.g., "Mar 5" instead of "2025-03-05")
                String originalLabel = labels.get(i);
                String shortLabel = formatDateLabel(originalLabel);
                reducedLabels.add(shortLabel);

                entryIndex++;
            }

            // Create dataset with dashboard-style formatting
            LineDataSet dataSet = new LineDataSet(entries, chartName);
            dataSet.setColor(getResources().getColor(R.color.brown));
            dataSet.setCircleColor(getResources().getColor(R.color.brown));
            dataSet.setLineWidth(2f);
            dataSet.setCircleRadius(4f);
            dataSet.setDrawCircleHole(false);
            dataSet.setValueTextSize(0f);
            dataSet.setDrawFilled(true);
            dataSet.setFillColor(color);
            dataSet.setFillAlpha(50);

            LineData lineData = new LineData(dataSet);
            chart.setData(lineData);

            // Apply dashboard-style configuration
            configureHistoricalChart(chart, reducedLabels);

        } catch (Exception e) {
            Log.e(TAG, "Error setting up daily chart", e);
        }
    }

    // New method for historical chart configuration
    private void configureHistoricalChart(LineChart chart, List<String> labels) {
        // Match dashboard style
        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(false);
        chart.setPinchZoom(false);
        chart.setDrawGridBackground(false);
        chart.setDrawBorders(false);

        // Disable right Y-axis
        chart.getAxisRight().setEnabled(false);

        // Configure X-axis with better readability
        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setTextColor(getResources().getColor(R.color.brown));
        xAxis.setTextSize(9f); // Readable size
        xAxis.setDrawGridLines(true);
        xAxis.setGridColor(Color.LTGRAY);
        xAxis.setGranularity(1f);
        xAxis.setLabelRotationAngle(-45f); // Tilt labels to prevent overlap
        xAxis.setLabelCount(Math.min(labels.size(), 6), false); // Max 6 labels

        // Configure Y-axis (left)
        chart.getAxisLeft().setTextColor(getResources().getColor(R.color.brown));
        chart.getAxisLeft().setTextSize(9f);
        chart.getAxisLeft().setDrawGridLines(true);
        chart.getAxisLeft().setGridColor(Color.LTGRAY);
        chart.getAxisLeft().setAxisMinimum(0f);

        // Add extra space for rotated labels
        chart.setExtraBottomOffset(20f);
        chart.setExtraRightOffset(10f);

        // Configure legend
        Legend legend = chart.getLegend();
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.TOP);
        legend.setForm(Legend.LegendForm.LINE);
        legend.setTextColor(getResources().getColor(R.color.brown));
        legend.setEnabled(true);

        chart.invalidate();
    }

    // Helper method to format date labels (e.g., "2025-03-05" -> "Mar 5")
    private String formatDateLabel(String dateString) {
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            SimpleDateFormat outputFormat = new SimpleDateFormat("MMM d", Locale.getDefault());
            Date date = inputFormat.parse(dateString);
            return outputFormat.format(date);
        } catch (Exception e) {
            // If parsing fails, return last 5 characters (MM-dd)
            if (dateString.length() >= 5) {
                return dateString.substring(dateString.length() - 5);
            }
            return dateString;
        }
    }

    /**
     * Setup 24-hour area chart
     */
    private void setupAreaDailyChart(LineChart chart, List<Double> dailyAreaData, List<String> dateLabels, String areaName, int color) {
        try {
            // Apply same data reduction as main chart
            List<Entry> entries = new ArrayList<>();
            List<String> reducedLabels = new ArrayList<>();

            int step = 1;
            if (dailyAreaData.size() > 10) {
                step = Math.max(1, dailyAreaData.size() / 8);
            }

            int entryIndex = 0;
            for (int i = 0; i < dailyAreaData.size(); i += step) {
                entries.add(new Entry(entryIndex, dailyAreaData.get(i).floatValue()));
                reducedLabels.add(formatDateLabel(dateLabels.get(i)));
                entryIndex++;
            }

            // Create dataset with area-specific color
            LineDataSet dataSet = new LineDataSet(entries, areaName + " Daily Usage");
            dataSet.setColor(color);
            dataSet.setCircleColor(color);
            dataSet.setLineWidth(2f);
            dataSet.setCircleRadius(3f);
            dataSet.setDrawCircleHole(false);
            dataSet.setValueTextSize(0f);
            dataSet.setDrawFilled(true);
            dataSet.setFillColor(color);
            dataSet.setFillAlpha(50);

            LineData lineData = new LineData(dataSet);
            chart.setData(lineData);

            // Use the same configuration as main historical chart
            configureHistoricalChart(chart, reducedLabels);

        } catch (Exception e) {
            Log.e(TAG, "Error setting up area daily chart", e);
        }
    }

    /**
     * Configure chart appearance
     */
    private void configureChart(LineChart chart, String[] labels) {
        try {
            chart.getDescription().setEnabled(false);
            chart.setTouchEnabled(true);
            chart.setDragEnabled(true);
            chart.setScaleEnabled(false);
            chart.setPinchZoom(false);
            chart.setDrawGridBackground(false);
            chart.setDrawBorders(false);
            chart.getLegend().setEnabled(false);

            // Configure X-axis
            XAxis xAxis = chart.getXAxis();
            xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
            xAxis.setDrawGridLines(true);
            xAxis.setGridColor(Color.LTGRAY);
            xAxis.setTextColor(Color.GRAY);
            xAxis.setTextSize(8f);
            xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));

            if (labels.length <= 7) {
                xAxis.setLabelCount(labels.length);
            } else {
                xAxis.setLabelCount(6);
            }

            // Configure Y-axis (left)
            YAxis leftAxis = chart.getAxisLeft();
            leftAxis.setDrawGridLines(true);
            leftAxis.setGridColor(Color.LTGRAY);
            leftAxis.setTextColor(Color.GRAY);
            leftAxis.setTextSize(8f);
            leftAxis.setAxisMinimum(0f);

            // Disable right Y-axis
            chart.getAxisRight().setEnabled(false);

            chart.invalidate();

        } catch (Exception e) {
            Log.e(TAG, "Error configuring chart", e);
        }
    }

    /**
     * Safely convert Object to Double
     */
    private Double getDoubleValue(Object value) {
        if (value == null) return null;

        try {
            if (value instanceof Double) {
                return (Double) value;
            } else if (value instanceof Number) {
                return ((Number) value).doubleValue();
            } else if (value instanceof String) {
                return Double.parseDouble((String) value);
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not convert value to Double: " + value);
        }

        return null;
    }
}