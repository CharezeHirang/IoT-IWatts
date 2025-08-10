package com.example.sampleiwatts;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.VH> {

    interface Listener {
        void onDelete(long id);
    }

    private final List<NotificationLogEntry> items = new ArrayList<>();
    private Listener listener;

    void setListener(Listener l) { this.listener = l; }

    void submit(List<NotificationLogEntry> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    void submitFiltered(List<NotificationLogEntry> all, String category) {
        items.clear();
        if (category == null || category.equals("All")) {
            items.addAll(all);
        } else {
            for (NotificationLogEntry e : all) {
                if (category.equalsIgnoreCase(e.category)) items.add(e);
            }
        }
        notifyDataSetChanged();
    }

    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_notification, parent, false);  // <-- not simple_list_item_1
        return new VH(v);
    }


    @Override public void onBindViewHolder(@NonNull VH h, int pos) {
        NotificationLogEntry e = items.get(pos);
        h.tvTitle.setText(e.title);
        h.tvMsg.setText(e.message);

        // Dim read items slightly
        h.itemView.setAlpha(e.read ? 0.7f : 1f);

        h.btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDelete(e.id);
        });
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final TextView tvTitle, tvMsg;
        final ImageButton btnDelete;
        VH(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvMsg = itemView.findViewById(R.id.tvMessage);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}
