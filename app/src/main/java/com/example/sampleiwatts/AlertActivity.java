package com.example.sampleiwatts;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AlertActivity extends AppCompatActivity {

    private DatabaseReference db;
    private TextView tvTotalKwh, tvTotalCost;
    private EditText etPowerValue, etBudgetValue;
    private MaterialButton btnSave;
    private static final String PREFS = "AppPrefs";
    private static final String KEY_PUSH = "push_enabled";
    private static final int REQ_POST_NOTIFS = 1001;
    private static final String TOPIC_ALERTS = "alerts";

    private com.google.android.material.materialswitch.MaterialSwitch switchPush;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alert);

        db = FirebaseDatabase.getInstance().getReference();
        tvTotalCost = findViewById(R.id.total_cost);
        tvTotalKwh = findViewById(R.id.total_kwh);
        etPowerValue = findViewById(R.id.etPowerValue);
        etBudgetValue = findViewById(R.id.etBudgetValue);
        switchPush = findViewById(R.id.switchPush);
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        switchPush.setChecked(prefs.getBoolean(KEY_PUSH, false));
        btnSave = findViewById(R.id.btnSave);
        btnSave.setOnClickListener(v -> saveSetting());
        fetchData();
        fetchValue();


    }

    private void fetchData() {
        db.child("threshold").child("kwh_value").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot thresholdSnapshot) {
                String kwhThreshold = thresholdSnapshot.getValue(String.class);
                db.child("threshold").child("cost_value").addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot costSnapshot) {
                        String costThreshold = costSnapshot.getValue(String.class);
                        if (kwhThreshold != null && costThreshold != null) {
                            db.child("hourly_summaries").orderByKey().addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(DataSnapshot dataSnapshot) {
                                    double cumulativeKwh = 0;
                                    double cumulativeCost = 0;
                                    String timestampKwh = "No timestamp available";
                                    String timestampCost = "No timestamp available";
                                    boolean isKwhThresholdMet = false;
                                    boolean isCostThresholdMet = false;
                                    boolean isKwhNearAlertSent = false;
                                    boolean isCostNearAlertSent = false;

                                    String alertMessageKwh = "";
                                    String alertMessageCost = "";

                                    for (DataSnapshot dateSnapshot : dataSnapshot.getChildren()) {
                                        for (DataSnapshot hourSnapshot : dateSnapshot.getChildren()) {
                                            Double hourlyKwh = hourSnapshot.child("total_kwh").getValue(Double.class);
                                            if (hourlyKwh != null) {
                                                cumulativeKwh += hourlyKwh;
                                            }

                                            Double hourlyCost = hourSnapshot.child("total_cost").getValue(Double.class);
                                            if (hourlyCost != null) {
                                                cumulativeCost += hourlyCost;
                                            }
                                        }
                                    }

                                    // ✅ update totals only once after loop finishes
                                    tvTotalKwh.setText("Total kWh: " + cumulativeKwh);
                                    tvTotalCost.setText("Total Cost: ₱" + cumulativeCost);

                                    // ---- kWh alerts ----
                                    double kwhDouble = Double.parseDouble(kwhThreshold);
                                    if (!isKwhThresholdMet && !isKwhNearAlertSent &&
                                            cumulativeKwh >= (kwhDouble - 0.001) && cumulativeKwh < kwhDouble) {
                                        String nearTimestamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                                                .format(new java.util.Date());
                                        TextView kwhTimeTextView = findViewById(R.id.kwh_time);
                                        kwhTimeTextView.setText("You are close to meeting your kWh threshold of " + kwhThreshold +
                                                " kWh (Current: " + cumulativeKwh + " kWh) at " + nearTimestamp);
                                    }
                                    if (cumulativeKwh >= kwhDouble && !isKwhThresholdMet) {
                                        String dateKey = dataSnapshot.getChildren().iterator().next().getKey();
                                        String hourKey = dataSnapshot.getChildren().iterator().next().getChildren().iterator().next().getKey();
                                        timestampKwh = dateKey + " " + hourKey + ":00:00";
                                        alertMessageKwh = "You have met your kWh threshold of " + kwhThreshold + " kWh at " + timestampKwh;
                                        isKwhThresholdMet = true;
                                    }

                                    // ---- Cost alerts ✅ after full accumulation
                                    double costDouble = Double.parseDouble(costThreshold);
                                    if (!isCostThresholdMet && !isCostNearAlertSent &&
                                            cumulativeCost >= (costDouble - 1) && cumulativeCost < costDouble) {
                                        String nearTimestamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                                                .format(new java.util.Date());
                                        String nearAlertMessage = "You are close to meeting your budget limit of ₱" + costThreshold +
                                                " (Current: ₱" + cumulativeCost + ") at " + nearTimestamp;
                                        TextView costTimeTextView = findViewById(R.id.cost_time);
                                        costTimeTextView.setText(nearAlertMessage);
                                        isCostNearAlertSent = true;
                                    }
                                    if (cumulativeCost >= costDouble && !isCostThresholdMet) {
                                        String dateKey = dataSnapshot.getChildren().iterator().next().getKey();
                                        String hourKey = dataSnapshot.getChildren().iterator().next().getChildren().iterator().next().getKey();
                                        timestampCost = dateKey + " " + hourKey + ":00:00";
                                        alertMessageCost = "You have met your budget limit of ₱" + costThreshold + " at " + timestampCost;
                                        isCostThresholdMet = true;
                                    }

                                    // ✅ Final messages (only show in TextView now)
                                    if (isKwhThresholdMet) {
                                        TextView kwhTimeTextView = findViewById(R.id.kwh_time);
                                        kwhTimeTextView.setText("kWh Threshold met at: " + timestampKwh);
                                    } else if (!isKwhNearAlertSent) {
                                        TextView kwhTimeTextView = findViewById(R.id.kwh_time);
                                        kwhTimeTextView.setText("No kWh threshold met yet.");
                                    }

                                    if (isCostThresholdMet) {
                                        TextView costTimeTextView = findViewById(R.id.cost_time);
                                        costTimeTextView.setText("Cost Threshold met at: " + timestampCost);
                                    } else if (!isCostNearAlertSent) {
                                        TextView costTimeTextView = findViewById(R.id.cost_time);
                                        costTimeTextView.setText("No Cost threshold met yet.");
                                    }
                                }

                                @Override
                                public void onCancelled(DatabaseError databaseError) {
                                    Toast.makeText(AlertActivity.this, "Failed to fetch data", Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        Toast.makeText(AlertActivity.this, "Failed to fetch cost value", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Toast.makeText(AlertActivity.this, "Failed to fetch kwh value", Toast.LENGTH_SHORT).show();
            }
        });
    }
    private void fetchDataFromDatabase() {
        db.child("threshold").child("kwh_value").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot thresholdSnapshot) {
                String kwhThreshold = thresholdSnapshot.getValue(String.class);
                db.child("threshold").child("cost_value").addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot costSnapshot) {
                        String costThreshold = costSnapshot.getValue(String.class);
                        if (kwhThreshold != null && costThreshold != null) {
                            db.child("hourly_summaries").orderByKey().addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(DataSnapshot dataSnapshot) {
                                    double cumulativeKwh = 0;
                                    double cumulativeCost = 0;
                                    String timestampKwh = "No timestamp available";
                                    String timestampCost = "No timestamp available";
                                    boolean isKwhThresholdMet = false;
                                    boolean isCostThresholdMet = false;
                                    boolean isKwhNearAlertSent = false;
                                    boolean isCostNearAlertSent = false;

                                    String alertMessageKwh = "";
                                    String alertMessageCost = "";

                                    for (DataSnapshot dateSnapshot : dataSnapshot.getChildren()) {
                                        for (DataSnapshot hourSnapshot : dateSnapshot.getChildren()) {
                                            Double hourlyKwh = hourSnapshot.child("total_kwh").getValue(Double.class);
                                            if (hourlyKwh != null) {
                                                cumulativeKwh += hourlyKwh;
                                            }

                                            Double hourlyCost = hourSnapshot.child("total_cost").getValue(Double.class);
                                            if (hourlyCost != null) {
                                                cumulativeCost += hourlyCost;
                                            }
                                        }
                                    }

// ✅ update totals only once after loop finishes
                                    tvTotalKwh.setText("Total kWh: " + cumulativeKwh);
                                    tvTotalCost.setText("Total Cost: ₱" + cumulativeCost);

// ---- kWh alerts ----
                                    double kwhDouble = Double.parseDouble(kwhThreshold);
                                    if (!isKwhThresholdMet && !isKwhNearAlertSent &&
                                            cumulativeKwh >= (kwhDouble - 0.001) && cumulativeKwh < kwhDouble) {
                                        String nearTimestamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                                                .format(new java.util.Date());
                                        String nearAlertMessage = "You are close to meeting your kWh threshold of " + kwhThreshold +
                                                " kWh (Current: " + cumulativeKwh + " kWh) at " + nearTimestamp;
                                        TextView kwhTimeTextView = findViewById(R.id.kwh_time);
                                        kwhTimeTextView.setText(nearAlertMessage);
                                        sendAlertToFirebase("kWh Near Limit", nearAlertMessage, nearTimestamp);
                                        isKwhNearAlertSent = true;
                                    }
                                    if (cumulativeKwh >= kwhDouble && !isKwhThresholdMet) {
                                        String dateKey = dataSnapshot.getChildren().iterator().next().getKey();
                                        String hourKey = dataSnapshot.getChildren().iterator().next().getChildren().iterator().next().getKey();
                                        timestampKwh = dateKey + " " + hourKey + ":00:00";
                                        alertMessageKwh = "You have met your kWh threshold of " + kwhThreshold + " kWh at " + timestampKwh;
                                        isKwhThresholdMet = true;
                                    }

// ---- Cost alerts ✅ now checked after full accumulation
                                    double costDouble = Double.parseDouble(costThreshold);
                                    if (!isCostThresholdMet && !isCostNearAlertSent &&
                                            cumulativeCost >= (costDouble - 1) && cumulativeCost < costDouble) {
                                        String nearTimestamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                                                .format(new java.util.Date());
                                        String nearAlertMessage = "You are close to meeting your budget limit of ₱" + costThreshold +
                                                " (Current: ₱" + cumulativeCost + ") at " + nearTimestamp;
                                        TextView costTimeTextView = findViewById(R.id.cost_time);
                                        costTimeTextView.setText(nearAlertMessage);
                                        sendAlertToFirebase("Budget Near Limit", nearAlertMessage, nearTimestamp);
                                        isCostNearAlertSent = true;
                                    }
                                    if (cumulativeCost >= costDouble && !isCostThresholdMet) {
                                        String dateKey = dataSnapshot.getChildren().iterator().next().getKey();
                                        String hourKey = dataSnapshot.getChildren().iterator().next().getChildren().iterator().next().getKey();
                                        timestampCost = dateKey + " " + hourKey + ":00:00";
                                        alertMessageCost = "You have met your budget limit of ₱" + costThreshold + " at " + timestampCost;
                                        isCostThresholdMet = true;
                                    }

// ✅ Final messages
                                    if (isKwhThresholdMet) {
                                        sendAlertToFirebase("kWh Limit Met", alertMessageKwh, timestampKwh);
                                        TextView kwhTimeTextView = findViewById(R.id.kwh_time);
                                        kwhTimeTextView.setText("kWh Threshold met at: " + timestampKwh);
                                    } else if (!isKwhNearAlertSent) {
                                        TextView kwhTimeTextView = findViewById(R.id.kwh_time);
                                        kwhTimeTextView.setText("No kWh threshold met yet.");
                                    }

                                    if (isCostThresholdMet) {
                                        sendAlertToFirebase("Budget Limit Met", alertMessageCost, timestampCost);
                                        TextView costTimeTextView = findViewById(R.id.cost_time);
                                        costTimeTextView.setText("Cost Threshold met at: " + timestampCost);
                                    } else if (!isCostNearAlertSent) {
                                        TextView costTimeTextView = findViewById(R.id.cost_time);
                                        costTimeTextView.setText("No Cost threshold met yet.");
                                    }
                                }
                                @Override
                                public void onCancelled(DatabaseError databaseError) {
                                    Toast.makeText(AlertActivity.this, "Failed to fetch data", Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        Toast.makeText(AlertActivity.this, "Failed to fetch cost value", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Toast.makeText(AlertActivity.this, "Failed to fetch kwh value", Toast.LENGTH_SHORT).show();
            }
        });
    }
    private void sendAlertToFirebase(String alertType, String alertMessage, String timestamp) {
        // Reference to the counter value in Firebase
        DatabaseReference counterRef = db.child("alerts").child("counter");

        // Get the current counter value and increment it
        counterRef.runTransaction(new Transaction.Handler() {
            @Override
            public Transaction.Result doTransaction(MutableData currentData) {
                // If counter doesn't exist, start it at 1
                if (currentData.getValue() == null) {
                    currentData.setValue(1);  // Initialize the counter at 1
                }

                long newCounterValue = (long) currentData.getValue() + 1;  // Increment the counter
                currentData.setValue(newCounterValue);  // Set the new counter value
                return Transaction.success(currentData);  // Transaction success
            }

            @Override
            public void onComplete(DatabaseError databaseError, boolean committed, DataSnapshot currentData) {
                if (committed) {
                    long newAlertId = (long) currentData.getValue();  // Get the new incremented ID
                    // Now save the new alert with the new alert ID
                    db.child("alerts").child(String.valueOf(newAlertId))
                            .child("alert_type").setValue(alertType);
                    db.child("alerts").child(String.valueOf(newAlertId))
                            .child("message").setValue(alertMessage);
                    db.child("alerts").child(String.valueOf(newAlertId))
                            .child("created_at").setValue(timestamp);

                    // Optionally update the UI with a confirmation
                    Toast.makeText(AlertActivity.this, "Alert added with ID: " + newAlertId, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(AlertActivity.this, "Failed to update the counter", Toast.LENGTH_SHORT).show();
                }
            }
        });

}
    private void saveSetting(){
        String timestamp = getCurrentTimestamp();
        updateKwhValue(timestamp);
        updateCostValue(timestamp);
        fetchDataFromDatabase();
    }
    private String getCurrentTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        Date date = new Date();
        return sdf.format(date);  // Return the timestamp as a string
    }
    private void updateKwhValue(String timestamp) {
        String updateKwhValue = etPowerValue.getText().toString().trim();
        if (updateKwhValue.isEmpty()) {
            Toast.makeText(AlertActivity.this, "Put Kwh Value!", Toast.LENGTH_SHORT).show();
            return;
        }

        DatabaseReference deviceRef = db.child("threshold");

        deviceRef.child("kwh_value").setValue(updateKwhValue);
        deviceRef.child("time").setValue(timestamp)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(AlertActivity.this, "kwh value updated", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(AlertActivity.this, "Error updating kwh value", Toast.LENGTH_SHORT).show();
                });
    }
    private void updateCostValue(String timestamp) {
        String updateCostValue = etBudgetValue.getText().toString().trim();
        if (updateCostValue.isEmpty()) {
            Toast.makeText(AlertActivity.this, "Put Cost Value!", Toast.LENGTH_SHORT).show();
            return;
        }
        DatabaseReference deviceRef = db.child("threshold");

        deviceRef.child("cost_value").setValue(updateCostValue);
        deviceRef.child("time").setValue(timestamp)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(AlertActivity.this, "cost value updated", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(AlertActivity.this, "Error updating cost value", Toast.LENGTH_SHORT).show();
                });
    }
    private void fetchValue() {
        DatabaseReference kwhValue = db.child("threshold").child("kwh_value");
        DatabaseReference costValue = db.child("threshold").child("cost_value");

        // Fetch kwh_value
        kwhValue.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                String kwhValue = dataSnapshot.getValue(String.class);

                if (kwhValue != null) {
                    etPowerValue.setText(kwhValue);
                } else {
                    etPowerValue.setText("No value available");
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                // Handle error in fetching kwh value
                Toast.makeText(AlertActivity.this, "Error fetching kwh value", Toast.LENGTH_SHORT).show();
            }
        });

        // Fetch cost_value
        costValue.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                String costValue = dataSnapshot.getValue(String.class);

                if (costValue != null) {
                    etBudgetValue.setText(costValue);
                } else {
                    etBudgetValue.setText("No value available");
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                // Handle error in fetching cost value
                Toast.makeText(AlertActivity.this, "Error fetching cost value", Toast.LENGTH_SHORT).show();
            }
        });
    }


}