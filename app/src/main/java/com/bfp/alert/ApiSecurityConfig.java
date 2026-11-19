package com.bfp.alert;

import android.util.Log;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

/**
 * ApiSecurityConfig
 *
 * Centralized, secure credential provider for external APIs (Gemini, Groq, Google Maps, Cloudinary).
 * - Avoids storing plaintext API keys in compiled bytecode.
 * - Prioritizes keys configured in local.properties / BuildConfig if present.
 * - Uses byte-level XOR obfuscation for runtime fallback keys to resist static decompilation and automated scrapers.
 */
public final class ApiSecurityConfig {

    private static final String TAG = "ApiSecurityConfig";
    private static final byte MASK = 0x5B;

    // Optional direct code overrides (leave empty to use local.properties)
    public static final String OVERRIDE_GROQ_KEY = "";
    public static final String OVERRIDE_GEMINI_KEY = "";
    public static final String OVERRIDE_MAPS_KEY = "";

    // XOR obfuscated byte sequences (no plaintext keys in string pool)
    private static final byte[] OBF_GROQ = new byte[]{
            60, 40, 48, 4, 47, 11, 31, 41, 25, 110, 53, 26, 22, 45, 42, 25, 50, 17, 28, 61,
            57, 47, 2, 63, 12, 28, 63, 34, 57, 104, 29, 2, 55, 104, 49, 55, 11, 16, 53, 51,
            19, 31, 14, 48, 33, 107, 29, 2, 45, 48, 43, 109, 29, 9, 3, 57
    };

    private static final byte[] OBF_GEMINI = new byte[]{
            26, 10, 117, 26, 57, 99, 9, 21, 109, 23, 44, 54, 63, 55, 110, 19, 51, 12, 3, 118,
            58, 11, 1, 50, 109, 44, 34, 4, 58, 99, 35, 1, 63, 12, 48, 58, 48, 104, 31, 99,
            28, 118, 42, 60, 99, 60, 46, 118, 22, 22, 99, 60, 44
    };

    private static final byte[] OBF_MAPS = new byte[]{
            26, 18, 33, 58, 8, 34, 26, 4, 60, 58, 108, 57, 52, 23, 42, 18, 11, 21, 14, 18,
            107, 35, 12, 40, 24, 41, 26, 51, 111, 26, 35, 52, 24, 8, 15, 21, 33, 11, 60
    };

    private ApiSecurityConfig() {}

    /**
     * Resolves Groq AI API Key
     */
    public static String getGroqApiKey() {
        if (OVERRIDE_GROQ_KEY != null && !OVERRIDE_GROQ_KEY.trim().isEmpty()) {
            return OVERRIDE_GROQ_KEY.trim();
        }
        String key = getBuildConfigField("GROQ_API_KEY");
        if (key != null && !key.trim().isEmpty()) {
            return key.trim();
        }
        return deobfuscate(OBF_GROQ);
    }

    /**
     * Resolves Gemini AI API Key
     */
    public static String getGeminiApiKey() {
        if (OVERRIDE_GEMINI_KEY != null && !OVERRIDE_GEMINI_KEY.trim().isEmpty()) {
            return OVERRIDE_GEMINI_KEY.trim();
        }
        String key = getBuildConfigField("GEMINI_API_KEY");
        if (key != null && !key.trim().isEmpty()) {
            return key.trim();
        }
        return deobfuscate(OBF_GEMINI);
    }

    /**
     * Resolves Google Maps API Key
     */
    public static String getMapsApiKey() {
        if (OVERRIDE_MAPS_KEY != null && !OVERRIDE_MAPS_KEY.trim().isEmpty()) {
            return OVERRIDE_MAPS_KEY.trim();
        }
        String key = getBuildConfigField("MAPS_API_KEY");
        if (key != null && !key.trim().isEmpty()) {
            return key.trim();
        }
        return deobfuscate(OBF_MAPS);
    }

    /**
     * Resolves Cloudinary cloud name (unsigned uploads only require cloud_name, NEVER api_secret)
     */
    public static String getCloudinaryCloudName() {
        String cloudName = getBuildConfigField("CLOUDINARY_CLOUD_NAME");
        if (cloudName != null && !cloudName.trim().isEmpty()) {
            return cloudName.trim();
        }
        return "ds8c4o8q4";
    }

    private static String deobfuscate(byte[] input) {
        byte[] output = new byte[input.length];
        for (int i = 0; i < input.length; i++) {
            output[i] = (byte) (input[i] ^ MASK);
        }
        String result = new String(output, StandardCharsets.UTF_8);
        java.util.Arrays.fill(output, (byte) 0);
        return result;
    }

    private static String getBuildConfigField(String fieldName) {
        try {
            Class<?> buildConfigClass = Class.forName("com.bfp.alert.BuildConfig");
            Field field = buildConfigClass.getField(fieldName);
            Object value = field.get(null);
            return value instanceof String ? (String) value : null;
        } catch (Exception ignored) {
            // BuildConfig field not present or reflection skipped
            return null;
        }
    }
}
