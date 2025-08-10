package com.example.sampleiwatts;

import android.content.Intent;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class TipsActivity extends AppCompatActivity {

    ImageView icBack;
    private FrameLayout[] categories;
    private int currentIndex = 0;
    private float downX;
    Button  btnCategory1,  btnCategory2,  btnCategory3,  btnCategory4,  btnCategory5,  btnCategory6,  btnCategory7,  btnCategory8,  btnCategory9,  btnCategory10;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_tips);

        icBack = findViewById(R.id.ic_back);
        icBack.setOnClickListener(v -> {
            Intent intent = new Intent(TipsActivity.this, SettingsActivity.class);
            startActivity(intent);
            finish();
        });
        btnCategory1 = findViewById(R.id.category1);
        btnCategory1.setOnClickListener(v -> {
            Intent intent = new Intent(TipsActivity.this, A_TipsActivity.class);
            startActivity(intent);
            finish();
        });
        categories = new FrameLayout[]{
                findViewById(R.id.categoryLayout1),
                findViewById(R.id.categoryLayout2),
                findViewById(R.id.categoryLayout3),
                findViewById(R.id.categoryLayout4),
                findViewById(R.id.categoryLayout5),
                findViewById(R.id.categoryLayout6),
                findViewById(R.id.categoryLayout7),
                findViewById(R.id.categoryLayout8),
                findViewById(R.id.categoryLayout9),
                findViewById(R.id.categoryLayout10)
        };

        // Touch listener on the whole screen
        findViewById(R.id.main).setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getX();
                    return true;

                case MotionEvent.ACTION_UP:
                    float upX = event.getX();
                    float deltaX = downX - upX;

                    if (Math.abs(deltaX) > 150) { // Minimum swipe distance
                        if (deltaX > 0) {
                            // Swipe Left → Next category
                            showNextCategory();
                        } else {
                            // Swipe Right → Previous category
                            showPreviousCategory();
                        }
                    }
                    return true;
            }
            return false;
        });



        btnCategory1 = findViewById(R.id.category1);
        btnCategory2 = findViewById(R.id.category2);
        btnCategory3 = findViewById(R.id.category3);
        btnCategory4 = findViewById(R.id.category4);
        btnCategory5 = findViewById(R.id.category5);
        btnCategory6 = findViewById(R.id.category6);
        btnCategory7 = findViewById(R.id.category7);
        btnCategory8 = findViewById(R.id.category8);
        btnCategory9 = findViewById(R.id.category9);
        btnCategory10 = findViewById(R.id.category10);

    }

    private void showNextCategory() {
        categories[currentIndex].setVisibility(View.GONE);
        currentIndex = (currentIndex + 1) % categories.length; // Loops back to 0
        categories[currentIndex].setVisibility(View.VISIBLE);
    }

    private void showPreviousCategory() {
        categories[currentIndex].setVisibility(View.GONE);
        currentIndex = (currentIndex - 1 + categories.length) % categories.length; // Loops back to last
        categories[currentIndex].setVisibility(View.VISIBLE);
    }
}