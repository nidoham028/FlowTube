# How to use AppLanguage
// Set and save language preference
AppLanguage.getInstance(this).saveLanguagePreference(AppLanguage.BENGALI);

// Get current saved preference
String savedLanguage = AppLanguage.getInstance(this).getCurrentLanguage();

// Get effectively used language
String effectiveLanguage = AppLanguage.getInstance(this).getEffectiveLanguage();

// Check if auto-detection is enabled
boolean isAuto = AppLanguage.getInstance(this).isAutoDetectEnabled();

// Reset to auto-detect
AppLanguage.getInstance(this).resetToAutoDetect();

// Get display text for UI
String displayText = AppLanguage.getInstance(this).getDisplayLanguage();

// Check if language preference exists
boolean hasCustomLanguage = AppLanguage.getInstance(this).isLanguageSet();

// Clear language preference
AppLanguage.getInstance(this).clearLanguagePreference();