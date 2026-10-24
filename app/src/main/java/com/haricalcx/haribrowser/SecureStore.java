package com.haricalcx.haribrowser;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.ByteBuffer;
import java.security.KeyStore;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Small encrypted preference store for bookmarks and opt-in history.
 * AES keys live in Android Keystore; only authenticated ciphertext is persisted.
 */
public final class SecureStore {
    private static final String KEY_ALIAS = "HariBrowser.DataKey.v1";
    private static final String PREFS_NAME = "hari_encrypted_data";
    private final String keyAlias;
    private final SharedPreferences preferences;

    public SecureStore(Context context) {
        this(context, false);
    }

    /**
     * Keep the private profile's encrypted namespace and Keystore key separate
     * from the standard profile as defense in depth. Private-mode UI operations
     * also forbid saving or importing bookmarks/history.
     */
    public SecureStore(Context context, boolean privateProfile) {
        Context app = context.getApplicationContext();
        String suffix = privateProfile ? ".private" : "";
        keyAlias = KEY_ALIAS + suffix;
        preferences = app.getSharedPreferences(PREFS_NAME + suffix, Context.MODE_PRIVATE);
    }

    public synchronized void put(String name, String value) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
            byte[] iv = cipher.getIV();
            byte[] encrypted = cipher.doFinal(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(4 + iv.length + encrypted.length);
            buffer.putInt(iv.length);
            buffer.put(iv);
            buffer.put(encrypted);
            preferences.edit().putString(name, Base64.encodeToString(buffer.array(), Base64.NO_WRAP)).apply();
        } catch (Exception ignored) {
            // Fail closed: do not fall back to storing sensitive data in plaintext.
            preferences.edit().remove(name).apply();
        }
    }

    public synchronized String get(String name, String fallback) {
        String encoded = preferences.getString(name, null);
        if (encoded == null) return fallback;
        try {
            byte[] all = Base64.decode(encoded, Base64.NO_WRAP);
            ByteBuffer buffer = ByteBuffer.wrap(all);
            int ivLength = buffer.getInt();
            if (ivLength < 12 || ivLength > 16 || buffer.remaining() <= ivLength) {
                throw new IllegalArgumentException("Invalid encrypted record");
            }
            byte[] iv = new byte[ivLength];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
            byte[] plain = cipher.doFinal(encrypted);
            return new String(plain, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // Corrupt or unauthenticated records are never returned.
            preferences.edit().remove(name).apply();
            return fallback;
        }
    }

    /**
     * Decrypt a value on the serialized storage queue and dispatch the result to the
     * caller-selected executor (normally an Activity's main executor). The callback
     * must still check its lifecycle before updating UI.
     */
    public void getAsync(String name, String fallback, Executor callbackExecutor,
                         Consumer<String> callback) {
        Objects.requireNonNull(callbackExecutor, "callbackExecutor");
        Objects.requireNonNull(callback, "callback");
        StorageTaskQueue.execute(() -> {
            String value = get(name, fallback);
            try {
                callbackExecutor.execute(() -> callback.accept(value));
            } catch (RuntimeException ignored) {
                // A shutting-down callback executor must not crash the storage worker.
            }
        });
    }

    public synchronized void remove(String name) {
        preferences.edit().remove(name).apply();
    }

    /** Queue encryption and persistence away from the UI thread. Tasks share a serial process queue. */
    public void putAsync(String name, String value) {
        StorageTaskQueue.execute(() -> put(name, value));
    }

    /** Queue removal so it is ordered with other asynchronous writes. */
    public void removeAsync(String name) {
        StorageTaskQueue.execute(() -> remove(name));
    }

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (keyStore.containsAlias(keyAlias)) {
            KeyStore.Entry entry = keyStore.getEntry(keyAlias, null);
            if (entry instanceof KeyStore.SecretKeyEntry) {
                return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
            }
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build();
        generator.init(spec);
        return generator.generateKey();
    }
}
