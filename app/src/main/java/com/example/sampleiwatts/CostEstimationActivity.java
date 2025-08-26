package com.example.sampleiwatts;

import android.app.DatePickerDialog;
import android.icu.util.Calendar;
import android.os.Bundle;
import android.util.Log;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.w3c.dom.Text;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class CostEstimationActivity extends AppCompatActivity {
    EditText etStartingDate, etEndingDate, etBatelecRate;
    TextView tvCostView, tvKwhView, tvElectricityRate, tvTotalUsage, tvDailyCost, tvArea1,tvArea2,tvArea3, tvProjectedText, tvProjectedCost;
    private DatabaseReference db;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_cost_estimation);
        LinearLayout buttonLayout = findViewById(R.id.button);

        tvProjectedCost = findViewById(R.id.tvProjectedCost);
        tvProjectedText = findViewById(R.id.tvProjectedText);
        tvArea1 = findViewById(R.id.tvArea1);
        tvArea2 = findViewById(R.id.tvArea2);
        tvArea3 = findViewById(R.id.tvArea3);
        tvDailyCost = findViewById(R.id.tvDailyCost);
        tvTotalUsage = findViewById(R.id.tvTotalUsage);
        tvCostView = findViewById(R.id.tvTotalCost);
        tvKwhView = findViewById(R.id.tvTotalKwh);
        etBatelecRate = findViewById(R.id.etBatelecRate);
        etBatelecRate.setOnClickListener(v -> {
            updateElectricityRate();
        });
        tvElectricityRate = findViewById(R.id.tvBatelecRate);
        db = FirebaseDatabase.getInstance().getReference();
        ButtonNavigator.setupButtons(this, buttonLayout);
        etStartingDate = findViewById(R.id.etStartingDate);
        etStartingDate.setOnClickListener(v -> {
            startingDate();
        });
        etEndingDate = findViewById(R.id.etEndingDate);
        etEndingDate.setOnClickListener(v -> {
            endingDate();
        });
        fetchFilterDates();
        fetchTotalCost();
        fetchTotalKwh();
        fetchElectricityRate();
        fetchTotalCostForDay();
        calculateCostForAllAreas();
        calculateProjectedMonthlyCost();
    }
    private void startingDate() {
        final Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this, // or getContext() if in Fragment
                (view, selectedYear, selectedMonth, selectedDay) -> {
                    // Format date (dd/MM/yyyy)
                    String date = selectedYear + "-" + (selectedMonth + 1) + "-" + selectedDay;
                    etStartingDate.setText(date);

                    // Save to Firebase
                    DatabaseReference costFilterRef = db.child("cost_filter_date");
                    costFilterRef.child("starting_date").setValue(date)
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(CostEstimationActivity.this, "Starting date saved", Toast.LENGTH_SHORT).show();
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(CostEstimationActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            });
                },
                year, month, day
        );

        datePickerDialog.show();
    }
    private void endingDate(){
        final Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this, // or getContext() if in Fragment
                (view, selectedYear, selectedMonth, selectedDay) -> {
                    // Format date
                    String date = selectedYear + "-" + (selectedMonth + 1) + "-" + selectedDay;
                    etEndingDate.setText(date);
                    // Save to Firebase
                    DatabaseReference costFilterRef = db.child("cost_filter_date");
                    costFilterRef.child("ending_date").setValue(date)
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(CostEstimationActivity.this, "Ending date saved", Toast.LENGTH_SHORT).show();
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(CostEstimationActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            });
                }, year, month, day);

        datePickerDialog.show();
    }
    private void fetchFilterDates() {
        // Reference to the "cost_filter_date" node
        DatabaseReference filterDateRef = db.child("cost_filter_date");

        filterDateRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                // Get starting_date
                String startingDate = dataSnapshot.child("starting_date").getValue(String.class);
                // Get ending_date
                String endingDate = dataSnapshot.child("ending_date").getValue(String.class);

                if (startingDate != null) {
                    etStartingDate.setText(startingDate); // your TextView for starting date
                } else {
                    etStartingDate.setText("Not set");
                }

                if (endingDate != null) {
                    etEndingDate.setText(endingDate); // your TextView for ending date
                } else {
                    etEndingDate.setText("Not set");
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Toast.makeText(CostEstimationActivity.this, "Error fetching dates", Toast.LENGTH_SHORT).show();
            }
        });
    }
    private void fetchTotalCost() {
        DatabaseReference costFilterDateRef = db.child("cost_filter_date");

        costFilterDateRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                String startingDateString = dataSnapshot.child("starting_date").getValue(String.class);
                String endingDateString = dataSnapshot.child("ending_date").getValue(String.class);

                // Log the raw data fetched
                Log.d("CostEstimation", "Raw Starting Date: " + startingDateString);
                Log.d("CostEstimation", "Raw Ending Date: " + endingDateString);

                SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                try {
                    Date startingDate = dateFormatter.parse(startingDateString);
                    Date endingDate = dateFormatter.parse(endingDateString);

                    Log.d("CostEstimation", "Parsed Starting Date: " + startingDate);
                    Log.d("CostEstimation", "Parsed Ending Date: " + endingDate);

                    db.child("hourly_summaries").orderByKey().addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            double cumulativeCost = 0;

                            // Loop through each date in the hourly summaries
                            for (DataSnapshot dateSnapshot : dataSnapshot.getChildren()) {
                                String dateKey = dateSnapshot.getKey();
                                Date currentDate = null;
                                try {
                                    currentDate = dateFormatter.parse(dateKey);
                                } catch (ParseException e) {
                                    Log.e("CostEstimation", "Error parsing date: " + dateKey);
                                }

                                Log.d("CostEstimation", "Processing Date: " + dateKey);
                                Log.d("CostEstimation", "Current Date: " + currentDate);

                                // Check if the current date is within the range of starting and ending dates
                                if ((currentDate.equals(startingDate) || currentDate.after(startingDate)) &&
                                        (currentDate.equals(endingDate) || currentDate.before(endingDate))) {

                                    // Loop through hourly data for the current date
                                    for (DataSnapshot hourSnapshot : dateSnapshot.getChildren()) {
                                        Double hourlyCost = hourSnapshot.child("total_cost").getValue(Double.class);

                                        if (hourlyCost != null) {
                                            Log.d("CostEstimation", "Hourly Cost for " + dateKey + ": ₱" + hourlyCost);
                                            cumulativeCost += hourlyCost;
                                        } else {
                                            Log.d("CostEstimation", "No hourly cost for " + dateKey);
                                        }
                                    }
                                }
                            }

                            // Format the total cost
                            String formattedCost = String.format("%.2f", cumulativeCost);
                            tvCostView.setText("₱ " + formattedCost);
                        }

                        @Override
                        public void onCancelled(DatabaseError databaseError) {
                            Log.e("FirebaseError", "Error fetching hourly summaries: " + databaseError.getMessage());
                        }
                    });
                } catch (ParseException e) {
                    e.printStackTrace();
                    Log.e("CostEstimation", "Error parsing dates: " + e.getMessage());
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e("FirebaseError", "Error fetching cost filter data: " + databaseError.getMessage());
            }
        });
    }
    private void fetchTotalKwh() {
        // Reference to the cost_filter_date to get the starting and ending dates
        DatabaseReference costFilterDateRef = db.child("cost_filter_date");

        costFilterDateRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                // Fetch the starting and ending dates from Firebase
                String startingDateString = dataSnapshot.child("starting_date").getValue(String.class);
                String endingDateString = dataSnapshot.child("ending_date").getValue(String.class);

                // Log the fetched data for debugging
                Log.d("CostEstimation", "Starting Date: " + startingDateString);
                Log.d("CostEstimation", "Ending Date: " + endingDateString);

                SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());  // Adjust as per your Firebase format
                try {
                    Date startingDate = dateFormatter.parse(startingDateString);
                    Date endingDate = dateFormatter.parse(endingDateString);

                    Log.d("CostEstimation", "Parsed Starting Date: " + startingDate);
                    Log.d("CostEstimation", "Parsed Ending Date: " + endingDate);

                    // Now, fetch the hourly summaries within the date range
                    db.child("hourly_summaries").orderByKey().addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            double cumulativeKwh = 0;

                            // Loop through the hourly summaries to accumulate the total kWh
                            for (DataSnapshot dateSnapshot : dataSnapshot.getChildren()) {
                                String dateKey = dateSnapshot.getKey();
                                Date currentDate = null;
                                try {
                                    currentDate = dateFormatter.parse(dateKey);
                                } catch (ParseException e) {
                                    Log.e("CostEstimation", "Error parsing date: " + dateKey);
                                }

                                Log.d("CostEstimation", "Processing Date: " + dateKey);
                                Log.d("CostEstimation", "Current Date: " + currentDate);

                                // Check if the current date is within the range of starting and ending dates
                                if ((currentDate.equals(startingDate) || currentDate.after(startingDate)) &&
                                        (currentDate.equals(endingDate) || currentDate.before(endingDate))) {

                                    // Loop through the hourly data for the current date
                                    for (DataSnapshot hourSnapshot : dateSnapshot.getChildren()) {
                                        Double hourlyKwh = hourSnapshot.child("total_kwh").getValue(Double.class);

                                        if (hourlyKwh != null) {
                                            Log.d("CostEstimation", "Hourly KWh for " + dateKey + ": " + hourlyKwh);
                                            cumulativeKwh += hourlyKwh;
                                        } else {
                                            Log.d("CostEstimation", "No hourly kWh for " + dateKey);
                                        }
                                    }
                                }
                            }

                            // Format and display the total kWh value
                            String formattedKwh = String.format("%.3f", cumulativeKwh);
                            Log.d("KwhTotal", "Total KWh: " + formattedKwh);
                            tvKwhView.setText(formattedKwh + " kwh");
                            tvTotalUsage.setText(formattedKwh + " kwh");
                        }

                        @Override
                        public void onCancelled(DatabaseError databaseError) {
                            Log.e("FirebaseError", "Error fetching hourly summaries: " + databaseError.getMessage());
                        }
                    });
                } catch (ParseException e) {
                    e.printStackTrace();
                    Log.e("CostEstimation", "Error parsing dates: " + e.getMessage());
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e("FirebaseError", "Error fetching cost filter data: " + databaseError.getMessage());
            }
        });
    }
    private void fetchElectricityRate() {
        DatabaseReference electricityRateRef = db.child("system_settings").child("electricity_rate_per_kwh");
        electricityRateRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                // Get the value from the database
                Object value = dataSnapshot.getValue();

                // Check if the value is a numeric type
                if (value instanceof Number) {
                    // Cast the value to a Number and then to a Double
                    Double electricityRatePerKwh = ((Number) value).doubleValue();

                    // Format the rate to 2 decimal places
                    String formattedRate = String.format("%.2f", electricityRatePerKwh);

                    // Update the UI
                    tvElectricityRate.setText("₱ " + formattedRate + " / kWh");
                    etBatelecRate.setText(formattedRate);
                } else {
                    // If the value is not a number, show the "not available" message
                    tvElectricityRate.setText("Electricity rate not available");
                    etBatelecRate.setText("Electricity rate not available");
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                // Handle database error
                Toast.makeText(CostEstimationActivity.this, "Error fetching electricity rate", Toast.LENGTH_SHORT).show();
            }
        });
    }
    private void updateElectricityRate() {
        String updateElectricityRate = etBatelecRate.getText().toString().trim();

        // Ensure the rate is not empty
        if (updateElectricityRate.isEmpty()) {
            Toast.makeText(CostEstimationActivity.this, "Electricity Rate cannot be empty!", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // Parse the rate as a double
            double rate = Double.parseDouble(updateElectricityRate);

            // Save the rate as a double to Firebase
            DatabaseReference deviceRef = db.child("system_settings");
            deviceRef.child("electricity_rate_per_kwh").setValue(rate)
                    .addOnSuccessListener(aVoid -> {
                        // Successfully updated the rate
                        Toast.makeText(CostEstimationActivity.this, "Electricity Rate updated", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> {
                        // Handle the error
                        Toast.makeText(CostEstimationActivity.this, "Error updating Electricity Rate", Toast.LENGTH_SHORT).show();
                    });
        } catch (NumberFormatException e) {
            // Handle the case where the input is not a valid number
            Toast.makeText(CostEstimationActivity.this, "Invalid Electricity Rate format", Toast.LENGTH_SHORT).show();
        }
    }
    private void fetchTotalCostForDay() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String currentDate = dateFormat.format(new Date());
        DatabaseReference hourlySummariesRef = db.child("hourly_summaries").child(currentDate);
        hourlySummariesRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                double totalCost = 0.0;
                for (DataSnapshot hourlySnapshot : dataSnapshot.getChildren()) {
                    Object value = hourlySnapshot.child("total_cost").getValue();
                    if (value != null) {
                        totalCost += ((Number) value).doubleValue();
                    }
                }
                tvDailyCost.setText("₱ " + String.format("%.2f", totalCost));
            }
            @Override
            public void onCancelled(DatabaseError databaseError) {
                Toast.makeText(CostEstimationActivity.this, "Error fetching data", Toast.LENGTH_SHORT).show();
            }
        });
    }
    private void calculateCostForAllAreas() {
        DatabaseReference systemSettingsRef = db.child("system_settings");
        DatabaseReference hourlySummariesRef = db.child("hourly_summaries");

        // Fetch the electricity rate from system_settings
        systemSettingsRef.child("electricity_rate_per_kwh").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                // Get the electricity rate per kWh
                Double electricityRatePerKwh = dataSnapshot.getValue(Double.class);

                // Check if the electricity rate is null
                if (electricityRatePerKwh == null) {
                    Toast.makeText(CostEstimationActivity.this, "Electricity rate is not available", Toast.LENGTH_SHORT).show();
                    return;
                }

                final double[] totalArea1Kwh = {0};
                final double[] totalArea2Kwh = {0};
                final double[] totalArea3Kwh = {0};

                // Iterate over all dates in the hourly_summaries collection
                hourlySummariesRef.addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        // Loop through all the dates
                        for (DataSnapshot dateSnapshot : dataSnapshot.getChildren()) {
                            // Loop through hourly data for each date
                            for (DataSnapshot hourlySnapshot : dateSnapshot.getChildren()) {
                                // Get kWh values for each area
                                Double area1Kwh = hourlySnapshot.child("area1_kwh").getValue(Double.class);
                                Double area2Kwh = hourlySnapshot.child("area2_kwh").getValue(Double.class);
                                Double area3Kwh = hourlySnapshot.child("area3_kwh").getValue(Double.class);

                                // If the kWh values are null, treat them as 0
                                area1Kwh = (area1Kwh == null) ? 0 : area1Kwh;
                                area2Kwh = (area2Kwh == null) ? 0 : area2Kwh;
                                area3Kwh = (area3Kwh == null) ? 0 : area3Kwh;

                                totalArea1Kwh[0] += area1Kwh;
                                totalArea2Kwh[0] += area2Kwh;
                                totalArea3Kwh[0] += area3Kwh;
                            }
                        }

                        // Calculate total consumption for all areas
                        double totalConsumption = totalArea1Kwh[0] + totalArea2Kwh[0] + totalArea3Kwh[0];

                        // Calculate the percentage of each area
                        double percentageArea1 = (totalConsumption == 0) ? 0 : (totalArea1Kwh[0] / totalConsumption) * 100;
                        double percentageArea2 = (totalConsumption == 0) ? 0 : (totalArea2Kwh[0] / totalConsumption) * 100;
                        double percentageArea3 = (totalConsumption == 0) ? 0 : (totalArea3Kwh[0] / totalConsumption) * 100;

                        // Calculate the total cost for each area
                        double totalCostArea1 = totalArea1Kwh[0] * electricityRatePerKwh;
                        double totalCostArea2 = totalArea2Kwh[0] * electricityRatePerKwh;
                        double totalCostArea3 = totalArea3Kwh[0] * electricityRatePerKwh;

                        // Update the TextViews to display the total cost and percentage for each area
                        tvArea1.setText("₱ " + String.format("%.2f", totalCostArea1) + " (" + String.format("%.2f", percentageArea1) + "%)");
                        tvArea2.setText("₱ " + String.format("%.2f", totalCostArea2) + " (" + String.format("%.2f", percentageArea2) + "%)");
                        tvArea3.setText("₱ " + String.format("%.2f", totalCostArea3) + " (" + String.format("%.2f", percentageArea3) + "%)");
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        Toast.makeText(CostEstimationActivity.this, "Error fetching hourly data", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Toast.makeText(CostEstimationActivity.this, "Error fetching electricity rate", Toast.LENGTH_SHORT).show();
            }
        });
    }
    private void calculateProjectedMonthlyCost() {
        // Reference to the cost_filter_date in Firebase to get the starting and ending dates
        DatabaseReference costFilterDateRef = db.child("cost_filter_date");

        costFilterDateRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                // 1. Fetch the starting date from Firebase
                String startingDateString = dataSnapshot.child("starting_date").getValue(String.class);
                SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

                try {
                    // 2. Parse the starting date
                    Date startingDate = dateFormatter.parse(startingDateString);

                    // 3. Get today's date
                    Date todayDate = new Date();

                    // 4. Calculate the number of days passed from the starting date to today
                    long diffInMillis = todayDate.getTime() - startingDate.getTime();
                    long daysPassed = TimeUnit.MILLISECONDS.toDays(diffInMillis);

                    // 5. Fetch the hourly cost data from Firebase for the period from the starting date to today
                    DatabaseReference hourlySummariesRef = db.child("hourly_summaries");

                    // 6. Calculate the cumulative cost for the period
                    final double[] cumulativeCost = {0};  // Using an array to handle it in the callback

                    hourlySummariesRef.orderByKey().addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            // Loop through the hourly summaries to accumulate the total cost
                            for (DataSnapshot dateSnapshot : dataSnapshot.getChildren()) {
                                String dateKey = dateSnapshot.getKey();
                                Date currentDate = null;
                                try {
                                    currentDate = dateFormatter.parse(dateKey);
                                } catch (ParseException e) {
                                    e.printStackTrace();
                                }

                                // If the date is within the range (from startingDate to today), accumulate the cost
                                if (currentDate != null && currentDate.after(startingDate) && currentDate.before(todayDate) ||
                                        currentDate.equals(startingDate) || currentDate.equals(todayDate)) {
                                    for (DataSnapshot hourSnapshot : dateSnapshot.getChildren()) {
                                        Double hourlyCost = hourSnapshot.child("total_cost").getValue(Double.class);
                                        if (hourlyCost != null) {
                                            cumulativeCost[0] += hourlyCost; // Accumulate the hourly cost
                                        }
                                    }
                                }
                            }

                            // 7. Calculate the daily average cost
                            double dailyAverageCost = cumulativeCost[0] / daysPassed;

                            // 8. Project the monthly cost (assuming a 30-day month)
                            double projectedMonthlyCost = dailyAverageCost * 30;  // For a 30-day month

                            // 9. Display the projected monthly cost in the TextView
                            String formattedCost = String.format("%.2f", projectedMonthlyCost);
                            tvProjectedCost.setText("₱ " + formattedCost);
                            tvProjectedText.setText("Based on the current " + daysPassed + "-day consumption pattern");
                        }

                        @Override
                        public void onCancelled(DatabaseError databaseError) {
                            Toast.makeText(CostEstimationActivity.this, "Error fetching hourly summaries", Toast.LENGTH_SHORT).show();
                        }
                    });
                } catch (ParseException e) {
                    e.printStackTrace();
                    Toast.makeText(CostEstimationActivity.this, "Error parsing starting date", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Toast.makeText(CostEstimationActivity.this, "Error fetching cost filter data", Toast.LENGTH_SHORT).show();
            }
        });
    }



}