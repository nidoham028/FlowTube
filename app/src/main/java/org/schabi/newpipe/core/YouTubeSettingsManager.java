package org.schabi.newpipe.core;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Set;
import java.util.HashSet;

public class YouTubeSettingsManager {

    private static final String PREF_NAME = "YouTubeSettings";
    
    // Video Playback Settings
    private static final String KEY_VIDEO_QUALITY = "video_quality";
    private static final String KEY_VIDEO_QUALITY_MOBILE = "video_quality_mobile";
    private static final String KEY_VIDEO_QUALITY_WIFI = "video_quality_wifi";
    private static final String KEY_AUTOPLAY_ENABLED = "autoplay_enabled";
    private static final String KEY_AUTOPLAY_ON_HOME = "autoplay_on_home";
    private static final String KEY_MUTED_PLAYBACK = "muted_playback";
    private static final String KEY_CAPTIONS_ENABLED = "captions_enabled";
    private static final String KEY_CAPTION_SIZE = "caption_size";
    private static final String KEY_CAPTION_LANGUAGE = "caption_language";
    private static final String KEY_PLAYBACK_SPEED = "playback_speed";
    private static final String KEY_AMBIENT_MODE = "ambient_mode";
    
    // Privacy and Safety Settings
    private static final String KEY_RESTRICTED_MODE = "restricted_mode";
    private static final String KEY_LOCATION_ENABLED = "location_enabled";
    private static final String KEY_SEARCH_HISTORY = "search_history_enabled";
    private static final String KEY_WATCH_HISTORY = "watch_history_enabled";
    private static final String KEY_PAUSE_SEARCH_HISTORY = "pause_search_history";
    private static final String KEY_PAUSE_WATCH_HISTORY = "pause_watch_history";
    private static final String KEY_ACTIVITY_CONTROLS = "activity_controls_enabled";
    
    // Appearance Settings
    private static final String KEY_DARK_THEME = "dark_theme_enabled";
    private static final String KEY_LANGUAGE = "display_language";
    private static final String KEY_DISPLAY_LANGUAGE = "display_language";
    private static final String KEY_REGION_CODE = "region_code";
    private static final String KEY_TIME_ZONE = "time_zone";
    
    // Notification Settings
    private static final String KEY_NOTIFICATIONS_ENABLED = "notifications_enabled";
    private static final String KEY_SUBSCRIPTION_NOTIFICATIONS = "subscription_notifications";
    private static final String KEY_RECOMMENDED_VIDEOS = "recommended_video_notifications";
    private static final String KEY_ACTIVITY_ON_CHANNEL = "activity_notifications";
    private static final String KEY_REPLIES_TO_COMMENTS = "reply_notifications";
    private static final String KEY_MENTION_NOTIFICATIONS = "mention_notifications";
    private static final String KEY_SHARED_CONTENT_NOTIFICATIONS = "shared_content_notifications";
    private static final String KEY_NOTIFICATION_SOUND = "notification_sound_enabled";
    private static final String KEY_NOTIFICATION_VIBRATION = "notification_vibration";
    
    // Data Usage Settings
    private static final String KEY_LIMIT_MOBILE_DATA = "limit_mobile_data_usage";
    private static final String KEY_UPLOAD_QUALITY_MOBILE = "upload_quality_mobile";
    private static final String KEY_UPLOAD_QUALITY_WIFI = "upload_quality_wifi";
    private static final String KEY_SMART_DOWNLOADS = "smart_downloads_enabled";
    private static final String KEY_DOWNLOAD_QUALITY = "download_quality";
    private static final String KEY_DOWNLOAD_OVER_WIFI_ONLY = "download_wifi_only";
    
    // Accessibility Settings
    private static final String KEY_HIGH_CONTRAST = "high_contrast_enabled";
    private static final String KEY_REDUCED_MOTION = "reduced_motion";
    private static final String KEY_SCREEN_READER_SUPPORT = "screen_reader_support";
    
    // Content Preferences
    private static final String KEY_TRENDING_LOCATION = "trending_location";
    private static final String KEY_BLOCKED_CHANNELS = "blocked_channels";
    private static final String KEY_INTERESTED_TOPICS = "interested_topics";
    private static final String KEY_MATURE_CONTENT = "mature_content_enabled";
    
    // Live Chat and Comments
    private static final String KEY_LIVE_CHAT_ENABLED = "live_chat_enabled";
    private static final String KEY_SLOW_MODE = "slow_mode_enabled";
    private static final String KEY_CHAT_REPLAY = "chat_replay_enabled";
    private static final String KEY_HOLD_INAPPROPRIATE_COMMENTS = "hold_inappropriate_comments";
    
    // Creator Studio Settings (if applicable)
    private static final String KEY_CREATOR_MODE = "creator_mode_enabled";
    private static final String KEY_MONETIZATION = "monetization_enabled";
    private static final String KEY_COPYRIGHT_MATCH = "copyright_match_enabled";
    
    private final SharedPreferences prefs;

    public YouTubeSettingsManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // Video Playback Settings
    public void setVideoQuality(String quality) {
        prefs.edit().putString(KEY_VIDEO_QUALITY, quality).apply();
    }

    public String getVideoQuality() {
        return prefs.getString(KEY_VIDEO_QUALITY, "Auto");
    }

    public void setVideoQualityForMobileData(String quality) {
        prefs.edit().putString(KEY_VIDEO_QUALITY_MOBILE, quality).apply();
    }

    public String getVideoQualityForMobileData() {
        return prefs.getString(KEY_VIDEO_QUALITY_MOBILE, "360p");
    }

    public void setVideoQualityForWiFi(String quality) {
        prefs.edit().putString(KEY_VIDEO_QUALITY_WIFI, quality).apply();
    }

    public String getVideoQualityForWiFi() {
        return prefs.getString(KEY_VIDEO_QUALITY_WIFI, "720p");
    }

    public void setAutoplayEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTOPLAY_ENABLED, enabled).apply();
    }

    public boolean isAutoplayEnabled() {
        return prefs.getBoolean(KEY_AUTOPLAY_ENABLED, true);
    }

    public void setAutoplayOnHomeEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTOPLAY_ON_HOME, enabled).apply();
    }

    public boolean isAutoplayOnHomeEnabled() {
        return prefs.getBoolean(KEY_AUTOPLAY_ON_HOME, true);
    }

    public void setMutedPlaybackEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_MUTED_PLAYBACK, enabled).apply();
    }

    public boolean isMutedPlaybackEnabled() {
        return prefs.getBoolean(KEY_MUTED_PLAYBACK, false);
    }

    public void setCaptionsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_CAPTIONS_ENABLED, enabled).apply();
    }

    public boolean areCaptionsEnabled() {
        return prefs.getBoolean(KEY_CAPTIONS_ENABLED, false);
    }

    public void setCaptionSize(String size) {
        prefs.edit().putString(KEY_CAPTION_SIZE, size).apply();
    }

    public String getCaptionSize() {
        return prefs.getString(KEY_CAPTION_SIZE, "Medium");
    }

    public void setCaptionLanguage(String language) {
        prefs.edit().putString(KEY_CAPTION_LANGUAGE, language).apply();
    }

    public String getCaptionLanguage() {
        return prefs.getString(KEY_CAPTION_LANGUAGE, "Auto");
    }

    public void setPlaybackSpeed(float speed) {
        prefs.edit().putFloat(KEY_PLAYBACK_SPEED, speed).apply();
    }

    public float getPlaybackSpeed() {
        return prefs.getFloat(KEY_PLAYBACK_SPEED, 1.0f);
    }

    public void setAmbientModeEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_AMBIENT_MODE, enabled).apply();
    }

    public boolean isAmbientModeEnabled() {
        return prefs.getBoolean(KEY_AMBIENT_MODE, true);
    }

    // Privacy and Safety Settings
    public void setRestrictedModeEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_RESTRICTED_MODE, enabled).apply();
    }

    public boolean isRestrictedModeEnabled() {
        return prefs.getBoolean(KEY_RESTRICTED_MODE, false);
    }

    public void setLocationEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_LOCATION_ENABLED, enabled).apply();
    }

    public boolean isLocationEnabled() {
        return prefs.getBoolean(KEY_LOCATION_ENABLED, true);
    }

    public void setSearchHistoryEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SEARCH_HISTORY, enabled).apply();
    }

    public boolean isSearchHistoryEnabled() {
        return prefs.getBoolean(KEY_SEARCH_HISTORY, true);
    }

    public void setWatchHistoryEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_WATCH_HISTORY, enabled).apply();
    }

    public boolean isWatchHistoryEnabled() {
        return prefs.getBoolean(KEY_WATCH_HISTORY, true);
    }

    public void pauseSearchHistory(boolean paused) {
        prefs.edit().putBoolean(KEY_PAUSE_SEARCH_HISTORY, paused).apply();
    }

    public boolean isSearchHistoryPaused() {
        return prefs.getBoolean(KEY_PAUSE_SEARCH_HISTORY, false);
    }

    public void pauseWatchHistory(boolean paused) {
        prefs.edit().putBoolean(KEY_PAUSE_WATCH_HISTORY, paused).apply();
    }

    public boolean isWatchHistoryPaused() {
        return prefs.getBoolean(KEY_PAUSE_WATCH_HISTORY, false);
    }

    public void setActivityControlsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_ACTIVITY_CONTROLS, enabled).apply();
    }

    public boolean areActivityControlsEnabled() {
        return prefs.getBoolean(KEY_ACTIVITY_CONTROLS, true);
    }

    // Appearance Settings
    public void setDarkThemeEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_DARK_THEME, enabled).apply();
    }

    public boolean isDarkThemeEnabled() {
        return prefs.getBoolean(KEY_DARK_THEME, false);
    }

    public void setDisplayLanguage(String language) {
        prefs.edit().putString(KEY_LANGUAGE, language).apply();
    }

    public String getDisplayLanguage() {
        return prefs.getString(KEY_LANGUAGE, "English");
    }

    public void setRegionCode(String regionCode) {
        prefs.edit().putString(KEY_REGION_CODE, regionCode).apply();
    }

    public String getRegionCode() {
        return prefs.getString(KEY_REGION_CODE, "US");
    }

    public void setTimeZone(String timeZone) {
        prefs.edit().putString(KEY_TIME_ZONE, timeZone).apply();
    }

    public String getTimeZone() {
        return prefs.getString(KEY_TIME_ZONE, "Auto");
    }

    // Notification Settings
    public void setNotificationsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled).apply();
    }

    public boolean areNotificationsEnabled() {
        return prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true);
    }

    public void setSubscriptionNotificationsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SUBSCRIPTION_NOTIFICATIONS, enabled).apply();
    }

    public boolean areSubscriptionNotificationsEnabled() {
        return prefs.getBoolean(KEY_SUBSCRIPTION_NOTIFICATIONS, true);
    }

    public void setRecommendedVideoNotificationsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_RECOMMENDED_VIDEOS, enabled).apply();
    }

    public boolean areRecommendedVideoNotificationsEnabled() {
        return prefs.getBoolean(KEY_RECOMMENDED_VIDEOS, false);
    }

    public void setActivityNotificationsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_ACTIVITY_ON_CHANNEL, enabled).apply();
    }

    public boolean areActivityNotificationsEnabled() {
        return prefs.getBoolean(KEY_ACTIVITY_ON_CHANNEL, true);
    }

    public void setReplyNotificationsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_REPLIES_TO_COMMENTS, enabled).apply();
    }

    public boolean areReplyNotificationsEnabled() {
        return prefs.getBoolean(KEY_REPLIES_TO_COMMENTS, true);
    }

    public void setMentionNotificationsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_MENTION_NOTIFICATIONS, enabled).apply();
    }

    public boolean areMentionNotificationsEnabled() {
        return prefs.getBoolean(KEY_MENTION_NOTIFICATIONS, true);
    }

    public void setSharedContentNotificationsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SHARED_CONTENT_NOTIFICATIONS, enabled).apply();
    }

    public boolean areSharedContentNotificationsEnabled() {
        return prefs.getBoolean(KEY_SHARED_CONTENT_NOTIFICATIONS, true);
    }

    public void setNotificationSoundEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_NOTIFICATION_SOUND, enabled).apply();
    }

    public boolean isNotificationSoundEnabled() {
        return prefs.getBoolean(KEY_NOTIFICATION_SOUND, true);
    }

    public void setNotificationVibrationEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_NOTIFICATION_VIBRATION, enabled).apply();
    }

    public boolean isNotificationVibrationEnabled() {
        return prefs.getBoolean(KEY_NOTIFICATION_VIBRATION, true);
    }

    // Data Usage Settings
    public void setLimitMobileDataUsage(boolean enabled) {
        prefs.edit().putBoolean(KEY_LIMIT_MOBILE_DATA, enabled).apply();
    }

    public boolean isLimitMobileDataUsage() {
        return prefs.getBoolean(KEY_LIMIT_MOBILE_DATA, false);
    }

    public void setUploadQualityForMobileData(String quality) {
        prefs.edit().putString(KEY_UPLOAD_QUALITY_MOBILE, quality).apply();
    }

    public String getUploadQualityForMobileData() {
        return prefs.getString(KEY_UPLOAD_QUALITY_MOBILE, "360p");
    }

    public void setUploadQualityForWiFi(String quality) {
        prefs.edit().putString(KEY_UPLOAD_QUALITY_WIFI, quality).apply();
    }

    public String getUploadQualityForWiFi() {
        return prefs.getString(KEY_UPLOAD_QUALITY_WIFI, "1080p");
    }

    public void setSmartDownloadsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SMART_DOWNLOADS, enabled).apply();
    }

    public boolean areSmartDownloadsEnabled() {
        return prefs.getBoolean(KEY_SMART_DOWNLOADS, false);
    }

    public void setDownloadQuality(String quality) {
        prefs.edit().putString(KEY_DOWNLOAD_QUALITY, quality).apply();
    }

    public String getDownloadQuality() {
        return prefs.getString(KEY_DOWNLOAD_QUALITY, "720p");
    }

    public void setDownloadOverWiFiOnly(boolean enabled) {
        prefs.edit().putBoolean(KEY_DOWNLOAD_OVER_WIFI_ONLY, enabled).apply();
    }

    public boolean isDownloadOverWiFiOnly() {
        return prefs.getBoolean(KEY_DOWNLOAD_OVER_WIFI_ONLY, true);
    }

    // Accessibility Settings
    public void setHighContrastEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_HIGH_CONTRAST, enabled).apply();
    }

    public boolean isHighContrastEnabled() {
        return prefs.getBoolean(KEY_HIGH_CONTRAST, false);
    }

    public void setReducedMotionEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_REDUCED_MOTION, enabled).apply();
    }

    public boolean isReducedMotionEnabled() {
        return prefs.getBoolean(KEY_REDUCED_MOTION, false);
    }

    public void setScreenReaderSupportEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SCREEN_READER_SUPPORT, enabled).apply();
    }

    public boolean isScreenReaderSupportEnabled() {
        return prefs.getBoolean(KEY_SCREEN_READER_SUPPORT, false);
    }

    // Content Preferences
    public void setTrendingLocation(String location) {
        prefs.edit().putString(KEY_TRENDING_LOCATION, location).apply();
    }

    public String getTrendingLocation() {
        return prefs.getString(KEY_TRENDING_LOCATION, "United States");
    }

    public void setBlockedChannels(Set<String> channels) {
        prefs.edit().putStringSet(KEY_BLOCKED_CHANNELS, channels).apply();
    }

    public Set<String> getBlockedChannels() {
        return prefs.getStringSet(KEY_BLOCKED_CHANNELS, new HashSet<>());
    }

    public void setInterestedTopics(Set<String> topics) {
        prefs.edit().putStringSet(KEY_INTERESTED_TOPICS, topics).apply();
    }

    public Set<String> getInterestedTopics() {
        return prefs.getStringSet(KEY_INTERESTED_TOPICS, new HashSet<>());
    }

    public void setMatureContentEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_MATURE_CONTENT, enabled).apply();
    }

    public boolean isMatureContentEnabled() {
        return prefs.getBoolean(KEY_MATURE_CONTENT, false);
    }

    // Live Chat and Comments
    public void setLiveChatEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_LIVE_CHAT_ENABLED, enabled).apply();
    }

    public boolean isLiveChatEnabled() {
        return prefs.getBoolean(KEY_LIVE_CHAT_ENABLED, true);
    }

    public void setSlowModeEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SLOW_MODE, enabled).apply();
    }

    public boolean isSlowModeEnabled() {
        return prefs.getBoolean(KEY_SLOW_MODE, false);
    }

    public void setChatReplayEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_CHAT_REPLAY, enabled).apply();
    }

    public boolean isChatReplayEnabled() {
        return prefs.getBoolean(KEY_CHAT_REPLAY, true);
    }

    public void setHoldInappropriateCommentsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_HOLD_INAPPROPRIATE_COMMENTS, enabled).apply();
    }

    public boolean isHoldInappropriateCommentsEnabled() {
        return prefs.getBoolean(KEY_HOLD_INAPPROPRIATE_COMMENTS, true);
    }

    // Creator Studio Settings
    public void setCreatorModeEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_CREATOR_MODE, enabled).apply();
    }

    public boolean isCreatorModeEnabled() {
        return prefs.getBoolean(KEY_CREATOR_MODE, false);
    }

    public void setMonetizationEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_MONETIZATION, enabled).apply();
    }

    public boolean isMonetizationEnabled() {
        return prefs.getBoolean(KEY_MONETIZATION, false);
    }

    public void setCopyrightMatchEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_COPYRIGHT_MATCH, enabled).apply();
    }

    public boolean isCopyrightMatchEnabled() {
        return prefs.getBoolean(KEY_COPYRIGHT_MATCH, true);
    }

    // Utility Methods
    public void resetToDefaultSettings() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.clear();
        
        // Set default values
        editor.putString(KEY_VIDEO_QUALITY, "Auto");
        editor.putString(KEY_VIDEO_QUALITY_MOBILE, "360p");
        editor.putString(KEY_VIDEO_QUALITY_WIFI, "720p");
        editor.putBoolean(KEY_AUTOPLAY_ENABLED, true);
        editor.putBoolean(KEY_AUTOPLAY_ON_HOME, true);
        editor.putBoolean(KEY_DARK_THEME, false);
        editor.putString(KEY_REGION_CODE, "US");
        editor.putString(KEY_DISPLAY_LANGUAGE, "English");
        editor.putBoolean(KEY_NOTIFICATIONS_ENABLED, true);
        editor.putBoolean(KEY_DOWNLOAD_OVER_WIFI_ONLY, true);
        
        editor.apply();
    }

    public void exportSettings() {
        // Implementation for exporting settings to JSON or other format
        // This would be useful for backup and restore functionality
    }

    public void importSettings(String settingsData) {
        // Implementation for importing settings from JSON or other format
        // This would complement the export functionality
    }

    public boolean hasCustomSettings() {
        return prefs.getAll().size() > 0;
    }
}