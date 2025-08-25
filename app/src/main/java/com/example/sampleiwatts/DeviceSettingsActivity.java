package com.example.sampleiwatts;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
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

    private TextView activationTimeTextView, batteryTextView;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_device_settings);

        db = FirebaseDatabase.getInstance().getReference();

        deviceNameEditText = findViewById(R.id.deviceName);
        activationTimeTextView = findViewById(R.id.activationTime);
        batteryTextView = findViewById(R.id.batteryText);
        fetchDeviceName();

        fetchBatteryLife();
        findViewById(R.id.btnSave).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateDeviceName();
            }
        });
        fetchActivationTime();
    }
    private void fetchBatteryLife() {
        // Reference to the "device_name" field under the "system_settings" node
        DatabaseReference batteryLifeRef = db.child("system_settings").child("battery_life");

        // Add a listener to fetch the device name from Firebase
        batteryLifeRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                String batteryLife = dataSnapshot.getValue(String.class);

                if (batteryLife != null) {
                    batteryTextView.setText(batteryLife);
                } else {
                    batteryTextView.setText("Battery Life not available");
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                // Handle any error in retrieving the data
                Toast.makeText(DeviceSettingsActivity.this, "Error fetching battery life", Toast.LENGTH_SHORT).show();
            }
        });
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
            return;
        }

        // Reference to the "device_name" field under the "system_settings" node
        DatabaseReference deviceRef = db.child("system_settings");

        // Update the device name in Realtime Database
        deviceRef.child("device_name").setValue(updatedDeviceName)
                .addOnSuccessListener(aVoid -> {
                    // Successfully updated the device name
                    Toast.makeText(DeviceSettingsActivity.this, "Device name updated", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    // Handle the error
                    Toast.makeText(DeviceSettingsActivity.this, "Error updating device name", Toast.LENGTH_SHORT).show();
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
