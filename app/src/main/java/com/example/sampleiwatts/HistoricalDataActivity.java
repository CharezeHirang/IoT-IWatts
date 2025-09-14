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
    private static final TimeZone PHILIPPINE_TIMEZONE = TimeZone.getTimeZone("Asia/Manila");

    // Maximum date range allowed (30 days)
    private static final int MAX_DATE_RANGE_DAYS = 30;

    // Minimum date - when the system started collecting data (adjust as needed)
    private static final String SYSTEM_START_DATE = "2025-01-01"; // Adjust this date

    // Date selection components
    private EditText startDateEdit;
    private EditText endDateEdit;
    private Calendar startDate;
    private Calendar endDate;
    private Calendar todayCalendar;
    private Calendar systemStartCalendar;

    // Date formatters with Philippine timezone
    private SimpleDateFormat displayFormat;
    private SimpleDateFormat firebaseFormat;

    // Summary components
    private TextView selectedTotalConsumption;
    private TextView selectedDailyAverage;
    private TextView selectedTotalCost;
    private TextView selectedDateRange;
    private TextView previousTotalConsumption;
    private TextView previousDailyAverage;
    private TextView previousTotalCost;
    private TextView previousDateRange;

    // Chart components
    private LineChart mainChart;
    private LineChart area1Chart;
    private LineChart area2Chart;
    private LineChart area3Chart;
    private FrameLayout mainChartContainer;
    private FrameLayout area1ChartContainer;
    private FrameLayout area2ChartContainer;
    private FrameLayout area3ChartContainer;

    // Area consumption displays
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
    private Map<String, Object> historicalData;
    private DatabaseReference databaseRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_historical_data);

        // Set up navigation
        LinearLayout buttonLayout = findViewById(R.id.button);
        ButtonNavigator.setupButtons(this, buttonLayout);

        // Initialize Firebase
        databaseRef = FirebaseDatabase.getInstance().getReference();

        // Initialize components
        initializeDateComponents();
        initializeViews();
        setupDatePickers();
        initializeDates();
        setupChartContainers();

        // Load initial data
        loadHistoricalData();

        Log.d(TAG, "HistoricalDataActivity initialized successfully");
    }

    /**
     * Initialize date-related components and formatters
     */
    private void initializeDateComponents() {
        // Initialize date formatters with Philippine timezone
        displayFormat = new SimpleDateFormat("MM/dd/yyyy", Locale.getDefault());
        displayFormat.setTimeZone(PHILIPPINE_TIMEZONE);

        firebaseFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        firebaseFormat.setTimeZone(PHILIPPINE_TIMEZONE);

        // Initialize today's date in Philippine timezone
        todayCalendar = Calendar.getInstance(PHILIPPINE_TIMEZONE);

        // Initialize system start date
        systemStartCalendar = Calendar.getInstance(PHILIPPINE_TIMEZONE);
        try {
            systemStartCalendar.setTime(firebaseFormat.parse(SYSTEM_START_DATE));
        } catch (Exception e) {
            Log.e(TAG, "Error parsing system start date, using 30 days ago as fallback");
            systemStartCalendar.setTime(todayCalendar.getTime());
            systemStartCalendar.add(Calendar.DAY_OF_MONTH, -30);
        }

        Log.d(TAG, "Date components initialized - Today: " + firebaseFormat.format(todayCalendar.getTime()) +
                ", System Start: " + firebaseFormat.format(systemStartCalendar.getTime()));
    }

    /**
     * Initialize all UI components
     */
    private void initializeViews() {
        // Initialize date picker fields
        startDateEdit = findViewById(R.id.startdate);
        endDateEdit = findViewById(R.id.enddate);

        // Make date fields non-editable but clickable
        if (startDateEdit != null) {
            startDateEdit.setFocusable(false);
            startDateEdit.setClickable(true);
        }
        if (endDateEdit != null) {
            endDateEdit.setFocusable(false);
            endDateEdit.setClickable(true);
        }

        // Initialize summary components
        selectedTotalConsumption = findViewById(R.id.selected_total_consumption);
        selectedDailyAverage = findViewById(R.id.selected_daily_average);
        selectedTotalCost = findViewById(R.id.selected_total_cost);
        selectedDateRange = findViewById(R.id.selecteddaterange);

        previousTotalConsumption = findViewById(R.id.previoustotalconsumption);

        previousTotalCost = findViewById(R.id.previoustotalcost);
        previousDateRange = findViewById(R.id.previousdaterange);

        // Initialize chart containers
        mainChartContainer = findViewById(R.id.selecteddaterange_chart_container);
        area1ChartContainer = findViewById(R.id.area1_chart_container);
        area2ChartContainer = findViewById(R.id.area2_chart_container);
        area3ChartContainer = findViewById(R.id.area3_chart_container);

        // Initialize area consumption displays
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
     * Initialize default date range (last 7 days) using Philippine timezone
     */
    private void initializeDates() {
        // Initialize calendars with Philippine timezone
        startDate = Calendar.getInstance(PHILIPPINE_TIMEZONE);
        endDate = Calendar.getInstance(PHILIPPINE_TIMEZONE);

        // Set end date to today
        endDate.setTime(todayCalendar.getTime());

        // Set start date to 7 days ago (or system start date if more recent)
        startDate.setTime(todayCalendar.getTime());
        startDate.add(Calendar.DAY_OF_MONTH, -7);

        // Ensure start date is not before system start date
        if (startDate.before(systemStartCalendar)) {
            startDate.setTime(systemStartCalendar.getTime());
        }

        updateDateDisplays();

        Log.d(TAG, "Dates initialized - Start: " + firebaseFormat.format(startDate.getTime()) +
                ", End: " + firebaseFormat.format(endDate.getTime()));
    }

    /**
     * Setup date picker dialogs with proper validation
     */
    private void setupDatePickers() {
        if (startDateEdit != null) {
            startDateEdit.setOnClickListener(v -> showStartDatePicker());
        }
        if (endDateEdit != null) {
            endDateEdit.setOnClickListener(v -> showEndDatePicker());
        }
    }

    /**
     * Show start date picker with appropriate restrictions
     */
    private void showStartDatePicker() {
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    // Create selected date
                    Calendar selectedDate = Calendar.getInstance(PHILIPPINE_TIMEZONE);
                    selectedDate.set(Calendar.YEAR, year);
                    selectedDate.set(Calendar.MONTH, month);
                    selectedDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    selectedDate.set(Calendar.HOUR_OF_DAY, 0);
                    selectedDate.set(Calendar.MINUTE, 0);
                    selectedDate.set(Calendar.SECOND, 0);
                    selectedDate.set(Calendar.MILLISECOND, 0);

                    // Validate selected start date
                    if (validateStartDate(selectedDate)) {
                        startDate.setTime(selectedDate.getTime());
                        updateDateDisplays();
                        loadHistoricalData();

                        Log.d(TAG, "Start date selected: " + firebaseFormat.format(startDate.getTime()));
                    }
                },
                startDate.get(Calendar.YEAR),
                startDate.get(Calendar.MONTH),
                startDate.get(Calendar.DAY_OF_MONTH)
        );

        // Set date restrictions for start date picker
        // Minimum date: system start date
        datePickerDialog.getDatePicker().setMinDate(systemStartCalendar.getTimeInMillis());

        // Maximum date: either today or end date (whichever is earlier)
        Calendar maxStartDate = Calendar.getInstance(PHILIPPINE_TIMEZONE);
        maxStartDate.setTime(endDate.getTime());
        if (maxStartDate.after(todayCalendar)) {
            maxStartDate.setTime(todayCalendar.getTime());
        }
        datePickerDialog.getDatePicker().setMaxDate(maxStartDate.getTimeInMillis());

        datePickerDialog.setTitle("Select Start Date");
        datePickerDialog.show();
    }

    /**
     * Show end date picker with appropriate restrictions
     */
    private void showEndDatePicker() {
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    // Create selected date
                    Calendar selectedDate = Calendar.getInstance(PHILIPPINE_TIMEZONE);
                    selectedDate.set(Calendar.YEAR, year);
                    selectedDate.set(Calendar.MONTH, month);
                    selectedDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    selectedDate.set(Calendar.HOUR_OF_DAY, 23);
                    selectedDate.set(Calendar.MINUTE, 59);
                    selectedDate.set(Calendar.SECOND, 59);
                    selectedDate.set(Calendar.MILLISECOND, 999);

                    // Validate selected end date
                    if (validateEndDate(selectedDate)) {
                        endDate.setTime(selectedDate.getTime());
                        updateDateDisplays();
                        loadHistoricalData();

                        Log.d(TAG, "End date selected: " + firebaseFormat.format(endDate.getTime()));
                    }
                },
                endDate.get(Calendar.YEAR),
                endDate.get(Calendar.MONTH),
                endDate.get(Calendar.DAY_OF_MONTH)
        );

        // Set date restrictions for end date picker
        // Minimum date: start date
        datePickerDialog.getDatePicker().setMinDate(startDate.getTimeInMillis());

        // Maximum date: today (cannot select future dates)
        datePickerDialog.getDatePicker().setMaxDate(todayCalendar.getTimeInMillis());

        datePickerDialog.setTitle("Select End Date");
        datePickerDialog.show();
    }

    /**
     * Validate start date selection
     */
    private boolean validateStartDate(Calendar selectedStartDate) {
        // Check if start date is before system start date
        if (selectedStartDate.before(systemStartCalendar)) {
            showValidationError("Start date cannot be before " + displayFormat.format(systemStartCalendar.getTime()) +
                    " (system start date)");
            return false;
        }

        // Check if start date is after today
        if (selectedStartDate.after(todayCalendar)) {
            showValidationError("Start date cannot be in the future");
            return false;
        }

        // Check if start date is after current end date
        if (selectedStartDate.after(endDate)) {
            showValidationError("Start date cannot be after end date");
            return false;
        }

        // Check date range limit
        long daysDifference = (endDate.getTimeInMillis() - selectedStartDate.getTimeInMillis()) / (1000 * 60 * 60 * 24);
        if (daysDifference > MAX_DATE_RANGE_DAYS) {
            showValidationError("Date range cannot exceed " + MAX_DATE_RANGE_DAYS + " days");
            return false;
        }

        return true;
    }

    /**
     * Validate end date selection
     */
    private boolean validateEndDate(Calendar selectedEndDate) {
        // Check if end date is after today
        if (selectedEndDate.after(todayCalendar)) {
            showValidationError("End date cannot be in the future");
            return false;
        }

        // Check if end date is before start date
        if (selectedEndDate.before(startDate)) {
            showValidationError("End date cannot be before start date");
            return false;
        }

        // Check date range limit
        long daysDifference = (selectedEndDate.getTimeInMillis() - startDate.getTimeInMillis()) / (1000 * 60 * 60 * 24);
        if (daysDifference > MAX_DATE_RANGE_DAYS) {
            showValidationError("Date range cannot exceed " + MAX_DATE_RANGE_DAYS + " days");
            return false;
        }

        return true;
    }

    /**
     * Show validation error message to user
     */
    private void showValidationError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        Log.w(TAG, "Date validation error: " + message);
    }

    /**
     * Update date display fields using Philippine timezone
     */
    private void updateDateDisplays() {
        if (startDateEdit != null) {
            startDateEdit.setText(displayFormat.format(startDate.getTime()));
        }
        if (endDateEdit != null) {
            endDateEdit.setText(displayFormat.format(endDate.getTime()));
        }

        // Update date range displays if they exist
        if (selectedDateRange != null) {
            String selectedRange = displayFormat.format(startDate.getTime()) + " - " + displayFormat.format(endDate.getTime());
            selectedDateRange.setText(selectedRange);
        }

        // Calculate and display previous period for comparison
        if (previousDateRange != null) {
            long daysDiff = (endDate.getTimeInMillis() - startDate.getTimeInMillis()) / (1000 * 60 * 60 * 24);
            Calendar prevStart = Calendar.getInstance(PHILIPPINE_TIMEZONE);
            Calendar prevEnd = Calendar.getInstance(PHILIPPINE_TIMEZONE);

            prevEnd.setTime(startDate.getTime());
            prevEnd.add(Calendar.DAY_OF_MONTH, -1); // End of previous period is day before start

            prevStart.setTime(prevEnd.getTime());
            prevStart.add(Calendar.DAY_OF_MONTH, -(int)daysDiff); // Same duration as selected period

            String previousRange = displayFormat.format(prevStart.getTime()) + " - " + displayFormat.format(prevEnd.getTime());
            previousDateRange.setText(previousRange);
        }

        // Display current date range info
        long daysDiff = (endDate.getTimeInMillis() - startDate.getTimeInMillis()) / (1000 * 60 * 60 * 24) + 1;
        Log.d(TAG, "Date range updated - " + daysDiff + " days selected");
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
                setupChart(mainChart, "Daily Total Consumption");
            }

            // Create area charts
            if (area1ChartContainer != null) {
                area1Chart = new LineChart(this);
                area1ChartContainer.removeAllViews();
                area1ChartContainer.addView(area1Chart);
                setupChart(area1Chart, "Area 1 Consumption");
            }

            if (area2ChartContainer != null) {
                area2Chart = new LineChart(this);
                area2ChartContainer.removeAllViews();
                area2ChartContainer.addView(area2Chart);
                setupChart(area2Chart, "Area 2 Consumption");
            }

            if (area3ChartContainer != null) {
                area3Chart = new LineChart(this);
                area3ChartContainer.removeAllViews();
                area3ChartContainer.addView(area3Chart);
                setupChart(area3Chart, "Area 3 Consumption");
            }

            Log.d(TAG, "Chart containers setup completed");
        } catch (Exception e) {
            Log.e(TAG, "Error setting up chart containers: " + e.getMessage());
        }
    }

    /**
     * Setup individual chart configuration
     */
    private void setupChart(LineChart chart, String description) {
        if (chart == null) return;

        chart.setDescription(null);
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.setPinchZoom(true);
        chart.setDrawGridBackground(false);

        // Configure X-axis
        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(true);
        xAxis.setGranularity(1f);
        xAxis.setLabelCount(7);

        // Configure Y-axis
        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setGranularityEnabled(true);

        YAxis rightAxis = chart.getAxisRight();
        rightAxis.setEnabled(false);

        // Configure legend
        Legend legend = chart.getLegend();
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.TOP);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.RIGHT);
        legend.setOrientation(Legend.LegendOrientation.VERTICAL);
        legend.setDrawInside(false);
    }

    /**
     * Load historical data based on selected date range
     */
    private void loadHistoricalData() {
        Log.d(TAG, "Loading historical data for range: " +
                firebaseFormat.format(startDate.getTime()) + " to " +
                firebaseFormat.format(endDate.getTime()));

        // Show loading indicator
        showLoadingIndicator(true);

        // Query daily summaries for the selected date range
        DatabaseReference dailySummariesRef = FirebaseDatabase.getInstance()
                .getReference("daily_summaries");

        String startDateStr = firebaseFormat.format(startDate.getTime());
        String endDateStr = firebaseFormat.format(endDate.getTime());

        dailySummariesRef.orderByKey()
                .startAt(startDateStr)
                .endAt(endDateStr)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        processHistoricalData(snapshot);
                        showLoadingIndicator(false);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Error loading historical data: " + error.getMessage());
                        showValidationError("Failed to load historical data");
                        showLoadingIndicator(false);
                    }
                });
    }

    /**
     * Process loaded historical data
     */
    private void processHistoricalData(DataSnapshot snapshot) {
        if (!snapshot.exists()) {
            showValidationError("No data available for the selected date range");
            clearDisplays();
            return;
        }

        try {
            // Variables to track totals
            double totalConsumption = 0.0;
            double totalCost = 0.0;
            double totalArea1Consumption = 0.0;
            double totalArea2Consumption = 0.0;
            double totalArea3Consumption = 0.0;
            double maxPeakWatts = 0.0;
            int dayCount = 0;

            // Lists for chart data
            List<Entry> mainChartEntries = new ArrayList<>();
            List<Entry> area1ChartEntries = new ArrayList<>();
            List<Entry> area2ChartEntries = new ArrayList<>();
            List<Entry> area3ChartEntries = new ArrayList<>();
            List<String> dateLabels = new ArrayList<>();

            // Process each day's data
            for (DataSnapshot daySnapshot : snapshot.getChildren()) {
                String dateKey = daySnapshot.getKey();
                DataSnapshot dayData = daySnapshot;

                if (dayData.exists()) {
                    // Get daily totals
                    Double dailyKwh = getDoubleValue(dayData.child("total_kwh"));
                    Double dailyCost = getDoubleValue(dayData.child("total_cost"));
                    Double peakWatts = getDoubleValue(dayData.child("peak_watts"));

                    if (dailyKwh != null) {
                        totalConsumption += dailyKwh;
                        mainChartEntries.add(new Entry(dayCount, dailyKwh.floatValue()));
                    }

                    if (dailyCost != null) {
                        totalCost += dailyCost;
                    }

                    if (peakWatts != null && peakWatts > maxPeakWatts) {
                        maxPeakWatts = peakWatts;
                    }

                    // Get area breakdown
                    DataSnapshot areaBreakdown = dayData.child("area_breakdown");
                    if (areaBreakdown.exists()) {
                        Double area1Kwh = getDoubleValue(areaBreakdown.child("area1").child("kwh"));
                        Double area2Kwh = getDoubleValue(areaBreakdown.child("area2").child("kwh"));
                        Double area3Kwh = getDoubleValue(areaBreakdown.child("area3").child("kwh"));

                        if (area1Kwh != null) {
                            totalArea1Consumption += area1Kwh;
                            area1ChartEntries.add(new Entry(dayCount, area1Kwh.floatValue()));
                        }

                        if (area2Kwh != null) {
                            totalArea2Consumption += area2Kwh;
                            area2ChartEntries.add(new Entry(dayCount, area2Kwh.floatValue()));
                        }

                        if (area3Kwh != null) {
                            totalArea3Consumption += area3Kwh;
                            area3ChartEntries.add(new Entry(dayCount, area3Kwh.floatValue()));
                        }
                    }

                    // Add date label
                    try {
                        Date date = firebaseFormat.parse(dateKey);
                        SimpleDateFormat chartLabelFormat = new SimpleDateFormat("MM/dd", Locale.getDefault());
                        dateLabels.add(chartLabelFormat.format(date));
                    } catch (Exception e) {
                        dateLabels.add(dateKey);
                    }

                    dayCount++;
                }
            }

            // Update UI with processed data
            updateSummaryDisplays(totalConsumption, totalCost, totalArea1Consumption,
                    totalArea2Consumption, totalArea3Consumption, maxPeakWatts, dayCount);

            // Update charts
            updateCharts(mainChartEntries, area1ChartEntries, area2ChartEntries, area3ChartEntries, dateLabels);

            Log.d(TAG, "Historical data processed successfully for " + dayCount + " days");

        } catch (Exception e) {
            Log.e(TAG, "Error processing historical data: " + e.getMessage());
            showValidationError("Error processing historical data");
        }
    }

    /**
     * Helper method to safely get double values from Firebase
     */
    private Double getDoubleValue(DataSnapshot snapshot) {
        if (snapshot.exists()) {
            Object value = snapshot.getValue();
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
        }
        return null;
    }

    /**
     * Update summary displays with calculated data
     */
    private void updateSummaryDisplays(double totalConsumption, double totalCost,
                                       double area1Consumption, double area2Consumption, double area3Consumption,
                                       double maxPeakWatts, int dayCount) {

        runOnUiThread(() -> {
            // Update selected period summary
            if (selectedTotalConsumption != null) {
                selectedTotalConsumption.setText(String.format(Locale.getDefault(), "%.2f kWh", totalConsumption));
            }

            if (selectedTotalCost != null) {
                selectedTotalCost.setText(String.format(Locale.getDefault(), "₱%.2f", totalCost));
            }

            if (selectedDailyAverage != null && dayCount > 0) {
                double dailyAverage = totalConsumption / dayCount;
                selectedDailyAverage.setText(String.format(Locale.getDefault(), "%.2f kWh", dailyAverage));
            }

            // Update area displays
            double totalForPercentage = area1Consumption + area2Consumption + area3Consumption;

            if (area1TotalConsumption != null) {
                area1TotalConsumption.setText(String.format(Locale.getDefault(), "%.2f kWh", area1Consumption));
            }
            if (area1SharePercentage != null && totalForPercentage > 0) {
                double area1Percentage = (area1Consumption / totalForPercentage) * 100;
                area1SharePercentage.setText(String.format(Locale.getDefault(), "%.1f%%", area1Percentage));
            }

            if (area2TotalConsumption != null) {
                area2TotalConsumption.setText(String.format(Locale.getDefault(), "%.2f kWh", area2Consumption));
            }
            if (area2SharePercentage != null && totalForPercentage > 0) {
                double area2Percentage = (area2Consumption / totalForPercentage) * 100;
                area2SharePercentage.setText(String.format(Locale.getDefault(), "%.1f%%", area2Percentage));
            }

            if (area3TotalConsumption != null) {
                area3TotalConsumption.setText(String.format(Locale.getDefault(), "%.2f kWh", area3Consumption));
            }
            if (area3SharePercentage != null && totalForPercentage > 0) {
                double area3Percentage = (area3Consumption / totalForPercentage) * 100;
                area3SharePercentage.setText(String.format(Locale.getDefault(), "%.1f%%", area3Percentage));
            }
        });
    }

    /**
     * Update all charts with processed data
     */
    private void updateCharts(List<Entry> mainEntries, List<Entry> area1Entries,
                              List<Entry> area2Entries, List<Entry> area3Entries, List<String> dateLabels) {

        runOnUiThread(() -> {
            try {
                // Update main chart
                if (mainChart != null && !mainEntries.isEmpty()) {
                    LineDataSet dataSet = new LineDataSet(mainEntries, "Total Consumption (kWh)");
                    dataSet.setColor(Color.BLUE);
                    dataSet.setValueTextColor(Color.BLACK);
                    dataSet.setLineWidth(2f);
                    dataSet.setCircleRadius(4f);

                    LineData lineData = new LineData(dataSet);
                    mainChart.setData(lineData);
                    mainChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(dateLabels));
                    mainChart.invalidate();
                }

                // Update area charts
                updateAreaChart(area1Chart, area1Entries, "Area 1 (kWh)", Color.GREEN, dateLabels);
                updateAreaChart(area2Chart, area2Entries, "Area 2 (kWh)", Color.RED, dateLabels);
                updateAreaChart(area3Chart, area3Entries, "Area 3 (kWh)", Color.YELLOW, dateLabels);

            } catch (Exception e) {
                Log.e(TAG, "Error updating charts: " + e.getMessage());
            }
        });
    }

    /**
     * Update individual area chart
     */
    private void updateAreaChart(LineChart chart, List<Entry> entries, String label, int color, List<String> dateLabels) {
        if (chart != null && !entries.isEmpty()) {
            LineDataSet dataSet = new LineDataSet(entries, label);
            dataSet.setColor(color);
            dataSet.setValueTextColor(Color.BLACK);
            dataSet.setLineWidth(2f);
            dataSet.setCircleRadius(3f);
            dataSet.setCircleColor(color);

            LineData lineData = new LineData(dataSet);
            chart.setData(lineData);
            chart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(dateLabels));
            chart.invalidate();
        }
    }

    /**
     * Clear all displays when no data is available
     */
    private void clearDisplays() {
        runOnUiThread(() -> {
            // Clear summary displays
            if (selectedTotalConsumption != null) selectedTotalConsumption.setText("0.00 kWh");
            if (selectedTotalCost != null) selectedTotalCost.setText("₱0.00");
            if (selectedDailyAverage != null) selectedDailyAverage.setText("0.00 kWh");

            // Clear area displays
            if (area1TotalConsumption != null) area1TotalConsumption.setText("0.00 kWh");
            if (area1SharePercentage != null) area1SharePercentage.setText("0.0%");
            if (area2TotalConsumption != null) area2TotalConsumption.setText("0.00 kWh");
            if (area2SharePercentage != null) area2SharePercentage.setText("0.0%");
            if (area3TotalConsumption != null) area3TotalConsumption.setText("0.00 kWh");
            if (area3SharePercentage != null) area3SharePercentage.setText("0.0%");

            // Clear charts
            if (mainChart != null) mainChart.clear();
            if (area1Chart != null) area1Chart.clear();
            if (area2Chart != null) area2Chart.clear();
            if (area3Chart != null) area3Chart.clear();
        });
    }

    /**
     * Show/hide loading indicator
     */
    private void showLoadingIndicator(boolean show) {
        runOnUiThread(() -> {
            if (show) {
                Toast.makeText(this, "Loading historical data...", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Get current date in yyyy-MM-dd format using Philippine timezone
     */
    private String getCurrentDate() {
        return firebaseFormat.format(todayCalendar.getTime());
    }

    /**
     * Convert date string to display format
     */
    private String formatDateForDisplay(String firebaseDate) {
        try {
            Date date = firebaseFormat.parse(firebaseDate);
            return displayFormat.format(date);
        } catch (Exception e) {
            return firebaseDate;
        }
    }

    /**
     * Calculate days between two dates
     */
    private long getDaysBetween(Calendar start, Calendar end) {
        return (end.getTimeInMillis() - start.getTimeInMillis()) / (1000 * 60 * 60 * 24) + 1;
    }

    /**
     * Check if date is within valid range
     */
    private boolean isDateInValidRange(Calendar date) {
        return !date.before(systemStartCalendar) && !date.after(todayCalendar);
    }

    /**
     * Get electricity rate for cost calculations
     */
    private void getElectricityRate(final ElectricityRateCallback callback) {
        databaseRef.child("system_settings").child("electricity_rate_per_kwh")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        double rate = 12.5; // Default rate
                        if (snapshot.exists()) {
                            Object value = snapshot.getValue();
                            if (value instanceof Number) {
                                rate = ((Number) value).doubleValue();
                            }
                        }
                        callback.onRateLoaded(rate);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Error loading electricity rate: " + error.getMessage());
                        callback.onRateLoaded(12.5); // Use default rate
                    }
                });
    }

    /**
     * Interface for electricity rate callback
     */
    private interface ElectricityRateCallback {
        void onRateLoaded(double rate);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh data when activity resumes
        Log.d(TAG, "HistoricalDataActivity resumed, refreshing data");
        loadHistoricalData();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up any resources if needed
        Log.d(TAG, "HistoricalDataActivity destroyed");
    }
}