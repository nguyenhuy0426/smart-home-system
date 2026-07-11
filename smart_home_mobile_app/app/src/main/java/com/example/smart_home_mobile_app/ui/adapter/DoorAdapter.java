package com.example.smart_home_mobile_app.ui.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smart_home_mobile_app.R;
import com.example.smart_home_mobile_app.data.MetricReading;
import com.example.smart_home_mobile_app.data.NodeSummary;
import com.example.smart_home_mobile_app.data.TelemetryReading;

import java.util.ArrayList;
import java.util.List;

/**
 * Bind {@link NodeSummary} cho các node khoá cửa.
 *
 * QUAN TRỌNG: NodeSummary.status là tình trạng KẾT NỐI ("online"/"offline"),
 * KHÔNG PHẢI trạng thái khoá/mở — đây là điểm đã sửa lại so với bản cũ (bản cũ
 * giả định sai rằng status = locked/unlocked). Trạng thái khoá/mở thật hiện chưa
 * xác nhận được vì FakeData.java chưa có node mẫu kiểu khoá cửa; tạm thời đọc từ
 * metric "locked" trong latestReading() nếu backend cung cấp (METRIC_LOCKED bên
 * dưới là GIẢ ĐỊNH, cần đối chiếu lại khi có node khoá cửa mẫu thật).
 */
public class DoorAdapter extends RecyclerView.Adapter<DoorAdapter.DoorViewHolder> {

    // ----- GIẢ ĐỊNH: cần đối chiếu khi có node khoá cửa mẫu thật -----
    private static final String METRIC_LOCKED = "locked";
    private static final String METRIC_FINGERPRINT_OK = "fingerprint_ok";
    private static final String METRIC_CARD_OK = "card_ok";
    private static final String METRIC_BATTERY = "battery";
    // ----- CONFIRMED (từ SmartHomeAppController.ACCESS_COMMANDS) -----
    public static final String ACTION_UNLOCK = "unlock";
    public static final String ACTION_OPEN_DOOR = "open_door";
    // ----- GIẢ ĐỊNH: chưa xác nhận tên action để khoá lại -----
    public static final String ACTION_LOCK = "lock";

    public interface OnLockActionListener {
        void onLockAction(NodeSummary node, String action);
    }

    private final List<NodeSummary> items = new ArrayList<>();
    private final OnLockActionListener listener;

    public DoorAdapter(OnLockActionListener listener) {
        this.listener = listener;
    }

    public void submitList(List<NodeSummary> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public DoorViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_door_status_card, parent, false);
        return new DoorViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DoorViewHolder holder, int position) {
        holder.bind(items.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class DoorViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvName;
        private final TextView tvStatus;
        private final TextView tvFingerprint;
        private final TextView tvCard;
        private final TextView tvBattery;

        DoorViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_door_name);
            tvStatus = itemView.findViewById(R.id.tv_door_status);
            tvFingerprint = itemView.findViewById(R.id.tv_fingerprint_badge);
            tvCard = itemView.findViewById(R.id.tv_card_badge);
            tvBattery = itemView.findViewById(R.id.tv_door_battery);
        }

        void bind(NodeSummary item, OnLockActionListener listener) {
            Context context = itemView.getContext();
            TelemetryReading latest = item.latestReading();

            tvName.setText(item.label);

            boolean online = "online".equalsIgnoreCase(item.status);
            Boolean locked = readBoolean(latest, METRIC_LOCKED);
            String statusText;
            if (locked != null) {
                statusText = (locked ? "Đã khoá" : "Đang mở") + (online ? "" : " • Mất kết nối");
            } else {
                // Chưa xác nhận được key metric trạng thái khoá thật
                statusText = online ? "Đang kết nối" : "Mất kết nối";
            }
            tvStatus.setText(statusText);

            Boolean fingerprintOk = readBoolean(latest, METRIC_FINGERPRINT_OK);
            tvFingerprint.setText(fingerprintOk == null ? "Vân tay: —"
                    : (fingerprintOk ? "Vân tay: OK" : "Vân tay: Lỗi"));
            tvFingerprint.setTextColor(context.getColor(
                    Boolean.TRUE.equals(fingerprintOk) ? R.color.sh_success
                            : Boolean.FALSE.equals(fingerprintOk) ? R.color.sh_danger
                              : R.color.sh_text_secondary));

            Boolean cardOk = readBoolean(latest, METRIC_CARD_OK);
            tvCard.setText(cardOk == null ? "Thẻ từ: —"
                    : (cardOk ? "Thẻ từ: OK" : "Thẻ từ: Lỗi"));
            tvCard.setTextColor(context.getColor(
                    Boolean.TRUE.equals(cardOk) ? R.color.sh_success
                            : Boolean.FALSE.equals(cardOk) ? R.color.sh_danger
                              : R.color.sh_text_secondary));

            Double battery = readNumber(latest, METRIC_BATTERY);
            tvBattery.setText(battery == null ? "—%" : Math.round(battery) + "%");

            // Bấm vào thẻ để gửi lệnh khoá/mở khoá (nếu node hỗ trợ action tương ứng)
            itemView.setOnClickListener(v -> {
                if (listener == null) return;
                if (locked != null && locked && item.actions.contains(ACTION_UNLOCK)) {
                    listener.onLockAction(item, ACTION_UNLOCK);
                } else if (item.actions.contains(ACTION_LOCK)) {
                    listener.onLockAction(item, ACTION_LOCK);
                }
            });
        }

        private Boolean readBoolean(TelemetryReading latest, String key) {
            Double value = readNumber(latest, key);
            return value == null ? null : value != 0.0;
        }

        private Double readNumber(TelemetryReading latest, String key) {
            if (latest == null) return null;
            MetricReading reading = latest.metrics.get(key);
            return (reading != null && reading.isValid()) ? reading.value : null;
        }
    }
}