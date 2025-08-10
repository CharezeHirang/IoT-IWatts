package com.example.sampleiwatts;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;

public class AlertActivity extends AppCompatActivity {
    private TextInputEditText etPowerValue, etBudgetValue;
    private MaterialSwitch switchVoltage, switchSystemUpdates, switchPush;
    private ActivityResultLauncher<String> notifPermLauncher;
    private AlertSettingsStore store;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_alert);
        etPowerValue = findViewById(R.id.etPowerValue);
        etBudgetValue = findViewById(R.id.etBudgetValue);
        switchVoltage = findViewById(R.id.switchVoltage);
        switchSystemUpdates = findViewById(R.id.switchSystemUpdates);
        switchPush = findViewById(R.id.switchPush);
        MaterialButton btnSave = findViewById(R.id.btnSave);

        store = new AlertSettingsStore(this);
        loadSaved();

        btnSave.setOnClickListener(v -> {
            String wattsStr = textOf(etPowerValue);
            String budgetStr = textOf(etBudgetValue);

            Double watts = safeDouble(wattsStr);
            Double budget = safeDouble(budgetStr);

            if (watts == null) {
                toast("Please enter a power value (watts).");
                return;
            }
            if (budget == null) {
                toast("Please enter a budget amount (₱).");
                return;
            }

            AlertSettings settings = new AlertSettings(
                    watts,
                    budget,
                    switchVoltage.isChecked(),
                    switchSystemUpdates.isChecked(),
                    switchPush.isChecked()
            );
            store.save(settings);

            toast("Settings saved.");
            // Optional: immediate confirmation push (respects Push toggle)
            AlertNotifier.ensureChannel(this);
            AlertNotifier.notifyIfAllowed(this, settings,
                    "Settings Updated",
                    "We'll alert you based on your new thresholds.");
        });
        notifPermLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (!granted) toast("Notifications are disabled. You can enable them in Settings.");
                });

        switchPush.setOnCheckedChangeListener((btn, checked) -> {
            if (checked && android.os.Build.VERSION.SDK_INT >= 33) {
                if (ActivityCompat.checkSelfPermission(this,
                        Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                }
            }
        });
    }

    private void loadSaved() {
        AlertSettings s = store.load();
        if (s.powerThresholdWatts != null) etPowerValue.setText(trimZeros(s.powerThresholdWatts));
        if (s.budgetPhp != null) etBudgetValue.setText(trimZeros(s.budgetPhp));
        switchVoltage.setChecked(s.alertVoltageFluctuations);
        switchSystemUpdates.setChecked(s.alertSystemUpdates);
        switchPush.setChecked(s.pushEnabled);
    }

    private static String textOf(TextInputEditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }

    private static Double safeDouble(String s) {
        if (TextUtils.isEmpty(s)) return null;
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return null; }
    }

    private static String trimZeros(Double d) {
        String t = String.valueOf(d);
        return t.endsWith(".0") ? t.substring(0, t.length() - 2) : t;
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }


}

