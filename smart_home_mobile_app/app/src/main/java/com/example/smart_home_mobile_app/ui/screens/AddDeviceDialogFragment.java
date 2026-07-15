package com.example.smart_home_mobile_app.ui.screens;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.textfield.TextInputEditText;

import com.example.smart_home_mobile_app.R;
import com.example.smart_home_mobile_app.data.RoomSummary;
import com.example.smart_home_mobile_app.ui.adapter.DeviceAdapter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AddDeviceDialogFragment extends DialogFragment {
    public static final class RoomOption {
        public final String roomId;
        public final String label;

        public RoomOption(String roomId, String label) {
            this.roomId = roomId;
            this.label = label;
        }

        @NonNull
        @Override
        public String toString() {
            return label == null || label.trim().isEmpty() ? roomId : label;
        }
    }

    public interface OnDeviceSavedListener {
        void onDeviceSaved(String name, String roomId, String nodeType);
    }

    private static final Map<String, String> DEVICE_TYPE_TO_NODE_TYPE = buildTypeMap();

    private static Map<String, String> buildTypeMap() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("Đèn", DeviceAdapter.NODE_TYPE_LIGHT);
        map.put("Quạt", DeviceAdapter.NODE_TYPE_FAN);
        map.put("Điều hoà", DeviceAdapter.NODE_TYPE_AIR_CONDITIONER);
        map.put("Khoá cửa (vân tay/thẻ từ)", "door_lock");
        map.put("Cảm biến nhiệt độ", "environment_sensor");
        map.put("Cảm biến độ ẩm", "environment_sensor");
        map.put("Cảm biến khói", "environment_sensor");
        map.put("Cảm biến bụi mịn PM2.5", "environment_sensor");
        map.put("Camera", "camera");
        return map;
    }

    private OnDeviceSavedListener onDeviceSavedListener;
    private List<RoomOption> roomOptions = new ArrayList<>();

    public void setOnDeviceSavedListener(OnDeviceSavedListener listener) {
        this.onDeviceSavedListener = listener;
    }

    public void setRoomOptions(List<RoomSummary> rooms) {
        ArrayList<RoomOption> options = new ArrayList<>();
        if (rooms != null) {
            for (RoomSummary room : rooms) {
                options.add(new RoomOption(room.roomId, room.label));
            }
        }
        this.roomOptions = options;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View view = getLayoutInflater().inflate(R.layout.dialog_add_device, null);
        dialog.setContentView(view);

        Spinner spinnerRoom = view.findViewById(R.id.spinner_device_room);
        Spinner spinnerType = view.findViewById(R.id.spinner_device_type);
        TextInputEditText editName = view.findViewById(R.id.edit_device_name);
        TextView btnCancel = view.findViewById(R.id.btn_cancel_device);
        TextView btnSave = view.findViewById(R.id.btn_save_device);

        List<String> deviceTypeLabels = Arrays.asList(DEVICE_TYPE_TO_NODE_TYPE.keySet().toArray(new String[0]));

        ArrayAdapter<RoomOption> roomAdapter = new ArrayAdapter<>(
                requireContext(),
                R.layout.item_spinner_white,
                roomOptions
        );
        roomAdapter.setDropDownViewResource(R.layout.item_spinner_white);
        spinnerRoom.setAdapter(roomAdapter);

        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(
                requireContext(),
                R.layout.item_spinner_white,
                deviceTypeLabels
        );
        typeAdapter.setDropDownViewResource(R.layout.item_spinner_white);
        spinnerType.setAdapter(typeAdapter);

        btnCancel.setOnClickListener(v -> dismiss());
        btnSave.setOnClickListener(v -> {
            String name = editName.getText() != null ? editName.getText().toString().trim() : "";
            Object selectedRoom = spinnerRoom.getSelectedItem();
            String typeLabel = spinnerType.getSelectedItem() != null
                    ? spinnerType.getSelectedItem().toString()
                    : deviceTypeLabels.get(0);
            String nodeType = DEVICE_TYPE_TO_NODE_TYPE.get(typeLabel);

            if (roomOptions.isEmpty() || !(selectedRoom instanceof RoomOption)) {
                editName.setError("Tạo phòng trước khi thêm thiết bị");
                return;
            }
            if (name.isEmpty()) {
                editName.setError("Nhập tên thiết bị");
                return;
            }
            if (onDeviceSavedListener != null) {
                onDeviceSavedListener.onDeviceSaved(name, ((RoomOption) selectedRoom).roomId, nodeType);
            }
            dismiss();
        });

        return dialog;
    }
}
