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
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;
import com.google.android.material.button.MaterialButton;

import java.util.Arrays;

public class LoginFragment extends Fragment implements SmartHomeAppController.Listener {
    private SmartHomeAppController controller;
    private CallbackManager facebookCallbackManager;
    private boolean registering;

    private EditText emailInput;
    private EditText passwordInput;
    private TextView subtitle;
    private TextView error;
    private TextView previewNote;
    private MaterialButton primary;
    private MaterialButton toggle;
    private MaterialButton google;
    private MaterialButton facebook;
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
        facebook = view.findViewById(R.id.btn_facebook);
        preview = view.findViewById(R.id.btn_preview);
        progress = view.findViewById(R.id.login_progress);
        facebookCallbackManager = CallbackManager.Factory.create();

        LoginManager.getInstance().registerCallback(
                facebookCallbackManager,
                new FacebookCallback<LoginResult>() {
                    @Override
                    public void onSuccess(LoginResult loginResult) {
                        controller.signInWithFacebookAccessToken(
                                loginResult.getAccessToken().getToken());
                    }

                    @Override
                    public void onCancel() {
                        controller.reportAuthFailure("Đã hủy đăng nhập Facebook.");
                    }

                    @Override
                    public void onError(@NonNull FacebookException exception) {
                        String message = exception.getMessage();
                        controller.reportAuthFailure(message == null || message.trim().isEmpty()
                                ? "Đăng nhập Facebook thất bại."
                                : "Đăng nhập Facebook thất bại: " + message);
                    }
                });

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
        facebook.setOnClickListener(v -> LoginManager.getInstance().logInWithReadPermissions(
                requireActivity(), Arrays.asList("public_profile", "email")));
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
    public void onDestroyView() {
        if (facebookCallbackManager != null) {
            LoginManager.getInstance().unregisterCallback(facebookCallbackManager);
            facebookCallbackManager = null;
        }
        super.onDestroyView();
    }

    @Override
    public void onControllerStateChanged() {
        render();
    }

    private void render() {
        AuthUiState state = controller.authState();
        boolean busy = state.status == AuthStatus.LOADING;
        boolean providersEnabled = !busy && state.status != AuthStatus.CONFIG_REQUIRED;

        subtitle.setText(registering
                ? "Enter your email and password, then tap Create account"
                : "Sign in to your homes");
        emailInput.setEnabled(!busy);
        passwordInput.setEnabled(!busy);

        if (state.message != null) {
            error.setText(state.message);
            error.setVisibility(View.VISIBLE);
        } else {
            error.setVisibility(View.GONE);
        }

        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        primary.setText(busy ? "" : (registering ? "Create account" : "Sign in"));
        primary.setEnabled(!busy && state.status != AuthStatus.CONFIG_REQUIRED);
        toggle.setText(registering ? "Back to sign in" : "Create account");
        toggle.setEnabled(!busy);
        google.setEnabled(providersEnabled);
        facebook.setEnabled(providersEnabled);
        preview.setEnabled(!busy);
    }
}
