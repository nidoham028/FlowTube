package com.nidoham.flowtube.stream.prefs;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;

/**
 * Utility class for managing streaming preferences with optimized performance
 * and reduced SharedPreferences access overhead.
 * 
 * Use initialize() once before other methods to set up SharedPreferences.
 */
public class PrefsHelper {
    private static final String PREF_NAME = "stream_prefs";
    private static final String KEY_AUDIO_MODE = "audio_mode";
    private static final String KEY_VIDEO_MODE = "video_mode";

    private static final String DEFAULT_AUDIO_MODE = "low";
    private static final String DEFAULT_VIDEO_MODE = "low";

    private static SharedPreferences sPreferences;

    /**
     * Initialize the SharedPreferences instance.
     * Call once, typically in Application.onCreate()
     *
     * @param context Context to access SharedPreferences
     */
    public static synchronized void initialize(@NonNull Context context) {
        if (sPreferences == null) {
            sPreferences = context.getApplicationContext()
                    .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        }
    }

    @NonNull
    private static synchronized SharedPreferences getPrefs(@NonNull Context context) {
        if (sPreferences == null) {
            initialize(context);
        }
        return sPreferences;
    }

    // ---------- AUDIO ----------
    public static void setAudioMode(@NonNull Context context, @NonNull String mode) {
        getPrefs(context).edit()
                .putString(KEY_AUDIO_MODE, mode)
                .apply();
    }

    @NonNull
    public static String getAudioMode(@NonNull Context context) {
        return getPrefs(context).getString(KEY_AUDIO_MODE, DEFAULT_AUDIO_MODE);
    }

    // ---------- VIDEO ----------
    public static void setVideoMode(@NonNull Context context, @NonNull String mode) {
        getPrefs(context).edit()
                .putString(KEY_VIDEO_MODE, mode)
                .apply();
    }

    @NonNull
    public static String getVideoMode(@NonNull Context context) {
        return getPrefs(context).getString(KEY_VIDEO_MODE, DEFAULT_VIDEO_MODE);
    }

    // ---------- BULK OPERATIONS ----------
    /**
     * Set both audio and video modes in single atomic apply for better performance
     * 
     * @param context Context to access SharedPreferences
     * @param audioMode audio mode string value
     * @param videoMode video mode string value
     */
    public static void setModes(@NonNull Context context, @NonNull String audioMode, @NonNull String videoMode) {
        getPrefs(context).edit()
                .putString(KEY_AUDIO_MODE, audioMode)
                .putString(KEY_VIDEO_MODE, videoMode)
                .apply();
    }

    /**
     * Get both audio and video modes as a single data object
     * 
     * @param context Context to access SharedPreferences
     * @return StreamModes object containing audio and video modes
     */
    @NonNull
    public static StreamModes getModes(@NonNull Context context) {
        SharedPreferences prefs = getPrefs(context);
        return new StreamModes(
                prefs.getString(KEY_AUDIO_MODE, DEFAULT_AUDIO_MODE),
                prefs.getString(KEY_VIDEO_MODE, DEFAULT_VIDEO_MODE)
        );
    }

    /**
     * Reset all streaming preferences to default values
     *
     * @param context Context to access SharedPreferences
     */
    public static void resetToDefaults(@NonNull Context context) {
        getPrefs(context).edit()
                .putString(KEY_AUDIO_MODE, DEFAULT_AUDIO_MODE)
                .putString(KEY_VIDEO_MODE, DEFAULT_VIDEO_MODE)
                .apply();
    }

    /**
     * Data container class for audio and video streaming modes
     */
    public static class StreamModes {
        public final String audioMode;
        public final String videoMode;

        public StreamModes(String audioMode, String videoMode) {
            this.audioMode = audioMode;
            this.videoMode = videoMode;
        }

        /**
         * Check if both audio and video modes are high quality
         *
         * @return true if both modes are "high", false otherwise
         */
        public boolean isHighQuality() {
            return "high".equals(audioMode) && "high".equals(videoMode);
        }

        /**
         * Check if both audio and video modes are low quality
         *
         * @return true if both modes are "low", false otherwise
         */
        public boolean isLowQuality() {
            return "low".equals(audioMode) && "low".equals(videoMode);
        }
    }
}
