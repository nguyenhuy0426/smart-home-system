package com.example.smart_home_mobile_app.auth;

import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.FacebookAuthProvider;
import com.google.firebase.auth.GoogleAuthProvider;

public class FirebaseAuthAdapter implements AuthAdapter {
    private final FirebaseAuth auth;

    public FirebaseAuthAdapter(FirebaseAuth auth) {
        this.auth = auth;
    }

    @Override
    public AuthUser currentUser() {
        FirebaseUser user = auth.getCurrentUser();
        return user == null ? null : toAuthUser(user);
    }

    @Override
    public void signIn(String email, String password, AuthCallback callback) {
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    FirebaseUser user = result.getUser();
                    callback.onResult(user == null
                            ? new AuthResult.Failure("Firebase returned no authenticated user")
                            : new AuthResult.Success(toAuthUser(user)));
                })
                .addOnFailureListener(e -> callback.onResult(new AuthResult.Failure(safeMessage(e))));
    }

    @Override
    public void register(String email, String password, AuthCallback callback) {
        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    FirebaseUser user = result.getUser();
                    callback.onResult(user == null
                            ? new AuthResult.Failure("Firebase returned no registered user")
                            : new AuthResult.Success(toAuthUser(user)));
                })
                .addOnFailureListener(e -> callback.onResult(new AuthResult.Failure(safeMessage(e))));
    }

    public void signInWithGoogleIdToken(String idToken, AuthCallback callback) {
        signInWithCredential(GoogleAuthProvider.getCredential(idToken, null), callback);
    }

    public void signInWithFacebookAccessToken(String accessToken, AuthCallback callback) {
        signInWithCredential(FacebookAuthProvider.getCredential(accessToken), callback);
    }

    @Override
    public void signOut() {
        auth.signOut();
    }

    private void signInWithCredential(AuthCredential credential, AuthCallback callback) {
        auth.signInWithCredential(credential)
                .addOnSuccessListener(result -> {
                    FirebaseUser user = result.getUser();
                    callback.onResult(user == null
                            ? new AuthResult.Failure("Firebase returned no authenticated user")
                            : new AuthResult.Success(toAuthUser(user)));
                })
                .addOnFailureListener(e -> callback.onResult(new AuthResult.Failure(safeMessage(e))));
    }

    private AuthUser toAuthUser(FirebaseUser user) {
        return new AuthUser(user.getUid(), user.getEmail());
    }

    private String safeMessage(Throwable error) {
        if (error instanceof FirebaseAuthException) {
            String code = ((FirebaseAuthException) error).getErrorCode();
            if ("ERROR_OPERATION_NOT_ALLOWED".equals(code)) {
                return "Phương thức đăng nhập này chưa được bật trong Firebase Authentication.";
            }
            if ("ERROR_EMAIL_ALREADY_IN_USE".equals(code)) {
                return "Email này đã có tài khoản. Hãy quay lại và đăng nhập.";
            }
            if ("ERROR_WEAK_PASSWORD".equals(code)) {
                return "Mật khẩu chưa đáp ứng chính sách bảo mật của Firebase.";
            }
            if ("ERROR_INVALID_EMAIL".equals(code)) {
                return "Địa chỉ email không hợp lệ.";
            }
            if ("ERROR_NETWORK_REQUEST_FAILED".equals(code)) {
                return "Không kết nối được Firebase. Hãy kiểm tra mạng và thử lại.";
            }
        }
        String message = error.getMessage();
        return message == null || message.trim().isEmpty() ? "Firebase authentication failed" : message;
    }
}
