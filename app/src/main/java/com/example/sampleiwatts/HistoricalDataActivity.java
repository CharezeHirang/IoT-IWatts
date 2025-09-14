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

        // Basic chart settings (match dashboard)
        chart.setDescription(null);
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(false);  // Disable scaling like dashboard
        chart.setPinchZoom(false);     // Disable pinch zoom like dashboard
        chart.setDrawGridBackground(false);
        chart.setDrawBorders(false);
        chart.setExtraBottomOffset(10f); // Match dashboard

        // Configure X-axis (match dashboard style)
        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(true);
        xAxis.setGridColor(getResources().getColor(R.color.brown)); // Match dashboard
        xAxis.setTextColor(getResources().getColor(R.color.brown)); // Match dashboard
        xAxis.setGranularity(1f);
        xAxis.setDrawLabels(true);

        // Configure left Y-axis (match dashboard)
        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(getResources().getColor(R.color.brown)); // Match dashboard
        leftAxis.setTextColor(getResources().getColor(R.color.brown)); // Match dashboard
        leftAxis.setGranularityEnabled(true);

        // Disable right Y-axis (match dashboard)
        YAxis rightAxis = chart.getAxisRight();
        rightAxis.setEnabled(false);

        // Configure legend (match dashboard)
        Legend legend = chart.getLegend();
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.TOP);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.LEFT);
        legend.setOrientation(Legend.LegendOrientation.HORIZONTAL);
        legend.setDrawInside(false);
        legend.setForm(Legend.LegendForm.LINE); // Match dashboard
        legend.setTextColor(getResources().getColor(R.color.brown)); // Match dashboard
    }

    /**
     * Load historical data based on selected date range
     */
    private void loadHistoricalData() {
        Log.d(TAG, "Loading historical data for range: " +
                firebaseFormat.format(startDate.getTime()) + " to " +
                firebaseFormat.format(endDate.getTime()));

        // NO MORE TOAST - REMOVED showLoadingIndicator(true);

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
                        // NO MORE TOAST - REMOVED showLoadingIndicator(false);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Error loading historical data: " + error.getMessage());
                        showValidationError("Failed to load historical data");
                        // NO MORE TOAST - REMOVED showLoadingIndicator(false);
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
            double totalConsumption = 0.0;
            double totalCost = 0.0;
            double totalArea1Consumption = 0.0;
            double totalArea2Consumption = 0.0;
            double totalArea3Consumption = 0.0;

            double maxPeakWatts = 0.0;
            String peakUsageDay = "";
            double area1PeakWatts = 0.0;
            String area1PeakDay = "";           // ← This one
            double area2PeakWatts = 0.0;
            String area2PeakDay = "";           // ← This one causing error
            double area3PeakWatts = 0.0;
            String area3PeakDay = "";

            int dayCount = 0;

            List<Entry> mainChartEntries = new ArrayList<>();
            List<Entry> area1ChartEntries = new ArrayList<>();
            List<Entry> area2ChartEntries = new ArrayList<>();
            List<Entry> area3ChartEntries = new ArrayList<>();
            List<String> dateLabels = new ArrayList<>();

            List<String> sortedDates = new ArrayList<>();
            for (DataSnapshot daySnapshot : snapshot.getChildren()) {
                sortedDates.add(daySnapshot.getKey());
            }
            sortedDates.sort(String::compareTo);

            for (String dateKey : sortedDates) {
                DataSnapshot dayData = snapshot.child(dateKey);

                if (dayData.exists()) {
                    Double dailyKwh = getDoubleValue(dayData.child("total_kwh"));
                    Double dailyCost = getDoubleValue(dayData.child("total_cost"));
                    Double peakWatts = getDoubleValue(dayData.child("peak_watts"));

                    if (dailyKwh != null && dailyKwh > 0) {
                        totalConsumption += dailyKwh;
                        mainChartEntries.add(new Entry(dayCount, dailyKwh.floatValue()));
                    } else {
                        mainChartEntries.add(new Entry(dayCount, 0f));
                    }

                    if (dailyCost != null) {
                        totalCost += dailyCost;
                    }

                    if (peakWatts != null && peakWatts > maxPeakWatts) {
                        maxPeakWatts = peakWatts;
                        peakUsageDay = dateKey;
                    }

                    DataSnapshot areaBreakdown = dayData.child("area_breakdown");
                    if (areaBreakdown.exists()) {
                        Double area1Kwh = getDoubleValue(areaBreakdown.child("area1").child("kwh"));
                        Double area2Kwh = getDoubleValue(areaBreakdown.child("area2").child("kwh"));
                        Double area3Kwh = getDoubleValue(areaBreakdown.child("area3").child("kwh"));

                        // AREA 1 - Track both peak consumption and peak day
                        if (area1Kwh != null && area1Kwh > 0) {
                            totalArea1Consumption += area1Kwh;
                            area1ChartEntries.add(new Entry(dayCount, area1Kwh.floatValue()));
                            if (area1Kwh > area1PeakWatts) {
                                area1PeakWatts = area1Kwh;
                                area1PeakDay = dateKey; // FIX: Capture the peak day
                            }
                        } else {
                            area1ChartEntries.add(new Entry(dayCount, 0f));
                        }

                        // AREA 2 - Track both peak consumption and peak day
                        if (area2Kwh != null && area2Kwh > 0) {
                            totalArea2Consumption += area2Kwh;
                            area2ChartEntries.add(new Entry(dayCount, area2Kwh.floatValue()));
                            if (area2Kwh > area2PeakWatts) {
                                area2PeakWatts = area2Kwh;
                                area2PeakDay = dateKey; // FIX: Capture the peak day
                            }
                        } else {
                            area2ChartEntries.add(new Entry(dayCount, 0f));
                        }

                        // AREA 3 - Track both peak consumption and peak day
                        if (area3Kwh != null && area3Kwh > 0) {
                            totalArea3Consumption += area3Kwh;
                            area3ChartEntries.add(new Entry(dayCount, area3Kwh.floatValue()));
                            if (area3Kwh > area3PeakWatts) {
                                area3PeakWatts = area3Kwh;
                                area3PeakDay = dateKey; // FIX: Capture the peak day
                            }
                        } else {
                            area3ChartEntries.add(new Entry(dayCount, 0f));
                        }
                    } else {
                        area1ChartEntries.add(new Entry(dayCount, 0f));
                        area2ChartEntries.add(new Entry(dayCount, 0f));
                        area3ChartEntries.add(new Entry(dayCount, 0f));
                    }

                    try {
                        Date date = firebaseFormat.parse(dateKey);
                        SimpleDateFormat chartLabelFormat = new SimpleDateFormat("MM/dd", Locale.getDefault());
                        dateLabels.add(chartLabelFormat.format(date));
                    } catch (Exception e) {
                        String shortDate = dateKey.substring(5);
                        dateLabels.add(shortDate.replace("-", "/"));
                    }

                    dayCount++;
                }
            }

            if (dayCount == 0) {
                showValidationError("No valid data found in the selected date range");
                clearDisplays();
                return;
            }

            // Update displays with all data
            updateSummaryDisplaysWithPeaksAndDays(totalConsumption, totalCost, totalArea1Consumption,
                    totalArea2Consumption, totalArea3Consumption,
                    maxPeakWatts, peakUsageDay,
                    area1PeakWatts, area1PeakDay,
                    area2PeakWatts, area2PeakDay,
                    area3PeakWatts, area3PeakDay, dayCount);

            // Update charts with area names from database
            updateChartsWithAreaNames(mainChartEntries, area1ChartEntries, area2ChartEntries, area3ChartEntries, dateLabels);

            Log.d(TAG, "Historical data processed successfully for " + dayCount + " days");

        } catch (Exception e) {
            Log.e(TAG, "Error processing historical data: " + e.getMessage());
            showValidationError("Error processing historical data");
            clearDisplays();
        }
    }

    private void updateChartsWithAreaNames(List<Entry> mainEntries, List<Entry> area1Entries,
                                           List<Entry> area2Entries, List<Entry> area3Entries, List<String> dateLabels) {

        // Fetch area names from database
        DatabaseReference systemSettingsRef = FirebaseDatabase.getInstance()
                .getReference("system_settings");

        systemSettingsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                final String area1Name = snapshot.child("area1_name").getValue(String.class) != null
                        ? snapshot.child("area1_name").getValue(String.class) : "Area 1";
                final String area2Name = snapshot.child("area2_name").getValue(String.class) != null
                        ? snapshot.child("area2_name").getValue(String.class) : "Area 2";
                final String area3Name = snapshot.child("area3_name").getValue(String.class) != null
                        ? snapshot.child("area3_name").getValue(String.class) : "Area 3";

                // Update charts with proper names
                runOnUiThread(() -> {
                    updateCharts(mainEntries, area1Entries, area2Entries, area3Entries, dateLabels,
                            area1Name, area2Name, area3Name);

                    // Update area labels in UI
                    updateHistoricalAreaLabels(area1Name, area2Name, area3Name);
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error fetching area names: " + error.getMessage());
                // Use defaults
                runOnUiThread(() -> {
                    updateCharts(mainEntries, area1Entries, area2Entries, area3Entries, dateLabels,
                            "Area 1", "Area 2", "Area 3");
                });
            }
        });
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
    private void updateSummaryDisplaysWithPeaksAndDays(double totalConsumption, double totalCost,
                                                       double area1Consumption, double area2Consumption, double area3Consumption,
                                                       double maxPeakWatts, String peakUsageDay,
                                                       double area1PeakWatts, String area1PeakDay,
                                                       double area2PeakWatts, String area2PeakDay,
                                                       double area3PeakWatts, String area3PeakDay,
                                                       int dayCount) {

        runOnUiThread(() -> {
            try {
                // FIXED: Display 2 decimal places for total consumption
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

                // FIXED: Display peak usage day
                TextView selectedPeakDay = findViewById(R.id.selected_peak_day);
                if (selectedPeakDay != null && !peakUsageDay.isEmpty()) {
                    try {
                        Date date = firebaseFormat.parse(peakUsageDay);
                        SimpleDateFormat displayFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
                        selectedPeakDay.setText(displayFormat.format(date));
                    } catch (Exception e) {
                        selectedPeakDay.setText(peakUsageDay);
                    }
                }

                TextView selectedDateRangeTotalCost = findViewById(R.id.selecteddaterangetotalcost);
                if (selectedDateRangeTotalCost != null) {
                    selectedDateRangeTotalCost.setText(String.format(Locale.getDefault(), "₱%.2f", totalCost));
                }

                // Get electricity rate for cost calculations
                getElectricityRate(rate -> {
                    runOnUiThread(() -> {
                        // AREA 1 with peak day information
                        if (area1TotalConsumption != null) {
                            area1TotalConsumption.setText(String.format(Locale.getDefault(), "%.2f kWh", area1Consumption));
                        }
                        if (area1EstimatedCost != null) {
                            double area1Cost = area1Consumption * rate;
                            area1EstimatedCost.setText(String.format(Locale.getDefault(), "₱%.2f", area1Cost));
                        }
                        // FIXED: Show peak consumption with day
                        if (area1PeakConsumption != null) {
                            if (area1PeakWatts > 0) {
                                String peakText = String.format(Locale.getDefault(), "%.2f kWh", area1PeakWatts);
                                if (!area1PeakDay.isEmpty()) {
                                    try {
                                        Date date = firebaseFormat.parse(area1PeakDay);
                                        SimpleDateFormat shortFormat = new SimpleDateFormat("MM/dd", Locale.getDefault());
                                        peakText += " on " + shortFormat.format(date);
                                    } catch (Exception e) {
                                        peakText += " on " + area1PeakDay.substring(5).replace("-", "/");
                                    }
                                }
                                area1PeakConsumption.setText(peakText);
                            } else {
                                area1PeakConsumption.setText("0.00 kWh");
                            }
                        }
                        if (area1SharePercentage != null) {
                            double totalForPercentage = area1Consumption + area2Consumption + area3Consumption;
                            if (totalForPercentage > 0) {
                                double area1Percentage = (area1Consumption / totalForPercentage) * 100;
                                area1SharePercentage.setText(String.format(Locale.getDefault(), "%.1f%%", area1Percentage));
                            }
                        }

                        // AREA 2 with peak day information
                        if (area2PeakConsumption != null) {
                            if (area2PeakWatts > 0) {
                                String peakText = String.format(Locale.getDefault(), "%.2f kWh", area2PeakWatts);
                                if (!area2PeakDay.isEmpty()) {
                                    try {
                                        Date date = firebaseFormat.parse(area2PeakDay);
                                        SimpleDateFormat shortFormat = new SimpleDateFormat("MM/dd", Locale.getDefault());
                                        peakText += " on " + shortFormat.format(date);
                                    } catch (Exception e) {
                                        peakText += " on " + area2PeakDay.substring(5).replace("-", "/");
                                    }
                                }
                                area2PeakConsumption.setText(peakText);
                            } else {
                                area2PeakConsumption.setText("0.00 kWh");
                            }
                        }
                        if (area2SharePercentage != null) {
                            double totalForPercentage = area1Consumption + area2Consumption + area3Consumption;
                            if (totalForPercentage > 0) {
                                double area2Percentage = (area2Consumption / totalForPercentage) * 100;
                                area2SharePercentage.setText(String.format(Locale.getDefault(), "%.1f%%", area2Percentage));
                            }
                        }

                        // AREA 3 with peak day information
                        if (area3TotalConsumption != null) {
                            area3TotalConsumption.setText(String.format(Locale.getDefault(), "%.2f kWh", area3Consumption));
                        }
                        if (area3EstimatedCost != null) {
                            double area3Cost = area3Consumption * rate;
                            area3EstimatedCost.setText(String.format(Locale.getDefault(), "₱%.2f", area3Cost));
                        }
                        // FIXED: Show peak consumption with day
                        if (area3PeakConsumption != null) {
                            if (area3PeakWatts > 0) {
                                String peakText = String.format(Locale.getDefault(), "%.2f kWh", area3PeakWatts);
                                if (!area3PeakDay.isEmpty()) {
                                    try {
                                        Date date = firebaseFormat.parse(area3PeakDay);
                                        SimpleDateFormat shortFormat = new SimpleDateFormat("MM/dd", Locale.getDefault());
                                        peakText += " on " + shortFormat.format(date);
                                    } catch (Exception e) {
                                        peakText += " on " + area3PeakDay.substring(5).replace("-", "/");
                                    }
                                }
                                area3PeakConsumption.setText(peakText);
                            } else {
                                area3PeakConsumption.setText("0.00 kWh");
                            }
                        }
                        if (area3SharePercentage != null) {
                            double totalForPercentage = area1Consumption + area2Consumption + area3Consumption;
                            if (totalForPercentage > 0) {
                                double area3Percentage = (area3Consumption / totalForPercentage) * 100;
                                area3SharePercentage.setText(String.format(Locale.getDefault(), "%.1f%%", area3Percentage));
                            }
                        }

                        // FIXED: Update previous period consumption with 2 decimal places
                        TextView previousTotalConsumption = findViewById(R.id.previoustotalconsumption);
                        if (previousTotalConsumption != null) {
                            // Calculate and display previous period data (you may need to implement this based on your requirements)
                            // For now, just ensure it shows 2 decimal places when updated
                            String currentText = previousTotalConsumption.getText().toString();
                            if (currentText.contains("kWh")) {
                                // Extract number and reformat to 2 decimal places
                                try {
                                    String[] parts = currentText.split(" ");
                                    if (parts.length > 0) {
                                        double value = Double.parseDouble(parts[0]);
                                        previousTotalConsumption.setText(String.format(Locale.getDefault(), "%.2f kWh", value));
                                    }
                                } catch (Exception e) {
                                    Log.w(TAG, "Could not reformat previous total consumption");
                                }
                            }
                        }

                        // FIXED: Update selected date range total consumption with 2 decimal places
                        TextView selectedDateRangeTotalConsumption = findViewById(R.id.selecteddaterangetotalconsumption);
                        if (selectedDateRangeTotalConsumption != null) {
                            selectedDateRangeTotalConsumption.setText(String.format(Locale.getDefault(), "%.2f kWh", totalConsumption));
                        }
                    });
                });

            } catch (Exception e) {
                Log.e(TAG, "Error updating summary displays: " + e.getMessage());
            }
        });
    }

    /**
     * Update all charts with processed data
     */
    private void updateCharts(List<Entry> mainEntries, List<Entry> area1Entries,
                              List<Entry> area2Entries, List<Entry> area3Entries, List<String> dateLabels,
                              String area1Name, String area2Name, String area3Name) {

        runOnUiThread(() -> {
            try {
                if (mainChart != null && !mainEntries.isEmpty()) {
                    updateSingleChartDashboardStyle(mainChart, mainEntries, "Total Consumption (kWh)",
                            getResources().getColor(R.color.brown), dateLabels);
                }

                updateSingleChartDashboardStyle(area1Chart, area1Entries, area1Name + " (kWh)",
                        getResources().getColor(R.color.brown), dateLabels);

                updateSingleChartDashboardStyle(area2Chart, area2Entries, area2Name + " (kWh)",
                        getResources().getColor(R.color.brown), dateLabels);

                updateSingleChartDashboardStyle(area3Chart, area3Entries, area3Name + " (kWh)",
                        getResources().getColor(R.color.brown), dateLabels);

            } catch (Exception e) {
                Log.e(TAG, "Error updating charts: " + e.getMessage());
            }
        });
    }

    /**
     * Update area labels in historical UI
     */
    private void updateHistoricalAreaLabels(String area1Name, String area2Name, String area3Name) {
        TextView area1Label = findViewById(R.id.area1_label);
        TextView area2Label = findViewById(R.id.area2_label);
        TextView area3Label = findViewById(R.id.area3_label);

        if (area1Label != null) area1Label.setText(area1Name);
        if (area2Label != null) area2Label.setText(area2Name);
        if (area3Label != null) area3Label.setText(area3Name);
    }


    private void updateSingleChartDashboardStyle(LineChart chart, List<Entry> entries,
                                                 String label, int color, List<String> dateLabels) {
        if (chart == null || entries.isEmpty()) return;

        try {
            // Create dataset with dashboard-style formatting
            LineDataSet dataSet = new LineDataSet(entries, label);

            // Apply dashboard styling
            dataSet.setColor(color);
            dataSet.setValueTextColor(color);
            dataSet.setLineWidth(2f);           // Match dashboard line width
            dataSet.setDrawCircles(true);       // Show data points like dashboard
            dataSet.setCircleColor(color);
            dataSet.setCircleRadius(3f);
            dataSet.setDrawFilled(true);        // Fill area under line like dashboard
            dataSet.setFillColor(color);
            dataSet.setFillAlpha(50);           // Semi-transparent fill
            dataSet.setDrawValues(false);       // Don't show values on points

            // Create line data
            LineData lineData = new LineData(dataSet);
            chart.setData(lineData);

            // Apply axis colors (match dashboard)
            chart.getAxisLeft().setGridColor(color);
            chart.getAxisLeft().setTextColor(color);
            chart.getXAxis().setGridColor(color);
            chart.getXAxis().setTextColor(color);

            // Set date labels
            chart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(dateLabels));

            // Apply legend styling (match dashboard)
            Legend legend = chart.getLegend();
            legend.setTextColor(color);

            // Refresh chart
            chart.invalidate();

            Log.d(TAG, "Updated chart with " + entries.size() + " data points");

        } catch (Exception e) {
            Log.e(TAG, "Error updating single chart: " + e.getMessage(), e);
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