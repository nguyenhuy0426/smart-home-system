package com.example.smart_home_mobile_app.ui.screens;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.smart_home_mobile_app.R;
import com.example.smart_home_mobile_app.ui.MobileDashboardActivity;
import com.example.smart_home_mobile_app.ui.SmartHomeAppController;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class CreateHomeFragment extends Fragment {
    private SmartHomeAppController controller;
    private TextInputEditText nameInput;
    private TextInputEditText typeInput;
    private TextInputEditText addressInput;
    private TextView error;
    private MaterialButton submit;

    public CreateHomeFragment() {
        super(R.layout.fragment_create_home);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        MobileDashboardActivity activity = (MobileDashboardActivity) requireActivity();
        controller = activity.getController();
        nameInput = view.findViewById(R.id.input_home_name);
        typeInput = view.findViewById(R.id.input_home_type);
        addressInput = view.findViewById(R.id.input_home_address);
        error = view.findViewById(R.id.tv_create_home_error);
        submit = view.findViewById(R.id.btn_create_home_submit);

        view.findViewById(R.id.btn_back).setOnClickListener(v -> requireActivity().onBackPressed());
        submit.setOnClickListener(v -> submitHome(activity));
    }

    private void submitHome(MobileDashboardActivity activity) {
        String name = text(nameInput);
        String type = text(typeInput);
        String address = text(addressInput);
        if (name.isEmpty()) {
            showError("Nhập tên nhà trước");
            return;
        }
        setBusy(true);
        controller.createHome(name, type.isEmpty() ? "Nhà riêng" : type, address, (homeId, errorMessage) -> {
            setBusy(false);
            if (errorMessage != null) {
                showError(errorMessage);
                return;
            }
            Toast.makeText(requireContext(), "Đã tạo nhà", Toast.LENGTH_SHORT).show();
            activity.returnToMain();
        });
    }

    private String text(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    private void showError(String message) {
        error.setText(message);
        error.setVisibility(View.VISIBLE);
    }

    private void setBusy(boolean busy) {
        submit.setEnabled(!busy);
        submit.setText(busy ? "Đang tạo..." : "Tạo nhà");
    }
}
