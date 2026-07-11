package com.example.smart_home_mobile_app.ui.screens;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.switchmaterial.SwitchMaterial;

import com.example.smart_home_mobile_app.R;
import com.example.smart_home_mobile_app.data.DetectionEvent;
import com.example.smart_home_mobile_app.data.HomeSnapshot;
import com.example.smart_home_mobile_app.data.HomeUiState;
import com.example.smart_home_mobile_app.data.NodeSummary;
import com.example.smart_home_mobile_app.ui.MobileDashboardActivity;
import com.example.smart_home_mobile_app.ui.SmartHomeAppController;
import com.example.smart_home_mobile_app.ui.adapter.DetectionEventAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class CameraFragment extends Fragment implements SmartHomeAppController.Listener {

    public CameraFragment() {
        super(R.layout.fragment_camera);
    }

    // GIẢ ĐỊNH — chưa có node mẫu camera trong FakeData để đối chiếu:
    private static final String NODE_TYPE_CAMERA = "camera";
    private static final String ACTION_ENABLE_FALL_DETECTION = "enable_fall_detection";
    private static final String ACTION_DISABLE_FALL_DETECTION = "disable_fall_detection";
    private static final String ACTION_ENABLE_HUMAN_DETECTION = "enable_human_detection";
    private static final String ACTION_DISABLE_HUMAN_DETECTION = "disable_human_detection";

    private DetectionEventAdapter eventAdapter;
    private SwitchMaterial switchFall;
    private SwitchMaterial switchHuman;
    private TextView tvFallStatus;
    private TextView tvHumanStatus;

    private SmartHomeAppController controller;
    /** Nullable: node camera hiện đang xem (node đầu tiên có nodeType = camera). */
    private String cameraNodeId;

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        controller = ((MobileDashboardActivity) requireActivity()).getController();

        MaterialToolbar toolbar = view.findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> getParentFragmentManager().popBackStack());

        switchFall = view.findViewById(R.id.switch_fall_detection);
        switchHuman = view.findViewById(R.id.switch_human_detection);
        tvFallStatus = view.findViewById(R.id.tv_fall_status);
        tvHumanStatus = view.findViewById(R.id.tv_human_status);
        RecyclerView rvEvents = view.findViewById(R.id.rv_detection_events);
        TextView btnClearHistory = view.findViewById(R.id.btn_clear_history);

        switchFall.setOnCheckedChangeListener((buttonView, isChecked) -> {
            tvFallStatus.setText(isChecked
                    ? "Đang bật • Không phát hiện bất thường"
                    : "Đã tắt • Sẽ không nhận cảnh báo té ngã");
            if (cameraNodeId != null) {
                controller.sendCommand(cameraNodeId,
                        isChecked ? ACTION_ENABLE_FALL_DETECTION : ACTION_DISABLE_FALL_DETECTION);
            }
        });

        switchHuman.setOnCheckedChangeListener((buttonView, isChecked) -> {
            tvHumanStatus.setText(isChecked
                    ? "Đang bật • Sẽ nhận cảnh báo khi có người"
                    : "Đã tắt • Sẽ không nhận cảnh báo có người");
            if (cameraNodeId != null) {
                controller.sendCommand(cameraNodeId,
                        isChecked ? ACTION_ENABLE_HUMAN_DETECTION : ACTION_DISABLE_HUMAN_DETECTION);
            }
        });

        eventAdapter = new DetectionEventAdapter();
        rvEvents.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvEvents.setAdapter(eventAdapter);

        btnClearHistory.setOnClickListener(v -> {
            eventAdapter.clear();
            // TODO: project chưa có API xoá lịch sử detectionEvents phía Firebase;
            // đây mới chỉ xoá trên UI cục bộ, sẽ hiện lại khi HomeUiState cập nhật lần sau.
        });

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
        if (snapshot == null) {
            eventAdapter.submitList(new ArrayList<>());
            return;
        }

        cameraNodeId = snapshot.nodes.stream()
                .filter(node -> NODE_TYPE_CAMERA.equals(node.nodeType))
                .map(node -> node.nodeId)
                .findFirst()
                .orElse(null);

        List<DetectionEvent> events = cameraNodeId == null
                ? snapshot.detectionEvents
                : snapshot.detectionEvents.stream()
                  .filter(event -> cameraNodeId.equals(event.cameraNodeId))
                  .collect(Collectors.toList());
        eventAdapter.submitList(events);

        NodeSummary cameraNode = snapshot.nodes.stream()
                .filter(node -> node.nodeId != null && node.nodeId.equals(cameraNodeId))
                .findFirst()
                .orElse(null);
        if (cameraNode != null) {
            switchFall.setEnabled(cameraNode.actions.contains(ACTION_ENABLE_FALL_DETECTION)
                    || cameraNode.actions.contains(ACTION_DISABLE_FALL_DETECTION));
            switchHuman.setEnabled(cameraNode.actions.contains(ACTION_ENABLE_HUMAN_DETECTION)
                    || cameraNode.actions.contains(ACTION_DISABLE_HUMAN_DETECTION));
        }
    }
}