package com.example.sampleiwatts;

import android.os.Bundle;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class NotificationActivity extends AppCompatActivity {


    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Build UI in code so it can show many notifications dynamically
        ScrollView scrollView = new ScrollView(this);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(pad, pad, pad, pad);
        scrollView.addView(container, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scrollView);

        // If a single alert was passed, add it on top first
        String title = getIntent().getStringExtra("alert_title");
        String message = getIntent().getStringExtra("alert_message");
        String time = getIntent().getStringExtra("alert_time");
        if (title != null || message != null || time != null) {
            container.addView(buildAlertView(container, title, message, time));
        }

        // Then stream recent alerts from Firebase to show multiple entries
        DatabaseReference alertsRef = FirebaseDatabase.getInstance().getReference().child("alerts");
        alertsRef.limitToLast(50).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                container.removeAllViews();
                for (DataSnapshot alert : snapshot.getChildren()) {
                    String t = valueAsString(alert.child("title").getValue());
                    String m = valueAsString(alert.child("message").getValue());
                    String ti = valueAsString(alert.child("time").getValue());
                    container.addView(buildAlertView(container, t, m, ti));
                }
                // If there was a direct intent message and it's not in DB yet, keep it visible
                if (title != null || message != null || time != null) {
                    container.addView(buildAlertView(container, title, message, time), 0);
                }
            }
            @Override public void onCancelled(DatabaseError error) { }
        });
    }

    private LinearLayout buildAlertView(ViewGroup parent, String title, String message, String time) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (12 * getResources().getDisplayMetrics().density);
        card.setPadding(pad, pad, pad, pad);

        // Title
        TextView tvT = new TextView(this);
        tvT.setText(title != null ? title : "Notification");
        tvT.setTypeface(Typeface.DEFAULT_BOLD);
        tvT.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        card.addView(tvT, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Message
        TextView tvM = new TextView(this);
        tvM.setText(message != null ? message : "");
        tvM.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        LinearLayout.LayoutParams lpM = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpM.topMargin = (int) (6 * getResources().getDisplayMetrics().density);
        card.addView(tvM, lpM);

        // Time
        TextView tvTi = new TextView(this);
        tvTi.setText(time != null ? time : "");
        tvTi.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        LinearLayout.LayoutParams lpTi = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpTi.topMargin = (int) (4 * getResources().getDisplayMetrics().density);
        card.addView(tvTi, lpTi);

        // Card spacing
        LinearLayout.LayoutParams lpCard = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpCard.bottomMargin = (int) (10 * getResources().getDisplayMetrics().density);
        card.setLayoutParams(lpCard);
        card.setBackgroundResource(R.drawable.bg_yellowish); // optional if you have a rounded background

        return card;
    }

    private String valueAsString(Object v) {
        return v == null ? "" : String.valueOf(v);
    }
}