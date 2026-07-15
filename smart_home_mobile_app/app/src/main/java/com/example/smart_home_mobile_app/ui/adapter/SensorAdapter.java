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

import java.util.ArrayList;
import java.util.List;

/**
 * Bind {@link SensorMetricItem} — KHÔNG cần biết nodeType thật, MainFragment đã
 * đóng gói sẵn (label, MetricReading, icon) cho từng thẻ cảm biến muốn hiển thị.
 */
public class SensorAdapter extends RecyclerView.Adapter<SensorAdapter.SensorViewHolder> {

    private final List<SensorMetricItem> items = new ArrayList<>();

    public void submitList(List<SensorMetricItem> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SensorViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_sensor_card, parent, false);
        return new SensorViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SensorViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class SensorViewHolder extends RecyclerView.ViewHolder {
        private final ImageView imgIcon;
        private final TextView tvValue;
        private final TextView tvLabel;
        private final TextView tvStatus;

        SensorViewHolder(@NonNull View itemView) {
            super(itemView);
            imgIcon = itemView.findViewById(R.id.img_sensor_icon);
            tvValue = itemView.findViewById(R.id.tv_sensor_value);
            tvLabel = itemView.findViewById(R.id.tv_sensor_label);
            tvStatus = itemView.findViewById(R.id.tv_sensor_status);
        }

        void bind(SensorMetricItem item) {
            Context context = itemView.getContext();

            tvValue.setText(item.displayValue());
            tvLabel.setText(item.label);
            imgIcon.setImageResource(item.iconRes);

            if (item.reading == null) {
                tvStatus.setText("Không có dữ liệu");
                tvStatus.setTextColor(context.getColor(R.color.sh_text_secondary));
            } else if (!item.reading.isValid()) {
                tvStatus.setText(item.reading.error != null ? item.reading.error : "Lỗi cảm biến");
                tvStatus.setTextColor(context.getColor(R.color.sh_danger));
            } else {
                // Không có ngưỡng cảnh báo (min/max) từ backend nên tạm coi mọi giá trị
                // hợp lệ là "Bình thường". Khi có ngưỡng thật, thêm so sánh tại đây.
                tvStatus.setText("Bình thường");
                tvStatus.setTextColor(context.getColor(R.color.sh_success));
            }
        }
    }
}