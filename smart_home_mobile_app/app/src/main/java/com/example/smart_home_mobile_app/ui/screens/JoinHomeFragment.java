package com.example.smart_home_mobile_app.ui.screens;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.util.regex.Pattern;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.smart_home_mobile_app.R;
import com.example.smart_home_mobile_app.ui.MobileDashboardActivity;
import com.example.smart_home_mobile_app.ui.SmartHomeAppController;
import com.google.android.material.textfield.TextInputEditText;

public class JoinHomeFragment extends Fragment implements SmartHomeAppController.Listener {
    private static final Pattern HOME_CODE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,127}");

    private SmartHomeAppController controller;
    private TextInputEditText codeInput;
    private TextView error;
    private String lastShownMessage;

    public JoinHomeFragment() {
        super(R.layout.fragment_join_home);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        MobileDashboardActivity activity = (MobileDashboardActivity) requireActivity();
        controller = activity.getController();
        codeInput = view.findViewById(R.id.input_invitation_code);
        error = view.findViewById(R.id.tv_join_error);

        view.findViewById(R.id.btn_back).setOnClickListener(v -> requireActivity().onBackPressed());
        view.findViewById(R.id.btn_join_submit).setOnClickListener(v -> {
            String code = codeInput.getText() == null ? "" : codeInput.getText().toString().trim();
            if (code.isEmpty()) {
                error.setText("Nhập mã mời hoặc Home ID trước");
                error.setVisibility(View.VISIBLE);
                return;
            }
            if (!HOME_CODE.matcher(code).matches()) {
                error.setText("Mã chỉ dùng chữ, số, dấu gạch ngang hoặc gạch dưới");
                error.setVisibility(View.VISIBLE);
                return;
            }
            error.setVisibility(View.GONE);
            Toast.makeText(requireContext(), "Đang kiểm tra mã mời...", Toast.LENGTH_SHORT).show();
            controller.redeemInvite(code, (homeId, errorMessage) -> {
                if (errorMessage != null) {
                    error.setText(errorMessage);
                    error.setVisibility(View.VISIBLE);
                    return;
                }
                Toast.makeText(requireContext(), "Đã tham gia nhà", Toast.LENGTH_SHORT).show();
                activity.returnToMain();
            });
        });
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
        if (!isAdded()) return;
        String message = controller.homeState().message;
        if (message == null || message.equals(lastShownMessage)) return;
        lastShownMessage = message;
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
    }
}
