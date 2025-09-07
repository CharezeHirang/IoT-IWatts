package com.example.sampleiwatts;

import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.sampleiwatts.managers.DataProcessingManager;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class DashboardActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private TextView tvTotalCost, tvElectricityRate, tvBatteryLife,tvTotalConsumption,tvTodayKwh, tvCurrentTime;
    private TextView tvArea1Kwh, tvArea2Kwh, tvArea3Kwh,  tvArea1Percentage, tvArea2Percentage, tvArea3Percentage, tvPeakTime, tvPeakValue;
    private ImageView ivBatteryImage;
    private LineChart lineChart1, lineChart2, lineChart3;

    private DatabaseReference db;
    private EditText etArea1, etArea2, etArea3;
    private DataProcessingManager processingManager;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_dashboard);

        lineChart1 = findViewById(R.id.area1_chart);
        lineChart2 = findViewById(R.id.area2_chart);
        lineChart3 = findViewById(R.id.area3_chart);
        db = FirebaseDatabase.getInstance().getReference();
        tvArea1Kwh = findViewById(R.id.tvArea1Kwh);
        tvArea2Kwh = findViewById(R.id.tvArea2Kwh);
        tvArea3Kwh = findViewById(R.id.tvArea3Kwh);

        tvArea1Percentage = findViewById(R.id.tvArea1Percentage);
        tvArea2Percentage = findViewById(R.id.tvArea2Percentage);
        tvArea3Percentage = findViewById(R.id.tvArea3Percentage);
        tvPeakTime = findViewById(R.id.tvPeakTime);
        tvPeakValue =  findViewById(R.id.tvPeakValue);
        etArea1 = findViewById(R.id.etArea1);
        etArea1.setOnClickListener(v -> {updateArea1Name();});
        etArea2 = findViewById(R.id.etArea2);
        etArea2.setOnClickListener(v -> {updateArea2Name();});
        etArea3 = findViewById(R.id.etArea3);
        etArea3.setOnClickListener(v -> {updateArea3Name();});
        tvTodayKwh = findViewById(R.id.tvTodayKwh);
        tvCurrentTime = findViewById(R.id.tvCurrentTime);
        tvTotalConsumption = findViewById(R.id.tvTotalConsumption);
        ivBatteryImage = findViewById(R.id.ivBatteryImage);
        tvBatteryLife = findViewById(R.id.tvBatteryLife);
        tvElectricityRate = findViewById(R.id.tvTotalKwh);
        tvTotalCost = findViewById(R.id.tvTotalCost);
        fetchTotalCost();
        fetchElectricityRate();
        fetchBatteryLife();
        fetchTotalKwh();
        fetchTotalKwhForDay();
        fetchAreaNames();
        fetchAreaKwh();
        fetchPeakWatts();
        fetchArea1();
        fetchArea2();
        fetchArea3();
        LinearLayout buttonLayout = findViewById(R.id.button);

        ButtonNavigator.setupButtons(this, buttonLayout);

        IWattsApplication app = (IWattsApplication) getApplication();
        processingManager = app.getProcessingManager();

        Log.d(TAG, "MainActivity created");


    }
    @Override
    protected void onResume() {
        super.onResume();

        Log.d(TAG, "MainActivity resumed - processing data");

        DataProcessingManager manager = ((IWattsApplication) getApplication()).getProcessingManager();
        manager.processDataInForeground();
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
                            tvTotalCost.setText("₱ " + formattedCost);
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
                Object value = dataSnapshot.getValue();

                if (value instanceof Number) {
                    Double electricityRatePerKwh = ((Number) value).doubleValue();

                    String formattedRate = String.format("%.2f", electricityRatePerKwh);

                    // Update the UI
                    tvElectricityRate.setText("₱ " + formattedRate + " / kWh");
                } else {
                    tvElectricityRate.setText("Electricity rate not available");
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                // Handle database error
                Toast.makeText(DashboardActivity.this, "Error fetching electricity rate", Toast.LENGTH_SHORT).show();
            }
        });
    }
    private void fetchBatteryLife() {
        // Reference to the "battery_life" field under the "system_settings" node
        DatabaseReference batteryLifeRef = db.child("system_settings").child("battery_life");

        // Add a listener to fetch the battery life from Firebase
        batteryLifeRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                String batteryLife = dataSnapshot.getValue(String.class);

                if (batteryLife != null) {
                    // Parse battery life percentage from the string (assuming it comes as "xx%")
                    int batteryPercentage = Integer.parseInt(batteryLife.replace("%", "").trim());

                    // Set appropriate drawable based on battery percentage
                    if (batteryPercentage >= 95) {
                        tvBatteryLife.setText(batteryPercentage + "%");  // Correctly set as string
                        ivBatteryImage.setImageResource(R.drawable.ic_battery1); // Battery 1 drawable
                    } else if (batteryPercentage >= 70) {
                        tvBatteryLife.setText(batteryPercentage + "%");  // Correctly set as string
                        ivBatteryImage.setImageResource(R.drawable.ic_battery2); // Battery 2 drawable
                    } else if (batteryPercentage >= 55) {
                        tvBatteryLife.setText(batteryPercentage + "%");  // Correctly set as string
                        ivBatteryImage.setImageResource(R.drawable.ic_battery3); // Battery 3 drawable
                    } else if (batteryPercentage >= 40) {
                        tvBatteryLife.setText(batteryPercentage + "%");  // Correctly set as string
                        ivBatteryImage.setImageResource(R.drawable.ic_battery4); // Battery 4 drawable
                    } else if (batteryPercentage >= 25) {
                        tvBatteryLife.setText(batteryPercentage + "%");  // Correctly set as string
                        ivBatteryImage.setImageResource(R.drawable.ic_battery5); // Battery 5 drawable
                    } else if (batteryPercentage >= 10) {
                        tvBatteryLife.setText(batteryPercentage + "%");  // Correctly set as string
                        ivBatteryImage.setImageResource(R.drawable.ic_battery6); // Battery 6 drawable
                    } else if (batteryPercentage >= 5) {
                        tvBatteryLife.setText(batteryPercentage + "%");  // Correctly set as string
                        ivBatteryImage.setImageResource(R.drawable.ic_battery7); // Battery 7 drawable
                    } else {
                        tvBatteryLife.setText(batteryPercentage + "%");  // Correctly set as string
                        ivBatteryImage.setImageResource(R.drawable.ic_battery8); // Battery 8 drawable
                    }
                } else {
                    tvBatteryLife.setText("Battery Life not available");
                    ivBatteryImage.setImageResource(R.drawable.ic_battery9); // Default drawable if not available
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                // Handle any error in retrieving the data
                Toast.makeText(DashboardActivity.this, "Error fetching battery life", Toast.LENGTH_SHORT).show();
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

                            String formattedKwh = String.format("%.3f", cumulativeKwh);
                            Log.d("KwhTotal", "Total KWh: " + formattedKwh);

                            String totalKwhText = formattedKwh + " kwh";
                            SpannableString spannableString = new SpannableString(totalKwhText);
                            spannableString.setSpan(new android.text.style.AbsoluteSizeSpan(50, true), 0, formattedKwh.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            spannableString.setSpan(new android.text.style.AbsoluteSizeSpan(20, true), formattedKwh.length() + 1, totalKwhText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            tvTotalConsumption.setText(spannableString);

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
    private void fetchTotalKwhForDay() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String currentDate = dateFormat.format(new Date());
        DatabaseReference hourlySummariesRef = db.child("hourly_summaries").child(currentDate);

        hourlySummariesRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                double totalKwh = 0.0;
                for (DataSnapshot hourlySnapshot : dataSnapshot.getChildren()) {
                    Object value = hourlySnapshot.child("total_kwh").getValue();
                    if (value != null) {
                        totalKwh += ((Number) value).doubleValue();
                    }
                }

                // Get the current time in the desired format, including AM/PM
                SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm:ss a", Locale.getDefault());
                String currentTime = timeFormat.format(new Date());

                // Display the total kWh in the first TextView
                tvTodayKwh.setText("Today's consumption: " + String.format("%.3f kWh", totalKwh));

                // Display the current time with AM/PM in the second TextView
                tvCurrentTime.setText("at " + currentTime);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Toast.makeText(DashboardActivity.this, "Error fetching data", Toast.LENGTH_SHORT).show();
            }
        });
    }
    private void updateArea1Name() {
        String area1Name = etArea1.getText().toString().trim();
        if (area1Name.isEmpty()) {
            Toast.makeText(DashboardActivity.this, "Area 1 Name cannot be empty!", Toast.LENGTH_SHORT).show();
            return;
        }
        area1Name = capitalizeFirstLetter(area1Name);
        DatabaseReference systemSettingsRef = db.child("system_settings");
        systemSettingsRef.child("area1_name").setValue(area1Name)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(DashboardActivity.this, "Area 1 Name updated", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(DashboardActivity.this, "Error updating Area 1 Name", Toast.LENGTH_SHORT).show();
                });
    }
    private String capitalizeFirstLetter(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }
    private void updateArea2Name() {
        String area2Name = etArea2.getText().toString().trim();
        if (area2Name.isEmpty()) {
            Toast.makeText(DashboardActivity.this, "Area 2 Name cannot be empty!", Toast.LENGTH_SHORT).show();
            return;
        }
        area2Name = capitalizeFirstLetter(area2Name);
        DatabaseReference systemSettingsRef = db.child("system_settings");
        systemSettingsRef.child("area2_name").setValue(area2Name)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(DashboardActivity.this, "Area 2 Name updated", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(DashboardActivity.this, "Error updating Area 2 Name", Toast.LENGTH_SHORT).show();
                });
    }
    private void updateArea3Name() {
        String area3Name = etArea3.getText().toString().trim();
        if (area3Name.isEmpty()) {
            Toast.makeText(DashboardActivity.this, "Area 3 Name cannot be empty!", Toast.LENGTH_SHORT).show();
            return;
        }
        area3Name = capitalizeFirstLetter(area3Name);
        DatabaseReference systemSettingsRef = db.child("system_settings");
        systemSettingsRef.child("area3_name").setValue(area3Name)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(DashboardActivity.this, "Area 3 Name updated", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(DashboardActivity.this, "Error updating Area 3 Name", Toast.LENGTH_SHORT).show();
                });
    }
    private void fetchAreaNames() {
        DatabaseReference areaNamesRef = db.child("system_settings");
        areaNamesRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                // Fetch the area names
                String area1Name = dataSnapshot.child("area1_name").getValue(String.class);
                String area2Name = dataSnapshot.child("area2_name").getValue(String.class);
                String area3Name = dataSnapshot.child("area3_name").getValue(String.class);

                // Set the area names to the TextViews
                if (area1Name != null) {
                    etArea1.setText(area1Name);
                } else {
                    etArea1.setText("Area 1 Name not available");
                }

                if (area2Name != null) {
                    etArea2.setText(area2Name);
                } else {
                    etArea2.setText("Area 2 Name not available");
                }

                if (area3Name != null) {
                    etArea3.setText(area3Name);
                } else {
                    etArea3.setText("Area 3 Name not available");
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                // Handle any error in retrieving the data
                Toast.makeText(DashboardActivity.this, "Error fetching area names", Toast.LENGTH_SHORT).show();
            }
        });
    }
    private void fetchAreaKwh() {
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
                            double cumulativeArea1Kwh = 0;
                            double cumulativeArea2Kwh = 0;
                            double cumulativeArea3Kwh = 0;

                            // Loop through the hourly summaries to accumulate the total kWh for each area
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
                                        // Fetch kWh for each area (assuming your data has area1_kwh, area2_kwh, and area3_kwh)
                                        Double area1Kwh = hourSnapshot.child("area1_kwh").getValue(Double.class);
                                        Double area2Kwh = hourSnapshot.child("area2_kwh").getValue(Double.class);
                                        Double area3Kwh = hourSnapshot.child("area3_kwh").getValue(Double.class);

                                        if (area1Kwh != null) {
                                            Log.d("CostEstimation", "Area 1 Hourly kWh for " + dateKey + ": " + area1Kwh);
                                            cumulativeArea1Kwh += area1Kwh;
                                        }

                                        if (area2Kwh != null) {
                                            Log.d("CostEstimation", "Area 2 Hourly kWh for " + dateKey + ": " + area2Kwh);
                                            cumulativeArea2Kwh += area2Kwh;
                                        }

                                        if (area3Kwh != null) {
                                            Log.d("CostEstimation", "Area 3 Hourly kWh for " + dateKey + ": " + area3Kwh);
                                            cumulativeArea3Kwh += area3Kwh;
                                        }
                                    }
                                }
                            }

                            // Calculate total kWh from all areas
                            double totalKwh = cumulativeArea1Kwh + cumulativeArea2Kwh + cumulativeArea3Kwh;

                            // Calculate percentage for each area
                            double area1Percentage = (cumulativeArea1Kwh / totalKwh) * 100;
                            double area2Percentage = (cumulativeArea2Kwh / totalKwh) * 100;
                            double area3Percentage = (cumulativeArea3Kwh / totalKwh) * 100;

                            // Format and display the total kWh value for each area
                            String formattedArea1Kwh = String.format("%.3f", cumulativeArea1Kwh);
                            String formattedArea2Kwh = String.format("%.3f", cumulativeArea2Kwh);
                            String formattedArea3Kwh = String.format("%.3f", cumulativeArea3Kwh);

                            String formattedArea1Percentage = String.format("%.2f", area1Percentage);
                            String formattedArea2Percentage = String.format("%.2f", area2Percentage);
                            String formattedArea3Percentage = String.format("%.2f", area3Percentage);

                            Log.d("AreaKwhTotal", "Area 1 Total kWh: " + formattedArea1Kwh + " (" + formattedArea1Percentage + "%)");
                            Log.d("AreaKwhTotal", "Area 2 Total kWh: " + formattedArea2Kwh + " (" + formattedArea2Percentage + "%)");
                            Log.d("AreaKwhTotal", "Area 3 Total kWh: " + formattedArea3Kwh + " (" + formattedArea3Percentage + "%)");

                            // Set the total kWh for each area
                            tvArea1Kwh.setText(formattedArea1Kwh);
                            tvArea2Kwh.setText(formattedArea2Kwh);
                            tvArea3Kwh.setText(formattedArea3Kwh);

                            // Set the corresponding percentages for each area in separate TextViews
                            tvArea1Percentage.setText(formattedArea1Percentage + "%");
                            tvArea2Percentage.setText(formattedArea2Percentage + "%");
                            tvArea3Percentage.setText(formattedArea3Percentage + "%");
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
    private void fetchPeakWatts() {
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

                    // Now, fetch the daily summaries within the date range
                    db.child("daily_summaries").orderByKey().addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            double highestPeakWatts = 0;
                            String peakDate = "";
                            String peakTime = "";

                            // Loop through the daily summaries to find the highest peak_watts
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

                                    // Get the peak_watts and peak_time for the current date
                                    Double peakWatts = dateSnapshot.child("peak_watts").getValue(Double.class);
                                    String peakTimeString = dateSnapshot.child("peak_time").getValue(String.class);

                                    if (peakWatts != null && peakTimeString != null) {
                                        Log.d("CostEstimation", "Peak Watts for " + dateKey + ": " + peakWatts + " at " + peakTimeString);

                                        // Update the highest peak_watts if the current one is higher
                                        if (peakWatts > highestPeakWatts) {
                                            highestPeakWatts = peakWatts;
                                            peakDate = dateKey;  // Store the date of the highest peak
                                            peakTime = peakTimeString;  // Store the time of the highest peak
                                        }
                                    } else {
                                        Log.d("CostEstimation", "No peak watts or peak time for " + dateKey);
                                    }
                                }
                            }

                            // Format the peak time to display with AM/PM
                            try {
                                // Parse the peak time (assuming it is in 24-hour format "HH:mm:ss")
                                SimpleDateFormat time24Format = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
                                Date peakTimeDate = time24Format.parse(peakTime);

                                // Convert to 12-hour format with AM/PM
                                SimpleDateFormat time12Format = new SimpleDateFormat("hh:mm a", Locale.getDefault());
                                String formattedPeakTime = time12Format.format(peakTimeDate);

                                // Display the highest peak_watts value along with the date and time
                                String formattedPeakWatts = String.format("%.0f", highestPeakWatts);
                                Log.d("PeakWatts", "Highest Peak Watts: " + formattedPeakWatts + " on " + peakDate + " at " + formattedPeakTime);

                                // Update UI with peak watt value in tvPeakValue and formatted time in tvPeakTime
                                tvPeakValue.setText(formattedPeakWatts + " W ");
                                tvPeakTime.setText(peakDate + " " + formattedPeakTime);  // Display formatted time (AM/PM) in tvPeakTime
                            } catch (ParseException e) {
                                Log.e("CostEstimation", "Error parsing or formatting peak time: " + e.getMessage());
                            }
                        }

                        @Override
                        public void onCancelled(DatabaseError databaseError) {
                            Log.e("FirebaseError", "Error fetching daily summaries: " + databaseError.getMessage());
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
    private void fetchArea1() {
        DatabaseReference costFilterDateRef = db.child("cost_filter_date");

        costFilterDateRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                String startingDateString = dataSnapshot.child("starting_date").getValue(String.class);
                String endingDateString = dataSnapshot.child("ending_date").getValue(String.class);

                SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                try {
                    Date startingDate = dateFormatter.parse(startingDateString);
                    Date endingDate = dateFormatter.parse(endingDateString);

                    db.child("hourly_summaries").orderByKey().addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            ArrayList<Entry> entries = new ArrayList<>();
                            ArrayList<String> dateLabels = new ArrayList<>();
                            int index = 0;

                            for (DataSnapshot dateSnapshot : dataSnapshot.getChildren()) {
                                String dateKey = dateSnapshot.getKey();
                                Date currentDate = null;
                                try {
                                    currentDate = dateFormatter.parse(dateKey);
                                } catch (ParseException e) {
                                    Log.e("CostEstimation", "Error parsing date: " + dateKey);
                                }

                                if ((currentDate.equals(startingDate) || currentDate.after(startingDate)) &&
                                        (currentDate.equals(endingDate) || currentDate.before(endingDate))) {
                                    double totalArea1Kwh = 0.0;

                                    for (DataSnapshot hourSnapshot : dateSnapshot.getChildren()) {
                                        Double area1Kwh = hourSnapshot.child("area1_kwh").getValue(Double.class);

                                        if (area1Kwh != null) {
                                            totalArea1Kwh += area1Kwh;
                                        }
                                    }

                                    entries.add(new Entry(index, (float) totalArea1Kwh));
                                    dateLabels.add(new SimpleDateFormat("MM-dd", Locale.getDefault()).format(currentDate));
                                    index++;
                                }
                            }

                            if (!entries.isEmpty()) {
                                LineDataSet dataSet = new LineDataSet(entries, "Area 1 Consumption (kWh)");
                                LineData lineData = new LineData(dataSet);

                                dataSet.setColor(getResources().getColor(R.color.brown));
                                dataSet.setValueTextColor(getResources().getColor(R.color.brown));
                                dataSet.setDrawFilled(true);
                                dataSet.setLineWidth(2f);
                                dataSet.setDrawCircles(true);

                                lineChart1.setData(lineData);
                                lineChart1.getAxisRight().setEnabled(false);
                                lineChart1.getAxisLeft().setGridColor(getResources().getColor(R.color.brown));
                                lineChart1.getAxisLeft().setTextColor(getResources().getColor(R.color.brown));
                                lineChart1.getXAxis().setGridColor(getResources().getColor(R.color.brown));
                                lineChart1.getAxisRight().setGridColor(getResources().getColor(R.color.brown));
                                lineChart1.getLegend().setTextColor(getResources().getColor(R.color.brown));


                                XAxis xAxis = lineChart1.getXAxis();
                                xAxis.setValueFormatter(new IndexAxisValueFormatter(dateLabels));
                                xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
                                xAxis.setGranularity(1f);
                                xAxis.setTextColor(getResources().getColor(R.color.brown));
                                lineChart1.getDescription().setEnabled(false);
                                lineChart1.setExtraBottomOffset(10f);
                                xAxis.setDrawLabels(true);


                                Legend legend = lineChart1.getLegend();
                                legend.setVerticalAlignment(Legend.LegendVerticalAlignment.TOP);
                                legend.setForm(Legend.LegendForm.LINE);
                                legend.setTextColor(getResources().getColor(R.color.brown));

                                lineChart1.invalidate();
                            } else {
                                Log.d("CostEstimation", "No data available for the selected date range.");
                            }
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
    private void fetchArea2() {
        DatabaseReference costFilterDateRef = db.child("cost_filter_date");

        costFilterDateRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                String startingDateString = dataSnapshot.child("starting_date").getValue(String.class);
                String endingDateString = dataSnapshot.child("ending_date").getValue(String.class);

                SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                try {
                    Date startingDate = dateFormatter.parse(startingDateString);
                    Date endingDate = dateFormatter.parse(endingDateString);

                    db.child("hourly_summaries").orderByKey().addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            ArrayList<Entry> entries = new ArrayList<>();
                            ArrayList<String> dateLabels = new ArrayList<>();
                            int index = 0;

                            for (DataSnapshot dateSnapshot : dataSnapshot.getChildren()) {
                                String dateKey = dateSnapshot.getKey();
                                Date currentDate = null;
                                try {
                                    currentDate = dateFormatter.parse(dateKey);
                                } catch (ParseException e) {
                                    Log.e("CostEstimation", "Error parsing date: " + dateKey);
                                }

                                if ((currentDate.equals(startingDate) || currentDate.after(startingDate)) &&
                                        (currentDate.equals(endingDate) || currentDate.before(endingDate))) {
                                    double totalArea2Kwh = 0.0;

                                    for (DataSnapshot hourSnapshot : dateSnapshot.getChildren()) {
                                        Double area2Kwh = hourSnapshot.child("area2_kwh").getValue(Double.class);

                                        if (area2Kwh != null) {
                                            totalArea2Kwh += area2Kwh;
                                        }
                                    }

                                    entries.add(new Entry(index, (float) totalArea2Kwh));
                                    dateLabels.add(new SimpleDateFormat("MM-dd", Locale.getDefault()).format(currentDate));
                                    index++;
                                }
                            }

                            if (!entries.isEmpty()) {
                                LineDataSet dataSet = new LineDataSet(entries, "Area 2 Consumption (kWh)");
                                LineData lineData = new LineData(dataSet);

                                dataSet.setColor(getResources().getColor(R.color.brown));
                                dataSet.setValueTextColor(getResources().getColor(R.color.brown));
                                dataSet.setDrawFilled(true);
                                dataSet.setLineWidth(2f);
                                dataSet.setDrawCircles(true);

                                lineChart2.setData(lineData);
                                lineChart2.getAxisRight().setEnabled(false);
                                lineChart2.getAxisLeft().setGridColor(getResources().getColor(R.color.brown));
                                lineChart2.getAxisLeft().setTextColor(getResources().getColor(R.color.brown));
                                lineChart2.getXAxis().setGridColor(getResources().getColor(R.color.brown));
                                lineChart2.getAxisRight().setGridColor(getResources().getColor(R.color.brown));
                                lineChart2.getLegend().setTextColor(getResources().getColor(R.color.brown));


                                XAxis xAxis = lineChart2.getXAxis();
                                xAxis.setValueFormatter(new IndexAxisValueFormatter(dateLabels));
                                xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
                                xAxis.setGranularity(1f);
                                xAxis.setTextColor(getResources().getColor(R.color.brown));
                                lineChart2.getDescription().setEnabled(false);
                                lineChart2.setExtraBottomOffset(10f);
                                xAxis.setDrawLabels(true);


                                Legend legend = lineChart2.getLegend();
                                legend.setVerticalAlignment(Legend.LegendVerticalAlignment.TOP);
                                legend.setForm(Legend.LegendForm.LINE);
                                legend.setTextColor(getResources().getColor(R.color.brown));

                                lineChart2.invalidate();
                            } else {
                                Log.d("CostEstimation", "No data available for the selected date range.");
                            }
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
    private void fetchArea3() {
        DatabaseReference costFilterDateRef = db.child("cost_filter_date");

        costFilterDateRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                String startingDateString = dataSnapshot.child("starting_date").getValue(String.class);
                String endingDateString = dataSnapshot.child("ending_date").getValue(String.class);

                SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                try {
                    Date startingDate = dateFormatter.parse(startingDateString);
                    Date endingDate = dateFormatter.parse(endingDateString);

                    db.child("hourly_summaries").orderByKey().addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            ArrayList<Entry> entries = new ArrayList<>();
                            ArrayList<String> dateLabels = new ArrayList<>();
                            int index = 0;

                            for (DataSnapshot dateSnapshot : dataSnapshot.getChildren()) {
                                String dateKey = dateSnapshot.getKey();
                                Date currentDate = null;
                                try {
                                    currentDate = dateFormatter.parse(dateKey);
                                } catch (ParseException e) {
                                    Log.e("CostEstimation", "Error parsing date: " + dateKey);
                                }

                                if ((currentDate.equals(startingDate) || currentDate.after(startingDate)) &&
                                        (currentDate.equals(endingDate) || currentDate.before(endingDate))) {
                                    double totalArea3Kwh = 0.0;

                                    for (DataSnapshot hourSnapshot : dateSnapshot.getChildren()) {
                                        Double area3Kwh = hourSnapshot.child("area3_kwh").getValue(Double.class);

                                        if (area3Kwh != null) {
                                            totalArea3Kwh += area3Kwh;
                                        }
                                    }

                                    entries.add(new Entry(index, (float) totalArea3Kwh));
                                    dateLabels.add(new SimpleDateFormat("MM-dd", Locale.getDefault()).format(currentDate));
                                    index++;
                                }
                            }

                            if (!entries.isEmpty()) {
                                LineDataSet dataSet = new LineDataSet(entries, "Area 3 Consumption (kWh)");
                                LineData lineData = new LineData(dataSet);

                                dataSet.setColor(getResources().getColor(R.color.brown));
                                dataSet.setValueTextColor(getResources().getColor(R.color.brown));
                                dataSet.setDrawFilled(true);
                                dataSet.setLineWidth(2f);
                                dataSet.setDrawCircles(true);

                                lineChart3.setData(lineData);
                                lineChart3.getAxisRight().setEnabled(false);
                                lineChart3.getAxisLeft().setGridColor(getResources().getColor(R.color.brown));
                                lineChart3.getAxisLeft().setTextColor(getResources().getColor(R.color.brown));
                                lineChart3.getXAxis().setGridColor(getResources().getColor(R.color.brown));
                                lineChart3.getAxisRight().setGridColor(getResources().getColor(R.color.brown));
                                lineChart3.getLegend().setTextColor(getResources().getColor(R.color.brown));


                                XAxis xAxis = lineChart3.getXAxis();
                                xAxis.setValueFormatter(new IndexAxisValueFormatter(dateLabels));
                                xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
                                xAxis.setGranularity(1f);
                                xAxis.setTextColor(getResources().getColor(R.color.brown));
                                lineChart3.getDescription().setEnabled(false);
                                lineChart3.setExtraBottomOffset(10f);
                                xAxis.setDrawLabels(true);


                                Legend legend = lineChart3.getLegend();
                                legend.setVerticalAlignment(Legend.LegendVerticalAlignment.TOP);
                                legend.setForm(Legend.LegendForm.LINE);
                                legend.setTextColor(getResources().getColor(R.color.brown));

                                lineChart3.invalidate();
                            } else {
                                Log.d("CostEstimation", "No data available for the selected date range.");
                            }
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
















}