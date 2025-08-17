package com.example.sampleiwatts;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NotificationActivity extends AppCompatActivity {

    private RecyclerView rv;
    private AlertsAdapter adapter;

    private DatabaseReference alertsMonthRef;
    private ValueEventListener monthListener;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification);
        findViewById(R.id.btnMarkAll).setOnClickListener(v -> {
            rv.post(() -> { for (int i=0;i<rv.getChildCount();i++) rv.getChildAt(i).findViewById(R.id.recycler).setBackgroundResource(R.drawable.bg_yellowish); });

            alertsMonthRef.get().addOnSuccessListener(snap -> {
                for (DataSnapshot c : snap.getChildren()) c.getRef().child("read").setValue(true);
            });
        });

        findViewById(R.id.btnClearAll).setOnClickListener(v -> {
            // WARNING: destructive
            alertsMonthRef.removeValue();
        });

        rv = findViewById(R.id.recycler);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AlertsAdapter(this::onAlertTap);
        rv.setAdapter(adapter);

        String uid = getUserId();
        String yyyy = new SimpleDateFormat("yyyy", Locale.getDefault()).format(new Date());
        String MM   = new SimpleDateFormat("MM",   Locale.getDefault()).format(new Date());

        alertsMonthRef = FirebaseDatabase.getInstance()
                .getReference("alerts").child(uid).child(yyyy).child(MM);

        // Query latest N (e.g., 200) and keep it sorted by timestamp
        Query q = alertsMonthRef.orderByChild("timestamp").limitToLast(200);

        monthListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snap) {
                List<AlertItem> list = new ArrayList<>();
                for (DataSnapshot child : snap.getChildren()) {
                    AlertItem a = child.getValue(AlertItem.class);
                    if (a == null) continue;
                    a.id = child.getKey();
                    list.add(a);
                }
                // reverse so newest first (limitToLast returns ascending by timestamp)
                Collections.sort(list, (a, b) -> Long.compare(b.timestamp, a.timestamp));
                adapter.submit(list);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { /* show empty/error state if needed */ }
        };
    }

    @Override protected void onStart() {
        super.onStart();
        if (alertsMonthRef != null && monthListener != null) {
            alertsMonthRef.orderByChild("timestamp").limitToLast(200).addValueEventListener(monthListener);
        }
    }

    @Override protected void onStop() {
        super.onStop();
        if (alertsMonthRef != null && monthListener != null) {
            alertsMonthRef.removeEventListener(monthListener);
        }
    }

    private void onAlertTap(AlertItem item) {
        // Mark as read in Firebase + open details if you have a detail screen
        alertsMonthRef.child(item.id).child("read").setValue(true);
        // Optional: open a details dialog/activity
        new AlertDialog.Builder(this)
                .setTitle(item.title)
                .setMessage(item.message)
                .setPositiveButton("OK", null)
                .show();
    }

    private String getUserId() {
        // Replace with FirebaseAuth if you use it
        return "device-001";
    }
}
