package com.example.smart_home_mobile_app.auth;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class SecureSessionStore implements SessionStore {
    private static final String TAG = "SecureSessionStore";
    private static final String PREFERENCES = "secure_firebase_session";
    private static final String KEY_IV = "iv";
    private static final String KEY_PAYLOAD = "payload";
    private static final String KEY_ALIAS = "smart_home_mobile_session_v1";
    private static final String ANDROID_KEY_STORE = "AndroidKeyStore";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private final SharedPreferences preferences;

    public SecureSessionStore(Context context) {
        preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    @Override
    public void save(AuthUser user) {
        try {
            String plaintext = user.uid + "\n" + (user.email == null ? "" : user.email);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey());
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            preferences.edit()
                    .putString(KEY_IV, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                    .putString(KEY_PAYLOAD, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                    .apply();
        } catch (Exception error) {
            // Keystore operations can fail at runtime (corrupted/invalidated key, vendor
            // KeyStoreException). Losing session persistence must never crash sign-in;
            // FirebaseAuth's own persisted user still restores the session on next launch.
            Log.w(TAG, "Failed to persist session; clearing stored session", error);
            clear();
        }
    }

    @Override
    public AuthUser load() {
        try {
            String iv = preferences.getString(KEY_IV, null);
            if (iv == null) return null;
            String payload = preferences.getString(KEY_PAYLOAD, null);
            if (payload == null) return null;
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey(),
                    new GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)));
            String plaintext = new String(
                    cipher.doFinal(Base64.decode(payload, Base64.NO_WRAP)), StandardCharsets.UTF_8);
            String[] parts = plaintext.split("\n", 2);
            String uid = parts.length > 0 ? parts[0] : null;
            if (uid == null || uid.trim().isEmpty()) return null;
            String email = parts.length > 1 && !parts[1].trim().isEmpty() ? parts[1] : null;
            return new AuthUser(uid, email);
        } catch (Exception error) {
            clear();
            return null;
        }
    }

    @Override
    public void clear() {
        preferences.edit().clear().apply();
    }

    private SecretKey secretKey() throws GeneralSecurityException, IOException {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEY_STORE);
        keyStore.load(null);
        Key existing = keyStore.getKey(KEY_ALIAS, null);
        if (existing instanceof SecretKey) {
            return (SecretKey) existing;
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE);
        generator.init(new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }
}
