package com.example.sampleiwatts;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class J_TipsActivity extends AppCompatActivity {

    ImageView icBack;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_jtips);

        icBack = findViewById(R.id.ic_back);
        icBack.setOnClickListener(v -> {
            Intent intent = new Intent(J_TipsActivity.this, TipsActivity.class);
            startActivity(intent);
            finish();
        });
    }
}