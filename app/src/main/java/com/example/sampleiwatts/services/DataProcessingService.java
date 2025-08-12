package com.example.sampleiwatts.services;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.database.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class DataProcessingService extends Service {
    private static final String TAG = "DataProcessingService";
    private static final String DEVICE_ID = "device_001";

    // Processing intervals
    private static final long PROCESSING_INTERVAL = TimeUnit.MINUTES.toMillis(5); // Every 5 minutes
    private static final long DAILY_PROCESSING_INTERVAL = TimeUnit.HOURS.toMillis(1); // Every hour

    private DatabaseReference databaseRef;
    private Handler processingHandler;
    private Handler dailyProcessingHandler;
    private SimpleDateFormat dateFormat;

    private Runnable processingRunnable = new Runnable() {
        @Override
        public void run() {
            processUnprocessedHours();
            processingHandler.postDelayed(this, PROCESSING_INTERVAL);
        }
    };

    private Runnable dailyProcessingRunnable = new Runnable() {
        @Override
        public void run() {
            processDailySummaries();
            dailyProcessingHandler.postDelayed(this, DAILY_PROCESSING_INTERVAL);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        databaseRef = FirebaseDatabase.getInstance().getReference();
        processingHandler = new Handler(Looper.getMainLooper());
        dailyProcessingHandler = new Handler(Looper.getMainLooper());
        dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

        Log.d(TAG, "DataProcessingService created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "DataProcessingService started - beginning automated processing");

        // Start automated processing
        processingHandler.post(processingRunnable);
        dailyProcessingHandler.post(dailyProcessingRunnable);

        return START_STICKY; // Restart service if killed
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        processingHandler.removeCallbacks(processingRunnable);
        dailyProcessingHandler.removeCallbacks(dailyProcessingRunnable);

        Log.d(TAG, "DataProcessingService destroyed");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * Main processing function - runs every 5 minutes
     */
    private void processUnprocessedHours() {
        String today = dateFormat.format(new Date());
        int currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);

        Log.d(TAG, "Processing unprocessed hours for " + today + ", current hour: " + currentHour);

        // Get device electricity rate
        databaseRef.child("devices").child(DEVICE_ID).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot deviceSnapshot) {
                double electricityRate = 8.50; // default
                if (deviceSnapshot.exists() && deviceSnapshot.child("electricity_rate").exists()) {
                    electricityRate = deviceSnapshot.child("electricity_rate").getValue(Double.class);
                }

                processHoursForDate(today, currentHour, electricityRate);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to get electricity rate: " + error.getMessage());
            }
        });
    }

    /**
     * Process hours for a specific date
     */
    private void processHoursForDate(String date, int maxHour, double electricityRate) {
        // Check existing summaries
        databaseRef.child("hourly_summaries").child(DEVICE_ID).child(date)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot existingSummariesSnapshot) {
                        final Map<String, Object> existingSummaries = new HashMap<>();
                        if (existingSummariesSnapshot.exists()) {
                            Map<String, Object> tempSummaries = (Map<String, Object>) existingSummariesSnapshot.getValue();
                            if (tempSummaries != null) {
                                existingSummaries.putAll(tempSummaries);
                            }
                        }

                        // Get raw readings
                        databaseRef.child("raw_readings").child(DEVICE_ID).child(date)
                                .addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override
                                    public void onDataChange(@NonNull DataSnapshot rawReadingsSnapshot) {
                                        if (!rawReadingsSnapshot.exists()) {
                                            Log.d(TAG, "No raw readings found for " + date);
                                            return;
                                        }

                                        Map<String, Object> rawReadings = (Map<String, Object>) rawReadingsSnapshot.getValue();
                                        processDateData(date, rawReadings, existingSummaries, electricityRate, maxHour);
                                    }

                                    @Override
                                    public void onCancelled(@NonNull DatabaseError error) {
                                        Log.e(TAG, "Failed to get raw readings: " + error.getMessage());
                                    }
                                });
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Failed to get existing summaries: " + error.getMessage());
                    }
                });
    }

    /**
     * Process data for a specific date
     */
    private void processDateData(String date, Map<String, Object> rawReadings,
                                 Map<String, Object> existingSummaries, double electricityRate, int maxHour) {

        // Group readings by hour
        Map<Integer, List<Map<String, Object>>> hourlyData = groupReadingsByHour(rawReadings, date);

        // Process each hour that doesn't have a summary
        for (int hour = 0; hour < maxHour; hour++) {
            String hourKey = String.valueOf(hour);

            // Skip if already processed
            if (existingSummaries.containsKey(hourKey)) {
                continue;
            }

            // Skip if no data for this hour
            if (!hourlyData.containsKey(hour)) {
                continue;
            }

            List<Map<String, Object>> hourReadings = hourlyData.get(hour);
            if (hourReadings.isEmpty()) {
                continue;
            }

            // Process this hour
            processSingleHour(date, hour, electricityRate, hourReadings);
        }
    }

    /**
     * Group readings by hour (data already in Philippine time)
     */
    private Map<Integer, List<Map<String, Object>>> groupReadingsByHour(Map<String, Object> rawReadings, String targetDate) {
        Map<Integer, List<Map<String, Object>>> hourlyData = new HashMap<>();

        for (Map.Entry<String, Object> entry : rawReadings.entrySet()) {
            try {
                long timestamp = Long.parseLong(entry.getKey());
                Date readingDate = new Date(timestamp);
                String readingDateStr = dateFormat.format(readingDate);

                // Only process readings for target date
                if (!readingDateStr.equals(targetDate)) {
                    continue;
                }

                Calendar cal = Calendar.getInstance();
                cal.setTime(readingDate);
                int hour = cal.get(Calendar.HOUR_OF_DAY);

                hourlyData.computeIfAbsent(hour, k -> new ArrayList<>()).add((Map<String, Object>) entry.getValue());

            } catch (NumberFormatException e) {
                Log.w(TAG, "Invalid timestamp: " + entry.getKey());
            }
        }

        return hourlyData;
    }

    /**
     * Process a single hour with incomplete reading handling
     */
    private void processSingleHour(String date, int hour, double electricityRate, List<Map<String, Object>> readings) {
        int actualReadingCount = readings.size();
        int expectedReadingsPerHour = 720; // 3600 seconds ÷ 5 seconds

        double completionPercentage = (double) actualReadingCount / expectedReadingsPerHour;

        Log.d(TAG, "Processing hour " + hour + ": " + actualReadingCount + "/" + expectedReadingsPerHour +
                " readings (" + String.format("%.1f", completionPercentage * 100) + "% complete)");

        // Initialize totals
        double totalEnergyKwh = 0.0;
        double area1EnergyKwh = 0.0;
        double area2EnergyKwh = 0.0;
        double area3EnergyKwh = 0.0;

        double totalPowerSum = 0.0;
        double area1PowerSum = 0.0;
        double area2PowerSum = 0.0;
        double area3PowerSum = 0.0;

        double batterySum = 0.0;
        int validReadingCount = 0;

        double peakPowerWatts = 0.0;
        double minPowerWatts = Double.MAX_VALUE;

        // Process each reading
        for (Map<String, Object> reading : readings) {
            try {
                // Extract readings data
                Map<String, Object> readingsData = (Map<String, Object>) reading.get("readings");
                if (readingsData == null) continue;

                double area1Power = extractAreaPower(readingsData, "area1");
                double area2Power = extractAreaPower(readingsData, "area2");
                double area3Power = extractAreaPower(readingsData, "area3");
                double totalPower = area1Power + area2Power + area3Power;

                // Each reading represents 5 seconds of consumption
                double timeIntervalHours = 5.0 / 3600.0; // 0.001388889 hours

                // Calculate energy (device was online for this reading)
                area1EnergyKwh += (area1Power / 1000.0) * timeIntervalHours;
                area2EnergyKwh += (area2Power / 1000.0) * timeIntervalHours;
                area3EnergyKwh += (area3Power / 1000.0) * timeIntervalHours;
                totalEnergyKwh += (totalPower / 1000.0) * timeIntervalHours;

                // Accumulate for averages
                totalPowerSum += totalPower;
                area1PowerSum += area1Power;
                area2PowerSum += area2Power;
                area3PowerSum += area3Power;

                // Track peak/min
                peakPowerWatts = Math.max(peakPowerWatts, totalPower);
                if (totalPower > 0) {
                    minPowerWatts = Math.min(minPowerWatts, totalPower);
                }

                // Battery level
                if (reading.containsKey("battery_level")) {
                    batterySum += ((Number) reading.get("battery_level")).doubleValue();
                }

                validReadingCount++;

            } catch (Exception e) {
                Log.w(TAG, "Error processing reading: " + e.getMessage());
            }
        }

        if (validReadingCount == 0) {
            Log.w(TAG, "No valid readings for hour " + hour);
            return;
        }

        // Calculate averages
        double avgPowerWatts = totalPowerSum / validReadingCount;
        double avgBatteryLevel = batterySum / validReadingCount;
        double avgArea1Power = area1PowerSum / validReadingCount;
        double avgArea2Power = area2PowerSum / validReadingCount;
        double avgArea3Power = area3PowerSum / validReadingCount;

        // Calculate costs
        double totalCostPhp = totalEnergyKwh * electricityRate;
        double area1CostPhp = area1EnergyKwh * electricityRate;
        double area2CostPhp = area2EnergyKwh * electricityRate;
        double area3CostPhp = area3EnergyKwh * electricityRate;

        // Data quality assessment
        String dataQuality = getDataQualityStatus(completionPercentage);

        // Save hourly summary
        saveHourlySummary(date, hour, electricityRate,
                totalEnergyKwh, totalCostPhp, avgPowerWatts, peakPowerWatts,
                minPowerWatts == Double.MAX_VALUE ? 0.0 : minPowerWatts,
                avgBatteryLevel, validReadingCount, completionPercentage, dataQuality,
                area1EnergyKwh, area1CostPhp, avgArea1Power,
                area2EnergyKwh, area2CostPhp, avgArea2Power,
                area3EnergyKwh, area3CostPhp, avgArea3Power);

        Log.d(TAG, "Completed hour " + hour + ": " + String.format("%.3f", totalEnergyKwh) +
                " kWh, ₱" + String.format("%.2f", totalCostPhp) + " [" + dataQuality + "]");
    }

    /**
     * Extract power for specific area
     */
    private double extractAreaPower(Map<String, Object> readings, String areaKey) {
        try {
            Map<String, Object> areaData = (Map<String, Object>) readings.get(areaKey);
            if (areaData != null && areaData.containsKey("power_watts")) {
                return ((Number) areaData.get("power_watts")).doubleValue();
            }
        } catch (Exception e) {
            Log.w(TAG, "Error extracting power for " + areaKey + ": " + e.getMessage());
        }
        return 0.0;
    }

    /**
     * Assess data quality based on completeness
     */
    private String getDataQualityStatus(double completionPercentage) {
        if (completionPercentage >= 0.95) return "Excellent";
        else if (completionPercentage >= 0.85) return "Good";
        else if (completionPercentage >= 0.70) return "Fair";
        else if (completionPercentage >= 0.50) return "Poor";
        else return "Very Poor";
    }

    /**
     * Save hourly summary to database
     */
    private void saveHourlySummary(String date, int hour, double electricityRate,
                                   double totalEnergyKwh, double totalCostPhp, double avgPowerWatts,
                                   double peakPowerWatts, double minPowerWatts, double avgBatteryLevel,
                                   int readingCount, double completionPercentage, String dataQuality,
                                   double area1EnergyKwh, double area1CostPhp, double avgArea1Power,
                                   double area2EnergyKwh, double area2CostPhp, double avgArea2Power,
                                   double area3EnergyKwh, double area3CostPhp, double avgArea3Power) {

        // Create hourly summary
        Map<String, Object> hourlySummary = new HashMap<>();
        hourlySummary.put("hour", hour);
        hourlySummary.put("date", date);
        hourlySummary.put("device_id", DEVICE_ID);
        hourlySummary.put("total_energy_kwh", Math.round(totalEnergyKwh * 1000.0) / 1000.0);
        hourlySummary.put("total_cost_php", Math.round(totalCostPhp * 100.0) / 100.0);
        hourlySummary.put("avg_power_watts", Math.round(avgPowerWatts * 10.0) / 10.0);
        hourlySummary.put("peak_power_watts", Math.round(peakPowerWatts * 10.0) / 10.0);
        hourlySummary.put("min_power_watts", Math.round(minPowerWatts * 10.0) / 10.0);
        hourlySummary.put("avg_battery_level", Math.round(avgBatteryLevel * 10.0) / 10.0);
        hourlySummary.put("readings_processed", readingCount);
        hourlySummary.put("data_completeness_percent", Math.round(completionPercentage * 1000.0) / 10.0);
        hourlySummary.put("data_quality", dataQuality);
        hourlySummary.put("electricity_rate", electricityRate);
        hourlySummary.put("processed_at", System.currentTimeMillis());

        // Area breakdown
        Map<String, Object> areaBreakdown = new HashMap<>();

        Map<String, Object> area1Data = new HashMap<>();
        area1Data.put("total_energy_kwh", Math.round(area1EnergyKwh * 1000.0) / 1000.0);
        area1Data.put("total_cost_php", Math.round(area1CostPhp * 100.0) / 100.0);
        area1Data.put("avg_power_watts", Math.round(avgArea1Power * 10.0) / 10.0);

        Map<String, Object> area2Data = new HashMap<>();
        area2Data.put("total_energy_kwh", Math.round(area2EnergyKwh * 1000.0) / 1000.0);
        area2Data.put("total_cost_php", Math.round(area2CostPhp * 100.0) / 100.0);
        area2Data.put("avg_power_watts", Math.round(avgArea2Power * 10.0) / 10.0);

        Map<String, Object> area3Data = new HashMap<>();
        area3Data.put("total_energy_kwh", Math.round(area3EnergyKwh * 1000.0) / 1000.0);
        area3Data.put("total_cost_php", Math.round(area3CostPhp * 100.0) / 100.0);
        area3Data.put("avg_power_watts", Math.round(avgArea3Power * 10.0) / 10.0);

        areaBreakdown.put("area1", area1Data);
        areaBreakdown.put("area2", area2Data);
        areaBreakdown.put("area3", area3Data);

        hourlySummary.put("area_breakdown", areaBreakdown);

        // Save to database
        databaseRef.child("hourly_summaries").child(DEVICE_ID).child(date).child(String.valueOf(hour))
                .setValue(hourlySummary)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "✅ Saved hourly summary: " + date + " hour " + hour);
                })
                .addOnFailureListener(exception -> {
                    Log.e(TAG, "❌ Failed to save hourly summary: " + exception.getMessage());
                });
    }

    /**
     * Process daily summaries - runs every hour
     */
    private void processDailySummaries() {
        Log.d(TAG, "Processing daily summaries");

        // Get electricity rate
        databaseRef.child("devices").child(DEVICE_ID).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot deviceSnapshot) {
                double electricityRate = 8.50;
                if (deviceSnapshot.exists() && deviceSnapshot.child("electricity_rate").exists()) {
                    electricityRate = deviceSnapshot.child("electricity_rate").getValue(Double.class);
                }

                // Process last 7 days
                for (int i = 0; i < 7; i++) {
                    Calendar cal = Calendar.getInstance();
                    cal.add(Calendar.DAY_OF_MONTH, -i);
                    String date = dateFormat.format(cal.getTime());

                    generateDailySummaryForDate(date, electricityRate);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to get electricity rate for daily processing: " + error.getMessage());
            }
        });
    }

    /**
     * Generate daily summary for a specific date
     */
    private void generateDailySummaryForDate(String date, double electricityRate) {
        // Check if already exists
        databaseRef.child("daily_summaries").child(DEVICE_ID).child(date)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot existingSnapshot) {
                        if (existingSnapshot.exists()) {
                            return; // Already processed
                        }

                        // Get hourly summaries
                        databaseRef.child("hourly_summaries").child(DEVICE_ID).child(date)
                                .addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override
                                    public void onDataChange(@NonNull DataSnapshot hourlySnapshot) {
                                        if (hourlySnapshot.exists()) {
                                            Map<String, Object> hourlySummaries = (Map<String, Object>) hourlySnapshot.getValue();
                                            createDailySummaryFromHourlyData(date, hourlySummaries, electricityRate);
                                        }
                                    }

                                    @Override
                                    public void onCancelled(@NonNull DatabaseError error) {
                                        Log.e(TAG, "Failed to get hourly summaries for " + date);
                                    }
                                });
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Failed to check daily summary for " + date);
                    }
                });
    }

    /**
     * Create daily summary from hourly data
     */
    private void createDailySummaryFromHourlyData(String date, Map<String, Object> hourlySummaries, double electricityRate) {
        double totalEnergyKwh = 0.0;
        double totalCostPhp = 0.0;
        int totalReadingsProcessed = 0;
        double avgBatteryLevel = 0.0;
        double peakPowerWatts = 0.0;
        double minPowerWatts = Double.MAX_VALUE;

        // Area totals
        double area1TotalEnergy = 0.0, area1TotalCost = 0.0, area1AvgPower = 0.0;
        double area2TotalEnergy = 0.0, area2TotalCost = 0.0, area2AvgPower = 0.0;
        double area3TotalEnergy = 0.0, area3TotalCost = 0.0, area3AvgPower = 0.0;

        List<Double> batteryLevels = new ArrayList<>();
        List<Double> avgPowers = new ArrayList<>();
        Map<String, Object> hourlyBreakdown = new HashMap<>();

        // Process each hour
        for (Map.Entry<String, Object> entry : hourlySummaries.entrySet()) {
            Map<String, Object> hourData = (Map<String, Object>) entry.getValue();

            totalEnergyKwh += getDoubleValue(hourData, "total_energy_kwh");
            totalCostPhp += getDoubleValue(hourData, "total_cost_php");
            totalReadingsProcessed += getIntValue(hourData, "readings_processed");

            double hourPeakPower = getDoubleValue(hourData, "peak_power_watts");
            double hourMinPower = getDoubleValue(hourData, "min_power_watts");
            double hourAvgPower = getDoubleValue(hourData, "avg_power_watts");
            double hourBattery = getDoubleValue(hourData, "avg_battery_level");

            peakPowerWatts = Math.max(peakPowerWatts, hourPeakPower);
            if (hourMinPower > 0) {
                minPowerWatts = Math.min(minPowerWatts, hourMinPower);
            }

            batteryLevels.add(hourBattery);
            avgPowers.add(hourAvgPower);

            // Process area breakdown
            Map<String, Object> areaBreakdown = (Map<String, Object>) hourData.get("area_breakdown");
            if (areaBreakdown != null) {
                area1TotalEnergy += getAreaDoubleValue(areaBreakdown, "area1", "total_energy_kwh");
                area1TotalCost += getAreaDoubleValue(areaBreakdown, "area1", "total_cost_php");
                area1AvgPower += getAreaDoubleValue(areaBreakdown, "area1", "avg_power_watts");

                area2TotalEnergy += getAreaDoubleValue(areaBreakdown, "area2", "total_energy_kwh");
                area2TotalCost += getAreaDoubleValue(areaBreakdown, "area2", "total_cost_php");
                area2AvgPower += getAreaDoubleValue(areaBreakdown, "area2", "avg_power_watts");

                area3TotalEnergy += getAreaDoubleValue(areaBreakdown, "area3", "total_energy_kwh");
                area3TotalCost += getAreaDoubleValue(areaBreakdown, "area3", "total_cost_php");
                area3AvgPower += getAreaDoubleValue(areaBreakdown, "area3", "avg_power_watts");
            }

            hourlyBreakdown.put(entry.getKey(), hourData);
        }

        int hourCount = hourlySummaries.size();
        if (hourCount == 0) {
            Log.w(TAG, "No hourly data for " + date);
            return;
        }

        // Calculate averages
        avgBatteryLevel = batteryLevels.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double avgPowerWatts = avgPowers.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        area1AvgPower /= hourCount;
        area2AvgPower /= hourCount;
        area3AvgPower /= hourCount;

        // Create daily summary
        Map<String, Object> dailySummary = new HashMap<>();
        dailySummary.put("date", date);
        dailySummary.put("device_id", DEVICE_ID);
        dailySummary.put("total_energy_kwh", Math.round(totalEnergyKwh * 1000.0) / 1000.0);
        dailySummary.put("total_cost_php", Math.round(totalCostPhp * 100.0) / 100.0);
        dailySummary.put("avg_power_watts", Math.round(avgPowerWatts * 10.0) / 10.0);
        dailySummary.put("peak_power_watts", Math.round(peakPowerWatts * 10.0) / 10.0);
        dailySummary.put("min_power_watts", minPowerWatts == Double.MAX_VALUE ? 0.0 : Math.round(minPowerWatts * 10.0) / 10.0);
        dailySummary.put("avg_battery_level", Math.round(avgBatteryLevel * 10.0) / 10.0);
        dailySummary.put("total_readings_processed", totalReadingsProcessed);
        dailySummary.put("hours_with_data", hourCount);
        dailySummary.put("electricity_rate", electricityRate);
        dailySummary.put("processed_at", System.currentTimeMillis());

        // Area daily data
        Map<String, Object> areaDailyData = new HashMap<>();

        Map<String, Object> area1Daily = new HashMap<>();
        area1Daily.put("total_energy_kwh", Math.round(area1TotalEnergy * 1000.0) / 1000.0);
        area1Daily.put("total_cost_php", Math.round(area1TotalCost * 100.0) / 100.0);
        area1Daily.put("avg_power_watts", Math.round(area1AvgPower * 10.0) / 10.0);

        Map<String, Object> area2Daily = new HashMap<>();
        area2Daily.put("total_energy_kwh", Math.round(area2TotalEnergy * 1000.0) / 1000.0);
        area2Daily.put("total_cost_php", Math.round(area2TotalCost * 100.0) / 100.0);
        area2Daily.put("avg_power_watts", Math.round(area2AvgPower * 10.0) / 10.0);

        Map<String, Object> area3Daily = new HashMap<>();
        area3Daily.put("total_energy_kwh", Math.round(area3TotalEnergy * 1000.0) / 1000.0);
        area3Daily.put("total_cost_php", Math.round(area3TotalCost * 100.0) / 100.0);
        area3Daily.put("avg_power_watts", Math.round(area3AvgPower * 10.0) / 10.0);

        areaDailyData.put("area1", area1Daily);
        areaDailyData.put("area2", area2Daily);
        areaDailyData.put("area3", area3Daily);

        dailySummary.put("area_daily", areaDailyData);
        dailySummary.put("hourly_breakdown", hourlyBreakdown);

        final double finalTotalEnergyKwh = totalEnergyKwh;
        final double finalTotalCostPhp = totalCostPhp;

        // Save daily summary
        databaseRef.child("daily_summaries").child(DEVICE_ID).child(date)
                .setValue(dailySummary)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "✅ Created daily summary for " + date +
                            " (" + hourCount + " hours, " + String.format("%.3f", finalTotalEnergyKwh) + " kWh, ₱" +
                            String.format("%.2f", finalTotalCostPhp) + ")");
                })
                .addOnFailureListener(exception -> {
                    Log.e(TAG, "❌ Failed to save daily summary: " + exception.getMessage());
                });
    }

    // Helper methods
    private double getDoubleValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return 0.0;
    }

    private int getIntValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }

    private double getAreaDoubleValue(Map<String, Object> areaBreakdown, String areaKey, String valueKey) {
        try {
            Map<String, Object> areaData = (Map<String, Object>) areaBreakdown.get(areaKey);
            if (areaData != null) {
                return getDoubleValue(areaData, valueKey);
            }
        } catch (Exception e) {
            Log.w(TAG, "Error getting area value: " + e.getMessage());
        }
        return 0.0;
    }
}