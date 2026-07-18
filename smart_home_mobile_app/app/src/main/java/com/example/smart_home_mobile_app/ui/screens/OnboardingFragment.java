package com.example.smart_home_mobile_app.ui.screens;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.smart_home_mobile_app.R;
import com.example.smart_home_mobile_app.ui.MobileDashboardActivity;

public class OnboardingFragment extends Fragment {

    public OnboardingFragment() {
        super(R.layout.fragment_onboarding);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.findViewById(R.id.get_started_button).setOnClickListener(v ->
                ((MobileDashboardActivity) requireActivity()).navigateToLoginFromOnboarding());
    }
}
