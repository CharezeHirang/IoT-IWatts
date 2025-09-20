package com.example.sampleiwatts;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class SettingsActivity extends AppCompatActivity {

    Button btnDeviceSettings, btnNotification, btnAlert, btnTips, btnAboutUs, btnLogout;
    ImageView icBack;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_settings);

        icBack = findViewById(R.id.ic_back);
        icBack.setOnClickListener(v -> {
            Intent intent = new Intent(SettingsActivity.this, DashboardActivity.class);
            startActivity(intent);
            finish();
        });
        btnDeviceSettings = findViewById(R.id.btn_device_settings);
        btnDeviceSettings. setOnClickListener(v -> {
            Intent intent = new Intent(SettingsActivity.this, DeviceSettingsActivity.class);
            startActivity(intent);
            finish();
        });
        btnNotification = findViewById(R.id.btn_notification);
        btnNotification. setOnClickListener(v -> {
            Intent intent = new Intent(SettingsActivity.this, NotificationActivity.class);
            startActivity(intent);
            finish();
        });

        btnAlert = findViewById(R.id.btn_alert);
        btnAlert. setOnClickListener(v -> {
            Intent intent = new Intent(SettingsActivity.this, AlertActivity.class);
            startActivity(intent);
            finish();
        });

        btnTips = findViewById(R.id.btn_tips);
        btnTips.setOnClickListener(v -> {
            Intent intent = new Intent(SettingsActivity.this, TipsActivity.class);
            startActivity(intent);
            finish();
        });

        btnAboutUs = findViewById(R.id.btn_about);
        btnAboutUs.setOnClickListener(v -> {
            Intent intent = new Intent(SettingsActivity.this, AboutUsActivity.class);
            startActivity(intent);
            finish();
        });

        btnLogout = findViewById(R.id.btn_logout);
        btnLogout.setOnClickListener(v -> {
            showLogoutConfirmation();
        });

    }
    private void showLogoutConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout? You'll need to login again next time you open the app.")
                .setPositiveButton("Logout", (dialog, which) -> {
                    performLogout();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performLogout() {
        // Show logout message
        Toast.makeText(this, "Logging out...", Toast.LENGTH_SHORT).show();

        // Call the static logout method from LoginActivity
        LoginActivity.logout(this);

        // This activity will be finished by the logout method
        finish();
    }
}