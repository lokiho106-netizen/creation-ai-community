package com.creationai.community.settings;

import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class SecretStore {
    private static final String ANDROID_KEY_STORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "creation_ai_community_secrets_v1";
    private static final String CIPHER_PREF = "silicon_flow_key_cipher";
    private static final String IV_PREF = "silicon_flow_key_iv";
    private static final String LEGACY_PREF = "silicon_flow_key";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private final SharedPreferences prefs;

    SecretStore(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    String loadApiKey() {
        String cipherText = prefs.getString(CIPHER_PREF, "");
        String ivText = prefs.getString(IV_PREF, "");
        if (!cipherText.isEmpty() && !ivText.isEmpty()) {
            try {
                Cipher cipher = Cipher.getInstance(TRANSFORMATION);
                cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(),
                        new GCMParameterSpec(128, Base64.decode(ivText, Base64.NO_WRAP)));
                byte[] plain = cipher.doFinal(Base64.decode(cipherText, Base64.NO_WRAP));
                return new String(plain, StandardCharsets.UTF_8);
            } catch (Exception ignored) {
                return "";
            }
        }

        String legacy = prefs.getString(LEGACY_PREF, "");
        if (!legacy.isEmpty()) saveApiKey(legacy);
        return legacy;
    }

    void saveApiKey(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            prefs.edit().remove(CIPHER_PREF).remove(IV_PREF).remove(LEGACY_PREF).apply();
            return;
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
            byte[] encrypted = cipher.doFinal(trimmed.getBytes(StandardCharsets.UTF_8));
            prefs.edit()
                    .putString(CIPHER_PREF, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                    .putString(IV_PREF, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                    .remove(LEGACY_PREF)
                    .apply();
        } catch (Exception e) {
            throw new IllegalStateException("设备密钥存储不可用", e);
        }
    }

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEY_STORE);
        keyStore.load(null);
        java.security.Key existing = keyStore.getKey(KEY_ALIAS, null);
        if (existing instanceof SecretKey) return (SecretKey) existing;

        KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE);
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
