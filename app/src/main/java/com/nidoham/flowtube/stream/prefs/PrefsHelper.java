package com.nidoham.flowtube.stream.prefs;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;

/**
 * Utility class for managing streaming preferences with optimized performance
 * and reduced SharedPreferences access overhead.
 */
public class PrefsHelper {
    private static final String PREF_NAME = "stream_prefs";
    private static final String KEY_AUDIO_MODE = "audio_mode";
    private static final String KEY_VIDEO_MODE = "video_mode";
    
    // Default values as constants for consistency
    private static final String DEFAULT_AUDIO_MODE = "low";
    private static final String DEFAULT_VIDEO_MODE = "low";
    
    // Cache SharedPreferences instance to avoid repeated getSharedPreferences() calls
    private static SharedPreferences sPreferences;
    
    /**
     * Initialize the preferences instance (call once, typically in Application.onCreate())
     */
    public static void initialize(@NonNull Context context) {
        if (sPreferences == null) {
            sPreferences = context.getApplicationContext()
                    .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        }
    }
    
    /**
     * Get cached SharedPreferences instance
     */
    @NonNull
    private static SharedPreferences getPrefs(@NonNull Context context) {
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
     * Set both audio and video modes in a single transaction for better performance
     */
    public static void setModes(@NonNull Context context, @NonNull String audioMode, @NonNull String videoMode) {
        getPrefs(context).edit()
                .putString(KEY_AUDIO_MODE, audioMode)
                .putString(KEY_VIDEO_MODE, videoMode)
                .apply();
    }
    
    /**
     * Get both modes efficiently in a single object
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
     * Reset all preferences to default values
     */
    public static void resetToDefaults(@NonNull Context context) {
        getPrefs(context).edit()
                .putString(KEY_AUDIO_MODE, DEFAULT_AUDIO_MODE)
                .putString(KEY_VIDEO_MODE, DEFAULT_VIDEO_MODE)
                .apply();
    }
    
    /**
     * Data class to hold both streaming modes
     */
    public static class StreamModes {
        public final String audioMode;
        public final String videoMode;
        
        StreamModes(String audioMode, String videoMode) {
            this.audioMode = audioMode;
            this.videoMode = videoMode;
        }
        
        public boolean isHighQuality() {
            return "high".equals(audioMode) && "high".equals(videoMode);
        }
        
        public boolean isLowQuality() {
            return "low".equals(audioMode) && "low".equals(videoMode);
        }
    }
}