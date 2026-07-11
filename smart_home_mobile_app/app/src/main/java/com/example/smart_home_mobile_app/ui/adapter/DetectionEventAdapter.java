package com.example.smart_home_mobile_app.ui.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smart_home_mobile_app.R;
import com.example.smart_home_mobile_app.data.DetectionEvent;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Bind {@link DetectionEvent} thật. Model thật KHÔNG có field severity — bản cũ
 * tự tạo severity là sai. Ở đây suy ra "mức độ hiển thị" (màu sắc) từ className +
 * confidence thay vì severity.
 *
 * CLASS_HUMAN = "human" đã XÁC NHẬN qua FakeData.SAMPLE_DETECTION_EVENT.
 * CLASS_FALL là GIẢ ĐỊNH (chưa có event mẫu té ngã trong FakeData) — cần đối
 * chiếu lại giá trị className thật khi camera AI phát hiện té ngã.
 *
 * Nếu project đã có sẵn tiện ích {@code EventTimeFormat}, dùng nó thay cho
 * formatTime() bên dưới để đồng nhất định dạng thời gian toàn app.
 */
public class DetectionEventAdapter extends RecyclerView.Adapter<DetectionEventAdapter.EventViewHolder> {

    public static final String CLASS_HUMAN = "human"; // CONFIRMED
    public static final String CLASS_FALL = "fall";    // GIẢ ĐỊNH — cần đối chiếu

    private static final double HIGH_CONFIDENCE_THRESHOLD = 0.9;
    private static final double MEDIUM_CONFIDENCE_THRESHOLD = 0.7;

    private final List<DetectionEvent> items = new ArrayList<>();

    public void submitList(List<DetectionEvent> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    public void clear() {
        items.clear();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_detection, parent, false);
        return new EventViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class EventViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvType;
        private final TextView tvLocationTime;
        private final TextView tvSeverity;

        EventViewHolder(@NonNull View itemView) {
            super(itemView);
            tvType = itemView.findViewById(R.id.tv_event_type);
            tvLocationTime = itemView.findViewById(R.id.tv_event_location_time);
            tvSeverity = itemView.findViewById(R.id.tv_event_severity);
        }

        void bind(DetectionEvent item) {
            Context context = itemView.getContext();

            String typeLabel;
            if (CLASS_FALL.equals(item.className)) {
                typeLabel = "Phát hiện té ngã";
            } else if (CLASS_HUMAN.equals(item.className)) {
                typeLabel = "Phát hiện có người";
            } else {
                typeLabel = item.className; // className lạ: hiển thị nguyên bản
            }
            tvType.setText(typeLabel);
            tvLocationTime.setText(item.roomId + " • " + formatTime(item.timestampEpochMs));

            String label;
            int colorRes;
            if (item.confidence >= HIGH_CONFIDENCE_THRESHOLD) {
                label = "Khẩn cấp";
                colorRes = R.color.sh_danger;
            } else if (item.confidence >= MEDIUM_CONFIDENCE_THRESHOLD) {
                label = "Cảnh báo";
                colorRes = R.color.sh_warning;
            } else {
                label = "Thông tin";
                colorRes = R.color.sh_info;
            }
            tvSeverity.setText(label);
            tvSeverity.setTextColor(context.getColor(colorRes));
        }

        private String formatTime(long epochMs) {
            return new SimpleDateFormat("HH:mm, dd/MM", Locale.getDefault()).format(new Date(epochMs));
        }
    }
}