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
        Log.d(TAG, "DataProcessingManager initialized for log-based data structure");
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
            }, 2000);
        });
    }

    private void processUnprocessedHoursSequential(Runnable onComplete) {
        Log.d(TAG, "🔄 Starting sequential hourly processing...");

        databaseRef.child("system_settings").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot settingsSnapshot) {
                double electricityRate = 12.5; // Default
                double voltageReference = 220.0; // Default

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
        databaseRef.child("logs").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot logsSnapshot) {
                if (!logsSnapshot.exists()) {
                    Log.w(TAG, "⚠️ No log data found");
                    if (onComplete != null) onComplete.run();
                    return;
                }

                // Group log data by date and hour
                Map<String, Map<String, List<Map<String, Object>>>> logsByDateAndHour = new HashMap<>();

                int totalLogs = 0;
                for (DataSnapshot logSnapshot : logsSnapshot.getChildren()) {
                    String timestamp = logSnapshot.getKey(); // e.g., "2025-01-15T14:30:25"
                    if (timestamp != null && timestamp.length() >= 13) {
                        try {
                            String date = timestamp.substring(0, 10); // "2025-01-15"
                            String hour = timestamp.substring(11, 13); // "14"

                            // Process each data entry within this timestamp
                            for (DataSnapshot timestampSnapshot : logSnapshot.getChildren()) {
                                Map<String, Object> logData = (Map<String, Object>) timestampSnapshot.getValue();
                                if (logData != null && hasRequiredLogFields(logData)) {
                                    // Add timestamp to the log data for reference
                                    logData.put("timestamp", timestamp);

                                    logsByDateAndHour
                                            .computeIfAbsent(date, k -> new HashMap<>())
                                            .computeIfAbsent(hour, k -> new ArrayList<>())
                                            .add(logData);

                                    totalLogs++;
                                }
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "Error parsing timestamp: " + timestamp + " - " + e.getMessage());
                        }
                    }
                }

                Log.d(TAG, "📊 Found " + totalLogs + " valid log entries across " + logsByDateAndHour.size() + " dates");

                // Process each date sequentially
                processDateHoursSequential(new ArrayList<>(logsByDateAndHour.keySet()), 0,
                        logsByDateAndHour, electricityRate, voltageReference, onComplete);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "❌ Failed to get log data: " + error.getMessage());
                if (onComplete != null) onComplete.run();
            }
        });
    }

    private boolean hasRequiredLogFields(Map<String, Object> logData) {
        return logData.containsKey("C1_A") &&
                logData.containsKey("C2_A") &&
                logData.containsKey("C3_A");
    }

    private void processDateHoursSequential(List<String> dates, int currentIndex,
                                            Map<String, Map<String, List<Map<String, Object>>>> logsByDateAndHour,
                                            double electricityRate, double voltageReference, Runnable onComplete) {
        if (currentIndex >= dates.size()) {
            Log.d(TAG, "✅ Sequential hourly processing completed for all dates");
            if (onComplete != null) onComplete.run();
            return;
        }

        String date = dates.get(currentIndex);
        Map<String, List<Map<String, Object>>> hoursData = logsByDateAndHour.get(date);

        Log.d(TAG, "📅 Processing date " + date + " (" + (currentIndex + 1) + "/" + dates.size() + ")");

        // Check if this date already has complete hourly summaries
        databaseRef.child("hourly_summaries").child(date).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot existingSummaries) {
                List<String> hoursToProcess = new ArrayList<>();

                for (String hour : hoursData.keySet()) {
                    if (!existingSummaries.hasChild(hour)) {
                        hoursToProcess.add(hour);
                    }
                }

                if (hoursToProcess.isEmpty()) {
                    Log.d(TAG, "⏭️ Date " + date + " already processed, skipping");
                    processDateHoursSequential(dates, currentIndex + 1, logsByDateAndHour,
                            electricityRate, voltageReference, onComplete);
                } else {
                    Log.d(TAG, "🔄 Processing " + hoursToProcess.size() + " hours for " + date);
                    processHoursSequential(date, hoursToProcess, 0, hoursData, electricityRate, voltageReference, () -> {
                        // Move to next date
                        processDateHoursSequential(dates, currentIndex + 1, logsByDateAndHour,
                                electricityRate, voltageReference, onComplete);
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "❌ Error checking existing summaries for " + date + ": " + error.getMessage());
                processDateHoursSequential(dates, currentIndex + 1, logsByDateAndHour,
                        electricityRate, voltageReference, onComplete);
            }
        });
    }

    private void processHoursSequential(String date, List<String> hours, int currentHourIndex,
                                        Map<String, List<Map<String, Object>>> hoursData,
                                        double electricityRate, double voltageReference, Runnable onComplete) {
        if (currentHourIndex >= hours.size()) {
            if (onComplete != null) onComplete.run();
            return;
        }

        String hour = hours.get(currentHourIndex);
        List<Map<String, Object>> hourLogs = hoursData.get(hour);

        if (hourLogs != null && !hourLogs.isEmpty()) {
            processHourlyLogData(date, hour, hourLogs, electricityRate, voltageReference, () -> {
                // Process next hour
                processHoursSequential(date, hours, currentHourIndex + 1, hoursData,
                        electricityRate, voltageReference, onComplete);
            });
        } else {
            // Skip empty hour
            processHoursSequential(date, hours, currentHourIndex + 1, hoursData,
                    electricityRate, voltageReference, onComplete);
        }
    }

    private void processHourlyLogData(String date, String hour, List<Map<String, Object>> hourLogs,
                                      double electricityRate, double voltageReference, Runnable onComplete) {

        int validReadings = 0;
        double totalWatts = 0.0;
        double area1Watts = 0.0;
        double area2Watts = 0.0;
        double area3Watts = 0.0;
        double maxWatts = 0.0;
        double minWatts = Double.MAX_VALUE;

        for (Map<String, Object> logData : hourLogs) {
            try {
                Double current1 = getDoubleValue(logData.get("C1_A"));
                Double current2 = getDoubleValue(logData.get("C2_A"));
                Double current3 = getDoubleValue(logData.get("C3_A"));

                if (current1 != null && current2 != null && current3 != null) {
                    // Calculate watts: P = V * I (same calculation as before)
                    double area1Power = voltageReference * current1;
                    double area2Power = voltageReference * current2;
                    double area3Power = voltageReference * current3;
                    double totalPower = area1Power + area2Power + area3Power;

                    area1Watts += area1Power;
                    area2Watts += area2Power;
                    area3Watts += area3Power;
                    totalWatts += totalPower;
                    maxWatts = Math.max(maxWatts, totalPower);
                    minWatts = Math.min(minWatts, totalPower);

                    validReadings++;
                }
            } catch (Exception e) {
                Log.w(TAG, "Error parsing log data: " + e.getMessage());
            }
        }

        if (validReadings > 0) {
            // Calculate averages
            double avgWatts = totalWatts / validReadings;
            double avgArea1Watts = area1Watts / validReadings;
            double avgArea2Watts = area2Watts / validReadings;
            double avgArea3Watts = area3Watts / validReadings;

            // Calculate kWh - Use actual reading count instead of fixed 720
            // Assume readings are approximately every 5 seconds for kWh calculation
            double estimatedHourlyReadings = Math.max(validReadings, 1);
            double readingIntervalHours = 1.0 / (estimatedHourlyReadings / 1.0); // Proportion of hour per reading

            double totalKwh = (avgWatts / 1000.0); // kWh for full hour
            double area1Kwh = (avgArea1Watts / 1000.0);
            double area2Kwh = (avgArea2Watts / 1000.0);
            double area3Kwh = (avgArea3Watts / 1000.0);

            // Create hourly summary object
            Map<String, Object> hourlySummary = new HashMap<>();
            hourlySummary.put("total_kwh", Math.round(totalKwh * 1000.0) / 1000.0);
            hourlySummary.put("total_cost", Math.round(totalKwh * electricityRate * 100.0) / 100.0);
            hourlySummary.put("peak_watts", (int) maxWatts);
            hourlySummary.put("min_watts", minWatts != Double.MAX_VALUE ? (int) minWatts : 0);
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
                        Log.d(TAG, "✅ Hourly summary saved for " + date + " hour " + hour + " (" + finalValidReadings + " log entries)");
                        if (onComplete != null) onComplete.run();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "❌ Failed to save hourly summary for " + date + " hour " + hour + ": " + e.getMessage());
                        if (onComplete != null) onComplete.run();
                    });
        } else {
            Log.w(TAG, "⚠️ No valid log entries found for " + date + " hour " + hour);
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

                Collections.sort(datesToProcess);
                Log.d(TAG, "📊 Processing daily summaries for " + datesToProcess.size() + " dates");

                processDailySummariesForDatesSequential(datesToProcess, 0, onComplete);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "❌ Failed to get hourly summaries: " + error.getMessage());
                if (onComplete != null) onComplete.run();
            }
        });
    }

    private void processDailySummariesForDatesSequential(List<String> dates, int currentIndex, Runnable onComplete) {
        if (currentIndex >= dates.size()) {
            Log.d(TAG, "✅ Sequential daily processing completed for all dates");
            if (onComplete != null) onComplete.run();
            return;
        }

        String date = dates.get(currentIndex);
        Log.d(TAG, "📅 Processing daily summary for " + date + " (" + (currentIndex + 1) + "/" + dates.size() + ")");

        processSingleDailySummary(date, () -> {
            // Process next date
            processDailySummariesForDatesSequential(dates, currentIndex + 1, onComplete);
        });
    }

    private void processSingleDailySummary(String date, Runnable onComplete) {
        databaseRef.child("hourly_summaries").child(date).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot hourlySnapshot) {
                if (!hourlySnapshot.exists()) {
                    Log.w(TAG, "⚠️ No hourly data found for " + date);
                    if (onComplete != null) onComplete.run();
                    return;
                }

                // Create wrapper class to hold mutable values
                class DailySummaryData {
                    double totalKwh = 0.0;
                    double totalArea1Kwh = 0.0;
                    double totalArea2Kwh = 0.0;
                    double totalArea3Kwh = 0.0;
                    double maxPeakWatts = 0.0;
                    double minWatts = Double.MAX_VALUE;
                    int totalReadings = 0;
                    double totalAvgWatts = 0.0;
                    int validHours = 0;
                }

                DailySummaryData summaryData = new DailySummaryData();

                for (DataSnapshot hourSnapshot : hourlySnapshot.getChildren()) {
                    try {
                        Map<String, Object> hourData = (Map<String, Object>) hourSnapshot.getValue();
                        if (hourData != null) {
                            Double hourKwh = getDoubleValue(hourData.get("total_kwh"));
                            Double area1Kwh = getDoubleValue(hourData.get("area1_kwh"));
                            Double area2Kwh = getDoubleValue(hourData.get("area2_kwh"));
                            Double area3Kwh = getDoubleValue(hourData.get("area3_kwh"));
                            Double peakWatts = getDoubleValue(hourData.get("peak_watts"));
                            Double hourMinWatts = getDoubleValue(hourData.get("min_watts"));
                            Double avgWatts = getDoubleValue(hourData.get("avg_watts"));
                            Integer readingsCount = getIntegerValue(hourData.get("readings_count"));

                            if (hourKwh != null) summaryData.totalKwh += hourKwh;
                            if (area1Kwh != null) summaryData.totalArea1Kwh += area1Kwh;
                            if (area2Kwh != null) summaryData.totalArea2Kwh += area2Kwh;
                            if (area3Kwh != null) summaryData.totalArea3Kwh += area3Kwh;
                            if (peakWatts != null) summaryData.maxPeakWatts = Math.max(summaryData.maxPeakWatts, peakWatts);
                            if (hourMinWatts != null) summaryData.minWatts = Math.min(summaryData.minWatts, hourMinWatts);
                            if (avgWatts != null) {
                                summaryData.totalAvgWatts += avgWatts;
                                summaryData.validHours++;
                            }
                            if (readingsCount != null) summaryData.totalReadings += readingsCount;
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Error processing hourly data for " + hourSnapshot.getKey() + ": " + e.getMessage());
                    }
                }

                if (summaryData.totalKwh > 0) {
                    databaseRef.child("system_settings").addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot settingsSnapshot) {
                            double electricityRate = 12.5; // Default
                            if (settingsSnapshot.exists() && settingsSnapshot.child("electricity_rate_per_kwh").exists()) {
                                electricityRate = settingsSnapshot.child("electricity_rate_per_kwh").getValue(Double.class);
                            }

                            // Make final for lambda usage
                            final double finalElectricityRate = electricityRate;

                            // Create daily summary
                            Map<String, Object> dailySummary = new HashMap<>();
                            dailySummary.put("total_kwh", Math.round(summaryData.totalKwh * 1000.0) / 1000.0);
                            dailySummary.put("total_cost", Math.round(summaryData.totalKwh * finalElectricityRate * 100.0) / 100.0);
                            dailySummary.put("peak_watts", (int) summaryData.maxPeakWatts);
                            dailySummary.put("min_watts", summaryData.minWatts != Double.MAX_VALUE ? (int) summaryData.minWatts : 0);
                            dailySummary.put("avg_watts", summaryData.validHours > 0 ? (int) (summaryData.totalAvgWatts / summaryData.validHours) : 0);

                            // Area breakdown
                            Map<String, Object> areaBreakdown = new HashMap<>();

                            Map<String, Object> area1 = new HashMap<>();
                            area1.put("kwh", Math.round(summaryData.totalArea1Kwh * 1000.0) / 1000.0);
                            area1.put("cost", Math.round(summaryData.totalArea1Kwh * finalElectricityRate * 100.0) / 100.0);
                            area1.put("percentage", summaryData.totalKwh > 0 ? Math.round((summaryData.totalArea1Kwh / summaryData.totalKwh) * 10000.0) / 100.0 : 0.0);

                            Map<String, Object> area2 = new HashMap<>();
                            area2.put("kwh", Math.round(summaryData.totalArea2Kwh * 1000.0) / 1000.0);
                            area2.put("cost", Math.round(summaryData.totalArea2Kwh * finalElectricityRate * 100.0) / 100.0);
                            area2.put("percentage", summaryData.totalKwh > 0 ? Math.round((summaryData.totalArea2Kwh / summaryData.totalKwh) * 10000.0) / 100.0 : 0.0);

                            Map<String, Object> area3 = new HashMap<>();
                            area3.put("kwh", Math.round(summaryData.totalArea3Kwh * 1000.0) / 1000.0);
                            area3.put("cost", Math.round(summaryData.totalArea3Kwh * finalElectricityRate * 100.0) / 100.0);
                            area3.put("percentage", summaryData.totalKwh > 0 ? Math.round((summaryData.totalArea3Kwh / summaryData.totalKwh) * 10000.0) / 100.0 : 0.0);

                            areaBreakdown.put("area1", area1);
                            areaBreakdown.put("area2", area2);
                            areaBreakdown.put("area3", area3);

                            dailySummary.put("area_breakdown", areaBreakdown);
                            dailySummary.put("total_readings", summaryData.totalReadings);

                            // Save daily summary
                            databaseRef.child("daily_summaries").child(date).setValue(dailySummary)
                                    .addOnSuccessListener(aVoid -> {
                                        Log.d(TAG, "✅ Daily summary saved for " + date + " (Total: " +
                                                String.format("%.3f kWh", summaryData.totalKwh) + ", " + summaryData.totalReadings + " log entries)");
                                        if (onComplete != null) onComplete.run();
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e(TAG, "❌ Failed to save daily summary for " + date + ": " + e.getMessage());
                                        if (onComplete != null) onComplete.run();
                                    });
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            Log.e(TAG, "❌ Failed to get electricity rate for daily summary: " + error.getMessage());
                            if (onComplete != null) onComplete.run();
                        }
                    });
                } else {
                    Log.w(TAG, "⚠️ No consumption data found for " + date);
                    if (onComplete != null) onComplete.run();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "❌ Failed to get hourly data for " + date + ": " + error.getMessage());
                if (onComplete != null) onComplete.run();
            }
        });
    }

    /**
     * Helper method to safely extract Double values
     */
    private Double getDoubleValue(Object value) {
        if (value == null) return null;
        if (value instanceof Double) return (Double) value;
        if (value instanceof Number) return ((Number) value).doubleValue();
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Helper method to safely extract Integer values
     */
    private Integer getIntegerValue(Object value) {
        if (value == null) return null;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Number) return ((Number) value).intValue();
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}