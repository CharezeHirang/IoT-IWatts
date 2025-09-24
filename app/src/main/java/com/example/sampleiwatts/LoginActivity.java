package com.example.sampleiwatts;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class LoginActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "IWattsPrefs";
    private static final String KEY_IS_LOGGED_IN = "isLoggedIn";
    private static final String HARDWARE_AP_SSID = "PowerLogger"; // Hardware device AP name
    private static final String DEFAULT_USERNAME = "PowerLogger"; // Default AP username
    private static final String DEFAULT_PASSWORD = "admin123"; // Default AP password
    private static final int LOCATION_PERMISSION_REQUEST = 1001;

    private EditText usernameEditText;
    private EditText passwordEditText;
    private Button loginButton;
    private Button setupButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Check if user is already logged in
        if (isUserLoggedIn()) {
            navigateToHomepage();
            return;
        }

        setContentView(R.layout.activity_login);

        initializeViews();
        setupClickListeners();

        // Request location permission first, then check WiFi
        requestLocationPermission();
    }

    private void requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST);
        } else {
            // Permission already granted
            checkWiFiConnection();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted
                checkWiFiConnection();
            } else {
                // Permission denied
                Toast.makeText(this, "Location permission needed to detect WiFi network", Toast.LENGTH_LONG).show();
                // Still try to check WiFi, but it might not work
                checkWiFiConnection();
            }
        }
    }

    private void initializeViews() {
        usernameEditText = findViewById(R.id.usernameEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        loginButton = findViewById(R.id.loginButton);
        setupButton = findViewById(R.id.setupButton);

        // Don't pre-fill credentials - let users enter them manually
    }

    private void setupClickListeners() {
        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performLogin();
            }
        });

        setupButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSetupActivity();
            }
        });
    }

    private void performLogin() {
        String username = usernameEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter both username and password", Toast.LENGTH_SHORT).show();
            return;
        }

        // Validate credentials (should match hardware AP credentials)
        if (username.equals(DEFAULT_USERNAME) && password.equals(DEFAULT_PASSWORD)) {
            // Save login state
            saveLoginState(true);

            Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show();
            navigateToHomepage();
        } else {
            Toast.makeText(this, "Invalid credentials. Use hardware device AP credentials.", Toast.LENGTH_LONG).show();
        }
    }

    private void checkWiFiConnection() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        if (wifiManager != null && wifiManager.isWifiEnabled()) {
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            String ssid = wifiInfo.getSSID();

            // Remove quotes from SSID if present
            if (ssid != null) {
                ssid = ssid.replace("\"", "");
                ssid = ssid.trim();
            }

            // Show setup button only when connected to hardware device AP
            if (HARDWARE_AP_SSID.equals(ssid)) {
                setupButton.setVisibility(View.VISIBLE);
                Toast.makeText(this, "✓ Connected to PowerLogger! Ready to configure WiFi.", Toast.LENGTH_LONG).show();
            } else {
                setupButton.setVisibility(View.GONE);
            }
        } else {
            setupButton.setVisibility(View.GONE);
        }
    }

    private void openSetupActivity() {
        Intent intent = new Intent(LoginActivity.this, HardwareSetupActivity.class);
        startActivity(intent);
    }

    private boolean isUserLoggedIn() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false);
    }

    private void saveLoginState(boolean isLoggedIn) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(KEY_IS_LOGGED_IN, isLoggedIn);
        editor.apply();
    }

    private void navigateToHomepage() {
        Intent intent = new Intent(this, DashboardActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Recheck WiFi connection when activity resumes
        checkWiFiConnection();
    }

    // Public method to logout (called from settings)
    public static void logout(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(KEY_IS_LOGGED_IN, false);
        editor.apply();

        // Navigate back to login
        Intent intent = new Intent(context, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}