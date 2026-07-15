package com.example.smart_home_mobile_app.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.switchmaterial.SwitchMaterial;

import com.example.smart_home_mobile_app.R;
import com.example.smart_home_mobile_app.data.MetricReading;
import com.example.smart_home_mobile_app.data.NodeSummary;
import com.example.smart_home_mobile_app.data.TelemetryReading;

import java.util.ArrayList;
import java.util.List;

public class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder> {
    public static final String NODE_TYPE_LIGHT = "light";
    public static final String NODE_TYPE_FAN = "fan";
    public static final String NODE_TYPE_AIR_CONDITIONER = "air_conditioner";

    public static final String METRIC_POWER = "power";

    public static final String ACTION_TOGGLE = "toggle";
    public static final String ACTION_SET_MODE = "set_mode";
    public static final String ACTION_SET_INTENSITY = "set_intensity";

    public interface OnTogglePowerListener {
        void onTogglePower(NodeSummary device, boolean isOn);
    }

    public interface OnModeChangeListener {
        void onModeChange(NodeSummary device, boolean isNormalMode);
    }

    public interface OnIntensityChangeListener {
        void onIntensityChange(NodeSummary device, int value);
    }

    public interface OnDeleteDeviceListener {
        void onDeleteDevice(NodeSummary device);
    }

    private final List<NodeSummary> items = new ArrayList<>();
    private final OnTogglePowerListener togglePowerListener;
    private final OnDeleteDeviceListener deleteDeviceListener;

    public DeviceAdapter(OnTogglePowerListener togglePowerListener,
                         OnModeChangeListener modeChangeListener,
                         OnIntensityChangeListener intensityChangeListener,
                         OnDeleteDeviceListener deleteDeviceListener) {
        this.togglePowerListener = togglePowerListener;
        this.deleteDeviceListener = deleteDeviceListener;
    }

    public void submitList(List<NodeSummary> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public DeviceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_device_card, parent, false);
        return new DeviceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DeviceViewHolder holder, int position) {
        holder.bind(items.get(position), togglePowerListener, deleteDeviceListener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class DeviceViewHolder extends RecyclerView.ViewHolder {
        private final ImageView imgPhoto;
        private final TextView tvName;
        private final TextView tvStatus;
        private final ImageView btnDelete;
        private final SwitchMaterial switchPower;

        DeviceViewHolder(@NonNull View itemView) {
            super(itemView);
            imgPhoto = itemView.findViewById(R.id.img_device_photo);
            tvName = itemView.findViewById(R.id.tv_device_name);
            tvStatus = itemView.findViewById(R.id.tv_device_status);
            btnDelete = itemView.findViewById(R.id.btn_delete_device);
            switchPower = itemView.findViewById(R.id.switch_device_power);
        }

        void bind(NodeSummary item,
                  OnTogglePowerListener togglePowerListener,
                  OnDeleteDeviceListener deleteDeviceListener) {
            TelemetryReading latest = item.latestReading();

            tvName.setText(item.label);
            if (deleteDeviceListener == null) {
                btnDelete.setVisibility(View.GONE);
                btnDelete.setOnClickListener(null);
            } else {
                btnDelete.setVisibility(View.VISIBLE);
                btnDelete.setOnClickListener(v -> deleteDeviceListener.onDeleteDevice(item));
            }

            int iconRes;
            if (NODE_TYPE_LIGHT.equals(item.nodeType)) iconRes = R.drawable.ic_bulb;
            else if (NODE_TYPE_FAN.equals(item.nodeType)) iconRes = R.drawable.ic_fan;
            else if (NODE_TYPE_AIR_CONDITIONER.equals(item.nodeType)) iconRes = R.drawable.ic_fan;
            else iconRes = R.drawable.ic_bulb;
            imgPhoto.setImageResource(iconRes);

            boolean isOn = readBoolean(latest, METRIC_POWER, false);
            setStatusText(isOn);
            if (switchPower == null) return;
            switchPower.setOnCheckedChangeListener(null);
            switchPower.setChecked(isOn);
            boolean canToggle = item.actions.contains(ACTION_TOGGLE);
            switchPower.setEnabled(canToggle);
            switchPower.setOnCheckedChangeListener((buttonView, isChecked) -> {
                setStatusText(isChecked);
                if (togglePowerListener != null) togglePowerListener.onTogglePower(item, isChecked);
            });
        }

        private void setStatusText(boolean isOn) {
            tvStatus.setText(isOn ? "Đang bật" : "Đang tắt");
            tvStatus.setTextColor(itemView.getContext().getColor(
                    isOn ? R.color.sh_accent : R.color.sh_text_secondary));
        }

        private boolean readBoolean(TelemetryReading latest, String key, boolean fallback) {
            if (latest == null) return fallback;
            MetricReading reading = latest.metrics.get(key);
            return reading != null && reading.isValid() ? reading.value != 0.0 : fallback;
        }
    }
}
