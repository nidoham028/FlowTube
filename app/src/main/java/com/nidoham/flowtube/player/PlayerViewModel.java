package com.nidoham.flowtube.player;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.exoplayer.ExoPlayer;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Enhanced ViewModel for managing media playback functionality with comprehensive state management,
 * error handling, and reactive programming patterns. This class serves as the bridge between
 * the UI layer and the PlayerManager singleton, providing a clean separation of concerns
 * while maintaining proper lifecycle awareness.
 */
public class PlayerViewModel extends AndroidViewModel implements PlayerManager.PlayerEventListener {

    private static final String TAG = "PlayerViewModel";

    // Core dependencies
    private final PlayerManager playerManager;
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);

    // LiveData for reactive UI updates
    private final MutableLiveData<PlayerState> playerStateLiveData = new MutableLiveData<>(PlayerState.IDLE);
    private final MutableLiveData<Long> positionLiveData = new MutableLiveData<>(0L);
    private final MutableLiveData<Long> durationLiveData = new MutableLiveData<>(0L);
    private final MutableLiveData<Float> playbackSpeedLiveData = new MutableLiveData<>(1.0f);
    private final MutableLiveData<Boolean> isPlayingLiveData = new MutableLiveData<>(false);
    private final MutableLiveData<PlayerError> errorLiveData = new MutableLiveData<>();
    private final MutableLiveData<List<PlayerManager.QualityInfo>> availableQualitiesLiveData = new MutableLiveData<>();
    private final MutableLiveData<List<String>> availableAudioLanguagesLiveData = new MutableLiveData<>();
    private final MutableLiveData<PlayerManager.QualityInfo> currentQualityLiveData = new MutableLiveData<>();

    /**
     * Enumeration representing the various states of the media player to provide
     * clear state management and facilitate proper UI updates.
     */
    public enum PlayerState {
        IDLE,
        BUFFERING,
        READY,
        ENDED,
        ERROR
    }

    /**
     * Data class encapsulating error information with additional context
     * for enhanced error handling and user feedback mechanisms.
     */
    public static class PlayerError {
        public final PlaybackException exception;
        public final boolean canRetry;
        public final String userFriendlyMessage;

        public PlayerError(@NonNull PlaybackException exception, boolean canRetry) {
            this.exception = exception;
            this.canRetry = canRetry;
            this.userFriendlyMessage = generateUserFriendlyMessage(exception);
        }

        private String generateUserFriendlyMessage(@NonNull PlaybackException exception) {
            switch (exception.errorCode) {
                case PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED:
                    return "Network connection failed. Please check your internet connection.";
                case PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT:
                    return "Connection timeout. Please try again.";
                case PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED:
                case PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED:
                    return "The media format is not supported or corrupted.";
                case PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND:
                    return "The requested media file could not be found.";
                default:
                    return "An error occurred during playback. Please try again.";
            }
        }
    }

    public PlayerViewModel(@NonNull Application application) {
        super(application);
        playerManager = PlayerManager.getInstance();
        Log.d(TAG, "PlayerViewModel initialized");
    }

    /**
     * Initializes the player manager with default configuration settings.
     * This method should be called during the early lifecycle of the associated UI component.
     *
     * @param context The context required for player initialization
     */
    public void initializePlayer(@NonNull Context context) {
        initializePlayer(context, new PlayerManager.PlayerConfig());
    }

    /**
     * Initializes the player manager with custom configuration parameters.
     * Provides flexibility for advanced use cases requiring specific buffer settings
     * or cache configurations.
     *
     * @param context The context required for player initialization
     * @param config Custom configuration parameters for player setup
     */
    public void initializePlayer(@NonNull Context context, @NonNull PlayerManager.PlayerConfig config) {
        if (isInitialized.get()) {
            Log.w(TAG, "Player already initialized");
            return;
        }

        try {
            playerManager.initialize(context, config);
            playerManager.addListener(this);
            isInitialized.set(true);
            Log.i(TAG, "Player initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize player", e);
            errorLiveData.postValue(new PlayerError(
                new PlaybackException("Player initialization failed", e, PlaybackException.ERROR_CODE_UNSPECIFIED),
                false
            ));
        }
    }

    /**
     * Provides access to the underlying ExoPlayer instance for advanced use cases
     * that require direct player manipulation not covered by this ViewModel.
     *
     * @return The ExoPlayer instance, or null if not initialized
     */
    @Nullable
    public ExoPlayer getExoPlayer() {
        return isInitialized.get() ? playerManager.getPlayer() : null;
    }

    /**
     * Loads media content from a single URL containing both video and audio streams.
     * Automatically detects the media format and configures appropriate decoders.
     *
     * @param url The media URL to load and play
     */
    public void loadMedia(@NonNull String url) {
        if (!validatePlayerState("loadMedia")) return;

        try {
            playerManager.loadMedia(url);
            Log.d(TAG, "Loading media from URL: " + url);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load media", e);
            handleLoadingError(e);
        }
    }

    /**
     * Loads media content from a URL with additional subtitle configuration.
     * Enables multi-language subtitle support for enhanced accessibility.
     *
     * @param url The primary media URL
     * @param subtitleConfig Configuration for subtitle tracks
     */
    public void loadMediaWithSubtitles(@NonNull String url, @Nullable PlayerManager.SubtitleConfiguration subtitleConfig) {
        if (!validatePlayerState("loadMediaWithSubtitles")) return;

        try {
            playerManager.loadMedia(url, subtitleConfig);
            Log.d(TAG, "Loading media with subtitles from URL: " + url);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load media with subtitles", e);
            handleLoadingError(e);
        }
    }

    /**
     * Loads and synchronizes separate video and audio streams for playback.
     * This functionality is particularly useful for adaptive streaming scenarios
     * where video and audio tracks are delivered separately.
     *
     * @param videoUrl The URL containing the video stream
     * @param audioUrl The URL containing the audio stream
     */
    public void loadMediaWithSeparateStreams(@NonNull String videoUrl, @NonNull String audioUrl) {
        if (!validatePlayerState("loadMediaWithSeparateStreams")) return;

        try {
            playerManager.loadMediaWithSeparateStreams(videoUrl, audioUrl);
            Log.d(TAG, "Loading separate video and audio streams");
        } catch (Exception e) {
            Log.e(TAG, "Failed to load separate streams", e);
            handleLoadingError(e);
        }
    }

    /**
     * Initiates media playback. If the player is currently paused, this will resume playback
     * from the current position. If no media is loaded, this operation will be ignored.
     */
    public void play() {
        if (validatePlayerState("play")) {
            playerManager.play();
            Log.d(TAG, "Playback started");
        }
    }

    /**
     * Pauses media playback while maintaining the current playback position.
     * Playback can be resumed using the play method.
     */
    public void pause() {
        if (validatePlayerState("pause")) {
            playerManager.pause();
            Log.d(TAG, "Playback paused");
        }
    }

    /**
     * Stops media playback and resets the player to its initial state.
     * This operation releases media-specific resources while keeping the player initialized.
     */
    public void stop() {
        if (validatePlayerState("stop")) {
            playerManager.stop();
            Log.d(TAG, "Playback stopped");
        }
    }

    /**
     * Seeks to the specified position in the media timeline.
     * The position will be clamped to valid bounds automatically.
     *
     * @param positionMs The target position in milliseconds
     */
    public void seekTo(long positionMs) {
        if (validatePlayerState("seekTo")) {
            playerManager.seekTo(positionMs);
            Log.d(TAG, "Seeking to position: " + positionMs + "ms");
        }
    }

    /**
     * Adjusts the playback speed while maintaining audio pitch.
     * Speed values are automatically clamped to a reasonable range.
     *
     * @param speed The desired playback speed (1.0 = normal speed)
     */
    public void setPlaybackSpeed(float speed) {
        if (validatePlayerState("setPlaybackSpeed")) {
            playerManager.setPlaybackSpeed(speed);
            playbackSpeedLiveData.postValue(speed);
            Log.d(TAG, "Playback speed set to: " + speed);
        }
    }

    /**
     * Enables adaptive bitrate streaming for optimal quality based on network conditions.
     * This allows the player to automatically adjust quality during playback.
     */
    public void enableAdaptiveQuality() {
        if (validatePlayerState("enableAdaptiveQuality")) {
            playerManager.enableAdaptiveQuality();
            Log.d(TAG, "Adaptive quality enabled");
        }
    }

    /**
     * Manually selects a specific video quality by resolution height.
     * Disables adaptive streaming for the current session.
     *
     * @param height The target resolution height (e.g., 720, 1080)
     * @return true if the quality was successfully selected, false otherwise
     */
    public boolean selectVideoQuality(int height) {
        if (!validatePlayerState("selectVideoQuality")) return false;

        boolean success = playerManager.selectQuality(height);
        if (success) {
            Log.d(TAG, "Video quality selected: " + height + "p");
        } else {
            Log.w(TAG, "Failed to select video quality: " + height + "p");
        }
        return success;
    }

    /**
     * Selects the preferred audio language for multi-language content.
     * Uses standard language codes such as "en", "es", "fr".
     *
     * @param languageCode The ISO language code for the desired audio track
     */
    public void selectAudioLanguage(@NonNull String languageCode) {
        if (validatePlayerState("selectAudioLanguage")) {
            playerManager.selectAudioLanguage(languageCode);
            Log.d(TAG, "Audio language selected: " + languageCode);
        }
    }

    /**
     * Selects the preferred subtitle language for accessible content consumption.
     * Uses standard language codes such as "en", "es", "fr".
     *
     * @param languageCode The ISO language code for the desired subtitle track
     */
    public void selectSubtitleLanguage(@NonNull String languageCode) {
        if (validatePlayerState("selectSubtitleLanguage")) {
            playerManager.selectSubtitleLanguage(languageCode);
            Log.d(TAG, "Subtitle language selected: " + languageCode);
        }
    }

    /**
     * Disables all subtitle tracks for the current media session.
     * This can improve performance on devices with limited processing capabilities.
     */
    public void disableSubtitles() {
        if (validatePlayerState("disableSubtitles")) {
            playerManager.disableSubtitles();
            Log.d(TAG, "Subtitles disabled");
        }
    }

    // LiveData accessors for reactive UI programming
    public LiveData<PlayerState> getPlayerState() {
        return playerStateLiveData;
    }

    public LiveData<Long> getCurrentPosition() {
        return positionLiveData;
    }

    public LiveData<Long> getDuration() {
        return durationLiveData;
    }

    public LiveData<Float> getPlaybackSpeed() {
        return playbackSpeedLiveData;
    }

    public LiveData<Boolean> getIsPlaying() {
        return isPlayingLiveData;
    }

    public LiveData<PlayerError> getPlayerError() {
        return errorLiveData;
    }

    public LiveData<List<PlayerManager.QualityInfo>> getAvailableQualities() {
        return availableQualitiesLiveData;
    }

    public LiveData<List<String>> getAvailableAudioLanguages() {
        return availableAudioLanguagesLiveData;
    }

    public LiveData<PlayerManager.QualityInfo> getCurrentQuality() {
        return currentQualityLiveData;
    }

    // PlayerManager.PlayerEventListener implementation
    @Override
    public void onPlayerReady() {
        playerStateLiveData.postValue(PlayerState.READY);
        updateMediaInfo();
        Log.d(TAG, "Player ready");
    }

    @Override
    public void onPlayerError(@NonNull PlaybackException error, boolean canRetry) {
        playerStateLiveData.postValue(PlayerState.ERROR);
        errorLiveData.postValue(new PlayerError(error, canRetry));
        Log.e(TAG, "Player error occurred", error);
    }

    @Override
    public void onPlaybackStateChanged(int state) {
        PlayerState viewModelState = mapPlayerState(state);
        playerStateLiveData.postValue(viewModelState);
        
        boolean playing = playerManager.isPlaying();
        isPlayingLiveData.postValue(playing);
        
        if (playing) {
            startPositionUpdates();
        }
        
        Log.d(TAG, "Playback state changed to: " + viewModelState);
    }

    @Override
    public void onTracksChanged(@NonNull Tracks tracks) {
        updateAvailableQualities();
        updateAvailableAudioLanguages();
        Log.d(TAG, "Tracks changed");
    }

    @Override
    public void onQualityChanged(int height, long bitrate) {
        PlayerManager.QualityInfo qualityInfo = new PlayerManager.QualityInfo(height, 0, bitrate, null);
        currentQualityLiveData.postValue(qualityInfo);
        Log.d(TAG, "Quality changed to: " + height + "p");
    }

    // Helper methods
    private boolean validatePlayerState(String operation) {
        if (!isInitialized.get()) {
            Log.w(TAG, "Cannot perform " + operation + ": Player not initialized");
            return false;
        }
        return true;
    }

    private void handleLoadingError(Exception e) {
        PlaybackException playbackException = new PlaybackException(
            "Failed to load media content",
            e,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED
        );
        errorLiveData.postValue(new PlayerError(playbackException, true));
    }

    private PlayerState mapPlayerState(int exoPlayerState) {
        switch (exoPlayerState) {
            case Player.STATE_IDLE:
                return PlayerState.IDLE;
            case Player.STATE_BUFFERING:
                return PlayerState.BUFFERING;
            case Player.STATE_READY:
                return PlayerState.READY;
            case Player.STATE_ENDED:
                return PlayerState.ENDED;
            default:
                return PlayerState.IDLE;
        }
    }

    private void updateMediaInfo() {
        long duration = playerManager.getDuration();
        durationLiveData.postValue(duration);
        
        float speed = playerManager.getPlaybackSpeed();
        playbackSpeedLiveData.postValue(speed);
    }

    private void updateAvailableQualities() {
        List<PlayerManager.QualityInfo> qualities = playerManager.getAvailableQualities();
        availableQualitiesLiveData.postValue(qualities);
    }

    private void updateAvailableAudioLanguages() {
        List<String> languages = playerManager.getAvailableAudioLanguages();
        availableAudioLanguagesLiveData.postValue(languages);
    }

    private void startPositionUpdates() {
        // Position updates would typically be handled by a background thread or handler
        // This is a simplified representation - actual implementation would use proper threading
        long position = playerManager.getCurrentPosition();
        positionLiveData.postValue(position);
    }

    /**
     * Cleans up resources and removes listeners when the ViewModel is no longer needed.
     * This method is automatically called by the Android framework during lifecycle management.
     */
    @Override
    protected void onCleared() {
        super.onCleared();
        
        if (isInitialized.get()) {
            playerManager.removeListener(this);
            playerManager.release();
            Log.d(TAG, "PlayerViewModel resources cleaned up");
        }
        
        isInitialized.set(false);
    }
}