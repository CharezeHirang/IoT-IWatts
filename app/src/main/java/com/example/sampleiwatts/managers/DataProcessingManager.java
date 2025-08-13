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
    private static final String DEVICE_ID = "device_001";

    private Context context;
    private SharedPreferences prefs;
    private DatabaseReference databaseRef;
    private SimpleDateFormat dateFormat;

    public DataProcessingManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.databaseRef = FirebaseDatabase.getInstance().getReference();
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    }

    public void processDataInForeground() {
        long lastProcessingTime = prefs.getLong(KEY_LAST_PROCESSING_TIME, 0);
        long currentTime = System.currentTimeMillis();

        // Only process if it's been more than 1 hour since last processing
        if (currentTime - lastProcessingTime > 60 * 60 * 1000) {
            processUnprocessedHours();
            processDailySummaries();
            prefs.edit().putLong(KEY_LAST_PROCESSING_TIME, currentTime).apply();
        }
    }

    public void initializeAppProcessing() {
        // Ready for processing
    }

    public boolean isAutoProcessingEnabled() {
        return true;
    }

    private void processUnprocessedHours() {
        databaseRef.child("devices").child(DEVICE_ID).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot deviceSnapshot) {
                double electricityRate = 8.50;
                if (deviceSnapshot.exists() && deviceSnapshot.child("electricity_rate").exists()) {
                    electricityRate = deviceSnapshot.child("electricity_rate").getValue(Double.class);
                }
                findAndProcessAllUnprocessedDates(electricityRate);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to get electricity rate: " + error.getMessage());
            }
        });
    }

    private void findAndProcessAllUnprocessedDates(double electricityRate) {
        databaseRef.child("raw_readings").child(DEVICE_ID)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot allDatesSnapshot) {
                        if (!allDatesSnapshot.exists()) {
                            return;
                        }

                        for (DataSnapshot dateSnapshot : allDatesSnapshot.getChildren()) {
                            String date = dateSnapshot.getKey();
                            int readingCount = (int) dateSnapshot.getChildrenCount();

                            if (readingCount > 0) {
                                processDateForUnprocessedHours(date, electricityRate);
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Failed to get available dates: " + error.getMessage());
                    }
                });
    }

    private void processDateForUnprocessedHours(String date, double electricityRate) {
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

                        databaseRef.child("raw_readings").child(DEVICE_ID).child(date)
                                .addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override
                                    public void onDataChange(@NonNull DataSnapshot rawReadingsSnapshot) {
                                        if (!rawReadingsSnapshot.exists()) {
                                            return;
                                        }

                                        Map<String, Object> rawReadings = (Map<String, Object>) rawReadingsSnapshot.getValue();
                                        processDateData(date, rawReadings, existingSummaries, electricityRate);
                                    }

                                    @Override
                                    public void onCancelled(@NonNull DatabaseError error) {
                                        Log.e(TAG, "Failed to get raw readings for " + date + ": " + error.getMessage());
                                    }
                                });
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Failed to get existing summaries for " + date + ": " + error.getMessage());
                    }
                });
    }

    private void processDateData(String date, Map<String, Object> rawReadings,
                                 Map<String, Object> existingSummaries, double electricityRate) {

        Map<Integer, List<Map<String, Object>>> readingsByHour = new HashMap<>();

        for (String readingId : rawReadings.keySet()) {
            Map<String, Object> reading = (Map<String, Object>) rawReadings.get(readingId);

            if (reading != null && reading.containsKey("timestamp")) {
                try {
                    long timestamp = ((Number) reading.get("timestamp")).longValue();
                    Calendar cal = Calendar.getInstance();
                    cal.setTimeInMillis(timestamp);
                    int hour = cal.get(Calendar.HOUR_OF_DAY);
                    readingsByHour.computeIfAbsent(hour, k -> new ArrayList<>()).add(reading);
                } catch (Exception e) {
                    // Skip invalid reading
                }
            }
        }

        for (int hour : readingsByHour.keySet()) {
            String hourKey = String.valueOf(hour);

            if (!existingSummaries.containsKey(hourKey)) {
                List<Map<String, Object>> hourReadings = readingsByHour.get(hour);

                if (!hourReadings.isEmpty()) {
                    Map<String, Object> hourlySummary = generateHourlySummary(hourReadings, electricityRate, date, hour);

                    if (hourlySummary != null) {
                        databaseRef.child("hourly_summaries").child(DEVICE_ID).child(date).child(hourKey)
                                .setValue(hourlySummary)
                                .addOnFailureListener(e -> Log.e(TAG, "Failed to save hourly summary", e));
                    }
                }
            }
        }
    }

    private Map<String, Object> generateHourlySummary(List<Map<String, Object>> readings, double electricityRate, String date, int hour) {
        if (readings == null || readings.isEmpty()) {
            return null;
        }

        double totalWatts = 0;
        double minWatts = Double.MAX_VALUE;
        double maxWatts = Double.MIN_VALUE;
        int validReadings = 0;
        int batteryLevel = 0;

        Map<String, Double> areaTotals = new HashMap<>();
        Map<String, Integer> areaCounts = new HashMap<>();

        for (Map<String, Object> reading : readings) {
            try {
                Number totalPowerNum = (Number) reading.get("total_power_watts");
                if (totalPowerNum != null) {
                    double watts = totalPowerNum.doubleValue();
                    totalWatts += watts;
                    minWatts = Math.min(minWatts, watts);
                    maxWatts = Math.max(maxWatts, watts);
                    validReadings++;
                }

                // Get actual battery level from readings
                Number batteryNum = (Number) reading.get("battery_level");
                if (batteryNum != null) {
                    batteryLevel = batteryNum.intValue(); // Use the most recent battery level
                }

                Map<String, Object> readingsMap = (Map<String, Object>) reading.get("readings");
                if (readingsMap != null) {
                    for (String areaId : readingsMap.keySet()) {
                        Map<String, Object> areaData = (Map<String, Object>) readingsMap.get(areaId);
                        if (areaData != null && areaData.containsKey("power_watts")) {
                            Number areaPowerNum = (Number) areaData.get("power_watts");
                            if (areaPowerNum != null) {
                                double areaPower = areaPowerNum.doubleValue();
                                areaTotals.put(areaId, areaTotals.getOrDefault(areaId, 0.0) + areaPower);
                                areaCounts.put(areaId, areaCounts.getOrDefault(areaId, 0) + 1);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // Skip invalid reading
            }
        }

        if (validReadings == 0) {
            return null;
        }

        double avgWatts = totalWatts / validReadings;
        double hourlyKWh = avgWatts / 1000.0;
        double hourCost = hourlyKWh * electricityRate;

        // Create hour timestamp
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, Integer.parseInt(date.split("-")[0]));
        cal.set(Calendar.MONTH, Integer.parseInt(date.split("-")[1]) - 1);
        cal.set(Calendar.DAY_OF_MONTH, Integer.parseInt(date.split("-")[2]));
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long timestamp = cal.getTimeInMillis();

        Map<String, Object> summary = new HashMap<>();
        summary.put("average_power_watts", avgWatts);
        summary.put("min_power_watts", minWatts == Double.MAX_VALUE ? 0 : minWatts);
        summary.put("peak_power_watts", maxWatts == Double.MIN_VALUE ? 0 : maxWatts);
        summary.put("total_energy_kwh", hourlyKWh);
        summary.put("total_cost_php", hourCost);
        summary.put("battery_level", batteryLevel);
        summary.put("date", date);
        summary.put("device_id", DEVICE_ID);
        summary.put("hour", hour);
        summary.put("timestamp", timestamp);

        // Add area breakdown if available
        if (!areaTotals.isEmpty()) {
            Map<String, Object> areaData = new HashMap<>();
            for (String areaId : areaTotals.keySet()) {
                double areaTotal = areaTotals.get(areaId);
                int areaCount = areaCounts.get(areaId);
                if (areaCount > 0) {
                    double areaAvg = areaTotal / areaCount;
                    double areaKWh = areaAvg / 1000.0;
                    double areaCost = areaKWh * electricityRate;

                    Map<String, Object> area = new HashMap<>();
                    area.put("average_power_watts", areaAvg);
                    area.put("circuit_id", areaId);
                    area.put("energy_kwh", areaKWh);
                    area.put("cost_php", areaCost);

                    areaData.put(areaId, area);
                }
            }
            if (!areaData.isEmpty()) {
                summary.put("area_data", areaData);
            }
        }

        return summary;
    }

    private void processDailySummaries() {
        databaseRef.child("devices").child(DEVICE_ID).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot deviceSnapshot) {
                double electricityRate = 8.50;
                if (deviceSnapshot.exists() && deviceSnapshot.child("electricity_rate").exists()) {
                    electricityRate = deviceSnapshot.child("electricity_rate").getValue(Double.class);
                }
                findAndProcessDailySummaries(electricityRate);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to get electricity rate for daily processing: " + error.getMessage());
            }
        });
    }

    private void findAndProcessDailySummaries(double electricityRate) {
        databaseRef.child("hourly_summaries").child(DEVICE_ID)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot allDatesSnapshot) {
                        if (!allDatesSnapshot.exists()) {
                            return;
                        }

                        for (DataSnapshot dateSnapshot : allDatesSnapshot.getChildren()) {
                            String date = dateSnapshot.getKey();
                            int hourlyCount = (int) dateSnapshot.getChildrenCount();

                            if (hourlyCount > 0) {
                                generateDailySummaryForDate(date, electricityRate);
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Failed to get hourly summaries for daily processing: " + error.getMessage());
                    }
                });
    }

    private void generateDailySummaryForDate(String date, double electricityRate) {
        databaseRef.child("daily_summaries").child(DEVICE_ID).child(date)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot existingSnapshot) {
                        if (existingSnapshot.exists()) {
                            return; // Already processed
                        }

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

    private void createDailySummaryFromHourlyData(String date, Map<String, Object> hourlySummaries, double electricityRate) {
        if (hourlySummaries == null || hourlySummaries.isEmpty()) {
            return;
        }

        double totalDailyCost = 0;
        double totalDailyKWh = 0;
        double totalAvgWatts = 0;
        double minWatts = Double.MAX_VALUE;
        double maxWatts = Double.MIN_VALUE;
        int hoursWithData = 0;
        int batteryLevel = 0;

        Map<String, Double> areaWattsTotal = new HashMap<>();
        Map<String, Double> areaCostTotal = new HashMap<>();
        Map<String, Double> areaKWhTotal = new HashMap<>();
        Map<String, Integer> areaCount = new HashMap<>();
        Map<String, Object> hourlyBreakdown = new HashMap<>();

        int peakUsageHour = 0;
        double peakUsagePower = 0;

        for (String hourKey : hourlySummaries.keySet()) {
            Map<String, Object> hourData = (Map<String, Object>) hourlySummaries.get(hourKey);

            if (hourData != null) {
                try {
                    Number hourCostNum = (Number) hourData.get("total_cost_php");
                    Number hourlyKWhNum = (Number) hourData.get("total_energy_kwh");
                    Number avgWattsNum = (Number) hourData.get("average_power_watts");
                    Number minWattsNum = (Number) hourData.get("min_power_watts");
                    Number maxWattsNum = (Number) hourData.get("peak_power_watts");
                    Number hourNum = (Number) hourData.get("hour");
                    Number batteryNum = (Number) hourData.get("battery_level");

                    if (hourCostNum != null) totalDailyCost += hourCostNum.doubleValue();
                    if (hourlyKWhNum != null) totalDailyKWh += hourlyKWhNum.doubleValue();
                    if (avgWattsNum != null) {
                        double avgW = avgWattsNum.doubleValue();
                        totalAvgWatts += avgW;
                        if (avgW > peakUsagePower && hourNum != null) {
                            peakUsagePower = avgW;
                            peakUsageHour = hourNum.intValue();
                        }
                        hoursWithData++;
                    }
                    if (minWattsNum != null) minWatts = Math.min(minWatts, minWattsNum.doubleValue());
                    if (maxWattsNum != null) maxWatts = Math.max(maxWatts, maxWattsNum.doubleValue());
                    if (batteryNum != null) batteryLevel = batteryNum.intValue();

                    // Hourly breakdown
                    if (hourNum != null && hourCostNum != null && hourlyKWhNum != null && avgWattsNum != null && maxWattsNum != null) {
                        Map<String, Object> hourBreakdown = new HashMap<>();
                        hourBreakdown.put("avg_power_watts", avgWattsNum.doubleValue());
                        hourBreakdown.put("cost_php", hourCostNum.doubleValue());
                        hourBreakdown.put("energy_kwh", hourlyKWhNum.doubleValue());
                        hourBreakdown.put("hour", hourNum.intValue());
                        hourBreakdown.put("peak_power_watts", maxWattsNum.doubleValue());
                        hourlyBreakdown.put(hourKey, hourBreakdown);
                    }

                    // Area data aggregation
                    Map<String, Object> areaData = (Map<String, Object>) hourData.get("area_data");
                    if (areaData != null) {
                        for (String areaId : areaData.keySet()) {
                            Map<String, Object> area = (Map<String, Object>) areaData.get(areaId);
                            if (area != null) {
                                Number areaWatts = (Number) area.get("average_power_watts");
                                Number areaCost = (Number) area.get("cost_php");
                                Number areaKWh = (Number) area.get("energy_kwh");

                                if (areaWatts != null) {
                                    areaWattsTotal.put(areaId, areaWattsTotal.getOrDefault(areaId, 0.0) + areaWatts.doubleValue());
                                    areaCount.put(areaId, areaCount.getOrDefault(areaId, 0) + 1);
                                }
                                if (areaCost != null) areaCostTotal.put(areaId, areaCostTotal.getOrDefault(areaId, 0.0) + areaCost.doubleValue());
                                if (areaKWh != null) areaKWhTotal.put(areaId, areaKWhTotal.getOrDefault(areaId, 0.0) + areaKWh.doubleValue());
                            }
                        }
                    }
                } catch (Exception e) {
                    // Skip invalid hourly data
                }
            }
        }

        if (hoursWithData == 0) {
            return;
        }

        double avgDailyWatts = totalAvgWatts / hoursWithData;

        // Final copies for lambda
        final double finalTotalDailyCost = totalDailyCost;
        final double finalTotalDailyKWh = totalDailyKWh;

        Map<String, Object> dailySummary = new HashMap<>();
        dailySummary.put("date", date);
        dailySummary.put("device_id", DEVICE_ID);
        dailySummary.put("total_cost_php", totalDailyCost);
        dailySummary.put("total_energy_kwh", totalDailyKWh);
        dailySummary.put("average_power_watts", avgDailyWatts);
        dailySummary.put("min_power_watts", minWatts == Double.MAX_VALUE ? 0 : minWatts);
        dailySummary.put("peak_power_watts", maxWatts == Double.MIN_VALUE ? 0 : maxWatts);
        dailySummary.put("hours_with_data", hoursWithData);
        dailySummary.put("electricity_rate", electricityRate);
        dailySummary.put("battery_level", batteryLevel);
        dailySummary.put("peak_usage_hour", peakUsageHour);
        dailySummary.put("peak_usage_power", peakUsagePower);
        dailySummary.put("peak_usage_time", peakUsageHour + ":00");
        dailySummary.put("timestamp", System.currentTimeMillis());
        dailySummary.put("hourly_breakdown", hourlyBreakdown);

        // Area data summary
        if (!areaWattsTotal.isEmpty()) {
            Map<String, Object> areaDataSummary = new HashMap<>();
            String[] areaNames = {"Living Room", "Kitchen", "Bedroom"};
            String[] areaIds = {"area1", "area2", "area3"};

            for (int i = 0; i < areaIds.length; i++) {
                String areaId = areaIds[i];
                if (areaWattsTotal.containsKey(areaId)) {
                    double areaAvgWatts = areaWattsTotal.get(areaId) / areaCount.get(areaId);
                    double areaTotalCost = areaCostTotal.getOrDefault(areaId, 0.0);
                    double areaTotalKWh = areaKWhTotal.getOrDefault(areaId, 0.0);
                    double percentage = (areaAvgWatts / avgDailyWatts) * 100;

                    Map<String, Object> areaSum = new HashMap<>();
                    areaSum.put("avg_power_watts", areaAvgWatts);
                    areaSum.put("circuit_id", areaId);
                    areaSum.put("circuit_name", areaNames[i]);
                    areaSum.put("percentage_of_total", percentage);
                    areaSum.put("total_cost_php", areaTotalCost);
                    areaSum.put("total_energy_kwh", areaTotalKWh);

                    areaDataSummary.put(areaId, areaSum);
                }
            }
            if (!areaDataSummary.isEmpty()) {
                dailySummary.put("area_data", areaDataSummary);
            }
        }

        // Save daily summary
        databaseRef.child("daily_summaries").child(DEVICE_ID).child(date)
                .setValue(dailySummary)
                .addOnFailureListener(e -> Log.e(TAG, "Failed to save daily summary for " + date, e));
    }
}