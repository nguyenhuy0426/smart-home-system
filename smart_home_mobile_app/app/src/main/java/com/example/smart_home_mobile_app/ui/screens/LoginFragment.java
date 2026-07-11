package com.example.smart_home_mobile_app.ui.screens;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.smart_home_mobile_app.BuildConfig;
import com.example.smart_home_mobile_app.R;
import com.example.smart_home_mobile_app.ui.AuthStatus;
import com.example.smart_home_mobile_app.ui.AuthUiState;
import com.example.smart_home_mobile_app.ui.MobileDashboardActivity;
import com.example.smart_home_mobile_app.ui.SmartHomeAppController;
import com.google.android.material.button.MaterialButton;

public class LoginFragment extends Fragment implements SmartHomeAppController.Listener {
    private SmartHomeAppController controller;
    private boolean registering;

    private EditText emailInput;
    private EditText passwordInput;
    private TextView subtitle;
    private TextView error;
    private TextView previewNote;
    private MaterialButton primary;
    private MaterialButton toggle;
    private MaterialButton google;
    private MaterialButton apple;
    private MaterialButton preview;
    private ProgressBar progress;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_login, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        controller = ((MobileDashboardActivity) requireActivity()).getController();
        emailInput = view.findViewById(R.id.input_email);
        passwordInput = view.findViewById(R.id.input_password);
        subtitle = view.findViewById(R.id.login_subtitle);
        error = view.findViewById(R.id.login_error);
        previewNote = view.findViewById(R.id.tv_preview_note);
        primary = view.findViewById(R.id.btn_primary);
        toggle = view.findViewById(R.id.btn_toggle_mode);
        google = view.findViewById(R.id.btn_google);
        apple = view.findViewById(R.id.btn_apple);
        preview = view.findViewById(R.id.btn_preview);
        progress = view.findViewById(R.id.login_progress);

        primary.setOnClickListener(v -> {
            String email = emailInput.getText().toString();
            String password = passwordInput.getText().toString();
            if (registering) {
                controller.register(email, password);
            } else {
                controller.signIn(email, password);
            }
        });
        toggle.setOnClickListener(v -> {
            registering = !registering;
            render();
        });
        google.setOnClickListener(v -> controller.signInWithGoogle(requireActivity()));
        apple.setOnClickListener(v -> controller.signInWithApple(requireActivity()));
        preview.setOnClickListener(v -> controller.enterPreviewMode());

        boolean debug = BuildConfig.DEBUG;
        preview.setVisibility(debug ? View.VISIBLE : View.GONE);
        previewNote.setVisibility(debug ? View.VISIBLE : View.GONE);
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
        super.onStop();
        controller.removeListener(this);
    }

    @Override
    public void onControllerStateChanged() {
        render();
    }

    private void render() {
        AuthUiState state = controller.authState();
        boolean busy = state.status == AuthStatus.LOADING;
        boolean providersEnabled = !busy && state.status != AuthStatus.CONFIG_REQUIRED;

        subtitle.setText(registering ? "Create a Firebase account" : "Sign in to your homes");
        emailInput.setEnabled(!busy);
        passwordInput.setEnabled(!busy);

        if (state.message != null) {
            error.setText(state.message);
            error.setVisibility(View.VISIBLE);
        } else {
            error.setVisibility(View.GONE);
        }

        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        primary.setText(busy ? "" : (registering ? "Register" : "Sign in"));
        primary.setEnabled(!busy && state.status != AuthStatus.CONFIG_REQUIRED);
        toggle.setText(registering ? "Use existing account" : "Create account");
        toggle.setEnabled(!busy);
        google.setEnabled(providersEnabled);
        apple.setEnabled(providersEnabled);
        preview.setEnabled(!busy);
    }
}
