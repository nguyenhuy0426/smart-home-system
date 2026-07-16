package com.example.smart_home_mobile_app.ui.screens;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import com.example.smart_home_mobile_app.R;
import com.example.smart_home_mobile_app.data.HomeSnapshot;
import com.example.smart_home_mobile_app.data.HomeUiState;
import com.example.smart_home_mobile_app.data.MetricReading;
import com.example.smart_home_mobile_app.data.NodeSummary;
import com.example.smart_home_mobile_app.data.RoomSummary;
import com.example.smart_home_mobile_app.data.TelemetryReading;
import com.example.smart_home_mobile_app.ui.MobileDashboardActivity;
import com.example.smart_home_mobile_app.ui.SmartHomeAppController;
import com.example.smart_home_mobile_app.ui.adapter.DeviceAdapter;
import com.example.smart_home_mobile_app.ui.adapter.DoorAdapter;
import com.example.smart_home_mobile_app.ui.adapter.RoomAdapter;
import com.example.smart_home_mobile_app.ui.adapter.SensorAdapter;
import com.example.smart_home_mobile_app.ui.adapter.SensorMetricItem;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class MainFragment extends Fragment implements SmartHomeAppController.Listener {

    public MainFragment() {
        super(R.layout.fragment_main);
    }

    // ===== Phân loại node theo nodeType =====
    // CONFIRMED (FakeData.SAMPLE_NODE_SUMMARY dùng đúng chuỗi này):
    private static final String NODE_TYPE_ENVIRONMENT_SENSOR = "environment_sensor";
    // GIẢ ĐỊNH — FakeData chưa có node mẫu khoá cửa / đèn / quạt / điều hoà,
    // cần đối chiếu lại khi có dữ liệu Firebase thật hoặc thêm mẫu vào FakeData.
    private static final String NODE_TYPE_DOOR_LOCK = "door_lock";
    private static final Set<String> CONTROLLABLE_DEVICE_TYPES = new HashSet<>(java.util.Arrays.asList(
            DeviceAdapter.NODE_TYPE_LIGHT, DeviceAdapter.NODE_TYPE_FAN, DeviceAdapter.NODE_TYPE_AIR_CONDITIONER));

    // ===== Key metric cảm biến môi trường hiển thị ở rv_sensors =====
    // "temperature"/"humidity" CONFIRMED qua FakeData; "smoke"/"pm25" GIẢ ĐỊNH.
    private static final String METRIC_TEMPERATURE = "temperature";
    private static final String METRIC_HUMIDITY = "humidity";
    private static final String METRIC_SMOKE = "smoke";
    private static final String METRIC_PM25 = "pm25";

    private SensorAdapter sensorAdapter;
    private RoomAdapter roomAdapter;
    private DoorAdapter doorAdapter;
    private DeviceAdapter deviceAdapter;

    private View btnAddRoom;
    private View btnDeleteRoom;
    private View emptyHomeMessage;
    private View emptyDevicesLayout;
    private View btnAddDeviceEmpty;
    private View btnAddDeviceBelowList;
    private android.widget.TextView deviceCount;


    private SmartHomeAppController controller;
    private String selectedRoomId;
    /** Tránh Toast lặp lại cùng 1 message mỗi lần render(). */
    private String lastShownCommandMessage;

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        controller = ((MobileDashboardActivity) requireActivity()).getController();

        RecyclerView rvSensors = view.findViewById(R.id.rv_sensors);
        RecyclerView rvRooms = view.findViewById(R.id.rv_rooms);
        RecyclerView rvDoors = view.findViewById(R.id.rv_doors);
        RecyclerView rvDevices = view.findViewById(R.id.rv_devices);
        btnAddRoom = view.findViewById(R.id.btn_add_room);
        btnDeleteRoom = view.findViewById(R.id.btn_delete_room);
        emptyHomeMessage = view.findViewById(R.id.tv_home_empty_message);
        emptyDevicesLayout = view.findViewById(R.id.layout_empty_devices);
        btnAddDeviceEmpty = view.findViewById(R.id.btn_add_device_empty);
        btnAddDeviceBelowList = view.findViewById(R.id.btn_add_device_below_list);
        deviceCount = view.findViewById(R.id.tv_device_count);


        sensorAdapter = new SensorAdapter();
        rvSensors.setLayoutManager(new LinearLayoutManager(
                requireContext(), LinearLayoutManager.HORIZONTAL, false));
        rvSensors.setAdapter(sensorAdapter);

        doorAdapter = new DoorAdapter((node, action) -> controller.sendCommand(node.nodeId, action));
        rvDoors.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvDoors.setAdapter(doorAdapter);

        deviceAdapter = new DeviceAdapter(
                (device, isOn) -> controller.sendCommand(device.nodeId, DeviceAdapter.ACTION_TOGGLE),
                (device, isNormalMode) -> controller.sendCommand(device.nodeId, DeviceAdapter.ACTION_SET_MODE),
                (device, value) -> controller.sendCommand(device.nodeId, DeviceAdapter.ACTION_SET_INTENSITY),
                this::confirmDeleteDevice
        );
        rvDevices.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvDevices.setAdapter(deviceAdapter);

        roomAdapter = new RoomAdapter(room -> {
            selectedRoomId = room.roomId;
            render(controller.homeState());
        });
        rvRooms.setLayoutManager(new LinearLayoutManager(
                requireContext(), LinearLayoutManager.HORIZONTAL, false));
        rvRooms.setAdapter(roomAdapter);

        btnAddRoom.setOnClickListener(v -> {
            AddRoomDialogFragment dialog = new AddRoomDialogFragment();
            dialog.setListener(roomName -> controller.addRoom(roomName));
            dialog.show(getChildFragmentManager(), "add_room");
        });

        btnDeleteRoom.setOnClickListener(v -> confirmDeleteSelectedRoom());

        btnAddDeviceEmpty.setOnClickListener(v -> showAddDeviceDialog());
        btnAddDeviceBelowList.setOnClickListener(v -> showAddDeviceDialog());

        render(controller.homeState());
    }

    @Override
    public void onStart() {
        super.onStart();
        controller.addListener(this);
    }

    @Override
    public void onStop() {
        controller.removeListener(this);
        super.onStop();
    }

    @Override
    public void onControllerStateChanged() {
        render(controller.homeState());
    }


    private void showAddDeviceDialog() {
        HomeSnapshot snapshot = controller.homeState().snapshot;
        AddDeviceDialogFragment dialog = new AddDeviceDialogFragment();
        if (snapshot != null) {
            dialog.setRoomOptions(snapshot.rooms);
        }
        dialog.setOnDeviceSavedListener((label, roomId, nodeType) ->
                controller.addDevice(label, roomId, nodeType));
        dialog.show(getChildFragmentManager(), "add_device");
    }

    private void confirmDeleteSelectedRoom() {
        if (selectedRoomId == null || selectedRoomId.trim().isEmpty()) {
            Toast.makeText(requireContext(), "Chọn phòng trước khi xóa", Toast.LENGTH_SHORT).show();
            return;
        }
        String roomName = selectedRoomId;
        HomeSnapshot snapshot = controller.homeState().snapshot;
        if (snapshot != null) {
            for (RoomSummary room : snapshot.rooms) {
                if (room.roomId.equals(selectedRoomId)) roomName = room.label;
            }
        }
        String roomId = selectedRoomId;
        new AlertDialog.Builder(requireContext())
                .setTitle("Xóa phòng?")
                .setMessage("Phòng " + roomName + " sẽ bị xóa nếu không còn thiết bị.")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xóa", (dialog, which) -> controller.deleteRoom(roomId))
                .show();
    }

    private void confirmDeleteDevice(NodeSummary device) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Xóa thiết bị?")
                .setMessage("Thiết bị " + device.label + " sẽ bị xóa khỏi phòng này.")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xóa", (dialog, which) ->
                        controller.deleteDevice(device.nodeId, device.label))
                .show();
    }

    /** Điểm nối chính: vẽ lại toàn bộ dashboard theo HomeUiState mới nhất. */
    private void render(HomeUiState state) {
        maybeShowCommandMessage();

        HomeSnapshot snapshot = state.snapshot;
        if (emptyHomeMessage != null) {
            boolean showEmptyHome = snapshot == null && controller.homeIds().isEmpty();
            emptyHomeMessage.setVisibility(showEmptyHome ? View.VISIBLE : View.GONE);
        }
        if (snapshot == null) {
            roomAdapter.submitList(new ArrayList<>());
            sensorAdapter.submitList(new ArrayList<>());
            doorAdapter.submitList(new ArrayList<>());
            deviceAdapter.submitList(new ArrayList<>());
            return;
        }

        List<RoomSummary> rooms = snapshot.rooms;
        roomAdapter.submitList(rooms);

        boolean selectedRoomStillExists = false;
        for (RoomSummary room : rooms) {
            if (room.roomId.equals(selectedRoomId)) selectedRoomStillExists = true;
        }
        if (!selectedRoomStillExists) selectedRoomId = null;
        if (selectedRoomId == null && !rooms.isEmpty()) {
            selectedRoomId = rooms.get(0).roomId;
        }
        if (selectedRoomId != null) {
            roomAdapter.selectRoom(selectedRoomId);
        }

        List<NodeSummary> nodesInRoom = snapshot.nodes.stream()
                .filter(node -> node.roomId.equals(selectedRoomId))
                .collect(Collectors.toList());

        sensorAdapter.submitList(buildSensorItems(nodesInRoom));

        doorAdapter.submitList(nodesInRoom.stream()
                .filter(this::classifyAsDoor)
                .collect(Collectors.toList()));

        List<NodeSummary> controllableDevices = nodesInRoom.stream()
                .filter(this::classifyAsControllableDevice)
                .collect(Collectors.toList());
        deviceAdapter.submitList(controllableDevices);
        if (deviceCount != null) {
            deviceCount.setText(controllableDevices.size() + " thiết bị");
        }
        if (emptyDevicesLayout != null) {
            emptyDevicesLayout.setVisibility(controllableDevices.isEmpty() ? View.VISIBLE : View.GONE);
        }
        if (btnAddDeviceBelowList != null) {
            btnAddDeviceBelowList.setVisibility(controllableDevices.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }

    /**
     * Trước đây mọi lỗi (kể cả lỗi ghi Firebase — ví dụ bị Rules chặn) đều
     * "âm thầm" không hiện gì ra UI. Giờ hiện Toast để thấy ngay khi có vấn đề.
     * Kiểm tra cả commandMessage (lỗi tạo phòng/gửi lệnh) LẪN homeState.message
     * (lỗi ngay từ bước addHome — ví dụ homeId sai định dạng).
     */
    private void maybeShowCommandMessage() {
        if (!isAdded()) return;

        String homeErrorMessage = controller.homeState().message;
        if (homeErrorMessage != null && !homeErrorMessage.equals(lastShownCommandMessage)) {
            lastShownCommandMessage = homeErrorMessage;
            Toast.makeText(requireContext(), homeErrorMessage, Toast.LENGTH_LONG).show();
            return;
        }

        String message = controller.commandMessage();
        if (message == null || message.equals(lastShownCommandMessage)) return;
        lastShownCommandMessage = message;
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
    }

    private boolean classifyAsEnvironmentSensor(NodeSummary node) {
        return NODE_TYPE_ENVIRONMENT_SENSOR.equals(node.nodeType);
    }

    private boolean classifyAsDoor(NodeSummary node) {
        return NODE_TYPE_DOOR_LOCK.equals(node.nodeType);
    }

    private boolean classifyAsControllableDevice(NodeSummary node) {
        return CONTROLLABLE_DEVICE_TYPES.contains(node.nodeType);
    }

    /** Gom metric của mọi node cảm biến môi trường trong phòng thành 4 thẻ cố định. */
    private List<SensorMetricItem> buildSensorItems(List<NodeSummary> nodesInRoom) {
        Map<String, MetricReading> merged = new LinkedHashMap<>();
        for (NodeSummary node : nodesInRoom) {
            if (!classifyAsEnvironmentSensor(node)) continue;
            TelemetryReading latest = node.latestReading();
            if (latest != null) merged.putAll(latest.metrics);
        }

        List<SensorMetricItem> items = new ArrayList<>();
        items.add(new SensorMetricItem("Nhiệt độ", merged.get(METRIC_TEMPERATURE), R.drawable.ic_thermometer));
        items.add(new SensorMetricItem("Độ ẩm", merged.get(METRIC_HUMIDITY), R.drawable.ic_humidity));
        items.add(new SensorMetricItem("Khói", merged.get(METRIC_SMOKE), R.drawable.ic_smoke));
        items.add(new SensorMetricItem("Bụi mịn PM2.5", merged.get(METRIC_PM25), R.drawable.ic_dust));
        return items;
    }
}
