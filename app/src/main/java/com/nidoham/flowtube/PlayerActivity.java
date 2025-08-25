package com.nidoham.flowtube;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.util.Util;
import com.nidoham.flowtube.databinding.ActivityPlayerBinding;

import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.stream.StreamInfo;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Professional video player activity implementing comprehensive view binding,
 * lifecycle management, orientation handling, thread safety, and optimal user experience.
 * 
 * Features:
 * - Complete lifecycle management with proper state persistence
 * - Thread-safe operations with atomic references
 * - Enhanced error handling and recovery mechanisms
 * - Optimized memory management and resource cleanup
 * - Comprehensive orientation and configuration change handling
 * - Accessibility support and user experience enhancements
 * - Performance optimizations and background processing
 */
public class PlayerActivity extends AppCompatActivity implements Player.Listener, DefaultLifecycleObserver {

    private static final String TAG = "PlayerActivity";

    // Intent Extras
    public static final String EXTRA_VIDEO_URL = "video_url";
    public static final String EXTRA_VIDEO_TITLE = "video_title";
    public static final String EXTRA_CHANNEL_NAME = "channel_name";

    // UI & Timing Constants
    private static final int CONTROLS_HIDE_DELAY_MS = 3000;
    private static final int PROGRESS_UPDATE_INTERVAL_MS = 500;
    private static final int SEEK_INCREMENT_MS = 10000;
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final int RETRY_DELAY_MS = 2000;

    // State Bundle Keys
    private static final String STATE_PLAYER_STATE = "player_state";
    private static final String STATE_VIDEO_METADATA = "video_metadata";
    private static final String STATE_UI_STATE = "ui_state";
    private static final String STATE_ERROR_STATE = "error_state";

    // Core Components - Thread Safe References
    private volatile ActivityPlayerBinding binding;
    private final AtomicReference<ExoPlayer> playerRef = new AtomicReference<>();
    private final AtomicReference<StreamExtractor> streamExtractorRef = new AtomicReference<>();
    private final AtomicReference<ExecutorService> backgroundExecutorRef = new AtomicReference<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // State Management - Thread Safe
    private final AtomicReference<PlayerState> playerStateRef = new AtomicReference<>();
    private final AtomicReference<VideoMetadata> videoMetadataRef = new AtomicReference<>();
    private final AtomicReference<UIController> uiControllerRef = new AtomicReference<>();
    private final AtomicReference<ErrorRecoveryManager> errorRecoveryRef = new AtomicReference<>();

    // Thread Safety Flags
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);
    private final AtomicBoolean isDestroyed = new AtomicBoolean(false);
    private final AtomicBoolean isConfigurationChanging = new AtomicBoolean(false);

    // UI Runnables
    private final Runnable hideControlsRunnable = this::hideControlsInternal;
    private final Runnable updateProgressRunnable = this::updateProgress;
    private final Runnable overlayHideRunnable = this::hideControlsInternal;
    private final AtomicReference<Future<?>> currentLoadingTask = new AtomicReference<>();

    private final AtomicBoolean isUserSeeking = new AtomicBoolean(false);
    
    private static String videosUrl = "Unknown";

    /**
     * Enhanced player state with comprehensive serialization support
     * and thread-safe operations for configuration changes and process restoration.
     */
    public static class PlayerState implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private volatile boolean isFullscreen = false;
        private volatile boolean controlsVisible = false;
        private volatile boolean isVideoLoaded = false;
        private volatile long playbackPosition = 0;
        private volatile boolean playWhenReady = true;
        private volatile int playbackState = Player.STATE_IDLE;
        private volatile boolean isBuffering = false;
        private volatile int retryCount = 0;
        private volatile long lastErrorTime = 0;
        private volatile String lastErrorMessage = "";

        public Bundle toBundle() {
            Bundle bundle = new Bundle();
            synchronized (this) {
                bundle.putBoolean("isFullscreen", isFullscreen);
                bundle.putBoolean("controlsVisible", controlsVisible);
                bundle.putBoolean("isVideoLoaded", isVideoLoaded);
                bundle.putLong("playbackPosition", Math.max(0, playbackPosition));
                bundle.putBoolean("playWhenReady", playWhenReady);
                bundle.putInt("playbackState", playbackState);
                bundle.putBoolean("isBuffering", isBuffering);
                bundle.putInt("retryCount", Math.max(0, retryCount));
                bundle.putLong("lastErrorTime", lastErrorTime);
                bundle.putString("lastErrorMessage", lastErrorMessage != null ? lastErrorMessage : "");
            }
            return bundle;
        }

        public void fromBundle(Bundle bundle) {
            if (bundle != null) {
                synchronized (this) {
                    isFullscreen = bundle.getBoolean("isFullscreen", false);
                    controlsVisible = bundle.getBoolean("controlsVisible", false);
                    isVideoLoaded = bundle.getBoolean("isVideoLoaded", false);
                    playbackPosition = Math.max(0, bundle.getLong("playbackPosition", 0));
                    playWhenReady = bundle.getBoolean("playWhenReady", true);
                    playbackState = bundle.getInt("playbackState", Player.STATE_IDLE);
                    isBuffering = bundle.getBoolean("isBuffering", false);
                    retryCount = Math.max(0, bundle.getInt("retryCount", 0));
                    lastErrorTime = bundle.getLong("lastErrorTime", 0);
                    lastErrorMessage = bundle.getString("lastErrorMessage", "");
                }
            }
        }

        public synchronized boolean isFullscreen() { return isFullscreen; }
        public synchronized void setFullscreen(boolean fullscreen) { this.isFullscreen = fullscreen; }
        
        public synchronized boolean isControlsVisible() { return controlsVisible; }
        public synchronized void setControlsVisible(boolean visible) { this.controlsVisible = visible; }
        
        public synchronized long getPlaybackPosition() { return playbackPosition; }
        public synchronized void setPlaybackPosition(long position) { this.playbackPosition = Math.max(0, position); }
        
        public synchronized boolean shouldPlayWhenReady() { return playWhenReady; }
        public synchronized void setPlayWhenReady(boolean play) { this.playWhenReady = play; }
        
        public synchronized int getRetryCount() { return retryCount; }
        public synchronized void incrementRetryCount() { this.retryCount++; }
        public synchronized void resetRetryCount() { this.retryCount = 0; }
    }

    /**
     * Enhanced video metadata with comprehensive validation and thread safety
     */
    public static class VideoMetadata implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private volatile String videoUrl;
        private volatile String videoTitle;
        private volatile String channelName;
        private volatile String videoInfo;
        private volatile String likeCount;
        private volatile boolean isLiveStream = false;
        private volatile long duration = 0;
        private volatile String thumbnailUrl;
        private volatile String description;

        public Bundle toBundle() {
            Bundle bundle = new Bundle();
            synchronized (this) {
                bundle.putString("videoUrl", videoUrl != null ? videoUrl : "");
                bundle.putString("videoTitle", videoTitle != null ? videoTitle : "");
                bundle.putString("channelName", channelName != null ? channelName : "");
                bundle.putString("videoInfo", videoInfo != null ? videoInfo : "");
                bundle.putString("likeCount", likeCount != null ? likeCount : "");
                bundle.putBoolean("isLiveStream", isLiveStream);
                bundle.putLong("duration", Math.max(0, duration));
                bundle.putString("thumbnailUrl", thumbnailUrl != null ? thumbnailUrl : "");
                bundle.putString("description", description != null ? description : "");
            }
            return bundle;
        }

        public void fromBundle(Bundle bundle) {
            if (bundle != null) {
                synchronized (this) {
                    videoUrl = bundle.getString("videoUrl", "");
                    videoTitle = bundle.getString("videoTitle", "");
                    channelName = bundle.getString("channelName", "");
                    videoInfo = bundle.getString("videoInfo", "");
                    likeCount = bundle.getString("likeCount", "");
                    isLiveStream = bundle.getBoolean("isLiveStream", false);
                    duration = Math.max(0, bundle.getLong("duration", 0));
                    thumbnailUrl = bundle.getString("thumbnailUrl", "");
                    description = bundle.getString("description", "");
                    
                    videosUrl = videoUrl;
                }
            }
        }

        public synchronized String getVideoUrl() { return videoUrl != null ? videoUrl : ""; }
        public synchronized void setVideoUrl(String url) { this.videoUrl = url != null ? url.trim() : ""; }
        
        public synchronized String getVideoTitle() { return videoTitle != null ? videoTitle : "Untitled"; }
        public synchronized void setVideoTitle(String title) { this.videoTitle = title != null ? title.trim() : "Untitled"; }
        
        public synchronized boolean isLiveStream() { return isLiveStream; }
        public synchronized void setLiveStream(boolean liveStream) { this.isLiveStream = liveStream; }
    }

    /**
     * Enhanced UI controller with comprehensive error handling and accessibility support
     */
    private class UIController {
        private final AtomicBoolean isInitialized = new AtomicBoolean(false);
        
        public void initialize() {
            if (isDestroyed.get() || binding == null) {
                Log.w(TAG, "UIController initialization skipped - activity destroyed or binding null");
                return;
            }
            
            try {
                setupSystemUI();
                setupEventListeners();
                setupBackPressHandler();
                setupAccessibility();
                isInitialized.set(true);
                Log.d(TAG, "UIController initialized successfully");
            } catch (Exception e) {
                Log.e(TAG, "Failed to initialize UIController", e);
                handleUIError("Failed to initialize UI", e);
            }
        }

        private void setupEventListeners() {
            if (binding == null) return;
            
            try {
                // Main player view click
                if (binding.playerView != null) {
                    binding.playerView.setOnClickListener(v -> {
                        if (!isDestroyed.get()) toggleOverlayVisibility();
                    });
                }
                
                // Control buttons with null checks
                safeSetClickListener(binding.btnPlayPause, v -> togglePlayPause());
                safeSetClickListener(binding.btnOrientation, v -> toggleOrientation());
                safeSetClickListener(binding.btnBack, v -> handleBackPress());
                safeSetClickListener(binding.btnReplay10, v -> seek(-SEEK_INCREMENT_MS));
                safeSetClickListener(binding.btnForward10, v -> seek(SEEK_INCREMENT_MS));
                safeSetClickListener(binding.btnCast, v -> showFeatureNotImplemented("Cast"));
                safeSetClickListener(binding.btnCC, v -> showFeatureNotImplemented("Subtitles"));
                safeSetClickListener(binding.btnSettings, v -> showFeatureNotImplemented("Settings"));

                setupSeekBar();
            } catch (Exception e) {
                Log.e(TAG, "Error setting up event listeners", e);
            }
        }

        private void safeSetClickListener(View view, View.OnClickListener listener) {
            if (view != null && listener != null) {
                view.setOnClickListener(listener);
            }
        }

        private void setupSeekBar() {
            if (binding == null || binding.videoProgress == null) return;
            
            binding.videoProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                private boolean wasPlaying;
                private final AtomicBoolean isUserSeeking = new AtomicBoolean(false);
                
                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    if (isDestroyed.get()) return;
                    
                    ExoPlayer player = playerRef.get();
                    VideoMetadata metadata = videoMetadataRef.get();
                    
                    if (player != null && metadata != null && !metadata.isLiveStream()) {
                        isUserSeeking.set(true);
                        wasPlaying = player.isPlaying();
                        player.pause();
                        stopProgressUpdates();
                    }
                }

                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (!fromUser || isDestroyed.get()) return;
                    
                    ExoPlayer player = playerRef.get();
                    VideoMetadata metadata = videoMetadataRef.get();
                    
                    if (player != null && metadata != null && !metadata.isLiveStream()) {
                        long duration = player.getDuration();
                        if (duration > 0) {
                            long position = (duration * progress) / 100;
                            updateTimeDisplays(position, duration);
                        }
                    }
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    if (isDestroyed.get()) return;
                    
                    ExoPlayer player = playerRef.get();
                    VideoMetadata metadata = videoMetadataRef.get();
                    
                    if (player != null && metadata != null && !metadata.isLiveStream()) {
                        try {
                            long duration = player.getDuration();
                            if (duration > 0) {
                                long position = (duration * seekBar.getProgress()) / 100;
                                player.seekTo(position);
                            }
                            if (wasPlaying) {
                                player.play();
                            }
                            startProgressUpdates();
                            resetOverlayTimer();
                        } catch (Exception e) {
                            Log.e(TAG, "Error during seek operation", e);
                        } finally {
                            isUserSeeking.set(false);
                        }
                    }
                }
            });
        }

        public void updateForOrientation() {
            if (binding == null || isDestroyed.get()) return;
            
            try {
                PlayerState state = playerStateRef.get();
                if (state == null) return;
                
                boolean isLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
                state.setFullscreen(isLandscape);
                
                if (isLandscape) {
                    hideSystemUI();
                    hideVideoDetails();
                } else {
                    showSystemUI();
                    showVideoDetails();
                }
                updateOrientationButtonIcon();
                
                Log.d(TAG, "Orientation updated - Fullscreen: " + isLandscape);
            } catch (Exception e) {
                Log.e(TAG, "Error updating orientation", e);
            }
        }

        public void displayVideoMetadata() {
            if (binding == null || isDestroyed.get()) return;
            
            VideoMetadata metadata = videoMetadataRef.get();
            if (metadata == null) return;
            
            try {
                runOnUiThread(() -> {
                    if (binding == null || isDestroyed.get()) return;
                    
                    safeSetText(binding.txtTitle, metadata.getVideoTitle());
                    safeSetText(binding.txtChannelName, metadata.channelName);
                    safeSetText(binding.txtMeta, metadata.videoInfo);
                    safeSetText(binding.txtLikeCount, metadata.likeCount);
                });
            } catch (Exception e) {
                Log.e(TAG, "Error displaying video metadata", e);
            }
        }

        private void safeSetText(android.widget.TextView textView, String text) {
            if (textView != null && text != null) {
                textView.setText(text);
            }
        }

        private void setupAccessibility() {
            if (binding == null) return;
            
            try {
                // Set content descriptions for accessibility
                if (binding.btnPlayPause != null) {
                    binding.btnPlayPause.setContentDescription("Play or pause video");
                }
                if (binding.btnOrientation != null) {
                    binding.btnOrientation.setContentDescription("Toggle fullscreen");
                }
                if (binding.btnBack != null) {
                    binding.btnBack.setContentDescription("Go back");
                }
                if (binding.videoProgress != null) {
                    binding.videoProgress.setContentDescription("Video progress");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error setting up accessibility", e);
            }
        }

        void updatePlayPauseButton() {
            if (playerRef.get() == null || binding == null || binding.btnPlayPause == null) return;
            
            ExoPlayer player = playerRef.get();
            int iconRes;
            if (player.getPlaybackState() == Player.STATE_ENDED) {
                iconRes = R.drawable.ic_replay;
            } else if (player.isPlaying()) {
                iconRes = R.drawable.ic_pause;
            } else {
                iconRes = R.drawable.ic_play_arrow;
            }
            binding.btnPlayPause.setImageResource(iconRes);
        }

        void updateOrientationButtonIcon() {
            if (binding == null || binding.btnOrientation == null) return;
            
            PlayerState state = playerStateRef.get();
            if (state == null) return;
            
            int iconRes = state.isFullscreen() ? R.drawable.ic_fullscreen_exit : R.drawable.ic_fullscreen;
            binding.btnOrientation.setImageResource(iconRes);
        }

        void updateTimeDisplays(long position, long duration) {
            if (binding == null) return;
            
            if (binding.txtCurrentTime != null) {
                binding.txtCurrentTime.setText(formatTime(position));
            }
            if (binding.txtRemainingTime != null && duration > 0) {
                binding.txtRemainingTime.setText("-" + formatTime(duration - position));
            }
        }

        void showLoadingState() {
            if (binding == null || binding.loadingIndicator == null) return;
            
            binding.loadingIndicator.setVisibility(View.VISIBLE);
        }

        void hideLoadingState() {
            if (binding == null || binding.loadingIndicator == null) return;
            
            binding.loadingIndicator.setVisibility(View.GONE);
        }

        void showControlsInternal() {
            PlayerState state = playerStateRef.get();
            if (state == null) return;
            
            state.setControlsVisible(true);
            if (binding == null || binding.controlsOverlay == null) return;
            
            binding.controlsOverlay.setVisibility(View.VISIBLE);
            if (!state.isFullscreen()) {
                showSystemUI();
            }
        }

        void hideControlsInternal() {
            PlayerState state = playerStateRef.get();
            if (state == null) return;
            
            state.setControlsVisible(false);
            if (binding == null || binding.controlsOverlay == null) return;
            
            binding.controlsOverlay.setVisibility(View.GONE);
            if (state.isFullscreen()) {
                hideSystemUI();
            }
        }

        private void hideVideoDetails() {
            if (binding == null || binding.videoInfoContainer == null) return;
            
            binding.videoInfoContainer.setVisibility(View.GONE);
        }

        private void showVideoDetails() {
            if (binding == null || binding.videoInfoContainer == null) return;
            
            binding.videoInfoContainer.setVisibility(View.VISIBLE);
        }

        private void hideSystemUI() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                getWindow().getInsetsController().hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                getWindow().getInsetsController().setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            } else {
                getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                );
            }
        }

        private void showSystemUI() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                getWindow().getInsetsController().show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
            } else {
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            }
        }

        private void setupSystemUI() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                getWindow().setStatusBarColor(ContextCompat.getColor(PlayerActivity.this, R.color.black));
                getWindow().setNavigationBarColor(ContextCompat.getColor(PlayerActivity.this, R.color.black));
            }
        }

        private void setupBackPressHandler() {
            getOnBackPressedDispatcher().addCallback(PlayerActivity.this, new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    handleBackPress();
                }
            });
        }
        
        private void handleUIError(String message, Exception e) {
            Log.e(TAG, message, e);
            ErrorRecoveryManager errorManager = errorRecoveryRef.get();
            if (errorManager != null) {
                errorManager.handleUIError(message, e);
            }
        }

        private void showFeatureNotImplemented(String feature) {
            try {
                String message = feature + " feature is not yet implemented";
                Toast.makeText(PlayerActivity.this, message, Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Feature not implemented: " + feature);
            } catch (Exception e) {
                Log.e(TAG, "Error showing feature not implemented message", e);
            }
        }
    }

    /**
     * Added comprehensive error recovery and retry mechanism
     */
    private class ErrorRecoveryManager {
        private final AtomicBoolean isRecovering = new AtomicBoolean(false);
        
        public void handlePlayerError(PlaybackException error) {
            if (isRecovering.get() || isDestroyed.get()) return;
            
            PlayerState state = playerStateRef.get();
            if (state == null) return;
            
            Log.e(TAG, "Player error occurred: " + error.getMessage(), error);
            
            if (state.getRetryCount() < MAX_RETRY_ATTEMPTS) {
                isRecovering.set(true);
                state.incrementRetryCount();
                
                mainHandler.postDelayed(() -> {
                    if (!isDestroyed.get()) {
                        attemptRecovery();
                    }
                }, RETRY_DELAY_MS);
            } else {
                showError("Playback failed after multiple attempts: " + error.getErrorCodeName());
                state.resetRetryCount();
            }
        }
        
        public void handleUIError(String message, Exception e) {
            Log.e(TAG, "UI Error: " + message, e);
            showError("UI Error: " + message);
        }
        
        private void attemptRecovery() {
            try {
                ExoPlayer player = playerRef.get();
                VideoMetadata metadata = videoMetadataRef.get();
                PlayerState state = playerStateRef.get();
                
                if (player != null && metadata != null && state != null) {
                    Log.d(TAG, "Attempting recovery - retry count: " + state.getRetryCount());
                    
                    // Save current position
                    long position = player.getCurrentPosition();
                    state.setPlaybackPosition(position);
                    
                    // Recreate player if necessary
                    player.stop();
                    player.clearMediaItems();
                    
                    // Reload video
                    loadVideoContent(metadata.getVideoUrl());
                }
            } catch (Exception e) {
                Log.e(TAG, "Recovery attempt failed", e);
                showError("Recovery failed: " + e.getMessage());
            } finally {
                isRecovering.set(false);
            }
        }
    }

    //region ENHANCED ACTIVITY LIFECYCLE & STATE MANAGEMENT
    //==============================================================================================

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
            Log.d(TAG, "onCreate - Starting initialization");
            
            binding = ActivityPlayerBinding.inflate(getLayoutInflater());
            setContentView(binding.getRoot());

            initializeComponents();
            restoreState(savedInstanceState);
            initializePlayer();
            setupUI();

            VideoMetadata metadata = videoMetadataRef.get();
            PlayerState state = playerStateRef.get();
            
            if (metadata != null && state != null && !state.isVideoLoaded && 
                metadata.getVideoUrl() != null && !metadata.getVideoUrl().isEmpty()) {
                loadVideoContent(metadata.getVideoUrl());
            }
            
            isInitialized.set(true);
            Log.d(TAG, "onCreate - Initialization completed successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "Critical error in onCreate", e);
            showError("Failed to initialize player: " + e.getMessage());
            finish();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        
        try {
            Log.d(TAG, "onSaveInstanceState - Saving state");
            
            saveCurrentState();
            
            PlayerState state = playerStateRef.get();
            VideoMetadata metadata = videoMetadataRef.get();
            
            if (state != null) {
                outState.putBundle(STATE_PLAYER_STATE, state.toBundle());
            }
            if (metadata != null) {
                outState.putBundle(STATE_VIDEO_METADATA, metadata.toBundle());
            }
            
            Bundle uiState = new Bundle();
            if (binding != null && binding.videoProgress != null) {
                uiState.putInt("seekbar_progress", binding.videoProgress.getProgress());
            }
            outState.putBundle(STATE_UI_STATE, uiState);
            
        } catch (Exception e) {
            Log.e(TAG, "Error saving instance state", e);
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        
        try {
            Log.d(TAG, "onConfigurationChanged - Handling configuration change");
            
            extractAndPlayVideo(videosUrl);
            
            isConfigurationChanging.set(true);
            saveCurrentState();
        
            PlayerState state = playerStateRef.get();
            boolean wasControlsVisible = state != null && state.isControlsVisible();
            long savedPosition = state != null ? state.getPlaybackPosition() : 0;
            boolean shouldPlayWhenReady = state != null ? state.shouldPlayWhenReady() : false;

            // Recreate binding
            binding = ActivityPlayerBinding.inflate(getLayoutInflater());
            setContentView(binding.getRoot());

            // Reinitialize UI
            UIController uiController = uiControllerRef.get();
            if (uiController != null) {
                uiController.initialize();
            }

            // Reattach player to view
            ExoPlayer player = playerRef.get();
            if (player != null && binding.playerView != null) {
                binding.playerView.setPlayer(player);
                binding.playerView.setUseController(false);
            
                if (savedPosition > 0) {
                    player.seekTo(savedPosition);
                }
                player.setPlayWhenReady(shouldPlayWhenReady);
            }

            setupUI();

            // Restore controls visibility
            if (uiController != null) {
                if (wasControlsVisible) {
                    uiController.showControlsInternal();
                } else {
                    uiController.hideControlsInternal();
                }
            }
        
            if (player != null && shouldPlayWhenReady) {
                startProgressUpdates();
            }
        
            isConfigurationChanging.set(false);
            Log.d(TAG, "onConfigurationChanged - Configuration change handled successfully");
        
        } catch (Exception e) {
            Log.e(TAG, "Error handling configuration change", e);
            isConfigurationChanging.set(false);
            Toast.makeText(getBaseContext(), "Failed" ,Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        
        try {
            Log.d(TAG, "onPause - Pausing activity");
            
            saveCurrentState();
            
            ExoPlayer player = playerRef.get();
            if (player != null && !isConfigurationChanging.get()) {
                player.pause();
            }
            
            stopAllTimers();
            
        } catch (Exception e) {
            Log.e(TAG, "Error in onPause", e);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        try {
            Log.d(TAG, "onResume - Resuming activity");
            
            ExoPlayer player = playerRef.get();
            PlayerState state = playerStateRef.get();
            
            if (player != null && state != null && state.shouldPlayWhenReady() && !isConfigurationChanging.get()) {
                player.play();
            }
            
            startProgressUpdates();
            
        } catch (Exception e) {
            Log.e(TAG, "Error in onResume", e);
        }
    }

    @Override
    protected void onDestroy() {
        try {
            Log.d(TAG, "onDestroy - Cleaning up resources");
            
            isDestroyed.set(true);
            
            // Cancel any ongoing loading tasks
            Future<?> loadingTask = currentLoadingTask.get();
            if (loadingTask != null && !loadingTask.isDone()) {
                loadingTask.cancel(true);
            }
            
            releasePlayer();
            cleanupResources();
            
        } catch (Exception e) {
            Log.e(TAG, "Error in onDestroy", e);
        } finally {
            binding = null;
            super.onDestroy();
        }
    }

    //endregion

    //region ENHANCED INITIALIZATION & SETUP
    //==============================================================================================

    private void initializeComponents() {
        try {
            playerStateRef.set(new PlayerState());
            videoMetadataRef.set(new VideoMetadata());
            uiControllerRef.set(new UIController());
            streamExtractorRef.set(new StreamExtractor());
            errorRecoveryRef.set(new ErrorRecoveryManager());
            
            ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "PlayerActivity-Background");
                t.setDaemon(true);
                return t;
            });
            backgroundExecutorRef.set(executor);
            
            Log.d(TAG, "Components initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize components", e);
            throw new RuntimeException("Component initialization failed", e);
        }
    }

    private void restoreState(Bundle savedInstanceState) {
        try {
            PlayerState state = playerStateRef.get();
            VideoMetadata metadata = videoMetadataRef.get();
            
            if (state == null || metadata == null) {
                Log.e(TAG, "State objects not initialized");
                return;
            }
            
            if (savedInstanceState != null) {
                Log.d(TAG, "Restoring state from saved instance");
                
                Bundle playerStateBundle = savedInstanceState.getBundle(STATE_PLAYER_STATE);
                Bundle metadataBundle = savedInstanceState.getBundle(STATE_VIDEO_METADATA);
                
                if (playerStateBundle != null) {
                    state.fromBundle(playerStateBundle);
                }
                if (metadataBundle != null) {
                    metadata.fromBundle(metadataBundle);
                }
            } else {
                Log.d(TAG, "Initializing from intent extras");
                
                Intent intent = getIntent();
                if (intent != null) {
                    metadata.setVideoUrl(intent.getStringExtra(EXTRA_VIDEO_URL));
                    metadata.setVideoTitle(intent.getStringExtra(EXTRA_VIDEO_TITLE));
                    metadata.channelName = intent.getStringExtra(EXTRA_CHANNEL_NAME);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error restoring state", e);
        }
    }

    private void initializePlayer() {
        try {
            ExoPlayer existingPlayer = playerRef.get();
            if (existingPlayer == null) {
                ExoPlayer newPlayer = new ExoPlayer.Builder(this)
                    .setSeekBackIncrementMs(SEEK_INCREMENT_MS)
                    .setSeekForwardIncrementMs(SEEK_INCREMENT_MS)
                    .build();
                
                newPlayer.addListener(this);
                playerRef.set(newPlayer);
                
                Log.d(TAG, "ExoPlayer initialized successfully");
            }
            
            ExoPlayer player = playerRef.get();
            if (player != null && binding != null && binding.playerView != null) {
                binding.playerView.setPlayer(player);
                binding.playerView.setUseController(false);
                
                PlayerState state = playerStateRef.get();
                if (state != null) {
                    if (state.getPlaybackPosition() > 0) {
                        player.seekTo(state.getPlaybackPosition());
                    }
                    player.setPlayWhenReady(state.shouldPlayWhenReady());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize player", e);
            showError("Failed to initialize video player");
        }
    }

    private void setupUI() {
        UIController uiController = uiControllerRef.get();
        if (uiController != null) {
            uiController.initialize();
            uiController.updateForOrientation();
            uiController.displayVideoMetadata();
        }
    }

    private void saveCurrentState() {
        ExoPlayer player = playerRef.get();
        PlayerState state = playerStateRef.get();
        
        if (player != null && state != null) {
            state.setPlaybackPosition(player.getCurrentPosition());
            state.setPlayWhenReady(player.getPlayWhenReady());
        }
    }

    //endregion

    //region ENHANCED VIDEO LOADING & METADATA
    //==============================================================================================

    private void loadVideoContent(String url) {
        if (url == null || url.trim().isEmpty()) {
            showError("No video URL provided");
            return;
        }
        
        if (isDestroyed.get()) {
            Log.w(TAG, "Activity destroyed, skipping video load");
            return;
        }
        
        // Cancel any existing loading task
        Future<?> existingTask = currentLoadingTask.get();
        if (existingTask != null && !existingTask.isDone()) {
            existingTask.cancel(true);
        }
        
        UIController uiController = uiControllerRef.get();
        if (uiController != null) {
            uiController.showLoadingState();
        }
        
        ExecutorService executor = backgroundExecutorRef.get();
        if (executor != null && !executor.isShutdown()) {
            Future<?> task = executor.submit(() -> extractAndPlayVideo(url.trim()));
            currentLoadingTask.set(task);
        }
    }

    private void extractAndPlayVideo(String url) {
        if (Thread.currentThread().isInterrupted() || isDestroyed.get()) {
            Log.d(TAG, "Video extraction cancelled");
            return;
        }
        
        try {
            Log.d(TAG, "Starting video extraction for: " + url);
            
            StreamExtractor extractor = streamExtractorRef.get();
            if (extractor == null) {
                throw new IllegalStateException("Stream extractor not initialized");
            }
            
            // Check for cancellation before extraction
            if (Thread.currentThread().isInterrupted()) return;
            
            StreamInfo streamInfo = extractor.extractStreamInfo(url);
            
            // Check for cancellation after extraction
            if (Thread.currentThread().isInterrupted()) return;
            
            String streamUrl = extractor.extractVideoStream(url);

            mainHandler.post(() -> {
                if (isDestroyed.get()) return;
                
                UIController uiController = uiControllerRef.get();
                
                try {
                    if (streamUrl != null && !streamUrl.isEmpty()) {
                        if (streamInfo != null) {
                            populateVideoMetadata(streamInfo);
                        }
                        playVideo(streamUrl);
                        
                        // Reset retry count on successful load
                        PlayerState state = playerStateRef.get();
                        if (state != null) {
                            state.resetRetryCount();
                        }
                    } else {
                        showError("Failed to extract video stream");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error in video playback setup", e);
                    showError("Failed to setup video playback: " + e.getMessage());
                } finally {
                    if (uiController != null) {
                        uiController.hideLoadingState();
                    }
                }
            });
            
        } catch (InterruptedException e) {
            Log.d(TAG, "Video extraction interrupted");
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            Log.e(TAG, "Failed to load video", e);
            mainHandler.post(() -> {
                if (!isDestroyed.get()) {
                    ErrorRecoveryManager errorManager = errorRecoveryRef.get();
                    if (errorManager != null) {
                        // This will trigger retry logic if appropriate
                        errorManager.handlePlayerError(new PlaybackException("Stream extraction failed", e, PlaybackException.ERROR_CODE_IO_UNSPECIFIED));
                    } else {
                        showError("Failed to load video: " + e.getMessage());
                    }
                    
                    UIController uiController = uiControllerRef.get();
                    if (uiController != null) {
                        uiController.hideLoadingState();
                    }
                }
            });
        }
    }

    private void playVideo(String streamUrl) {
        if (streamUrl == null) return;

        try {
            ExoPlayer player = playerRef.get();
            PlayerState state = playerStateRef.get();

            if (player == null) {
                Log.e(TAG, "Player is null, cannot play video");
                showError("Player is not initialized");
                return;
            }

            MediaItem mediaItem = MediaItem.fromUri(streamUrl);
            player.setMediaItem(mediaItem);

            if (state != null) {
                player.seekTo(state.getPlaybackPosition());
                player.setPlayWhenReady(state.shouldPlayWhenReady());
            }

            player.prepare();

            if (state != null) {
                state.isVideoLoaded = true;
            }

            Log.d(TAG, "Playing video: " + streamUrl);

        } catch (Exception e) {
            Log.e(TAG, "Error playing video", e);
            showError("Failed to play video: " + e.getMessage());
        }
    }

    private void populateVideoMetadata(StreamInfo info) {
        try {
            VideoMetadata metadata = videoMetadataRef.get();
            if (metadata == null) return;

            metadata.setVideoTitle(info.getName());
            metadata.channelName = info.getUploaderName();
            metadata.setLiveStream(info.getStreamType() == org.schabi.newpipe.extractor.stream.StreamType.LIVE_STREAM);

            String views = (info.getViewCount() > 0) ? formatViewCount(info.getViewCount()) + " views" : "";
            String date = "";
            if (info.getUploadDate() != null) {
                date = formatTimeAgo(info.getUploadDate().date().getTimeInMillis());
            }
            metadata.videoInfo = views.isEmpty() ? date : views + " • " + date;

            if (info.getLikeCount() > 0) {
                metadata.likeCount = formatViewCount(info.getLikeCount());
            }

            UIController uiController = uiControllerRef.get();
            if (uiController != null) {
                runOnUiThread(() -> uiController.displayVideoMetadata());
            }

        } catch (Exception e) {
            Log.e(TAG, "Error populating video metadata", e);
        }
    }

    //endregion

    //region ENHANCED PLAYER LISTENER IMPLEMENTATION
    //==============================================================================================

    @Override
    public void onPlaybackStateChanged(int playbackState) {
        if (isDestroyed.get()) return;
        
        try {
            PlayerState state = playerStateRef.get();
            UIController uiController = uiControllerRef.get();
            
            if (state != null) {
                state.playbackState = playbackState;
            }
            
            switch (playbackState) {
                case Player.STATE_BUFFERING:
                    if (state != null) state.isBuffering = true;
                    if (uiController != null) uiController.showLoadingState();
                    Log.d(TAG, "Player state: BUFFERING");
                    break;
                    
                case Player.STATE_READY:
                    if (state != null) {
                        state.isBuffering = false;
                        state.isVideoLoaded = true;
                    }
                    if (uiController != null) uiController.hideLoadingState();
                    Log.d(TAG, "Player state: READY");
                    break;
                    
                case Player.STATE_ENDED:
                    if (uiController != null) uiController.hideLoadingState();
                    Log.d(TAG, "Player state: ENDED");
                    break;
                    
                case Player.STATE_IDLE:
                    Log.d(TAG, "Player state: IDLE");
                    break;
            }
            
            if (uiController != null) {
                uiController.updatePlayPauseButton();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling playback state change", e);
        }
    }

    @Override
    public void onIsPlayingChanged(boolean isPlaying) {
        if (isDestroyed.get()) return;
        
        try {
            UIController uiController = uiControllerRef.get();
            if (uiController != null) {
                uiController.updatePlayPauseButton();
            }
            
            if (isPlaying) {
                startProgressUpdates();
                resetOverlayTimer();
                Log.d(TAG, "Playback started");
            } else {
                stopAllTimers();
                showControls();
                Log.d(TAG, "Playback paused");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling playing state change", e);
        }
    }

    @Override
    public void onPlayerError(@NonNull PlaybackException error) {
        if (isDestroyed.get()) return;
        
        Log.e(TAG, "Player error: " + error.getMessage(), error);
        
        ErrorRecoveryManager errorManager = errorRecoveryRef.get();
        if (errorManager != null) {
            errorManager.handlePlayerError(error);
        } else {
            showError("Playback error: " + error.getErrorCodeName());
        }
        
        UIController uiController = uiControllerRef.get();
        if (uiController != null) {
            uiController.hideLoadingState();
        }
    }

    //endregion

    //region ENHANCED RESOURCE MANAGEMENT & CLEANUP
    //==============================================================================================

    private void releasePlayer() {
        try {
            ExoPlayer player = playerRef.get();
            if (player != null) {
                Log.d(TAG, "Releasing ExoPlayer");
                
                saveCurrentState();
                player.removeListener(this);
                player.stop();
                player.release();
                playerRef.set(null);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error releasing player", e);
        }
    }

    private void cleanupResources() {
        try {
            Log.d(TAG, "Cleaning up resources");
            
            stopAllTimers();
            mainHandler.removeCallbacksAndMessages(null);
            
            // Cancel any ongoing tasks
            Future<?> loadingTask = currentLoadingTask.get();
            if (loadingTask != null && !loadingTask.isDone()) {
                loadingTask.cancel(true);
            }
            
            ExecutorService executor = backgroundExecutorRef.get();
            if (executor != null && !executor.isShutdown()) {
                executor.shutdown();
                try {
                    if (!executor.awaitTermination(1000, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                        executor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    executor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
                backgroundExecutorRef.set(null);
            }
            
            // Clear references
            playerStateRef.set(null);
            videoMetadataRef.set(null);
            uiControllerRef.set(null);
            streamExtractorRef.set(null);
            errorRecoveryRef.set(null);
            
        } catch (Exception e) {
            Log.e(TAG, "Error during resource cleanup", e);
        }
    }

    private void updateProgress() {
        if (isDestroyed.get()) return;

        try {
            ExoPlayer player = playerRef.get();
            VideoMetadata metadata = videoMetadataRef.get();

            if (player == null) return;

            if (metadata != null && metadata.isLiveStream()) {
                if (binding != null && binding.videoProgress != null) {
                    binding.videoProgress.setProgress(100);
                }
                if (binding != null && binding.txtCurrentTime != null) {
                    binding.txtCurrentTime.setText("LIVE");
                }
                if (binding != null && binding.txtRemainingTime != null) {
                    binding.txtRemainingTime.setText("");
                }
                return;
            }

            long position = player.getCurrentPosition();
            long duration = player.getDuration();

            if (binding != null && binding.videoProgress != null && duration > 0) {
                binding.videoProgress.setProgress((int) ((position * 100) / duration));
            }

            UIController uiController = uiControllerRef.get();
            if (uiController != null) {
                uiController.updateTimeDisplays(position, duration);
            }

            mainHandler.postDelayed(updateProgressRunnable, PROGRESS_UPDATE_INTERVAL_MS);

        } catch (Exception e) {
            Log.e(TAG, "Error updating progress", e);
        }
    }

    private void togglePlayPause() {
        ExoPlayer player = playerRef.get();
        if (player == null) return;

        try {
            if (player.isPlaying()) {
                player.pause();
            } else {
                player.prepare();
                player.play();
            }
            resetOverlayTimer();
        } catch (Exception e) {
            Log.e(TAG, "Error toggling play/pause", e);
        }
    }

    private void seek(int amountMs) {
        ExoPlayer player = playerRef.get();
        VideoMetadata metadata = videoMetadataRef.get();

        if (player == null || metadata == null || metadata.isLiveStream()) return;

        try {
            long newPosition = player.getCurrentPosition() + amountMs;
            long duration = player.getDuration();

            if (duration > 0) {
                newPosition = Math.max(0, Math.min(newPosition, duration));
            } else {
                newPosition = Math.max(0, newPosition);
            }

            player.seekTo(newPosition);
            resetOverlayTimer();

        } catch (Exception e) {
            Log.e(TAG, "Error seeking", e);
        }
    }

    private void toggleOrientation() {
        PlayerState state = playerStateRef.get();
        if (state == null) return;

        if (state.isFullscreen()) {
            exitFullscreen();
        } else {
            enterFullscreen();
        }
    }

    private void enterFullscreen() {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    }

    private void exitFullscreen() {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    private void handleBackPress() {
        PlayerState state = playerStateRef.get();
        if (state == null) return;

        if (state.isFullscreen()) {
            exitFullscreen();
        } else {
            finish();
        }
    }

    private void toggleOverlayVisibility() {
        PlayerState state = playerStateRef.get();
        if (state == null) return;

        if (state.isControlsVisible()) {
            hideControls();
        } else {
            showControls();
        }
    }

    private void showControls() {
        UIController uiController = uiControllerRef.get();
        if (uiController != null) {
            uiController.showControlsInternal();
        }
        resetOverlayTimer();
    }

    private void hideControls() {
        UIController uiController = uiControllerRef.get();
        if (uiController != null) {
            uiController.hideControlsInternal();
        }
        stopOverlayTimer();
    }

    private void resetOverlayTimer() {
        stopOverlayTimer();
        ExoPlayer player = playerRef.get();
        if (player != null && player.isPlaying()) {
            mainHandler.postDelayed(hideControlsRunnable, CONTROLS_HIDE_DELAY_MS);
        }
    }

    private void startProgressUpdates() {
        try {
            stopProgressUpdates(); // Stop any existing updates first
            
            if (progressUpdateRunnable == null) {
                progressUpdateRunnable = new Runnable() {
                    @Override
                    public void run() {
                        try {
                            ExoPlayer player = playerRef.get();
                            if (player != null && !isUserSeeking.get()) {
                                updateProgress();
                            }
                            
                            if (!isDestroyed() && !isFinishing()) {
                                mainHandler.postDelayed(this, PROGRESS_UPDATE_INTERVAL_MS);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error in progress update", e);
                        }
                    }
                };
            }
            
            mainHandler.post(progressUpdateRunnable);
            Log.d(TAG, "Progress updates started");
        } catch (Exception e) {
            Log.e(TAG, "Error starting progress updates", e);
        }
    }

    private void stopProgressUpdates() {
        try {
            if (progressUpdateRunnable != null) {
                mainHandler.removeCallbacks(progressUpdateRunnable);
                Log.d(TAG, "Progress updates stopped");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping progress updates", e);
        }
    }

    private void stopAllTimers() {
        try {
            stopProgressUpdates();
            stopOverlayTimer();
            Log.d(TAG, "All timers stopped");
        } catch (Exception e) {
            Log.e(TAG, "Error stopping all timers", e);
        }
    }

    private void stopOverlayTimer() {
        try {
            if (overlayHideRunnable != null) {
                mainHandler.removeCallbacks(overlayHideRunnable);
                Log.d(TAG, "Overlay timer stopped");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping overlay timer", e);
        }
    }

    private void hideControlsInternal() {
        UIController uiController = uiControllerRef.get();
        if (uiController != null) {
            uiController.hideControlsInternal();
        }
    }

    //endregion

    //region ENHANCED UTILITY METHODS
    //==============================================================================================

    private void showError(String message) {
        if (isDestroyed.get()) return;
        
        Log.e(TAG, message);
        
        runOnUiThread(() -> {
            if (!isDestroyed.get()) {
                String userMessage = getUserFriendlyErrorMessage(message);
                Toast.makeText(this, userMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private String getUserFriendlyErrorMessage(String technicalMessage) {
        if (technicalMessage == null) return "An unknown error occurred";
        
        String lower = technicalMessage.toLowerCase();
        
        if (lower.contains("network") || lower.contains("connection")) {
            return "Network connection error. Please check your internet connection.";
        } else if (lower.contains("timeout")) {
            return "Request timed out. Please try again.";
        } else if (lower.contains("not found") || lower.contains("404")) {
            return "Video not found or no longer available.";
        } else if (lower.contains("permission") || lower.contains("unauthorized")) {
            return "Access denied. This video may be private or restricted.";
        } else if (lower.contains("format") || lower.contains("codec")) {
            return "Video format not supported on this device.";
        } else {
            return "Unable to play video. Please try again later.";
        }
    }

    private String formatTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        
        seconds %= 60;
        minutes %= 60;
        
        if (hours > 0) {
            return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
        }
    }

    private String formatViewCount(long count) {
        if (count < 1000) {
            return String.valueOf(count);
        } else if (count < 1000000) {
            return String.format(Locale.getDefault(), "%.1fK", count / 1000.0);
        } else if (count < 1000000000) {
            return String.format(Locale.getDefault(), "%.1fM", count / 1000000.0);
        } else {
            return String.format(Locale.getDefault(), "%.1fB", count / 1000000000.0);
        }
    }

    private String formatTimeAgo(long timeMillis) {
        long diff = System.currentTimeMillis() - timeMillis;
        long seconds = diff / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        long months = days / 30;
        long years = days / 365;

        if (years > 0) {
            return years == 1 ? "1 year ago" : years + " years ago";
        } else if (months > 0) {
            return months == 1 ? "1 month ago" : months + " months ago";
        } else if (days > 0) {
            return days == 1 ? "1 day ago" : days + " days ago";
        } else if (hours > 0) {
            return hours == 1 ? "1 hour ago" : hours + " hours ago";
        } else if (minutes > 0) {
            return minutes == 1 ? "1 minute ago" : minutes + " minutes ago";
        } else {
            return "Just now";
        }
    }

    //endregion

    //region ENHANCED STREAM EXTRACTION SERVICE
    //==============================================================================================

    private static class StreamExtractor {
        private static final String TAG = "StreamExtractor";
        
        public StreamInfo extractStreamInfo(String url) throws Exception {
            if (url == null || url.trim().isEmpty()) {
                throw new IllegalArgumentException("URL cannot be null or empty");
            }
            
            try {
                Log.d(TAG, "Extracting stream info for: " + url);
                StreamInfo info = StreamInfo.getInfo(ServiceList.YouTube, url);
                
                if (info == null) {
                    throw new Exception("Failed to extract stream information");
                }
                
                return info;
            } catch (Exception e) {
                Log.e(TAG, "Failed to extract stream info", e);
                throw new Exception("Stream info extraction failed: " + e.getMessage(), e);
            }
        }
        
        public String extractVideoStream(String url) throws Exception {
            if (url == null || url.trim().isEmpty()) {
                throw new IllegalArgumentException("URL cannot be null or empty");
            }
            
            try {
                Log.d(TAG, "Extracting video stream for: " + url);
                StreamInfo streamInfo = StreamInfo.getInfo(ServiceList.YouTube, url);
                
                if (streamInfo == null) {
                    throw new Exception("Failed to get stream information");
                }
                
                // Try video streams first (with audio)
                if (!streamInfo.getVideoStreams().isEmpty()) {
                    String streamUrl = streamInfo.getVideoStreams().get(0).getUrl();
                    Log.d(TAG, "Found video stream: " + streamUrl);
                    return streamUrl;
                }
                
                // Try video-only streams
                if (!streamInfo.getVideoOnlyStreams().isEmpty()) {
                    String streamUrl = streamInfo.getVideoOnlyStreams().get(0).getUrl();
                    Log.d(TAG, "Found video-only stream: " + streamUrl);
                    return streamUrl;
                }
                
                // Fallback to audio streams
                if (!streamInfo.getAudioStreams().isEmpty()) {
                    String streamUrl = streamInfo.getAudioStreams().get(0).getUrl();
                    Log.d(TAG, "Found audio stream: " + streamUrl);
                    return streamUrl;
                }
                
                throw new Exception("No playable streams found for this video");
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to extract video stream", e);
                throw new Exception("Video stream extraction failed: " + e.getMessage(), e);
            }
        }
    }

    //endregion

    private Runnable progressUpdateRunnable;
}
