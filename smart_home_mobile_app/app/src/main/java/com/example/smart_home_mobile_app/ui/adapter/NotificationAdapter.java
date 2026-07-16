package com.example.smart_home_mobile_app.ui.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smart_home_mobile_app.R;
import com.example.smart_home_mobile_app.ui.notifications.NotificationItem;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NotificationAdapter
        extends RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder> {

    private final List<NotificationItem> items = new ArrayList<>();

    public void submitList(List<NotificationItem> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public NotificationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_notification, parent, false);
        return new NotificationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NotificationViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class NotificationViewHolder extends RecyclerView.ViewHolder {
        private final ImageView icon;
        private final TextView title;
        private final TextView description;
        private final TextView timestamp;

        NotificationViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.img_notification_type);
            title = itemView.findViewById(R.id.tv_notification_title);
            description = itemView.findViewById(R.id.tv_notification_description);
            timestamp = itemView.findViewById(R.id.tv_notification_time);
        }

        void bind(NotificationItem item) {
            Context context = itemView.getContext();
            boolean isAlert = item.kind == NotificationItem.Kind.SENSOR_ALERT;
            icon.setImageResource(isAlert ? R.drawable.ic_warning : R.drawable.ic_notification);
            icon.setColorFilter(context.getColor(
                    isAlert ? R.color.sh_danger : R.color.sh_accent));
            title.setText(item.title);
            description.setText(item.description);
            timestamp.setText(formatTimestamp(item.timestampEpochMs));
        }

        private String formatTimestamp(long epochMs) {
            if (epochMs <= 0L) return "Không rõ thời gian";
            SimpleDateFormat formatter =
                    new SimpleDateFormat("dd/MM/yyyy • HH:mm:ss", new Locale("vi", "VN"));
            return formatter.format(new Date(epochMs));
        }
    }
}
