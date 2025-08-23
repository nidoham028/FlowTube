package com.nidoham.flowtube.core.language;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import java.util.Locale;

/**
 * Centralized manager for application language settings and localization.
 * Automatically detects device language as default, with user override capability.
 */
public class AppLanguage {
    private static final String PREFS_NAME = "FlowTubeLanguagePrefs";
    private static final String KEY_LANGUAGE = "selected_language";
    
    public static final String ENGLISH = "en";
    public static final String BENGALI = "bn";
    public static final String AUTO = "auto"; // Value for automatic detection
    
    private static AppLanguage instance;
    private final SharedPreferences prefs;
    private final Context context;

    private AppLanguage(Context context) {
        this.context = context.getApplicationContext();
        prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized AppLanguage getInstance(Context context) {
        if (instance == null) {
            instance = new AppLanguage(context);
        }
        return instance;
    }

    public void setLanguage(String languageCode) {
        prefs.edit().putString(KEY_LANGUAGE, languageCode).apply();
        applyLanguageSetting(languageCode);
    }

    public String getCurrentLanguage() {
        return prefs.getString(KEY_LANGUAGE, AUTO); // Default to auto-detection
    }

    public void initialize() {
        String savedLanguage = getCurrentLanguage();
        applyLanguageSetting(savedLanguage);
    }

    public boolean isAutoDetectEnabled() {
        return AUTO.equals(getCurrentLanguage());
    }

    public String getDeviceLanguage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return LocaleListCompat.getAdjustedDefault().get(0).getLanguage();
        } else {
            return Locale.getDefault().getLanguage();
        }
    }

    public String getEffectiveLanguage() {
        if (isAutoDetectEnabled()) {
            return getDeviceLanguage();
        }
        return getCurrentLanguage();
    }

    private void applyLanguageSetting(String languageCode) {
        if (AUTO.equals(languageCode)) {
            // Use device's default language
            applyDeviceLanguage();
        } else {
            // Use user-selected language
            applySpecificLanguage(languageCode);
        }
    }

    private void applyDeviceLanguage() {
        String deviceLanguage = getDeviceLanguage();
        // Only apply if it's a supported language
        if (isSupportedLanguage(deviceLanguage)) {
            applySpecificLanguage(deviceLanguage);
        } else {
            // Fallback to English if device language isn't supported
            applySpecificLanguage(ENGLISH);
        }
    }

    private void applySpecificLanguage(String languageCode) {
        LocaleListCompat locale = (languageCode == null || languageCode.isEmpty()) ?
                LocaleListCompat.getEmptyLocaleList() :
                LocaleListCompat.forLanguageTags(languageCode);
        AppCompatDelegate.setApplicationLocales(locale);
    }

    private boolean isSupportedLanguage(String languageCode) {
        return ENGLISH.equals(languageCode) || BENGALI.equals(languageCode);
    }

    public void saveLanguagePreference(String languageCode) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_LANGUAGE, languageCode);
        editor.apply();
        applyLanguageSetting(languageCode);
    }

    public void resetToAutoDetect() {
        setLanguage(AUTO);
    }

    public String getDisplayLanguage() {
        if (isAutoDetectEnabled()) {
            return "Auto (" + getDeviceLanguage().toUpperCase() + ")";
        }
        return getCurrentLanguage().toUpperCase();
    }

    public boolean isLanguageSet() {
        return prefs.contains(KEY_LANGUAGE);
    }

    public void clearLanguagePreference() {
        prefs.edit().remove(KEY_LANGUAGE).apply();
        applyLanguageSetting(AUTO);
    }
}