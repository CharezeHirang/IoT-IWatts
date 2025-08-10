package com.example.sampleiwatts;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.List;

public class NotificationActivity extends AppCompatActivity {

    private NotificationAdapter adapter;
    private List<NotificationLogEntry> all;
    private ChipGroup chipGroup;
    private TextView btnMarkAll, btnClearAll;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_notification);
        RecyclerView rv = findViewById(R.id.rvNotifications);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new NotificationAdapter();
        rv.setAdapter(adapter);


        adapter.submit(NotificationLogger.getAll(this));
        chipGroup = findViewById(R.id.chipGroup);
        btnMarkAll = findViewById(R.id.btnMarkAll);
        btnClearAll = findViewById(R.id.btnClearAll);

        adapter.setListener(id -> {
            NotificationLogger.removeById(this, id);
            reload(true);
        });

        btnMarkAll.setOnClickListener(v -> {
            NotificationLogger.setAllRead(this);
            reload(false);
        });

        btnClearAll.setOnClickListener(v -> {
            NotificationLogger.clear(this);
            reload(false);
        });

        chipGroup.setOnCheckedStateChangeListener((group, ids) -> applyFilter());
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload(false);
    }

    private void reload(boolean keepFilter) {
        all = NotificationLogger.getAll(this);
        if (keepFilter) applyFilter();
        else adapter.submit(all);
    }

    private void applyFilter() {
        String category = "All";
        int id = chipGroup.getCheckedChipId();
        if (id != View.NO_ID) {
            Chip c = findViewById(id);
            category = c.getText().toString();
        }
        adapter.submitFiltered(all, category);
    }
}