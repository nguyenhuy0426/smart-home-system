package com.example.smart_home_mobile_app.ui.screens;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.smart_home_mobile_app.R;
import com.example.smart_home_mobile_app.auth.AuthUser;
import com.example.smart_home_mobile_app.ui.MobileDashboardActivity;
import com.example.smart_home_mobile_app.ui.SmartHomeAppController;

public class AccountFragment extends Fragment {
    private SmartHomeAppController controller;

    public AccountFragment() {
        super(R.layout.fragment_account);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        MobileDashboardActivity activity = (MobileDashboardActivity) requireActivity();
        controller = activity.getController();

        view.findViewById(R.id.btn_back).setOnClickListener(v -> requireActivity().onBackPressed());
        view.findViewById(R.id.row_manage_homes).setOnClickListener(v -> activity.navigateToManageHomes());
        view.findViewById(R.id.btn_logout).setOnClickListener(v -> controller.logout());

        TextView email = view.findViewById(R.id.tv_account_email);
            AuthUser user = controller.authState().user;
        if (user == null) {
            email.setText("Chưa đăng nhập");
        } else {
            email.setText(user.email == null || user.email.trim().isEmpty() ? "Tài khoản Firebase" : user.email);
        }
    }
}
