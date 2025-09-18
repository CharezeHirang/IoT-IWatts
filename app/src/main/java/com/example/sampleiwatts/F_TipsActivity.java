package com.example.sampleiwatts;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class F_TipsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_ftips);
        ImageView icBack = findViewById(R.id.ic_back);
        icBack.setOnClickListener(v -> {
            Intent intent = new Intent(F_TipsActivity.this, TipsActivity.class);
            startActivity(intent);
            finish();
        });
    }
}