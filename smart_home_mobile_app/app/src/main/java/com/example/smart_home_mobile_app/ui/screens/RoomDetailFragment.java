package com.example.smart_home_mobile_app.ui.screens;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smart_home_mobile_app.R;
import com.example.smart_home_mobile_app.data.HomeSnapshot;
import com.example.smart_home_mobile_app.data.HomeUiState;
import com.example.smart_home_mobile_app.data.NodeSummary;
import com.example.smart_home_mobile_app.data.RoomSummary;
import com.example.smart_home_mobile_app.ui.MobileDashboardActivity;
import com.example.smart_home_mobile_app.ui.SmartHomeAppController;
import com.example.smart_home_mobile_app.ui.adapter.DeviceAdapter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class RoomDetailFragment extends Fragment implements SmartHomeAppController.Listener {
    private static final String ARG_ROOM_ID = "room_id";
    private static final String NODE_TYPE_CAMERA = "camera";
    private static final Set<String> CONTROLLABLE_DEVICE_TYPES = new HashSet<>(java.util.Arrays.asList(
            DeviceAdapter.NODE_TYPE_LIGHT, DeviceAdapter.NODE_TYPE_FAN, DeviceAdapter.NODE_TYPE_AIR_CONDITIONER));

    private SmartHomeAppController controller;
    private DeviceAdapter deviceAdapter;
    private String roomId;
    private TextView tvTitle;
    private TextView tvSubtitle;
    private TextView tvCameraName;
    private TextView tvCameraStatus;
    private TextView tvDeviceCount;
    private View emptyDevicesLayout;

    public RoomDetailFragment() {
        super(R.layout.fragment_room_detail);
    }

    public static RoomDetailFragment newInstance(String roomId) {
        RoomDetailFragment fragment = new RoomDetailFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ROOM_ID, roomId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        roomId = getArguments() == null ? null : getArguments().getString(ARG_ROOM_ID);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        controller = ((MobileDashboardActivity) requireActivity()).getController();
        tvTitle = view.findViewById(R.id.tv_room_detail_title);
        tvSubtitle = view.findViewById(R.id.tv_room_detail_subtitle);
        tvCameraName = view.findViewById(R.id.tv_room_camera_name);
        tvCameraStatus = view.findViewById(R.id.tv_room_camera_status);
        tvDeviceCount = view.findViewById(R.id.tv_room_detail_device_count);
        emptyDevicesLayout = view.findViewById(R.id.layout_room_devices_empty);
        RecyclerView rvDevices = view.findViewById(R.id.rv_room_detail_devices);

        view.findViewById(R.id.btn_room_detail_back).setOnClickListener(v ->
                getParentFragmentManager().popBackStack());

        deviceAdapter = new DeviceAdapter(
                (device, isOn) -> controller.sendCommand(device.nodeId, DeviceAdapter.ACTION_TOGGLE),
                (device, isNormalMode) -> controller.sendCommand(device.nodeId, DeviceAdapter.ACTION_SET_MODE),
                (device, value) -> controller.sendCommand(device.nodeId, DeviceAdapter.ACTION_SET_INTENSITY),
                null
        );
        rvDevices.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvDevices.setAdapter(deviceAdapter);
        rvDevices.setNestedScrollingEnabled(false);

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

    private void render(HomeUiState state) {
        HomeSnapshot snapshot = state.snapshot;
        if (snapshot == null || roomId == null) {
            tvTitle.setText("Phòng");
            tvSubtitle.setText("Chưa có dữ liệu");
            tvCameraName.setText("Camera giám sát");
            tvCameraStatus.setText("Chưa chọn nhà");
            deviceAdapter.submitList(new ArrayList<>());
            emptyDevicesLayout.setVisibility(View.VISIBLE);
            tvDeviceCount.setText("0 thiết bị");
            return;
        }

        RoomSummary room = findRoom(snapshot);
        String roomName = room == null ? "Phòng" : room.label;
        tvTitle.setText(roomName);
        tvSubtitle.setText(snapshot.home.displayName == null ? "Đang giám sát" : snapshot.home.displayName);

        List<NodeSummary> nodesInRoom = snapshot.nodes.stream()
                .filter(node -> roomId.equals(node.roomId))
                .collect(Collectors.toList());
        NodeSummary camera = nodesInRoom.stream()
                .filter(node -> NODE_TYPE_CAMERA.equals(node.nodeType))
                .findFirst()
                .orElse(null);
        if (camera == null) {
            tvCameraName.setText("Camera giám sát");
            tvCameraStatus.setText("Chưa có camera trong phòng này");
        } else {
            tvCameraName.setText(camera.label);
            tvCameraStatus.setText("Đang theo dõi • " + camera.status);
        }

        List<NodeSummary> devices = nodesInRoom.stream()
                .filter(this::classifyAsControllableDevice)
                .collect(Collectors.toList());
        deviceAdapter.submitList(devices);
        tvDeviceCount.setText(devices.size() + " thiết bị");
        emptyDevicesLayout.setVisibility(devices.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private RoomSummary findRoom(HomeSnapshot snapshot) {
        for (RoomSummary room : snapshot.rooms) {
            if (room.roomId.equals(roomId)) return room;
        }
        return null;
    }

    private boolean classifyAsControllableDevice(NodeSummary node) {
        return CONTROLLABLE_DEVICE_TYPES.contains(node.nodeType);
    }
}
