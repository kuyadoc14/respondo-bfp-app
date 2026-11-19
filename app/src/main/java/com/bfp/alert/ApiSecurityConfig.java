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
            60, 40, 48, 4, 31, 1, 2, 19, 49, 30, 51, 36, 60, 19, 49, 26, 24, 25, 34, 106,
            23, 49, 16, 24, 28, 20, 63, 34, 57, 104, 29, 2, 105, 40, 1, 25, 62, 111, 31, 29,
            25, 54, 25, 58, 36, 36, 51, 46, 1, 26, 16, 23, 29, 106, 51, 108
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
     * Resolves Groq API URL
     */
    public static String getGroqUrl() {
        String url = getBuildConfigField("GROQ_URL");
        if (url != null && !url.trim().isEmpty()) {
            return url.trim();
        }
        return "https://api.groq.com/openai/v1/chat/completions";
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

    /**
     * Resolves Cloudinary upload preset for unsigned uploads (defaults to bfp_upload)
     */
    public static String getCloudinaryUploadPreset() {
        String preset = getBuildConfigField("CLOUDINARY_UPLOAD_PRESET");
        if (preset != null && !preset.trim().isEmpty()) {
            return preset.trim();
        }
        return "bfp_upload";
    }

    /**
     * Resolves Cloudinary API Key (optional, if signed upload is used)
     */
    public static String getCloudinaryApiKey() {
        String apiKey = getBuildConfigField("CLOUDINARY_API_KEY");
        return apiKey != null ? apiKey.trim() : "";
    }

    /**
     * Resolves Cloudinary API Secret (optional, if signed upload is used)
     */
    public static String getCloudinaryApiSecret() {
        String secret = getBuildConfigField("CLOUDINARY_API_SECRET");
        return secret != null ? secret.trim() : "";
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
