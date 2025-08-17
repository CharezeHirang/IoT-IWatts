package com.example.sampleiwatts;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class AlertsAdapter extends RecyclerView.Adapter<AlertsAdapter.VH> {
    private final List<AlertItem> data = new ArrayList<>();
    private final OnAlertClick onClick;

    interface OnAlertClick { void onClick(AlertItem item); }

    public AlertsAdapter(OnAlertClick onClick) { this.onClick = onClick; }

    public void submit(List<AlertItem> list){
        data.clear();
        data.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.row_alert, parent, false);
        return new VH(v);
    }

    @Override public void onBindViewHolder(@NonNull VH h, int pos) {
        AlertItem a = data.get(pos);
        h.title.setText(a.title);
        h.message.setText(a.message);
        h.time.setText(DateFormat.getDateTimeInstance().format(new Date(a.timestamp)));
        h.itemView.setAlpha(a.read ? 0.5f : 1f);
        h.itemView.setOnClickListener(v -> onClick.onClick(a));
    }

    @Override public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView title, message, time;
        VH(View v){ super(v);
            title = v.findViewById(R.id.title);
            message = v.findViewById(R.id.message);
            time = v.findViewById(R.id.time);
        }
    }
}

