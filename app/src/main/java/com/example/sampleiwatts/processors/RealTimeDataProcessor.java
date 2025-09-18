package com.example.sampleiwatts.processors;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import com.google.firebase.database.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class RealTimeDataProcessor {
    private static final String TAG = "RealTimeDataProcessor";

    // Philippine timezone
    private static final TimeZone PHILIPPINE_TIMEZONE = TimeZone.getTimeZone("Asia/Manila");

    private Context context;
    private DatabaseReference databaseRef;
    private SimpleDateFormat dateFormat;
    private SimpleDateFormat timeFormat;

    public RealTimeDataProcessor(Context context) {
        this.context = context.getApplicationContext();
        this.databaseRef = FirebaseDatabase.getInstance().getReference();

        // Set all date formatters to Philippine timezone
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        this.dateFormat.setTimeZone(PHILIPPINE_TIMEZONE);

        this.timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        this.timeFormat.setTimeZone(PHILIPPINE_TIMEZONE);

        Log.d(TAG, "RealTimeDataProcessor initialized with Philippine timezone");
    }

    /**
     * Process today's real-time data - INCLUDES current hour sensor readings
     */
    public void processTodaysData(DataProcessingCallback callback) {
        String todayDate = getCurrentPhilippineDate();
        Log.d(TAG, "Processing real-time data for: " + todayDate);

        // Get system settings first (electricity rate AND voltage reference)
        getSystemSettings(new SystemSettingsCallback() {
            @Override
            public void onSettingsLoaded(double electricityRate, double voltageReference) {
                // Process hourly summaries AND current hour sensor readings with correct settings
                processHourlyDataWithCurrentHour(todayDate, electricityRate, voltageReference, callback);
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Failed to get system settings: " + error);
                // Use default values
                processHourlyDataWithCurrentHour(todayDate, 12.5, 220.0, callback);
            }
        });
    }

    /**
     * Get system settings (electricity rate AND voltage reference)
     */
    private void getSystemSettings(SystemSettingsCallback callback) {
        databaseRef.child("system_settings").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                double electricityRate = 12.5; // Default
                double voltageReference = 220.0; // Default

                if (snapshot.exists()) {
                    if (snapshot.child("electricity_rate_per_kwh").exists()) {
                        electricityRate = snapshot.child("electricity_rate_per_kwh").getValue(Double.class);
                    }
                    if (snapshot.child("voltage_reference").exists()) {
                        voltageReference = snapshot.child("voltage_reference").getValue(Double.class);
                    }
                }

                Log.d(TAG, "Using settings - Rate: " + electricityRate + "/kWh, Voltage: " + voltageReference + "V");
                callback.onSettingsLoaded(electricityRate, voltageReference);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError(error.getMessage());
            }
        });
    }

    /**
     * Process hourly summaries AND current hour sensor readings for TRUE real-time
     */
    private void processHourlyDataWithCurrentHour(String date, double electricityRate, double voltageReference, DataProcessingCallback callback) {
        DatabaseReference hourlyRef = databaseRef.child("hourly_summaries").child(date);

        // First get hourly summaries
        hourlyRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot hourlySnapshot) {
                // Then get current hour sensor readings
                getCurrentHourSensorData(databaseRef.child("sensor_readings"), date, voltageReference, new CurrentHourCallback() {
                    @Override
                    public void onCurrentHourProcessed(CurrentHourData currentHourData) {
                        try {
                            RealTimeData realTimeData = buildCompleteRealTimeData(hourlySnapshot, currentHourData, electricityRate, date);

                            // NEW: Calculate real peaks from raw data (async)
                            calculateRealAreaPeaksFromRawData(realTimeData, date, voltageReference, callback);

                        } catch (Exception e) {
                            Log.e(TAG, "Error processing complete real-time data: " + e.getMessage(), e);
                            callback.onError("Failed to process data: " + e.getMessage());
                        }
                    }

                    @Override
                    public void onError(String error) {
                        Log.w(TAG, "Current hour data unavailable: " + error + ", using hourly data only");
                        try {
                            // Fallback to hourly data only
                            RealTimeData realTimeData = buildCompleteRealTimeData(hourlySnapshot, null, electricityRate, date);

                            // NEW: Still calculate real peaks from raw data (async)
                            calculateRealAreaPeaksFromRawData(realTimeData, date, voltageReference, callback);

                        } catch (Exception e) {
                            Log.e(TAG, "Error processing hourly data: " + e.getMessage(), e);
                            callback.onError("Failed to process data: " + e.getMessage());
                        }
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error getting hourly data: " + error.getMessage());
                callback.onError("Failed to get hourly data: " + error.getMessage());
            }
        });
    }

    /**
     * Get current hour sensor readings for real-time data (FIXED calculation)
     */
    private void getCurrentHourSensorData(DatabaseReference sensorRef, String date, double voltageReference, CurrentHourCallback callback) {
        Calendar currentTime = Calendar.getInstance(PHILIPPINE_TIMEZONE);
        int currentHour = currentTime.get(Calendar.HOUR_OF_DAY);
        String currentHourStr = String.format("%02d", currentHour);

        Log.d(TAG, "Getting current hour sensor readings for " + date + " hour " + currentHourStr);

        String startTime = date + "T" + currentHourStr + ":00:00";
        String endTime = date + "T" + currentHourStr + ":59:59";

        sensorRef.orderByChild("datetime")
                .startAt(startTime)
                .endAt(endTime)
                .limitToLast(50) // Get recent readings for current hour
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (!snapshot.exists()) {
                            Log.d(TAG, "No current hour sensor readings found");
                            callback.onError("No current hour data available");
                            return;
                        }

                        try {
                            CurrentHourData currentHourData = processCurrentHourReadings(snapshot, currentHourStr, voltageReference);
                            callback.onCurrentHourProcessed(currentHourData);
                        } catch (Exception e) {
                            Log.e(TAG, "Error processing current hour readings: " + e.getMessage(), e);
                            callback.onError("Failed to process current hour readings");
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Error getting current hour sensor readings: " + error.getMessage());
                        callback.onError("Database error: " + error.getMessage());
                    }
                });
    }

    /**
     * Process current hour sensor readings - FIXED to match DataProcessingManager calculation
     */
    private CurrentHourData processCurrentHourReadings(DataSnapshot readings, String hour, double voltageReference) {
        CurrentHourData currentHourData = new CurrentHourData();
        currentHourData.hour = hour;

        int validReadings = 0;
        double totalWatts = 0.0;
        double area1Watts = 0.0;
        double area2Watts = 0.0;
        double area3Watts = 0.0;
        double maxWatts = 0.0;

        for (DataSnapshot readingSnapshot : readings.getChildren()) {
            try {
                Map<String, Object> reading = (Map<String, Object>) readingSnapshot.getValue();
                if (reading != null && reading.containsKey("value")) {
                    String value = (String) reading.get("value");
                    if (value != null && !value.trim().isEmpty()) {
                        // Parse sensor reading: "BV,CV,A1,A2,A3,timestamp"
                        String[] parts = value.split(",");
                        if (parts.length >= 5) {
                            // FIXED: Use same calculation as DataProcessingManager
                            double current1 = parseDouble(parts[2]); // A1
                            double current2 = parseDouble(parts[3]); // A2
                            double current3 = parseDouble(parts[4]); // A3

                            // Calculate watts: P = V * I (same as DataProcessingManager)
                            double area1Power = voltageReference * current1;
                            double area2Power = voltageReference * current2;
                            double area3Power = voltageReference * current3;
                            double totalPower = area1Power + area2Power + area3Power;

                            area1Watts += area1Power;
                            area2Watts += area2Power;
                            area3Watts += area3Power;
                            totalWatts += totalPower;
                            maxWatts = Math.max(maxWatts, totalPower);

                            validReadings++;
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Error parsing sensor reading: " + e.getMessage());
            }
        }

        if (validReadings > 0) {
            // Calculate averages (same as DataProcessingManager)
            currentHourData.avgTotalWatts = totalWatts / validReadings;
            currentHourData.avgArea1Watts = area1Watts / validReadings;
            currentHourData.avgArea2Watts = area2Watts / validReadings;
            currentHourData.avgArea3Watts = area3Watts / validReadings;
            currentHourData.peakWatts = maxWatts;
            currentHourData.validReadings = validReadings;

            // Calculate partial kWh based on elapsed time in current hour
            Calendar now = Calendar.getInstance(PHILIPPINE_TIMEZONE);
            int minutesElapsed = now.get(Calendar.MINUTE);
            double hourProgress = minutesElapsed / 60.0; // 0.0 to 1.0

            // FIXED: Use same kWh calculation as DataProcessingManager
            double expectedReadings = 720.0; // 12 readings per minute * 60 minutes
            double timeCoverage = Math.min(1.0, validReadings / expectedReadings);

            // Use the same formula: (avgWatts / 1000.0) * timeCoverage
            currentHourData.partialKwh = (currentHourData.avgTotalWatts / 1000.0) * timeCoverage;
            currentHourData.area1PartialKwh = (currentHourData.avgArea1Watts / 1000.0) * timeCoverage;
            currentHourData.area2PartialKwh = (currentHourData.avgArea2Watts / 1000.0) * timeCoverage;
            currentHourData.area3PartialKwh = (currentHourData.avgArea3Watts / 1000.0) * timeCoverage;

            Log.d(TAG, String.format("Current hour (%s) processed: %.0fW avg, %.3f kWh partial (%.1f%% coverage, %d readings)",
                    hour, currentHourData.avgTotalWatts, currentHourData.partialKwh,
                    (timeCoverage * 100), validReadings));
        }

        return currentHourData;
    }

    /**
     * Build complete real-time data from hourly summaries AND current hour data
     */
    private RealTimeData buildCompleteRealTimeData(DataSnapshot hourlySnapshot, CurrentHourData currentHourData,
                                                   double electricityRate, String date) {
        RealTimeData realTimeData = new RealTimeData();
        realTimeData.lastUpdateTime = getCurrentPhilippineTime();
        realTimeData.electricityRate = electricityRate;
        realTimeData.date = date;

        // Initialize area data
        realTimeData.area1Data = new AreaData("Living Room");
        realTimeData.area2Data = new AreaData("Kitchen");
        realTimeData.area3Data = new AreaData("Bedroom");

        // Initialize 24-hour chart data
        realTimeData.hourlyChartData = new ArrayList<>();
        realTimeData.area1Data.hourlyData = new ArrayList<>();
        realTimeData.area2Data.hourlyData = new ArrayList<>();
        realTimeData.area3Data.hourlyData = new ArrayList<>();

        // Initialize with zeros for all 24 hours
        for (int hour = 0; hour < 24; hour++) {
            realTimeData.hourlyChartData.add(0.0);
            realTimeData.area1Data.hourlyData.add(0.0);
            realTimeData.area2Data.hourlyData.add(0.0);
            realTimeData.area3Data.hourlyData.add(0.0);
        }

        // Process completed hourly data
        processHourlyData(hourlySnapshot, realTimeData);

        // Add current hour data if available
        if (currentHourData != null && currentHourData.validReadings > 0) {
            addCurrentHourToRealTimeData(currentHourData, realTimeData);
        }

        // Calculate basic derived values (percentages only, peaks handled separately)
        calculateBasicDerivedValues(realTimeData);

        return realTimeData;
    }

    /**
     * Process hourly summary data - FIXED to use consistent calculations
     */
    private void processHourlyData(DataSnapshot hourlySnapshot, RealTimeData realTimeData) {
        double dailyTotalKwh = 0.0;
        double area1TotalKwh = 0.0;
        double area2TotalKwh = 0.0;
        double area3TotalKwh = 0.0;
        double maxPeakWatts = 0.0;
        String peakTime = "";

        for (DataSnapshot hourSnapshot : hourlySnapshot.getChildren()) {
            try {
                int hour = Integer.parseInt(hourSnapshot.getKey());
                if (hour < 24) {
                    // Process main hourly data
                    if (hourSnapshot.child("total_kwh").exists()) {
                        Double totalKwh = hourSnapshot.child("total_kwh").getValue(Double.class);
                        if (totalKwh != null) {
                            dailyTotalKwh += totalKwh;
                        }
                    }

                    if (hourSnapshot.child("avg_watts").exists()) {
                        Double avgWatts = hourSnapshot.child("avg_watts").getValue(Double.class);
                        if (avgWatts != null) {
                            realTimeData.hourlyChartData.set(hour, avgWatts / 1000.0); // Convert to kW
                        }
                    }

                    // FIXED: Process area-specific data for charts using calculated watts from kWh
                    if (hourSnapshot.child("area1_kwh").exists()) {
                        Double area1Kwh = hourSnapshot.child("area1_kwh").getValue(Double.class);
                        if (area1Kwh != null) {
                            area1TotalKwh += area1Kwh;
                            // Convert kWh back to average watts for chart consistency
                            double area1AvgWatts = area1Kwh * 1000.0; // kWh * 1000 = Wh, assume 1 hour
                            realTimeData.area1Data.hourlyData.set(hour, area1AvgWatts);
                        }
                    }

                    if (hourSnapshot.child("area2_kwh").exists()) {
                        Double area2Kwh = hourSnapshot.child("area2_kwh").getValue(Double.class);
                        if (area2Kwh != null) {
                            area2TotalKwh += area2Kwh;
                            double area2AvgWatts = area2Kwh * 1000.0;
                            realTimeData.area2Data.hourlyData.set(hour, area2AvgWatts);
                        }
                    }

                    if (hourSnapshot.child("area3_kwh").exists()) {
                        Double area3Kwh = hourSnapshot.child("area3_kwh").getValue(Double.class);
                        if (area3Kwh != null) {
                            area3TotalKwh += area3Kwh;
                            double area3AvgWatts = area3Kwh * 1000.0;
                            realTimeData.area3Data.hourlyData.set(hour, area3AvgWatts);
                        }
                    }

                    // Track peak usage (consistent with DataProcessingManager)
                    if (hourSnapshot.child("peak_watts").exists()) {
                        Double peakWatts = hourSnapshot.child("peak_watts").getValue(Double.class);
                        if (peakWatts != null && peakWatts > maxPeakWatts) {
                            maxPeakWatts = peakWatts;
                            peakTime = String.format("%02d:00", hour);
                        }
                    }
                }
            } catch (NumberFormatException e) {
                Log.w(TAG, "Invalid hour key: " + hourSnapshot.getKey());
            }
        }

        // Set calculated daily totals
        realTimeData.totalKwhToday = dailyTotalKwh;
        realTimeData.estimatedCostToday = dailyTotalKwh * realTimeData.electricityRate;
        realTimeData.peakUsageValue = maxPeakWatts;
        realTimeData.peakUsageTime = peakTime.isEmpty() ? "--:--" : peakTime;

        // Set area totals and costs
        realTimeData.area1Data.totalConsumption = area1TotalKwh;
        realTimeData.area1Data.estimatedCost = area1TotalKwh * realTimeData.electricityRate;

        realTimeData.area2Data.totalConsumption = area2TotalKwh;
        realTimeData.area2Data.estimatedCost = area2TotalKwh * realTimeData.electricityRate;

        realTimeData.area3Data.totalConsumption = area3TotalKwh;
        realTimeData.area3Data.estimatedCost = area3TotalKwh * realTimeData.electricityRate;

        Log.d(TAG, "Processed " + hourlySnapshot.getChildrenCount() + " hours of data");
    }

    /**
     * Add current hour data - FIXED to use consistent calculations
     */
    private void addCurrentHourToRealTimeData(CurrentHourData currentHourData, RealTimeData realTimeData) {
        int currentHour = Integer.parseInt(currentHourData.hour);

        // Add current hour data to charts (consistent with hourly data)
        if (currentHour < 24) {
            realTimeData.hourlyChartData.set(currentHour, currentHourData.avgTotalWatts / 1000.0);
            realTimeData.area1Data.hourlyData.set(currentHour, currentHourData.avgArea1Watts);
            realTimeData.area2Data.hourlyData.set(currentHour, currentHourData.avgArea2Watts);
            realTimeData.area3Data.hourlyData.set(currentHour, currentHourData.avgArea3Watts);
        }

        // Add partial consumption to daily totals
        realTimeData.totalKwhToday += currentHourData.partialKwh;
        realTimeData.estimatedCostToday += currentHourData.partialKwh * realTimeData.electricityRate;

        realTimeData.area1Data.totalConsumption += currentHourData.area1PartialKwh;
        realTimeData.area1Data.estimatedCost += currentHourData.area1PartialKwh * realTimeData.electricityRate;

        realTimeData.area2Data.totalConsumption += currentHourData.area2PartialKwh;
        realTimeData.area2Data.estimatedCost += currentHourData.area2PartialKwh * realTimeData.electricityRate;

        realTimeData.area3Data.totalConsumption += currentHourData.area3PartialKwh;
        realTimeData.area3Data.estimatedCost += currentHourData.area3PartialKwh * realTimeData.electricityRate;

        // Update peak if current hour has higher peak (now using consistent calculation)
        if (currentHourData.peakWatts > realTimeData.peakUsageValue) {
            realTimeData.peakUsageValue = currentHourData.peakWatts;
            Calendar now = Calendar.getInstance(PHILIPPINE_TIMEZONE);
            realTimeData.peakUsageTime = String.format("%02d:%02d", currentHour, now.get(Calendar.MINUTE));
        }

        Log.d(TAG, "Current hour data integrated - added " + currentHourData.partialKwh + " kWh to totals");
    }

    /**
     * Calculate basic derived values (percentages only, peaks handled separately)
     */
    private void calculateBasicDerivedValues(RealTimeData realTimeData) {
        double totalConsumption = realTimeData.totalKwhToday;

        // Calculate area percentages
        if (totalConsumption > 0) {
            realTimeData.area1Data.sharePercentage = (realTimeData.area1Data.totalConsumption / totalConsumption) * 100;
            realTimeData.area2Data.sharePercentage = (realTimeData.area2Data.totalConsumption / totalConsumption) * 100;
            realTimeData.area3Data.sharePercentage = (realTimeData.area3Data.totalConsumption / totalConsumption) * 100;
        } else {
            realTimeData.area1Data.sharePercentage = 0.0;
            realTimeData.area2Data.sharePercentage = 0.0;
            realTimeData.area3Data.sharePercentage = 0.0;
        }
    }

    /**
     * NEW: Enhanced method to calculate REAL area peaks from raw readings
     */
    private void calculateRealAreaPeaksFromRawData(RealTimeData realTimeData, String date,
                                                   double voltageReference, DataProcessingCallback callback) {
        Log.d(TAG, "Calculating real area peaks from raw data for date: " + date);

        // Use sensor_readings path (based on your existing code structure)
        DatabaseReference rawReadingsRef = databaseRef.child("sensor_readings");

        // Get today's raw readings using the same datetime format as existing code
        String startTime = date + "T00:00:00";
        String endTime = date + "T23:59:59";

        rawReadingsRef.orderByChild("datetime")
                .startAt(startTime)
                .endAt(endTime)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (!snapshot.exists()) {
                            Log.w(TAG, "No raw readings found, using proportional fallback");
                            useProportionalPeakDistribution(realTimeData);
                            callback.onDataProcessed(realTimeData);
                            return;
                        }

                        double maxArea1 = 0, maxArea2 = 0, maxArea3 = 0;
                        String area1PeakTime = "--:--", area2PeakTime = "--:--", area3PeakTime = "--:--";
                        int totalReadings = 0;

                        for (DataSnapshot readingSnapshot : snapshot.getChildren()) {
                            try {
                                Map<String, Object> reading = (Map<String, Object>) readingSnapshot.getValue();
                                if (reading != null && reading.containsKey("value") && reading.containsKey("datetime")) {
                                    String value = (String) reading.get("value");
                                    String datetime = (String) reading.get("datetime");

                                    if (value != null && datetime != null && !value.trim().isEmpty()) {
                                        // Parse sensor reading: "BV,CV,A1,A2,A3,IR"
                                        String[] parts = value.split(",");
                                        if (parts.length >= 5) {
                                            double current1 = parseDouble(parts[2]); // A1
                                            double current2 = parseDouble(parts[3]); // A2
                                            double current3 = parseDouble(parts[4]); // A3

                                            // Calculate power: P = V * I (same as DataProcessingManager)
                                            double area1Power = voltageReference * current1;
                                            double area2Power = voltageReference * current2;
                                            double area3Power = voltageReference * current3;

                                            String timeOnly = datetime.length() >= 16 ?
                                                    datetime.substring(11, 16) : "--:--"; // Extract HH:MM

                                            // Track individual area peaks
                                            if (area1Power > maxArea1) {
                                                maxArea1 = area1Power;
                                                area1PeakTime = timeOnly;
                                            }
                                            if (area2Power > maxArea2) {
                                                maxArea2 = area2Power;
                                                area2PeakTime = timeOnly;
                                            }
                                            if (area3Power > maxArea3) {
                                                maxArea3 = area3Power;
                                                area3PeakTime = timeOnly;
                                            }

                                            totalReadings++;
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                // Skip invalid readings
                                Log.w(TAG, "Skipping invalid reading: " + e.getMessage());
                            }
                        }

                        Log.d(TAG, String.format("Processed %d raw readings for peak calculation", totalReadings));

                        if (totalReadings > 0) {
                            // Update area data with REAL instantaneous peaks
                            realTimeData.area1Data.peakConsumption = maxArea1;
                            realTimeData.area1Data.peakTime = area1PeakTime;

                            realTimeData.area2Data.peakConsumption = maxArea2;
                            realTimeData.area2Data.peakTime = area2PeakTime;

                            realTimeData.area3Data.peakConsumption = maxArea3;
                            realTimeData.area3Data.peakTime = area3PeakTime;

                            // Verify the math matches the overall peak
                            double calculatedTotal = maxArea1 + maxArea2 + maxArea3;
                            Log.d(TAG, String.format("Peak validation - Overall: %.0fW, Areas: %.0f+%.0f+%.0f=%.0fW",
                                    realTimeData.peakUsageValue, maxArea1, maxArea2, maxArea3, calculatedTotal));

                            double difference = Math.abs(calculatedTotal - realTimeData.peakUsageValue);
                            if (difference > 10) {
                                Log.w(TAG, String.format("Peak mismatch: %.0fW difference - but using real instantaneous data", difference));
                            } else {
                                Log.d(TAG, "✅ Peak calculations validated!");
                            }
                        } else {
                            Log.w(TAG, "No valid readings found, using proportional fallback");
                            useProportionalPeakDistribution(realTimeData);
                        }

                        // Callback with corrected data
                        callback.onDataProcessed(realTimeData);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Failed to get raw readings: " + error.getMessage());
                        Log.w(TAG, "Using fallback proportional peak distribution");
                        useProportionalPeakDistribution(realTimeData);
                        callback.onDataProcessed(realTimeData);
                    }
                });
    }

    /**
     * NEW: Fallback method - Distribute total peak proportionally across areas
     */
    private void useProportionalPeakDistribution(RealTimeData realTimeData) {
        double totalConsumption = realTimeData.totalKwhToday;

        if (totalConsumption > 0 && realTimeData.peakUsageValue > 0) {
            // Calculate area percentages
            double area1Percentage = realTimeData.area1Data.totalConsumption / totalConsumption;
            double area2Percentage = realTimeData.area2Data.totalConsumption / totalConsumption;
            double area3Percentage = realTimeData.area3Data.totalConsumption / totalConsumption;

            // Distribute peak proportionally (ensures they add up to total)
            realTimeData.area1Data.peakConsumption = realTimeData.peakUsageValue * area1Percentage;
            realTimeData.area2Data.peakConsumption = realTimeData.peakUsageValue * area2Percentage;
            realTimeData.area3Data.peakConsumption = realTimeData.peakUsageValue * area3Percentage;

            // All areas show same peak time as overall peak
            realTimeData.area1Data.peakTime = realTimeData.peakUsageTime;
            realTimeData.area2Data.peakTime = realTimeData.peakUsageTime;
            realTimeData.area3Data.peakTime = realTimeData.peakUsageTime;

            Log.d(TAG, String.format("Using proportional peaks: %.0f + %.0f + %.0f = %.0fW (✅ Perfect match)",
                    realTimeData.area1Data.peakConsumption,
                    realTimeData.area2Data.peakConsumption,
                    realTimeData.area3Data.peakConsumption,
                    realTimeData.peakUsageValue));
        } else {
            // No data available, set to zero
            realTimeData.area1Data.peakConsumption = 0.0;
            realTimeData.area1Data.peakTime = "--:--";
            realTimeData.area2Data.peakConsumption = 0.0;
            realTimeData.area2Data.peakTime = "--:--";
            realTimeData.area3Data.peakConsumption = 0.0;
            realTimeData.area3Data.peakTime = "--:--";

            Log.w(TAG, "No consumption data available for peak calculation");
        }
    }

    /**
     * Helper method to safely parse double values
     */
    private double parseDouble(String value) {
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /**
     * Get current Philippine date
     */
    private String getCurrentPhilippineDate() {
        Calendar calendar = Calendar.getInstance(PHILIPPINE_TIMEZONE);
        return dateFormat.format(calendar.getTime());
    }

    /**
     * Get current Philippine time
     */
    private String getCurrentPhilippineTime() {
        Calendar calendar = Calendar.getInstance(PHILIPPINE_TIMEZONE);
        return timeFormat.format(calendar.getTime());
    }

    // Data classes
    public static class RealTimeData {
        public String date;
        public String lastUpdateTime;
        public double totalKwhToday;
        public double estimatedCostToday;
        public double peakUsageValue;
        public String peakUsageTime;
        public double electricityRate;

        public AreaData area1Data;
        public AreaData area2Data;
        public AreaData area3Data;

        public List<Double> hourlyChartData;
    }

    public static class AreaData {
        public String name;
        public double totalConsumption;
        public double estimatedCost;
        public double peakConsumption;
        public String peakTime;
        public double sharePercentage;
        public List<Double> hourlyData;

        public AreaData(String name) {
            this.name = name;
            this.totalConsumption = 0.0;
            this.estimatedCost = 0.0;
            this.peakConsumption = 0.0;
            this.peakTime = "--:--";
            this.sharePercentage = 0.0;
            this.hourlyData = new ArrayList<>();
        }
    }

    public static class CurrentHourData {
        public String hour;
        public double avgTotalWatts;
        public double avgArea1Watts;
        public double avgArea2Watts;
        public double avgArea3Watts;
        public double peakWatts;
        public double partialKwh;
        public double area1PartialKwh;
        public double area2PartialKwh;
        public double area3PartialKwh;
        public int validReadings;
    }

    // Callback interfaces
    public interface DataProcessingCallback {
        void onDataProcessed(RealTimeData realTimeData);
        void onError(String error);
    }

    public interface SystemSettingsCallback {
        void onSettingsLoaded(double electricityRate, double voltageReference);
        void onError(String error);
    }

    public interface CurrentHourCallback {
        void onCurrentHourProcessed(CurrentHourData currentHourData);
        void onError(String error);
    }
}