package com.example.smart_home_mobile_app.ui.screens;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smart_home_mobile_app.R;
import com.example.smart_home_mobile_app.data.HomeSnapshot;
import com.example.smart_home_mobile_app.data.HomeUiState;
import com.example.smart_home_mobile_app.ui.MobileDashboardActivity;
import com.example.smart_home_mobile_app.ui.SmartHomeAppController;
import com.example.smart_home_mobile_app.ui.adapter.RoomOverviewAdapter;

import java.util.ArrayList;

public class RoomsFragment extends Fragment implements SmartHomeAppController.Listener {
    private SmartHomeAppController controller;
    private RoomOverviewAdapter adapter;
    private TextView tvHomeName;
    private TextView tvEmpty;

    public RoomsFragment() {
        super(R.layout.fragment_rooms);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        controller = ((MobileDashboardActivity) requireActivity()).getController();
        tvHomeName = view.findViewById(R.id.tv_rooms_home_name);
        tvEmpty = view.findViewById(R.id.tv_rooms_empty);
        RecyclerView rvRooms = view.findViewById(R.id.rv_room_overview);

        adapter = new RoomOverviewAdapter(room ->
                ((MobileDashboardActivity) requireActivity()).navigateToRoomDetail(room.roomId));
        rvRooms.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        rvRooms.setAdapter(adapter);
        rvRooms.setNestedScrollingEnabled(false);

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
            tvHomeName.setText("Chưa chọn nhà");
            tvEmpty.setVisibility(View.VISIBLE);
            adapter.submitList(new ArrayList<>(), new ArrayList<>());
            return;
        }
        tvHomeName.setText(snapshot.home.displayName == null || snapshot.home.displayName.trim().isEmpty()
                ? snapshot.home.homeId
                : snapshot.home.displayName);
        tvEmpty.setVisibility(snapshot.rooms.isEmpty() ? View.VISIBLE : View.GONE);
        adapter.submitList(snapshot.rooms, snapshot.nodes);
    }
}
