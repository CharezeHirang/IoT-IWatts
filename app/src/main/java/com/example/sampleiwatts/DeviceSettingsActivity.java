package com.example.sampleiwatts;

import android.app.AlertDialog;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
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

public class DeviceSettingsActivity extends AppCompatActivity {
    private DatabaseReference db;  // Realtime Database reference
    private EditText deviceNameEditText;
    ImageView ivBatteryImage;
    private TextView activationTimeTextView, tvBatteryLife;
    private Button btnDisconnect;
    private boolean isDeviceNameEditable = false;
    private String originalDeviceName = "";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_settings);

        db = FirebaseDatabase.getInstance().getReference();
        ivBatteryImage = findViewById(R.id.ivBatteryImage);
        deviceNameEditText = findViewById(R.id.deviceName);
        activationTimeTextView = findViewById(R.id.activationTime);
        tvBatteryLife = findViewById(R.id.tvBatteryLife);
        btnDisconnect = findViewById(R.id.btnDisconnect);
        
        // Set up click listeners
        setupClickListeners();
        
        fetchDeviceName();
        fetchBatteryLife();
        fetchActivationTime();
    }
    
    private void setupClickListeners() {
        // Initialize EditText as non-editable (like dashboard)
        deviceNameEditText.setFocusable(false);
        deviceNameEditText.setClickable(false);
        deviceNameEditText.setLongClickable(false);
        deviceNameEditText.setCursorVisible(false);
        
        // Setup touch listener for EditText to detect clicks on edit icon (dashboard style)
        setupEditTextTouchListener(deviceNameEditText);
        
        // Disconnect button click listener with confirmation
        btnDisconnect.setOnClickListener(v -> showDisconnectConfirmation());
    }
    
    // Setup touch listener for EditText to detect clicks on edit icon (dashboard style)
    private void setupEditTextTouchListener(EditText editText) {
        editText.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    Drawable drawableEnd = editText.getCompoundDrawables()[2]; // Get drawableEnd
                    if (drawableEnd != null) {
                        int drawableWidth = drawableEnd.getIntrinsicWidth();
                        int editTextWidth = editText.getWidth();
                        int drawableX = editTextWidth - editText.getPaddingRight() - drawableWidth;
                        
                        if (event.getX() >= drawableX) {
                            // Edit icon was clicked
                            enableEditing(editText);
                            return true;
                        }
                    }
                }
                return false;
            }
        });
    }

    // Enable editing for the device name EditText (dashboard style)
    private void enableEditing(EditText editText) {
        editText.setFocusable(true);
        editText.setFocusableInTouchMode(true);
        editText.setClickable(true);
        editText.setCursorVisible(true);
        editText.requestFocus();
        
        isDeviceNameEditable = true;
        originalDeviceName = editText.getText().toString();
        
        // Add OnEditorActionListener to handle Enter key (dashboard style)
        editText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || 
                (event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER)) {
                // Save the changes
                updateDeviceName();
                return true;
            }
            return false;
        });
    }

    // Disable editing for the device name EditText (dashboard style)
    private void disableEditing(EditText editText) {
        editText.setFocusable(false);
        editText.setClickable(false);
        editText.setLongClickable(false);
        editText.setCursorVisible(false);
        editText.clearFocus();
        editText.setOnEditorActionListener(null);
        
        isDeviceNameEditable = false;
    }
    
    private void showDisconnectConfirmation() {
        // Clear focus from EditText when showing alert dialog
        if (isDeviceNameEditable) {
            deviceNameEditText.clearFocus();
            disableEditing(deviceNameEditText);
        }
        
        new AlertDialog.Builder(this)
                .setTitle("Disconnect Device")
                .setMessage("Are you sure you want to disconnect from the IWatts device?\n\n" +
                           "⚠️ WARNING: If you disconnect, you won't be able to read data in the app!")
                .setPositiveButton("DISCONNECT", (dialog, which) -> {
                    // Perform disconnect action here
                    disconnectDevice();
                })
                .setNegativeButton("CANCEL", (dialog, which) -> {
                    // Do nothing, just close the dialog
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }
    
    private void disconnectDevice() {
        // TODO: Implement actual disconnect logic here
        // This might involve clearing local data, stopping services, etc.
        Toast.makeText(this, "Device disconnected. You can no longer read data.", Toast.LENGTH_LONG).show();
        
        // For now, just show a message
        // You might want to navigate back to a setup screen or clear the app data
    }
    private void fetchBatteryLife() {
        // Reference to the "logs" node to get the most recent battery percentage across ALL dates
        DatabaseReference logsRef = db.child("logs");

        logsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                int batteryPercentage = 0;
                boolean isCharging = false;

                // Prefer the entry with the largest numeric timestamp (timestamp/created_at/ts).
                // If no numeric timestamp is present anywhere, fall back to the newest push key globally.
                String selectedDateKey = null;
                String selectedPushKey = null;
                DataSnapshot selectedSnapshot = null;
                long bestTimestamp = Long.MIN_VALUE;
                boolean usedTimestamp = false;

                for (DataSnapshot dateSnapshot : dataSnapshot.getChildren()) {
                    for (DataSnapshot logSnapshot : dateSnapshot.getChildren()) {
                        String pushKey = logSnapshot.getKey();
                        if (pushKey == null) continue;

                        // Try to read a timestamp from common field names
                        Long ts = extractTimestamp(logSnapshot);
                        if (ts != null) {
                            if (!usedTimestamp || ts > bestTimestamp) {
                                bestTimestamp = ts;
                                selectedSnapshot = logSnapshot;
                                selectedDateKey = dateSnapshot.getKey();
                                selectedPushKey = pushKey;
                                usedTimestamp = true;
                            }
                        } else if (!usedTimestamp) {
                            // Only use push key ordering if we haven't found any timestamps yet
                            if (selectedPushKey == null || pushKey.compareTo(selectedPushKey) > 0) {
                                selectedPushKey = pushKey;
                                selectedSnapshot = logSnapshot;
                                selectedDateKey = dateSnapshot.getKey();
                            }
                        }
                    }
                }

                if (selectedSnapshot != null) {
                    Object vbatPercentObj = selectedSnapshot.child("Vbat_percent").getValue();
                    Object chargingObj = selectedSnapshot.child("Charging").getValue();

                    if (vbatPercentObj instanceof Number) {
                        batteryPercentage = ((Number) vbatPercentObj).intValue();
                    } else if (vbatPercentObj instanceof String) {
                        try { batteryPercentage = Integer.parseInt((String) vbatPercentObj); } catch (NumberFormatException ignored) { }
                    }

                    if (chargingObj instanceof Boolean) {
                        isCharging = (Boolean) chargingObj;
                    } else if (chargingObj instanceof String) {
                        isCharging = Boolean.parseBoolean((String) chargingObj);
                    }

                    Log.d("BatteryLifeSelected", "date=" + selectedDateKey + ", key=" + selectedPushKey + ", pct=" + batteryPercentage + ", charging=" + isCharging + ", usedTimestamp=" + usedTimestamp + (usedTimestamp ? (", ts=" + bestTimestamp) : ""));
                }

                if (selectedSnapshot != null) {
                    String displayText = isCharging ? "Charging" : (batteryPercentage + "%");

                    if (isCharging) {
                        tvBatteryLife.setText(displayText);
                        tvBatteryLife.setTextSize(17); // Set text size to 17sp for "Charging"
                        ivBatteryImage.setImageResource(R.drawable.ic_battery10);
                    } else if (batteryPercentage >= 95) {
                        tvBatteryLife.setText(displayText);
                        ivBatteryImage.setImageResource(R.drawable.ic_battery1);
                    } else if (batteryPercentage >= 70) {
                        tvBatteryLife.setText(displayText);
                        ivBatteryImage.setImageResource(R.drawable.ic_battery2);
                    } else if (batteryPercentage >= 55) {
                        tvBatteryLife.setText(displayText);
                        ivBatteryImage.setImageResource(R.drawable.ic_battery3);
                    } else if (batteryPercentage >= 40) {
                        tvBatteryLife.setText(displayText);
                        ivBatteryImage.setImageResource(R.drawable.ic_battery4);
                    } else if (batteryPercentage >= 25) {
                        tvBatteryLife.setText(displayText);
                        ivBatteryImage.setImageResource(R.drawable.ic_battery5);
                    } else if (batteryPercentage >= 10) {
                        tvBatteryLife.setText(displayText);
                        ivBatteryImage.setImageResource(R.drawable.ic_battery6);
                    } else if (batteryPercentage >= 5) {
                        tvBatteryLife.setText(displayText);
                        ivBatteryImage.setImageResource(R.drawable.ic_battery7);
                    } else {
                        tvBatteryLife.setText(displayText);
                        ivBatteryImage.setImageResource(R.drawable.ic_battery8);
                    }
                } else {
                    tvBatteryLife.setText("Battery Life not available");
                    ivBatteryImage.setImageResource(R.drawable.ic_battery9);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Toast.makeText(DeviceSettingsActivity.this, "Error fetching battery life", Toast.LENGTH_SHORT).show();
            }
        });
    }
    private Long extractTimestamp(DataSnapshot logSnapshot) {
        // Try common field names for timestamps and coerce to long
        Object v;
        String[] keys = new String[] { "timestamp", "created_at", "createdAt", "ts", "time" };
        for (String k : keys) {
            v = logSnapshot.child(k).getValue();
            if (v instanceof Number) return ((Number) v).longValue();
            if (v instanceof String) {
                try {
                    return Long.parseLong((String) v);
                } catch (NumberFormatException ignored) { }
            }
        }
        return null;
    }
    private void fetchDeviceName() {
        // Reference to the "device_name" field under the "system_settings" node
        DatabaseReference deviceNameRef = db.child("system_settings").child("device_name");

        // Add a listener to fetch the device name from Firebase
        deviceNameRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                // Get the device name from the snapshot
                String deviceName = dataSnapshot.getValue(String.class);

                // Set the device name in the EditText
                if (deviceName != null) {
                    deviceNameEditText.setText(deviceName);
                } else {
                    deviceNameEditText.setText("No device name available");
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                // Handle any error in retrieving the data
                Toast.makeText(DeviceSettingsActivity.this, "Error fetching device name", Toast.LENGTH_SHORT).show();
            }
        });
    }
    private void updateDeviceName() {
        String updatedDeviceName = deviceNameEditText.getText().toString().trim();

        // Ensure the device name is not empty
        if (updatedDeviceName.isEmpty()) {
            Toast.makeText(DeviceSettingsActivity.this, "Device name cannot be empty!", Toast.LENGTH_SHORT).show();
            // Restore original name if empty
            deviceNameEditText.setText(originalDeviceName);
            disableEditing(deviceNameEditText);
            return;
        }
        
        if (updatedDeviceName.equals(originalDeviceName)) {
            // No changes made, just disable editing
            disableEditing(deviceNameEditText);
            return;
        }

        // Reference to the "device_name" field under the "system_settings" node
        DatabaseReference deviceRef = db.child("system_settings");

        // Update the device name in Realtime Database
        deviceRef.child("device_name").setValue(updatedDeviceName)
                .addOnSuccessListener(aVoid -> {
                    // Successfully updated the device name
                    Toast.makeText(DeviceSettingsActivity.this, "Device name updated successfully!", Toast.LENGTH_SHORT).show();
                    disableEditing(deviceNameEditText); // Exit editing mode after successful save
                })
                .addOnFailureListener(e -> {
                    // Handle the error
                    Toast.makeText(DeviceSettingsActivity.this, "Error updating device name", Toast.LENGTH_SHORT).show();
                    // Restore original name on error
                    deviceNameEditText.setText(originalDeviceName);
                    disableEditing(deviceNameEditText);
                });
    }
    private void fetchActivationTime() {
        // Reference to the "activation_time" under the "system_settings" node
        DatabaseReference activationTimeRef = db.child("system_settings").child("installation_date");

        // Add a listener to fetch the activation time from Firebase
        activationTimeRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                // Get the activation time from the snapshot
                String activationTime = dataSnapshot.getValue(String.class);

                // Set the activation time in the TextView
                if (activationTime != null) {
                    activationTimeTextView.setText(activationTime);
                } else {
                    activationTimeTextView.setText("No installation date available");
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                // Handle any error in retrieving the data
                Toast.makeText(DeviceSettingsActivity.this, "Error fetching installation date", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
