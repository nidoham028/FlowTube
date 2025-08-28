package com.nidoham.flowtube;

import android.app.UiModeManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

import com.nidoham.flowtube.databinding.ActivityPlayerBinding;
import com.nidoham.flowtube.player.PlayerViewModel;
import com.nidoham.flowtube.stream.extractor.StreamExtractor;
import org.schabi.newpipe.extractor.stream.StreamInfo;

/**
 * Professional video player activity with ExoPlayer integration.
 * Supports YouTube URL extraction, fullscreen playback, and Android TV compatibility.
 * 
 * Features:
 * - Stream extraction for YouTube URLs
 * - Fullscreen and orientation controls
 * - Auto-hiding controls with touch interaction
 * - Playback position persistence
 * - Android TV support
 */
public class PlayerActivity extends AppCompatActivity {

    private static final String TAG = "PlayerActivity";
    
    // Intent extras
    public static final String EXTRA_VIDEO_URL = "video_url";
    public static final String EXTRA_VIDEO_TITLE = "video_title";
    public static final String EXTRA_CHANNEL_NAME = "channel_name";
    public static final String EXTRA_PLAYBACK_POSITION = "playback_position";
    public static final String EXTRA_DIRECT_VIDEO_URL = "direct_video_url";
    
    // UI constants
    private static final int CONTROLS_HIDE_DELAY_MS = 2000;
    private static final int PROGRESS_UPDATE_INTERVAL_MS = 500;
    private static final int SEEK_INCREMENT_MS = 10000;
    private static final int SEEKBAR_MAX_VALUE = 1000;
    
    // Default values
    private static final String DEFAULT_TITLE = "Untitled";
    private static final String DEFAULT_CHANNEL = "";
    private static final long DEFAULT_POSITION = 0L;

    // UI Components
    private ActivityPlayerBinding binding;
    private PlayerViewModel playerViewModel;
    
    // Handlers and Runnables
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable controlsHideRunnable;
    
    // State variables
    private boolean isDestroyed = false;
    private boolean isFullscreen = false;
    private boolean isLandscape = false;
    private boolean isAndroidTv = false;
    private boolean isFirstCreate = true;
    
    // Video data
    private String videoUrl = "";
    private String videoTitle = "";
    private String channelName = "";
    private long playbackPosition = DEFAULT_POSITION;
    private String directVideoUrl = null;
    
    // Stream extraction
    private StreamExtractor streamExtractor;
    private String extractedVideoUrl = "";
    private String extractedAudioUrl = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        if (!initializeBinding()) {
            return; // Early exit if binding fails
        }
        
        initializeActivity(savedInstanceState);
        setupPlayer();
        loadVideoContent();
        setupUserInterface();
    }

    /**
     * Initialize view binding with error handling
     * @return true if successful, false otherwise
     */
    private boolean initializeBinding() {
        try {
            binding = ActivityPlayerBinding.inflate(getLayoutInflater());
            setContentView(binding.getRoot());
            return true;
        } catch (Exception e) {
            handleError("Failed to inflate layout", e, true);
            return false;
        }
    }

    /**
     * Initialize activity state and configuration
     */
    private void initializeActivity(@Nullable Bundle savedInstanceState) {
        setupSystemUI();
        detectDeviceConfiguration();
        initializeViewModel();
        initializeStreamExtractor();
        restoreOrExtractIntentData(savedInstanceState);
    }

    /**
     * Detect device configuration (Android TV, orientation)
     */
    private void detectDeviceConfiguration() {
        isAndroidTv = isAndroidTvDevice();
        int orientation = getResources().getConfiguration().orientation;
        isLandscape = (orientation == Configuration.ORIENTATION_LANDSCAPE);
    }

    /**
     * Initialize ViewModel and StreamExtractor
     */
    private void initializeViewModel() {
        playerViewModel = new ViewModelProvider(this).get(PlayerViewModel.class);
    }

    private void initializeStreamExtractor() {
        streamExtractor = new StreamExtractor(this);
    }

    /**
     * Restore state from savedInstanceState or extract from Intent
     */
    private void restoreOrExtractIntentData(@Nullable Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            restoreFromSavedState(savedInstanceState);
        } else {
            extractFromIntent();
        }
    }

    private void restoreFromSavedState(@NonNull Bundle savedInstanceState) {
        videoUrl = safeGetString(savedInstanceState, EXTRA_VIDEO_URL, "");
        videoTitle = safeGetString(savedInstanceState, EXTRA_VIDEO_TITLE, "");
        channelName = safeGetString(savedInstanceState, EXTRA_CHANNEL_NAME, "");
        playbackPosition = savedInstanceState.getLong(EXTRA_PLAYBACK_POSITION, DEFAULT_POSITION);
        directVideoUrl = safeGetString(savedInstanceState, EXTRA_DIRECT_VIDEO_URL, null);
        isFirstCreate = false;
    }

    private void extractFromIntent() {
        Intent intent = getIntent();
        if (intent != null) {
            videoUrl = safeGetString(intent, EXTRA_VIDEO_URL, "");
            videoTitle = safeGetString(intent, EXTRA_VIDEO_TITLE, "");
            channelName = safeGetString(intent, EXTRA_CHANNEL_NAME, "");
        }
        playbackPosition = DEFAULT_POSITION;
        directVideoUrl = null;
        isFirstCreate = true;
    }

    /**
     * Setup ExoPlayer and attach to PlayerView
     */
    private void setupPlayer() {
        playerViewModel.initializePlayer(this);
        final ExoPlayer player = playerViewModel.getExoPlayer();
        
        try {
            if (binding != null && binding.playerView != null) {
                binding.playerView.setPlayer(player);
            }
        } catch (Exception e) {
            handleError("Failed to attach player", e, false);
        }
    }

    /**
     * Load video content based on URL type and state
     */
    private void loadVideoContent() {
        if (!isFirstCreate) {
            restorePlaybackState();
            return;
        }

        if (isValidVideoUrl()) {
            if (isYouTubeUrl(videoUrl)) {
                extractAndLoadYouTubeContent();
            } else {
                loadDirectVideoUrl();
            }
        } else {
            handleError("Invalid video URL", null, true);
        }
    }

    private boolean isValidVideoUrl() {
        return videoUrl != null && !videoUrl.trim().isEmpty();
    }

    /**
     * Extract YouTube streams and load content
     */
    private void extractAndLoadYouTubeContent() {
        showLoading(true);
        
        final StreamUrlHolder urlHolder = new StreamUrlHolder();
        
        streamExtractor.extractAll(videoUrl, new StreamExtractor.OnStreamExtractionListener() {
            @Override
            public void onVideoReady(@NonNull String url) {
                runOnUiThread(() -> handleVideoStreamReady(url, urlHolder));
            }

            @Override
            public void onAudioReady(@NonNull String url) {
                runOnUiThread(() -> handleAudioStreamReady(url, urlHolder));
            }

            @Override
            public void onInformationReady(@NonNull StreamInfo streamInfo) {
                runOnUiThread(() -> updateStreamInfoUI(streamInfo));
            }

            @Override
            public void onExtractionError(@NonNull Exception error, @NonNull String operationType) {
                runOnUiThread(() -> handleExtractionError(error, operationType));
            }
        });
    }

    /**
     * Handle video stream extraction completion
     */
    private void handleVideoStreamReady(@NonNull String url, @NonNull StreamUrlHolder urlHolder) {
        Log.d(TAG, "Extracted video URL: " + url);
        urlHolder.videoUrl = url;
        extractedVideoUrl = url;
        
        if (!urlHolder.audioUrl.isEmpty()) {
            loadSeparateStreams(urlHolder.videoUrl, urlHolder.audioUrl);
        }
    }

    /**
     * Handle audio stream extraction completion
     */
    private void handleAudioStreamReady(@NonNull String url, @NonNull StreamUrlHolder urlHolder) {
        Log.d(TAG, "Extracted audio URL: " + url);
        urlHolder.audioUrl = url;
        extractedAudioUrl = url;
        
        if (!urlHolder.videoUrl.isEmpty()) {
            loadSeparateStreams(urlHolder.videoUrl, urlHolder.audioUrl);
        }
    }

    /**
     * Load video with separate video and audio streams
     */
    private void loadSeparateStreams(@NonNull String videoUrl, @NonNull String audioUrl) {
        try {
            playerViewModel.loadMediaWithSeparateStreams(videoUrl, audioUrl);
            seekToSavedPosition();
            showLoading(false);
        } catch (Exception e) {
            handleError("Failed to load separate streams", e, false);
        }
    }

    /**
     * Handle stream extraction errors
     */
    private void handleExtractionError(@NonNull Exception error, @NonNull String operationType) {
        Log.e(TAG, "Extraction error: " + operationType, error);
        showLoading(false);
        handleError("Failed to load video: " + error.getMessage(), null, true);
    }

    /**
     * Load direct video URL (non-YouTube)
     */
    private void loadDirectVideoUrl() {
        showLoading(true);
        try {
            directVideoUrl = videoUrl;
            playerViewModel.loadMedia(videoUrl);
            seekToSavedPosition();
            showLoading(false);
        } catch (Exception e) {
            showLoading(false);
            handleError("Failed to load video", e, true);
        }
    }

    /**
     * Restore playback state for configuration changes
     */
    private void restorePlaybackState() {
        try {
            final ExoPlayer player = playerViewModel.getExoPlayer();
            if (directVideoUrl != null && player != null && player.getMediaItemCount() == 0) {
                playerViewModel.loadMedia(directVideoUrl);
            }
            seekToSavedPosition();
        } catch (Exception e) {
            handleError("Failed to restore video position", e, false);
        }
    }

    /**
     * Seek to saved playback position if available
     */
    private void seekToSavedPosition() {
        final ExoPlayer player = playerViewModel.getExoPlayer();
        if (playbackPosition > 0 && player != null) {
            player.seekTo(playbackPosition);
        }
    }

    /**
     * Setup all user interface components
     */
    private void setupUserInterface() {
        final ExoPlayer player = playerViewModel.getExoPlayer();
        setupPlayerControls(player);
        setupSeekBarAndProgress(player);
        setupInitialUI();
        setupControlsOverlayAutoHide();
        configureInitialControlsVisibility();
        configureSystemUIForOrientation();
    }

    /**
     * Configure initial controls visibility based on orientation and device type
     */
    private void configureInitialControlsVisibility() {
        if (isLandscape && !isAndroidTv) {
            setAllControlsVisible(false);
        } else {
            setControlsOverlayVisible(false);
        }
    }

    /**
     * Configure system UI based on fullscreen state
     */
    private void configureSystemUIForOrientation() {
        if (isFullscreen) {
            hideSystemUI();
        } else {
            showSystemUI();
        }
    }

    /**
     * Update UI with extracted stream information
     */
    private void updateStreamInfoUI(@Nullable StreamInfo streamInfo) {
        if (streamInfo != null) {
            safeSetText(binding.txtTitle, streamInfo.getName());
            safeSetText(binding.txtChannelName, streamInfo.getUploaderName());
        }
    }

    /**
     * Setup player control button listeners
     */
    private void setupPlayerControls(@Nullable ExoPlayer player) {
        setupPlayPauseControl(player);
        setupSeekControls(player);
        setupOrientationControl();
        setupBackControl();
    }

    private void setupPlayPauseControl(@Nullable ExoPlayer player) {
        safeSetOnClickListener(binding.btnPlayPause, v -> {
            try {
                if (player != null && player.isPlaying()) {
                    playerViewModel.pause();
                } else {
                    playerViewModel.play();
                }
            } catch (Exception e) {
                handleError("Player control error", e, false);
            }
        });
    }

    private void setupSeekControls(@Nullable ExoPlayer player) {
        safeSetOnClickListener(binding.btnReplay10, v -> seekRelative(player, -SEEK_INCREMENT_MS));
        safeSetOnClickListener(binding.btnForward10, v -> seekRelative(player, SEEK_INCREMENT_MS));
    }

    /**
     * Seek relative to current position with bounds checking
     */
    private void seekRelative(@Nullable ExoPlayer player, long deltaMs) {
        try {
            if (player != null) {
                long currentPos = player.getCurrentPosition();
                long duration = player.getDuration();
                long newPos = Math.max(0, Math.min(currentPos + deltaMs, duration));
                player.seekTo(newPos);
            }
        } catch (Exception e) {
            String direction = deltaMs > 0 ? "forward" : "backward";
            handleError("Failed to seek " + direction, e, false);
        }
    }

    private void setupOrientationControl() {
        safeSetOnClickListener(binding.btnOrientation, v -> toggleOrientationOrFullscreen());
    }

    /**
     * Toggle orientation or fullscreen mode based on device type
     */
    private void toggleOrientationOrFullscreen() {
        try {
            if (isAndroidTv) {
                toggleFullscreenMode();
            } else {
                toggleOrientation();
            }
            updateSystemUIForFullscreen();
        } catch (Exception e) {
            handleError("Failed to change orientation", e, false);
        }
    }

    private void toggleFullscreenMode() {
        isFullscreen = !isFullscreen;
    }

    private void toggleOrientation() {
        int orientation = getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            isFullscreen = false;
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            isFullscreen = true;
        }
    }

    private void updateSystemUIForFullscreen() {
        if (isFullscreen) {
            hideSystemUI();
        } else {
            showSystemUI();
        }
    }

    private void setupBackControl() {
        safeSetOnClickListener(binding.btnBack, v -> {
            try {
                finish();
            } catch (Exception e) {
                handleError("Failed to exit", e, false);
            }
        });
    }

    /**
     * Setup seekbar and progress tracking
     */
    private void setupSeekBarAndProgress(@Nullable ExoPlayer player) {
        initializeSeekBar();
        setupPlayerListener(player);
        startProgressUpdateLoop(player);
        setupSeekBarInteraction(player);
    }

    private void initializeSeekBar() {
        final SeekBar seekBar = binding.videoProgress;
        if (seekBar != null) {
            seekBar.setMax(SEEKBAR_MAX_VALUE);
        }
    }

    private void setupPlayerListener(@Nullable ExoPlayer player) {
        if (player == null) return;
        
        final Player.Listener progressListener = new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                updatePlayPauseButton(isPlaying);
            }

            @Override
            public void onPlaybackStateChanged(int state) {
                updateSeekBar(player, binding.videoProgress);
            }

            @Override
            public void onPositionDiscontinuity(Player.PositionInfo oldPosition, 
                                               Player.PositionInfo newPosition, int reason) {
                updateSeekBar(player, binding.videoProgress);
            }
        };
        
        player.addListener(progressListener);
    }

    private void startProgressUpdateLoop(@Nullable ExoPlayer player) {
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                updateSeekBar(player, binding.videoProgress);
                if (!isDestroyed) {
                    mainHandler.postDelayed(this, PROGRESS_UPDATE_INTERVAL_MS);
                }
            }
        }, PROGRESS_UPDATE_INTERVAL_MS);
    }

    private void setupSeekBarInteraction(@Nullable ExoPlayer player) {
        final SeekBar seekBar = binding.videoProgress;
        if (seekBar == null) return;
        
        seekBar.setOnSeekBarChangeListener(new SeekBarChangeListener(player));
    }

    /**
     * Setup initial UI text content
     */
    private void setupInitialUI() {
        safeSetText(binding.txtTitle, videoTitle != null ? videoTitle : DEFAULT_TITLE);
        safeSetText(binding.txtChannelName, channelName != null ? channelName : DEFAULT_CHANNEL);
    }

    /**
     * Setup auto-hiding controls with touch interaction
     */
    private void setupControlsOverlayAutoHide() {
        View touchSurface = binding.playerView;
        if (touchSurface == null) return;

        touchSurface.setOnTouchListener(this::handleTouchEvent);
    }

    /**
     * Handle touch events for controls visibility
     */
    private boolean handleTouchEvent(View v, MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            cancelHideTimer();
            toggleControlsVisibility();
            return true;
        }
        return false;
    }

    private void cancelHideTimer() {
        if (controlsHideRunnable != null) {
            mainHandler.removeCallbacks(controlsHideRunnable);
        }
    }

    private void toggleControlsVisibility() {
        if (isLandscape && !isAndroidTv) {
            toggleAllControls();
        } else {
            toggleControlsOverlay();
        }
    }

    private void toggleAllControls() {
        boolean visible = !areAllControlsVisible();
        setAllControlsVisible(visible);
        if (visible) {
            startHideTimer();
        }
    }

    private void toggleControlsOverlay() {
        boolean visible = !isControlsOverlayVisible();
        setControlsOverlayVisible(visible);
        if (visible) {
            startHideTimer();
        }
    }

    private void startHideTimer() {
        cancelHideTimer();
        
        controlsHideRunnable = () -> {
            if (isLandscape && !isAndroidTv) {
                setAllControlsVisible(false);
            } else {
                setControlsOverlayVisible(false);
            }
        };

        mainHandler.postDelayed(controlsHideRunnable, CONTROLS_HIDE_DELAY_MS);
    }

    private void setControlsOverlayVisible(boolean visible) {
        if (binding.controlsOverlay != null) {
            binding.controlsOverlay.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
        }
    }

    private boolean isControlsOverlayVisible() {
        return binding.controlsOverlay != null && 
               binding.controlsOverlay.getVisibility() == View.VISIBLE;
    }

    private void setAllControlsVisible(boolean visible) {
        setControlsOverlayVisible(visible);
    }

    private boolean areAllControlsVisible() {
        return isControlsOverlayVisible();
    }

    /**
     * Setup system UI colors
     */
    private void setupSystemUI() {
        try {
            getWindow().setStatusBarColor(ContextCompat.getColor(this, android.R.color.black));
            getWindow().setNavigationBarColor(ContextCompat.getColor(this, android.R.color.black));
        } catch (Exception e) {
            Log.e(TAG, "Error setting up system UI", e);
        }
    }

    /**
     * Show or hide loading indicator
     */
    private void showLoading(boolean show) {
        if (binding != null && binding.loadingIndicator != null) {
            binding.loadingIndicator.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * Update play/pause button icon based on playback state
     */
    private void updatePlayPauseButton(boolean isPlaying) {
        if (binding != null && binding.btnPlayPause != null) {
            int iconRes = isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play;
            binding.btnPlayPause.setImageResource(iconRes);
        }
    }

    /**
     * Update seekbar progress and time displays
     */
    private void updateSeekBar(@Nullable Player player, @Nullable SeekBar seekBar) {
        long duration = player != null ? player.getDuration() : 0;
        long position = player != null ? player.getCurrentPosition() : 0;
        
        updateSeekBarProgress(seekBar, position, duration);
        updateTimeDisplays(position, duration);
    }

    private void updateSeekBarProgress(@Nullable SeekBar seekBar, long position, long duration) {
        if (seekBar != null) {
            if (duration > 0) {
                int progress = (int) ((position * SEEKBAR_MAX_VALUE) / duration);
                seekBar.setProgress(progress);
            } else {
                seekBar.setProgress(0);
            }
        }
    }

    private void updateTimeDisplays(long position, long duration) {
        safeSetText(binding != null ? binding.txtCurrentTime : null, formatTime(position));
        safeSetText(binding != null ? binding.txtTotalTime : null, formatTime(duration));
    }

    /**
     * Format milliseconds to MM:SS format
     */
    private String formatTime(long millis) {
        int totalSeconds = (int) (millis / 1000);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    /**
     * Hide system UI for fullscreen experience
     */
    private void hideSystemUI() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                hideSystemUIModern();
            } else {
                hideSystemUILegacy();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error hiding system UI", e);
        }
    }

    private void hideSystemUIModern() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void hideSystemUILegacy() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
        );
    }

    /**
     * Show system UI
     */
    private void showSystemUI() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                showSystemUIModern();
            } else {
                showSystemUILegacy();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error showing system UI", e);
        }
    }

    private void showSystemUIModern() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void showSystemUILegacy() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (playerViewModel != null) {
            playerViewModel.pause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Resume logic can be added here if needed
    }

    @Override
    protected void onDestroy() {
        cleanupResources();
        super.onDestroy();
    }

    /**
     * Clean up all resources and handlers
     */
    private void cleanupResources() {
        isDestroyed = true;
        
        if (controlsHideRunnable != null) {
            mainHandler.removeCallbacks(controlsHideRunnable);
            controlsHideRunnable = null;
        }
        
        if (streamExtractor != null) {
            streamExtractor.cleanup();
            streamExtractor = null;
        }
        
        binding = null;
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        savePlaybackState(outState);
    }

    /**
     * Save current playback state to bundle
     */
    private void savePlaybackState(@NonNull Bundle outState) {
        ExoPlayer player = playerViewModel != null ? playerViewModel.getExoPlayer() : null;
        if (player != null) {
            outState.putLong(EXTRA_PLAYBACK_POSITION, player.getCurrentPosition());
        }
        
        outState.putString(EXTRA_VIDEO_URL, videoUrl);
        outState.putString(EXTRA_VIDEO_TITLE, videoTitle);
        outState.putString(EXTRA_CHANNEL_NAME, channelName);
        outState.putString(EXTRA_DIRECT_VIDEO_URL, directVideoUrl);
    }

    /**
     * Safely set text on TextView with null checking
     */
    private void safeSetText(@Nullable TextView textView, @Nullable String text) {
        if (textView != null && text != null) {
            textView.setText(text);
        }
    }

    /**
     * Safely set click listener on View with null checking
     */
    private void safeSetOnClickListener(@Nullable View view, @Nullable View.OnClickListener listener) {
        if (view != null && listener != null) {
            view.setOnClickListener(listener);
        }
    }

    /**
     * Safely get string from Bundle with default value
     */
    private String safeGetString(@NonNull Bundle bundle, @NonNull String key, @NonNull String defaultValue) {
        String result = bundle.getString(key);
        return result != null ? result : defaultValue;
    }

    /**
     * Safely get string from Intent with default value
     */
    private String safeGetString(@NonNull Intent intent, @NonNull String key, @NonNull String defaultValue) {
        String result = intent.getStringExtra(key);
        return result != null ? result : defaultValue;
    }

    /**
     * Check if URL is a YouTube URL
     */
    private boolean isYouTubeUrl(@Nullable String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }
        
        try {
            String normalizedUrl = url.trim().toLowerCase();
            return normalizedUrl.contains("youtube.com/watch") ||
                   normalizedUrl.contains("youtu.be/") ||
                   normalizedUrl.contains("youtube.com/embed/") ||
                   normalizedUrl.contains("m.youtube.com/watch");
        } catch (Exception e) {
            Log.e(TAG, "Error checking YouTube URL", e);
            return false;
        }
    }

    /**
     * Check if device is Android TV
     */
    private boolean isAndroidTvDevice() {
        UiModeManager uiModeManager = (UiModeManager) getSystemService(Context.UI_MODE_SERVICE);
        return uiModeManager != null && 
               uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
    }

    /**
     * Handle errors with consistent logging and user feedback
     */
    private void handleError(@NonNull String message, @Nullable Exception exception, boolean shouldFinish) {
        Log.e(TAG, message, exception);
        
        String displayMessage = exception != null ? 
            message + ": " + exception.getMessage() : message;
        
        Toast.makeText(this, displayMessage, Toast.LENGTH_LONG).show();
        
        if (shouldFinish) {
            finish();
        }
    }

    /**
     * Helper class to hold extracted stream URLs
     */
    private static class StreamUrlHolder {
        String videoUrl = "";
        String audioUrl = "";
    }

    /**
     * SeekBar change listener with proper state management
     */
    private class SeekBarChangeListener implements SeekBar.OnSeekBarChangeListener {
        private final ExoPlayer player;
        private boolean wasPlaying = false;

        public SeekBarChangeListener(@Nullable ExoPlayer player) {
            this.player = player;
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            wasPlaying = player != null && player.isPlaying();
            if (playerViewModel != null) {
                playerViewModel.pause();
            }
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            seekToProgress(seekBar.getProgress());
            if (wasPlaying && playerViewModel != null) {
                playerViewModel.play();
            }
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            // No action needed during progress change
        }

        private void seekToProgress(int progress) {
            if (player == null) return;
            
            long duration = player.getDuration();
            if (duration > 0) {
                long seekTo = (progress * duration) / SEEKBAR_MAX_VALUE;
                player.seekTo(seekTo);
            }
        }
    }
}
