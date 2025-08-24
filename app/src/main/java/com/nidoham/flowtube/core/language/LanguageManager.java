package com.nidoham.flowtube.core.language;

// Correct code for LanguageManager.java

import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

/**
 * Manages the application's language settings.
 */
public class LanguageManager {

    private static final String PREFS_NAME = "LanguagePrefs";
    private static final String KEY_LANGUAGE = "selected_language";

    public static final String LANGUAGE_CODE_ENGLISH = "en";
    public static final String LANGUAGE_CODE_BENGALI = "bn";

    private final SharedPreferences sharedPreferences;

    // This is the constructor the error says is missing.
    // It requires a Context to work.
    public LanguageManager(Context context) {
        this.sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Sets the application's language and persists the choice.
     */
    public void setLanguage(String languageCode) {
        persistLanguage(languageCode);
        updateAppLocale(languageCode);
    }

    /**
     * Returns the currently selected language code.
     */
    public String getCurrentLanguage() {
        return sharedPreferences.getString(KEY_LANGUAGE,
                AppCompatDelegate.getApplicationLocales().toLanguageTags());
    }

    /**
     * This is the method the error says is missing.
     * It should be called from your Application class.
     */
    public void initialize() {
        String savedLanguage = sharedPreferences.getString(KEY_LANGUAGE, null);
        if (savedLanguage != null) {
            updateAppLocale(savedLanguage);
        }
    }

    private void persistLanguage(String languageCode) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_LANGUAGE, languageCode);
        editor.apply();
    }

    private void updateAppLocale(String languageCode) {
        if (languageCode == null || languageCode.isEmpty()) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList());
        } else {
            LocaleListCompat appLocale = LocaleListCompat.forLanguageTags(languageCode);
            AppCompatDelegate.setApplicationLocales(appLocale);
        }
    }
}