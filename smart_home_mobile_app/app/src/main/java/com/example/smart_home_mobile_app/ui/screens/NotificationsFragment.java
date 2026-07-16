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
import com.example.smart_home_mobile_app.auth.AuthUser;
import com.example.smart_home_mobile_app.data.HomeSnapshot;
import com.example.smart_home_mobile_app.data.HomeUiState;
import com.example.smart_home_mobile_app.ui.MobileDashboardActivity;
import com.example.smart_home_mobile_app.ui.SmartHomeAppController;
import com.example.smart_home_mobile_app.ui.adapter.NotificationAdapter;
import com.example.smart_home_mobile_app.ui.notifications.NotificationCenter;
import com.example.smart_home_mobile_app.ui.notifications.NotificationItem;

import java.util.ArrayList;
import java.util.List;

public class NotificationsFragment extends Fragment
        implements SmartHomeAppController.Listener {

    private SmartHomeAppController controller;
    private NotificationAdapter adapter;
    private TextView homeName;
    private TextView emptyMessage;

    public NotificationsFragment() {
        super(R.layout.fragment_notifications);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        controller = ((MobileDashboardActivity) requireActivity()).getController();
        homeName = view.findViewById(R.id.tv_notifications_home_name);
        emptyMessage = view.findViewById(R.id.tv_notifications_empty);

        RecyclerView notifications = view.findViewById(R.id.rv_notifications);
        notifications.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new NotificationAdapter();
        notifications.setAdapter(adapter);

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
            homeName.setText("Chưa chọn nhà");
            emptyMessage.setText("Chưa có dữ liệu thông báo");
            emptyMessage.setVisibility(View.VISIBLE);
            adapter.submitList(new ArrayList<>());
            return;
        }

        AuthUser user = controller.authState().user;
        String uid = user == null ? null : user.uid;
        String email = user == null ? null : user.email;
        List<NotificationItem> items = NotificationCenter.build(snapshot, uid, email);

        String displayName = snapshot.home.displayName == null
                || snapshot.home.displayName.trim().isEmpty()
                ? snapshot.home.homeId
                : snapshot.home.displayName;
        homeName.setText(displayName + " • " + items.size() + " thông báo");
        emptyMessage.setText("Chưa có lịch sử điều khiển hoặc cảnh báo cảm biến");
        emptyMessage.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        adapter.submitList(items);
    }
}
