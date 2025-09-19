package com.example.sampleiwatts.managers;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import androidx.annotation.NonNull;
import com.google.firebase.database.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class DataProcessingManager {
    private static final String TAG = "DataProcessingManager";
    private static final String PREFS_NAME = "iwatts_processing_prefs";
    private static final String KEY_LAST_PROCESSING_TIME = "last_processing_time";

    // Philippine timezone
    private static final TimeZone PHILIPPINE_TIMEZONE = TimeZone.getTimeZone("Asia/Manila");

    private Context context;
    private SharedPreferences prefs;
    private DatabaseReference databaseRef;
    private SimpleDateFormat dateFormat;
    private SimpleDateFormat timeFormat;
    private SimpleDateFormat dateTimeFormat;

    public DataProcessingManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.databaseRef = FirebaseDatabase.getInstance().getReference();

        // Set all date formatters to Philippine timezone
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        this.dateFormat.setTimeZone(PHILIPPINE_TIMEZONE);

        this.timeFormat = new SimpleDateFormat("HH", Locale.getDefault());
        this.timeFormat.setTimeZone(PHILIPPINE_TIMEZONE);

        this.dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
        this.dateTimeFormat.setTimeZone(PHILIPPINE_TIMEZONE);

        Log.d(TAG, "DataProcessingManager initialized with Philippine timezone: " + PHILIPPINE_TIMEZONE.getDisplayName());
    }

    public void initializeAppProcessing() {
        Log.d(TAG, "DataProcessingManager initialized for new database structure");
    }

    public boolean isAutoProcessingEnabled() {
        return true;
    }

    public void processDataInForeground() {
        // Always process when called (app resume/start)
        long currentTime = System.currentTimeMillis();

        Log.d(TAG, "🚀 Starting complete data processing on app resume/start...");

        // Step 1: Process hourly summaries first, then daily summaries
        processUnprocessedHoursSequential(() -> {
            // Step 2: After hourly processing completes, process daily summaries
            Log.d(TAG, "📊 Hourly processing completed, starting daily summaries...");

            // Wait a bit for Firebase to settle, then process daily
            new android.os.Handler().postDelayed(() -> {
                processDailySummariesSequential(() -> {
                    // Step 3: Mark completion
                    prefs.edit().putLong(KEY_LAST_PROCESSING_TIME, currentTime).apply();
                    Log.d(TAG, "✅ Complete data processing finished successfully!");
                });
            }, 3000); // 3 second delay for Firebase to settle
        });
    }

    // Method for scheduled/automatic processing with time restrictions
    public void processDataIfNeeded() {
        long lastProcessingTime = prefs.getLong(KEY_LAST_PROCESSING_TIME, 0);
        long currentTime = System.currentTimeMillis();
        long timeSinceLastRun = currentTime - lastProcessingTime;

        // Only process if more than 1 hour has passed (for scheduled processing)
        if (timeSinceLastRun > 60 * 60 * 1000) {
            Log.d(TAG, "Starting scheduled data processing... (Time since last run: " + (timeSinceLastRun / 60000) + " minutes)");
            processUnprocessedHoursSequential(() -> {
                new android.os.Handler().postDelayed(() -> {
                    processDailySummariesSequential(() -> {
                        prefs.edit().putLong(KEY_LAST_PROCESSING_TIME, currentTime).apply();
                        Log.d(TAG, "Scheduled data processing completed");
                    });
                }, 2000);
            });
        } else {
            long minutesLeft = (60 * 60 * 1000 - timeSinceLastRun) / (60 * 1000);
            Log.d(TAG, "Skipping scheduled processing - wait " + minutesLeft + " more minutes");
        }
    }

    // Sequential processing with callback
    private void processUnprocessedHoursSequential(Runnable onComplete) {
        Log.d(TAG, "🔄 Starting sequential hourly processing...");

        // Get system settings first
        databaseRef.child("system_settings").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot settingsSnapshot) {
                double electricityRate = 12.5; // Default rate
                double voltageReference = 220.0; // Default voltage

                if (settingsSnapshot.exists()) {
                    if (settingsSnapshot.child("electricity_rate_per_kwh").exists()) {
                        electricityRate = settingsSnapshot.child("electricity_rate_per_kwh").getValue(Double.class);
                    }
                    if (settingsSnapshot.child("voltage_reference").exists()) {
                        voltageReference = settingsSnapshot.child("voltage_reference").getValue(Double.class);
                    }
                }

                Log.d(TAG, "📋 Using rates - Electricity: " + electricityRate + "/kWh, Voltage: " + voltageReference + "V");
                findAndProcessAllUnprocessedDatesSequential(electricityRate, voltageReference, onComplete);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "❌ Failed to get system settings: " + error.getMessage());
                if (onComplete != null) onComplete.run();
            }
        });
    }

    private void findAndProcessAllUnprocessedDatesSequential(double electricityRate, double voltageReference, Runnable onComplete) {
        databaseRef.child("sensor_readings").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot readingsSnapshot) {
                if (!readingsSnapshot.exists()) {
                    Log.w(TAG, "⚠️ No sensor readings found");
                    if (onComplete != null) onComplete.run();
                    return;
                }

                // Group readings by date and hour
                Map<String, Map<String, List<Map<String, Object>>>> readingsByDateAndHour = new HashMap<>();

                int totalReadings = 0;
                for (DataSnapshot readingSnapshot : readingsSnapshot.getChildren()) {
                    Map<String, Object> reading = (Map<String, Object>) readingSnapshot.getValue();
                    if (reading != null && reading.containsKey("datetime")) {
                        try {
                            String datetime = (String) reading.get("datetime");
                            if (datetime != null && datetime.length() >= 13) {
                                String date = datetime.substring(0, 10); // Extract date (yyyy-MM-dd)
                                String hour = datetime.substring(11, 13); // Extract hour (HH)

                                // Skip current hour (Option 2: Skip until complete)
                                if (!isCurrentHour(date, hour)) {
                                    readingsByDateAndHour
                                            .computeIfAbsent(date, k -> new HashMap<>())
                                            .computeIfAbsent(hour, k -> new ArrayList<>())
                                            .add(reading);
                                    totalReadings++;
                                } else {
                                    Log.d(TAG, "⏳ Skipping current hour: " + date + " " + hour);
                                }
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "⚠️ Invalid datetime format in reading: " + reading.get("datetime"));
                        }
                    }
                }

                Log.d(TAG, "📊 Found " + totalReadings + " readings to process across " +
                        readingsByDateAndHour.size() + " dates");

                // Process each date-hour combination sequentially
                processDateHourCombinationsSequential(readingsByDateAndHour, electricityRate, voltageReference, onComplete);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "❌ Failed to get sensor readings: " + error.getMessage());
                if (onComplete != null) onComplete.run();
            }
        });
    }

    private boolean isCurrentHour(String date, String hour) {
        // Get current time in Philippine timezone
        Calendar philippineTime = Calendar.getInstance(PHILIPPINE_TIMEZONE);
        String currentDate = dateFormat.format(philippineTime.getTime());
        String currentHour = timeFormat.format(philippineTime.getTime());

        Log.d(TAG, "Current Philippine time - Date: " + currentDate + ", Hour: " + currentHour +
                " | Checking: " + date + " hour " + hour);

        return date.equals(currentDate) && hour.equals(currentHour);
    }

    private void processDateHourCombinationsSequential(Map<String, Map<String, List<Map<String, Object>>>> readingsByDateAndHour,
                                                       double electricityRate, double voltageReference, Runnable onComplete) {

        // Convert to list for sequential processing
        List<ProcessingTask> tasks = new ArrayList<>();
        for (String date : readingsByDateAndHour.keySet()) {
            Map<String, List<Map<String, Object>>> hoursData = readingsByDateAndHour.get(date);
            for (String hour : hoursData.keySet()) {
                List<Map<String, Object>> readings = hoursData.get(hour);
                tasks.add(new ProcessingTask(date, hour, readings));
            }
        }

        Log.d(TAG, "📝 Created " + tasks.size() + " processing tasks");

        if (tasks.isEmpty()) {
            Log.d(TAG, "✅ No hourly processing tasks needed");
            if (onComplete != null) onComplete.run();
            return;
        }

        // Process tasks sequentially
        processTasksSequentially(tasks, 0, electricityRate, voltageReference, onComplete);
    }

    private void processTasksSequentially(List<ProcessingTask> tasks, int currentIndex,
                                          double electricityRate, double voltageReference, Runnable onComplete) {
        if (currentIndex >= tasks.size()) {
            Log.d(TAG, "✅ All hourly processing tasks completed!");
            if (onComplete != null) onComplete.run();
            return;
        }

        ProcessingTask task = tasks.get(currentIndex);
        String date = task.date;
        String hour = task.hour;
        List<Map<String, Object>> readings = task.readings;

        Log.d(TAG, "🔄 Processing task " + (currentIndex + 1) + "/" + tasks.size() +
                ": " + date + " hour " + hour + " (" + readings.size() + " readings)");

        // Check if hourly summary already exists
        databaseRef.child("hourly_summaries").child(date).child(hour)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot existingSnapshot) {
                        if (!existingSnapshot.exists()) {
                            // Process this hour
                            processHourlyDataWithCallback(date, hour, readings, electricityRate, voltageReference, () -> {
                                // After processing, continue to next task
                                new android.os.Handler().postDelayed(() -> {
                                    processTasksSequentially(tasks, currentIndex + 1, electricityRate, voltageReference, onComplete);
                                }, 500); // Small delay between tasks
                            });
                        } else {
                            Log.d(TAG, "⏭️ Hourly summary already exists for " + date + " hour " + hour + ", skipping");
                            // Skip to next task
                            processTasksSequentially(tasks, currentIndex + 1, electricityRate, voltageReference, onComplete);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "❌ Failed to check existing hourly summary: " + error.getMessage());
                        // Continue to next task even on error
                        processTasksSequentially(tasks, currentIndex + 1, electricityRate, voltageReference, onComplete);
                    }
                });
    }

    private void processHourlyDataWithCallback(String date, String hour, List<Map<String, Object>> readings,
                                               double electricityRate, double voltageReference, Runnable onComplete) {
        if (readings == null || readings.isEmpty()) {
            if (onComplete != null) onComplete.run();
            return;
        }

        double totalWatts = 0;
        double minWatts = Double.MAX_VALUE;
        double maxWatts = Double.MIN_VALUE;
        double area1Watts = 0, area2Watts = 0, area3Watts = 0;
        int validReadings = 0;

        for (Map<String, Object> reading : readings) {
            try {
                String value = (String) reading.get("value");
                if (value != null && !value.isEmpty()) {
                    String[] values = value.split(",");
                    if (values.length >= 5) {
                        // Parse values: BV,CV,A1,A2,A3,IR
                        double batteryVoltage = Double.parseDouble(values[0]); // BV
                        double chargingVoltage = Double.parseDouble(values[1]); // CV
                        double current1 = Double.parseDouble(values[2]); // A1
                        double current2 = Double.parseDouble(values[3]); // A2
                        double current3 = Double.parseDouble(values[4]); // A3

                        // Calculate watts for each area (P = V * I)
                        double area1Power = voltageReference * current1;
                        double area2Power = voltageReference * current2;
                        double area3Power = voltageReference * current3;
                        double totalPower = area1Power + area2Power + area3Power;

                        area1Watts += area1Power;
                        area2Watts += area2Power;
                        area3Watts += area3Power;
                        totalWatts += totalPower;

                        minWatts = Math.min(minWatts, totalPower);
                        maxWatts = Math.max(maxWatts, totalPower);
                        validReadings++;
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "⚠️ Error parsing reading value: " + reading.get("value"));
            }
        }

        if (validReadings > 0) {
            // Calculate averages and energy consumption
            double avgWatts = totalWatts / validReadings;
            double avgArea1Watts = area1Watts / validReadings;
            double avgArea2Watts = area2Watts / validReadings;
            double avgArea3Watts = area3Watts / validReadings;

            // Calculate kWh based on actual readings (handling incomplete hours)
            double expectedReadings = 720.0;
            double timeCoverage = Math.min(1.0, validReadings / expectedReadings);

            double totalKwh = (avgWatts / 1000.0) * timeCoverage;
            double area1Kwh = (avgArea1Watts / 1000.0) * timeCoverage;
            double area2Kwh = (avgArea2Watts / 1000.0) * timeCoverage;
            double area3Kwh = (avgArea3Watts / 1000.0) * timeCoverage;

            double totalCost = totalKwh * electricityRate;

            // Create hourly summary
            Map<String, Object> hourlySummary = new HashMap<>();
            hourlySummary.put("total_kwh", Math.round(totalKwh * 1000.0) / 1000.0);
            hourlySummary.put("total_cost", Math.round(totalCost * 100.0) / 100.0);
            hourlySummary.put("peak_watts", (int) maxWatts);
            hourlySummary.put("min_watts", minWatts == Double.MAX_VALUE ? 0 : (int) minWatts);
            hourlySummary.put("avg_watts", (int) avgWatts);
            hourlySummary.put("area1_kwh", Math.round(area1Kwh * 1000.0) / 1000.0);
            hourlySummary.put("area2_kwh", Math.round(area2Kwh * 1000.0) / 1000.0);
            hourlySummary.put("area3_kwh", Math.round(area3Kwh * 1000.0) / 1000.0);
            hourlySummary.put("readings_count", validReadings);

            // Make final variable for lambda expression
            final int finalValidReadings = validReadings;

            // Save hourly summary
            databaseRef.child("hourly_summaries").child(date).child(hour).setValue(hourlySummary)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "✅ Hourly summary saved for " + date + " hour " + hour + " (" + finalValidReadings + " readings)");
                        if (onComplete != null) onComplete.run();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "❌ Failed to save hourly summary for " + date + " hour " + hour + ": " + e.getMessage());
                        if (onComplete != null) onComplete.run();
                    });
        } else {
            Log.w(TAG, "⚠️ No valid readings found for " + date + " hour " + hour);
            if (onComplete != null) onComplete.run();
        }
    }

    private void processDailySummariesSequential(Runnable onComplete) {
        Log.d(TAG, "🔄 Starting sequential daily processing...");

        databaseRef.child("hourly_summaries").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot hourlySummariesSnapshot) {
                if (!hourlySummariesSnapshot.exists()) {
                    Log.w(TAG, "⚠️ No hourly summaries found for daily processing");
                    if (onComplete != null) onComplete.run();
                    return;
                }

                // Collect all dates that have hourly summaries
                List<String> datesToProcess = new ArrayList<>();
                for (DataSnapshot dateSnapshot : hourlySummariesSnapshot.getChildren()) {
                    String date = dateSnapshot.getKey();
                    Log.d(TAG, "📅 Found hourly summaries for date: " + date);
                    datesToProcess.add(date);
                }

                Log.d(TAG, "📋 Will process daily summaries for " + datesToProcess.size() + " dates");

                if (datesToProcess.isEmpty()) {
                    Log.d(TAG, "✅ No daily processing needed");
                    if (onComplete != null) onComplete.run();
                    return;
                }

                // Process dates sequentially
                processDailyDatesSequentially(datesToProcess, 0, hourlySummariesSnapshot, onComplete);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "❌ Failed to get hourly summaries for daily processing: " + error.getMessage());
                if (onComplete != null) onComplete.run();
            }
        });
    }

    private void processDailyDatesSequentially(List<String> dates, int currentIndex,
                                               DataSnapshot hourlySummariesSnapshot, Runnable onComplete) {
        if (currentIndex >= dates.size()) {
            Log.d(TAG, "✅ All daily processing completed!");
            if (onComplete != null) onComplete.run();
            return;
        }

        String date = dates.get(currentIndex);
        DataSnapshot dateSnapshot = hourlySummariesSnapshot.child(date);

        Log.d(TAG, "🔄 Processing daily summary " + (currentIndex + 1) + "/" + dates.size() + ": " + date);

        generateDailySummaryForDateWithCallback(date, dateSnapshot, () -> {
            // Continue to next date after small delay
            new android.os.Handler().postDelayed(() -> {
                processDailyDatesSequentially(dates, currentIndex + 1, hourlySummariesSnapshot, onComplete);
            }, 500); // Small delay between daily summaries
        });
    }

    private void generateDailySummaryForDateWithCallback(String date, DataSnapshot hourlyDataSnapshot, Runnable onComplete) {
        Log.d(TAG, "🔄 Attempting to generate daily summary for: " + date);

        // Check if daily summary already exists
        databaseRef.child("daily_summaries").child(date).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot existingDailySnapshot) {
                if (existingDailySnapshot.exists()) {
                    Log.d(TAG, "⏭️ Daily summary already exists for " + date + ", skipping");
                    if (onComplete != null) onComplete.run();
                    return;
                }

                Log.d(TAG, "📊 No existing daily summary found for " + date + ", creating new one");

                // Get electricity rate for cost calculations
                databaseRef.child("system_settings").child("electricity_rate_per_kwh")
                        .addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot rateSnapshot) {
                                double electricityRate = 12.5; // Default
                                if (rateSnapshot.exists()) {
                                    electricityRate = rateSnapshot.getValue(Double.class);
                                    Log.d(TAG, "📋 Using electricity rate: " + electricityRate);
                                } else {
                                    Log.d(TAG, "📋 No electricity rate found, using default: " + electricityRate);
                                }

                                processDailyDataWithCallback(date, hourlyDataSnapshot, electricityRate, onComplete);
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {
                                Log.e(TAG, "❌ Failed to get electricity rate for daily summary: " + error.getMessage());
                                if (onComplete != null) onComplete.run();
                            }
                        });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "❌ Failed to check existing daily summary: " + error.getMessage());
                if (onComplete != null) onComplete.run();
            }
        });
    }

    private void processDailyDataWithCallback(String date, DataSnapshot hourlyDataSnapshot, double electricityRate, Runnable onComplete) {
        Log.d(TAG, "📊 Processing daily data for " + date + " with rate " + electricityRate);

        double totalKwh = 0;
        double totalCost = 0;
        int peakWatts = 0;
        int minWatts = Integer.MAX_VALUE;
        double totalAvgWatts = 0;
        double area1TotalKwh = 0, area2TotalKwh = 0, area3TotalKwh = 0;
        int hourCount = 0;
        String peakTime = "";

        for (DataSnapshot hourSnapshot : hourlyDataSnapshot.getChildren()) {
            Map<String, Object> hourData = (Map<String, Object>) hourSnapshot.getValue();
            if (hourData != null) {
                String hourKey = hourSnapshot.getKey();
                Log.d(TAG, "📈 Processing hour " + hourKey + " data");

                totalKwh += getDoubleValue(hourData, "total_kwh");
                totalCost += getDoubleValue(hourData, "total_cost");

                int hourPeak = getIntValue(hourData, "peak_watts");
                if (hourPeak > peakWatts) {
                    peakWatts = hourPeak;
                    peakTime = hourKey + ":30:00"; // Approximate peak time
                }

                int hourMin = getIntValue(hourData, "min_watts");
                if (hourMin < minWatts) {
                    minWatts = hourMin;
                }

                totalAvgWatts += getDoubleValue(hourData, "avg_watts");
                area1TotalKwh += getDoubleValue(hourData, "area1_kwh");
                area2TotalKwh += getDoubleValue(hourData, "area2_kwh");
                area3TotalKwh += getDoubleValue(hourData, "area3_kwh");
                hourCount++;
            }
        }

        Log.d(TAG, "📊 Daily summary calculations - Hours: " + hourCount + ", Total kWh: " + totalKwh + ", Total Cost: " + totalCost);

        if (hourCount > 0) {
            double avgWatts = totalAvgWatts / hourCount;

            // Create area breakdown
            Map<String, Object> area1Breakdown = new HashMap<>();
            area1Breakdown.put("kwh", Math.round(area1TotalKwh * 1000.0) / 1000.0);
            area1Breakdown.put("cost", Math.round(area1TotalKwh * electricityRate * 100.0) / 100.0);
            area1Breakdown.put("percentage", totalKwh > 0 ? Math.round((area1TotalKwh / totalKwh) * 1000.0) / 10.0 : 0.0);

            Map<String, Object> area2Breakdown = new HashMap<>();
            area2Breakdown.put("kwh", Math.round(area2TotalKwh * 1000.0) / 1000.0);
            area2Breakdown.put("cost", Math.round(area2TotalKwh * electricityRate * 100.0) / 100.0);
            area2Breakdown.put("percentage", totalKwh > 0 ? Math.round((area2TotalKwh / totalKwh) * 1000.0) / 10.0 : 0.0);

            Map<String, Object> area3Breakdown = new HashMap<>();
            area3Breakdown.put("kwh", Math.round(area3TotalKwh * 1000.0) / 1000.0);
            area3Breakdown.put("cost", Math.round(area3TotalKwh * electricityRate * 100.0) / 100.0);
            area3Breakdown.put("percentage", totalKwh > 0 ? Math.round((area3TotalKwh / totalKwh) * 1000.0) / 10.0 : 0.0);

            Map<String, Object> areaBreakdown = new HashMap<>();
            areaBreakdown.put("area1", area1Breakdown);
            areaBreakdown.put("area2", area2Breakdown);
            areaBreakdown.put("area3", area3Breakdown);

            // Create daily summary
            Map<String, Object> dailySummary = new HashMap<>();
            dailySummary.put("total_kwh", Math.round(totalKwh * 1000.0) / 1000.0);
            dailySummary.put("total_cost", Math.round(totalCost * 100.0) / 100.0);
            dailySummary.put("peak_watts", peakWatts);
            dailySummary.put("peak_time", peakTime);
            dailySummary.put("min_watts", minWatts == Integer.MAX_VALUE ? 0 : minWatts);
            dailySummary.put("avg_watts", (int) avgWatts);
            dailySummary.put("area_breakdown", areaBreakdown);
            dailySummary.put("power_interruptions", 0); // Could be calculated from data gaps if needed

            Log.d(TAG, "💾 Saving daily summary for " + date);

            // Make final variable for lambda expression
            final int finalHourCount = hourCount;

            // Save daily summary
            databaseRef.child("daily_summaries").child(date).setValue(dailySummary)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "✅ Daily summary saved successfully for " + date + " (" + finalHourCount + " hours processed)");
                        if (onComplete != null) onComplete.run();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "❌ Failed to save daily summary for " + date + ": " + e.getMessage());
                        if (onComplete != null) onComplete.run();
                    });
        } else {
            Log.w(TAG, "⚠️ No hour data found for " + date + ", skipping daily summary");
            if (onComplete != null) onComplete.run();
        }
    }

    // Helper class for processing tasks
    private static class ProcessingTask {
        String date;
        String hour;
        List<Map<String, Object>> readings;

        ProcessingTask(String date, String hour, List<Map<String, Object>> readings) {
            this.date = date;
            this.hour = hour;
            this.readings = readings;
        }
    }

    // Helper methods
    private double getDoubleValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return 0.0;
    }

    private int getIntValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }

    // Public method for manual processing trigger (TESTING ONLY)
    public void forceProcessData() {
        Log.d(TAG, "🔧 FORCE processing data (ignoring time restrictions)...");

        // Reset the last processing time to allow immediate processing
        prefs.edit().putLong(KEY_LAST_PROCESSING_TIME, 0).apply();

        // Get current Philippine time for logging
        Calendar philippineTime = Calendar.getInstance(PHILIPPINE_TIMEZONE);
        String currentDateTime = dateTimeFormat.format(philippineTime.getTime());
        Log.d(TAG, "Current Philippine time: " + currentDateTime);

        processDataInForeground();
    }

    // Method to check system settings
    public void getSystemSettings(SystemSettingsCallback callback) {
        databaseRef.child("system_settings").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Map<String, Object> settings = (Map<String, Object>) snapshot.getValue();
                    callback.onSuccess(settings);
                } else {
                    callback.onError("System settings not found");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError(error.getMessage());
            }
        });
    }

    // Utility method to calculate battery percentage (for future use in other parts of app)
    public static double calculateBatteryPercentage(double batteryVoltage) {
        // BV: 1.51V = 0%, 2.1V = 100%
        double minVoltage = 1.51;
        double maxVoltage = 2.1;
        double percentage = ((batteryVoltage - minVoltage) / (maxVoltage - minVoltage)) * 100.0;
        return Math.max(0.0, Math.min(100.0, percentage)); // Clamp between 0-100%
    }

    // Utility method to check if device is charging (for future use in other parts of app)
    public static boolean isDeviceCharging(double chargingVoltage) {
        // CV >= 2V = charging
        return chargingVoltage >= 2.0;
    }

    public interface SystemSettingsCallback {
        void onSuccess(Map<String, Object> settings);
        void onError(String error);
    }
}