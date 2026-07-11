package com.example.smart_home_mobile_app.ui.screens;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smart_home_mobile_app.R;
import com.example.smart_home_mobile_app.ui.MobileDashboardActivity;
import com.example.smart_home_mobile_app.ui.SmartHomeAppController;
import com.example.smart_home_mobile_app.ui.adapter.HomeListAdapter;
import com.example.smart_home_mobile_app.repository.UserHomesRepository.HomeListItem;

import java.util.ArrayList;
import java.util.regex.Pattern;

public class ManageHomesFragment extends Fragment implements SmartHomeAppController.Listener {
    private static final Pattern HOME_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,127}");

    private SmartHomeAppController controller;
    private HomeListAdapter adapter;
    private TextView empty;
    private String lastShownMessage;

    public ManageHomesFragment() {
        super(R.layout.fragment_manage_homes);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        MobileDashboardActivity activity = (MobileDashboardActivity) requireActivity();
        controller = activity.getController();

        view.findViewById(R.id.btn_back).setOnClickListener(v -> requireActivity().onBackPressed());
        view.findViewById(R.id.btn_join_home).setOnClickListener(v -> activity.navigateToJoinHome());
        view.findViewById(R.id.btn_add_home).setOnClickListener(v -> activity.navigateToCreateHome());
        view.findViewById(R.id.btn_create_invite).setOnClickListener(v -> createInvite());
        view.findViewById(R.id.btn_delete_home).setOnClickListener(v -> confirmDeleteHome());

        empty = view.findViewById(R.id.tv_empty_homes);
        RecyclerView list = view.findViewById(R.id.rv_homes);
        adapter = new HomeListAdapter(homeId -> {
            controller.selectHome(homeId);
            Toast.makeText(requireContext(), "Đang mở nhà " + homeId, Toast.LENGTH_SHORT).show();
            activity.returnToMain();
        });
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);
        render();
    }

    @Override
    public void onStart() {
        super.onStart();
        controller.addListener(this);
        render();
    }

    @Override
    public void onStop() {
        controller.removeListener(this);
        super.onStop();
    }

    @Override
    public void onControllerStateChanged() {
        render();
        maybeShowMessage();
    }

    private void render() {
        if (adapter == null) return;
        ArrayList<HomeListItem> homes = new ArrayList<>(controller.homeListItems());
        adapter.submitList(homes, controller.selectedHomeId());
        empty.setVisibility(homes.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void createInvite() {
        String homeId = controller.selectedHomeId();
        if (homeId == null || homeId.trim().isEmpty()) {
            Toast.makeText(requireContext(), "Chọn nhà trước khi tạo mã mời", Toast.LENGTH_SHORT).show();
            return;
        }
        controller.createInvite(homeId, (code, errorMessage) -> {
            if (errorMessage != null) {
                Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show();
                return;
            }
            if (code == null || code.trim().isEmpty()) {
                Toast.makeText(requireContext(), "Backend không trả về mã mời", Toast.LENGTH_LONG).show();
                return;
            }
            ClipboardManager clipboard = (ClipboardManager) requireContext()
                    .getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("Mã mời nhà", code));
            }
            new AlertDialog.Builder(requireContext())
                    .setTitle("Mã mời")
                    .setMessage(code + "\n\nMã đã được sao chép. Gửi mã này cho người cần tham gia nhà.")
                    .setPositiveButton("Đã hiểu", null)
                    .show();
        });
    }


    private void confirmDeleteHome() {
        String homeId = controller.selectedHomeId();
        if (homeId == null || homeId.trim().isEmpty()) {
            Toast.makeText(requireContext(), "Chọn nhà trước khi xóa", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle("Xóa nhà?")
                .setMessage("Nhà " + homeId + " sẽ bị xóa khỏi Firebase. Chỉ chủ nhà mới thực hiện được thao tác này.")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xóa", (dialog, which) -> deleteSelectedHome())
                .show();
    }

    private void deleteSelectedHome() {
        controller.deleteSelectedOwnedHome((value, errorMessage) -> {
            if (errorMessage != null) {
                Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show();
                return;
            }
            Toast.makeText(requireContext(), "Đã xóa nhà", Toast.LENGTH_SHORT).show();
            render();
        });
    }

    private void maybeShowMessage() {
        if (!isAdded()) return;
        String message = controller.homeState().message;
        if (message == null || message.equals(lastShownMessage)) return;
        lastShownMessage = message;
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
    }
}
