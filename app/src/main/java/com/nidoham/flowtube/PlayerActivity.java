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
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.nidoham.flowtube.databinding.ActivityPlayerBinding;

import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.stream.StreamInfo;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Formatter;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PlayerActivity extends AppCompatActivity implements Player.Listener {

    private static final String TAG = "PlayerActivity";
    
    // Intent extras
    public static final String EXTRA_VIDEO_URL = "video_url";
    public static final String EXTRA_VIDEO_TITLE = "video_title";
    public static final String EXTRA_CHANNEL_NAME = "channel_name";
    
    // UI constants
    private static final int CONTROLS_HIDE_DELAY_MS = 2000; // 2 seconds as requested
    private static final int PROGRESS_UPDATE_INTERVAL_MS = 500; // Faster updates for live streams
    private static final int SEEK_INCREMENT_MS = 10000;
    
    // Time constants
    private static final long MILLISECONDS_IN_SECOND = 1000;
    private static final long SECONDS_IN_MINUTE = 60;
    private static final long MINUTES_IN_HOUR = 60;
    private static final long HOURS_IN_DAY = 24;
    private static final long DAYS_IN_WEEK = 7;
    private static final long DAYS_IN_MONTH = 30;
    private static final long DAYS_IN_YEAR = 365;

    // Core components
    private ActivityPlayerBinding binding;
    private ExoPlayer player;
    private StreamExtractor streamExtractor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ExecutorService backgroundExecutor;

    // Playback state
    private long playbackPosition = 0;
    private boolean playWhenReady = true;
    private boolean isFullscreen = false;
    private boolean controlsVisible = false;
    private boolean isLiveStream = false;
    private boolean isVideoLoaded = false;
    
    // Video metadata (preserved across orientation changes)
    private String videoTitle;
    private String channelName;
    private String videoMetadata;
    private String likeCount;
    
    // UI runnables
    private final Runnable hideControlsRunnable = this::hideControlsInternal;
    private final Runnable updateProgressRunnable = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            if (player != null && !isFinishing()) {
                mainHandler.postDelayed(this, PROGRESS_UPDATE_INTERVAL_MS);
            }
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initializeActivity(savedInstanceState);
        setupUI();
        
        // Only load video content if not already loaded (prevents reload on orientation change)
        if (!isVideoLoaded) {
            loadVideoContent();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        savePlayerState(outState);
        saveVideoMetadata(outState);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null && player.isPlaying()) {
            player.pause();
        }
        stopAllTimers();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (player != null && playWhenReady) {
            player.play();
        }
        startProgressUpdates();
    }

    @Override
    protected void onDestroy() {
        releasePlayer();
        cleanupResources();
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        
        // Save current state
        boolean wasControlsVisible = controlsVisible;
        
        // Handle orientation change without recreating UI completely
        handleOrientationChange(newConfig);
        
        // Restore controls state
        if (wasControlsVisible) {
            showControlsInternal();
        }
    }

    // Initialization methods
    private void initializeActivity(@Nullable Bundle savedInstanceState) {
        binding = ActivityPlayerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        if (savedInstanceState != null) {
            restorePlayerState(savedInstanceState);
            restoreVideoMetadata(savedInstanceState);
        }
        
        backgroundExecutor = Executors.newSingleThreadExecutor();
        streamExtractor = new StreamExtractor();
        
        setupSystemUI();
        setupBackPressHandler();
    }

    private void setupUI() {
        initializePlayer();
        setupEventListeners();
        setupInitialLayout();
        
        // Restore video metadata if available
        if (videoTitle != null || channelName != null) {
            displayVideoMetadata();
        }
    }

    private void setupInitialLayout() {
        // Set initial overlay visibility
        hideControlsInternal();
        
        // Configure player view for touch handling
        binding.playerView.setUseController(false);
        binding.playerView.setOnClickListener(v -> toggleOverlayVisibility());
        
        // Setup initial orientation state
        Configuration config = getResources().getConfiguration();
        isFullscreen = (config.orientation == Configuration.ORIENTATION_LANDSCAPE);
        updateLayoutForOrientation();
    }

    private void setupSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            int color = ContextCompat.getColor(this, R.color.black);
            getWindow().setStatusBarColor(color);
            getWindow().setNavigationBarColor(color);
        }
    }

    private void setupBackPressHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isFullscreen) {
                    exitFullscreen();
                } else {
                    finish();
                }
            }
        });
    }

    // Player management
    private void initializePlayer() {
        if (player == null) {
            player = new ExoPlayer.Builder(this).build();
            player.addListener(this);
        }
        binding.playerView.setPlayer(player);
        binding.playerView.setUseController(false);
    }

    private void playVideo(String streamUrl) {
        if (player == null || streamUrl == null) return;
        
        MediaItem mediaItem = MediaItem.fromUri(streamUrl);
        player.setMediaItem(mediaItem);
        player.prepare();
        player.seekTo(playbackPosition);
        player.setPlayWhenReady(playWhenReady);
        
        isVideoLoaded = true;
        Log.d(TAG, "Playing video: " + streamUrl);
    }

    private void togglePlayPause() {
        if (player == null) return;
        
        if (player.isPlaying()) {
            player.pause();
        } else {
            player.play();
        }
        resetOverlayTimer();
    }

    private void seekBackward() {
        if (player == null || isLiveStream) return;
        long newPosition = Math.max(0, player.getCurrentPosition() - SEEK_INCREMENT_MS);
        player.seekTo(newPosition);
        resetOverlayTimer();
    }

    private void seekForward() {
        if (player == null || isLiveStream) return;
        long duration = player.getDuration();
        long newPosition = Math.min(duration, player.getCurrentPosition() + SEEK_INCREMENT_MS);
        player.seekTo(newPosition);
        resetOverlayTimer();
    }

    private void releasePlayer() {
        if (player != null) {
            playbackPosition = player.getCurrentPosition();
            playWhenReady = player.getPlayWhenReady();
            player.removeListener(this);
            player.release();
            player = null;
        }
    }

    // State management
    private void savePlayerState(Bundle outState) {
        if (player != null) {
            outState.putLong("video_position", player.getCurrentPosition());
            outState.putBoolean("video_playing", player.getPlayWhenReady());
        }
        outState.putBoolean("video_loaded", isVideoLoaded);
        outState.putBoolean("is_live_stream", isLiveStream);
        outState.putBoolean("is_fullscreen", isFullscreen);
        outState.putBoolean("controls_visible", controlsVisible);
    }

    private void restorePlayerState(Bundle savedInstanceState) {
        playbackPosition = savedInstanceState.getLong("video_position", 0);
        playWhenReady = savedInstanceState.getBoolean("video_playing", true);
        isVideoLoaded = savedInstanceState.getBoolean("video_loaded", false);
        isLiveStream = savedInstanceState.getBoolean("is_live_stream", false);
        isFullscreen = savedInstanceState.getBoolean("is_fullscreen", false);
        controlsVisible = savedInstanceState.getBoolean("controls_visible", false);
    }

    private void saveVideoMetadata(Bundle outState) {
        if (videoTitle != null) outState.putString("video_title", videoTitle);
        if (channelName != null) outState.putString("channel_name", channelName);
        if (videoMetadata != null) outState.putString("video_metadata", videoMetadata);
        if (likeCount != null) outState.putString("like_count", likeCount);
    }

    private void restoreVideoMetadata(Bundle savedInstanceState) {
        videoTitle = savedInstanceState.getString("video_title");
        channelName = savedInstanceState.getString("channel_name");
        videoMetadata = savedInstanceState.getString("video_metadata");
        likeCount = savedInstanceState.getString("like_count");
    }

    // Video loading
    private void loadVideoContent() {
        loadInitialVideoData();
        String videoUrl = getIntent().getStringExtra(EXTRA_VIDEO_URL);
        
        if (videoUrl != null && !videoUrl.trim().isEmpty()) {
            loadVideo(videoUrl.trim());
        } else {
            showError("No video URL provided");
        }
    }

    private void loadInitialVideoData() {
        Intent intent = getIntent();
        String intentTitle = intent.getStringExtra(EXTRA_VIDEO_TITLE);
        String intentChannelName = intent.getStringExtra(EXTRA_CHANNEL_NAME);
        
        // Only use intent data if not already loaded from saved state
        if (videoTitle == null && intentTitle != null && !intentTitle.isEmpty()) {
            videoTitle = intentTitle;
        }
        if (channelName == null && intentChannelName != null && !intentChannelName.isEmpty()) {
            channelName = intentChannelName;
        }
        
        displayVideoMetadata();
    }

    private void displayVideoMetadata() {
        setTextIfNotNull(binding.txtTitle, videoTitle);
        setTextIfNotNull(binding.txtChannelName, channelName);
        setTextIfNotNull(binding.txtMeta, videoMetadata);
        setTextIfNotNull(binding.txtLikeCount, likeCount);
    }

    private void populateVideoMetadata(StreamInfo streamInfo) {
        try {
            // Update title if not already set
            if (streamInfo.getName() != null && !streamInfo.getName().isEmpty()) {
                videoTitle = streamInfo.getName();
            }

            // Update channel name if available
            if (streamInfo.getUploaderName() != null && !streamInfo.getUploaderName().isEmpty()) {
                channelName = streamInfo.getUploaderName();
            }

            // Update view count and upload date metadata
            if (streamInfo.getViewCount() > 0 || streamInfo.getUploadDate() != null) {
                String metaText = "";
                if (streamInfo.getViewCount() > 0) {
                    metaText = formatViewCount(streamInfo.getViewCount()) + " views";
                }
                if (streamInfo.getUploadDate() != null) {
                    DateWrapper uploadDate = streamInfo.getUploadDate();
                    if (uploadDate != null && uploadDate.date() != null) {
                        long uploadTimeMs = uploadDate.date().getTimeInMillis();
                        String timeAgo = formatTimeAgo(uploadTimeMs);
                        if (!metaText.isEmpty()) {
                            metaText += " • " + timeAgo;
                        } else {
                            metaText = timeAgo;
                        }
                    }
                }
                videoMetadata = metaText;
            }

            // Update like count if available
            if (streamInfo.getLikeCount() > 0) {
                likeCount = formatViewCount(streamInfo.getLikeCount());
            }

            // Check if this is a live stream
            isLiveStream = streamInfo.getStreamType() == org.schabi.newpipe.extractor.stream.StreamType.LIVE_STREAM;

            displayVideoMetadata();
            Log.d(TAG, "Video metadata populated successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error populating video metadata", e);
        }
    }

    private void loadVideo(String videoUrl) {
        if (isFinishing()) return;
        
        showLoadingState();
        backgroundExecutor.submit(() -> extractAndPlayVideo(videoUrl));
    }

    private void extractAndPlayVideo(String videoUrl) {
        try {
            StreamInfo streamInfo = streamExtractor.extractStreamInfo(videoUrl);
            String streamUrl = streamExtractor.extractVideoStream(videoUrl);
            
            mainHandler.post(() -> {
                if (isFinishing()) return;
                
                if (streamUrl != null) {
                    if (streamInfo != null) {
                        populateVideoMetadata(streamInfo);
                    }
                    playVideo(streamUrl);
                } else {
                    showError("Failed to extract video stream");
                }
                hideLoadingState();
            });
        } catch (Exception e) {
            Log.e(TAG, "Failed to load video", e);
            mainHandler.post(() -> showError("Failed to load video: " + e.getMessage()));
        }
    }

    // UI event handlers
    private void setupEventListeners() {
        // Player view tap for overlay toggle
        setClickListenerIfNotNull(binding.playerView, v -> toggleOverlayVisibility());
        
        // Control buttons
        setClickListenerIfNotNull(binding.btnPlayPause, v -> togglePlayPause());
        setClickListenerIfNotNull(binding.btnReplay10, v -> seekBackward());
        setClickListenerIfNotNull(binding.btnForward10, v -> seekForward());
        setClickListenerIfNotNull(binding.btnOrientation, v -> toggleOrientation());
        setClickListenerIfNotNull(binding.btnBack, v -> {
            if (isFullscreen) {
                exitFullscreen();
            } else {
                finish();
            }
        });
        
        setupSeekBar();
    }

    private void setupSeekBar() {
        if (binding.videoProgress != null) {
            binding.videoProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser && player != null && !isLiveStream) {
                        long duration = player.getDuration();
                        if (duration > 0) {
                            long position = (duration * progress) / 100;
                            player.seekTo(position);
                        }
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    stopOverlayTimer();
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    resetOverlayTimer();
                }
            });
        }
    }

    // Overlay management
    private void toggleOverlayVisibility() {
        if (controlsVisible) {
            hideControls();
        } else {
            showControls();
        }
    }

    private void showControls() {
        showControlsInternal();
        resetOverlayTimer();
    }

    private void hideControls() {
        hideControlsInternal();
        stopOverlayTimer();
    }

    private void showControlsInternal() {
        if (binding.controlsOverlay != null) {
            binding.controlsOverlay.setVisibility(View.VISIBLE);
            binding.controlsOverlay.setAlpha(0f);
            binding.controlsOverlay.animate()
                .alpha(1f)
                .setDuration(300)
                .start();
        }
        
        if (!isFullscreen) {
            setVisibilityIfNotNull(binding.videoProgress, View.VISIBLE);
            //setVisibilityIfNotNull(binding.timeContainer, View.VISIBLE);
        }
        
        controlsVisible = true;
        startProgressUpdates();
    }

    private void hideControlsInternal() {
        if (binding.controlsOverlay != null) {
            binding.controlsOverlay.animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction(() -> {
                    if (binding.controlsOverlay != null) {
                        binding.controlsOverlay.setVisibility(View.GONE);
                    }
                })
                .start();
        }
        
        if (isFullscreen) {
            setVisibilityIfNotNull(binding.videoProgress, View.GONE);
           // setVisibilityIfNotNull(binding.timeContainer, View.GONE);
        }
        
        controlsVisible = false;
    }

    private void startOverlayTimer() {
        mainHandler.removeCallbacks(hideControlsRunnable);
        mainHandler.postDelayed(hideControlsRunnable, CONTROLS_HIDE_DELAY_MS);
    }

    private void stopOverlayTimer() {
        mainHandler.removeCallbacks(hideControlsRunnable);
    }

    private void resetOverlayTimer() {
        stopOverlayTimer();
        if (player != null && player.isPlaying()) {
            startOverlayTimer();
        }
    }

    // Orientation management
    private void handleOrientationChange(Configuration newConfig) {
        boolean newIsFullscreen = (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE);
        
        if (newIsFullscreen != isFullscreen) {
            isFullscreen = newIsFullscreen;
            updateLayoutForOrientation();
        }
    }

    private void toggleOrientation() {
        // Simple boolean toggle logic as requested
        if (isFullscreen) {
            exitFullscreen();
        } else {
            enterFullscreen();
        }
    }

    private void enterFullscreen() {
        isFullscreen = true;
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        updateLayoutForOrientation();
        
        if (controlsVisible) {
            showControlsInternal();
            resetOverlayTimer();
        }
    }

    private void exitFullscreen() {
        isFullscreen = false;
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        updateLayoutForOrientation();
        
        if (controlsVisible) {
            showControlsInternal();
            stopOverlayTimer(); // Don't auto-hide in portrait
        }
    }

    private void updateLayoutForOrientation() {
        if (isFullscreen) {
            hideSystemUI();
            adjustVideoPlayerLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            hideVideoDetails();
            updateOrientationButtonIcon();
        } else {
            showSystemUI();
            adjustVideoPlayerLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            showVideoDetails();
            updateOrientationButtonIcon();
        }
    }

    private void updateOrientationButtonIcon() {
        if (binding.btnOrientation != null) {
            int iconRes = isFullscreen ? R.drawable.ic_fullscreen_exit : R.drawable.ic_fullscreen;
            binding.btnOrientation.setImageResource(iconRes);
        }
    }

    private void adjustVideoPlayerLayout(int width, int height) {
        if (binding.playerView != null) {
            ViewGroup.LayoutParams params = binding.playerView.getLayoutParams();
            params.width = width;
            params.height = height;
            binding.playerView.setLayoutParams(params);
        }
    }

    private void hideVideoDetails() {
        // Hide video information in landscape mode
        setVisibilityIfNotNull(binding.videoInfoContainer, View.GONE);
    }

    private void showVideoDetails() {
        // Show video information in portrait mode
        setVisibilityIfNotNull(binding.videoInfoContainer, View.VISIBLE);
    }

    private void hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            );
        }
    }

    private void showSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(true);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            );
        }
    }

    // Progress updates for live streams
    private void startProgressUpdates() {
        mainHandler.removeCallbacks(updateProgressRunnable);
        mainHandler.post(updateProgressRunnable);
    }

    private void stopProgressUpdates() {
        mainHandler.removeCallbacks(updateProgressRunnable);
    }

    private void updateProgress() {
        if (player == null || binding == null) return;
        
        long currentPosition = player.getCurrentPosition();
        long duration = player.getDuration();
        
        // Update progress bar (disable for live streams)
        if (binding.videoProgress != null) {
            if (isLiveStream || duration <= 0) {
                binding.videoProgress.setEnabled(false);
                binding.videoProgress.setProgress(100); // Show full for live
            } else {
                binding.videoProgress.setEnabled(true);
                int progress = (int) ((currentPosition * 100) / duration);
                binding.videoProgress.setProgress(progress);
            }
        }
        
        // Update time displays
        if (binding.txtCurrentTime != null) {
            if (isLiveStream) {
                binding.txtCurrentTime.setText("LIVE");
            } else {
                binding.txtCurrentTime.setText(formatTime(currentPosition));
            }
        }
        
        if (binding.txtRemainingTime != null) {
            if (isLiveStream || duration <= 0) {
                binding.txtRemainingTime.setText("");
            } else {
                long remainingTime = duration - currentPosition;
                binding.txtRemainingTime.setText("-" + formatTime(remainingTime));
            }
        }
    }

    // Utility methods
    private String formatTime(long millis) {
        if (millis < 0) millis = 0;
        
        long totalSeconds = millis / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        
        if (minutes >= 60) {
            long hours = minutes / 60;
            minutes = minutes % 60;
            return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
        }
    }

    private String formatTimeAgo(long uploadTimeMs) {
        long currentTimeMs = System.currentTimeMillis();
        long timeDifferenceMs = currentTimeMs - uploadTimeMs;
            
        if (timeDifferenceMs < 0) {
            return "Just now";
        }

        long timeDifferenceSeconds = timeDifferenceMs / MILLISECONDS_IN_SECOND;

        if (timeDifferenceSeconds < SECONDS_IN_MINUTE) {
            return timeDifferenceSeconds <= 5 ? 
                "Just now" : 
                timeDifferenceSeconds + " seconds ago";
        }

        long timeDifferenceMinutes = timeDifferenceSeconds / SECONDS_IN_MINUTE;
        if (timeDifferenceMinutes < MINUTES_IN_HOUR) {
            return timeDifferenceMinutes == 1 ? 
                "1 minute ago" : 
                timeDifferenceMinutes + " minutes ago";
        }

        long timeDifferenceHours = timeDifferenceMinutes / MINUTES_IN_HOUR;
        if (timeDifferenceHours < HOURS_IN_DAY) {
            return timeDifferenceHours == 1 ? 
                "1 hour ago" : 
                timeDifferenceHours + " hours ago";
        }

        long timeDifferenceDays = timeDifferenceHours / HOURS_IN_DAY;
        if (timeDifferenceDays < DAYS_IN_WEEK) {
            return timeDifferenceDays == 1 ? 
                "1 day ago" : 
                timeDifferenceDays + " days ago";
        }

        long timeDifferenceWeeks = timeDifferenceDays / DAYS_IN_WEEK;
        if (timeDifferenceDays < DAYS_IN_MONTH) {
            return timeDifferenceWeeks == 1 ? 
                "1 week ago" : 
                timeDifferenceWeeks + " weeks ago";
        }

        long timeDifferenceMonths = timeDifferenceDays / DAYS_IN_MONTH;
        if (timeDifferenceDays < DAYS_IN_YEAR) {
            return timeDifferenceMonths == 1 ? 
                "1 month ago" : 
                timeDifferenceMonths + " months ago";
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
        return dateFormat.format(new Date(uploadTimeMs));
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

    // Loading states
    private void showLoadingState() {
        setVisibilityIfNotNull(binding.videoProgress, View.VISIBLE);
    }

    private void hideLoadingState() {
        setVisibilityIfNotNull(binding.videoProgress, View.GONE);
    }

    // Error handling
    private void showError(String message) {
        Log.w(TAG, "Error: " + message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        hideLoadingState();
    }

    // Timer management
    private void stopAllTimers() {
        stopOverlayTimer();
        stopProgressUpdates();
    }

    // Cleanup
    private void cleanupResources() {
        mainHandler.removeCallbacksAndMessages(null);
        if (backgroundExecutor != null && !backgroundExecutor.isShutdown()) {
            backgroundExecutor.shutdownNow();
        }
    }

    // ExoPlayer listener callbacks
    @Override
    public void onPlaybackStateChanged(int playbackState) {
        switch (playbackState) {
            case Player.STATE_BUFFERING:
                showLoadingState();
                break;
            case Player.STATE_READY:
                hideLoadingState();
                if (player != null && player.isPlaying()) {
                    resetOverlayTimer();
                }
                break;
            case Player.STATE_ENDED:
                hideLoadingState();
                stopOverlayTimer();
                showControls();
                if (binding.btnPlayPause != null) {
                    binding.btnPlayPause.setImageResource(R.drawable.ic_replay);
                }
                break;
        }
    }

    @Override
    public void onIsPlayingChanged(boolean isPlaying) {
        if (binding.btnPlayPause != null) {
            int iconRes = isPlaying ? R.drawable.ic_pause : R.drawable.ic_play_arrow;
            binding.btnPlayPause.setImageResource(iconRes);
        }
        
        if (isPlaying) {
            resetOverlayTimer();
        } else {
            stopOverlayTimer();
            if (!controlsVisible) {
                showControls();
            }
        }
    }

    @Override
    public void onPlayerError(@NonNull PlaybackException error) {
        Log.e(TAG, "Player error", error);
        showError("Playback error: " + error.getMessage());
        hideLoadingState();
        stopOverlayTimer();
        showControls();
    }

    // Utility methods
    private void setTextIfNotNull(android.widget.TextView textView, String text) {
        if (textView != null && text != null) {
            textView.setText(text);
        }
    }

    private void setVisibilityIfNotNull(View view, int visibility) {
        if (view != null) {
            view.setVisibility(visibility);
        }
    }

    private void setClickListenerIfNotNull(View view, View.OnClickListener listener) {
        if (view != null) {
            view.setOnClickListener(listener);
        }
    }

    // Stream extractor class
    private static class StreamExtractor {
        
        public StreamInfo extractStreamInfo(String videoUrl) throws Exception {
            Log.d(TAG, "Extracting stream info from: " + videoUrl);
            
            if (videoUrl.contains("youtube.com/watch") || videoUrl.contains("youtu.be/")) {
                return StreamInfo.getInfo(ServiceList.YouTube, videoUrl);
            }
            return null;
        }
        
        public String extractVideoStream(String videoUrl) throws Exception {
            Log.d(TAG, "Extracting stream from: " + videoUrl);
            
            if (videoUrl.contains("example.com/stream")) {
                return videoUrl;
            } else if (videoUrl.contains("youtube.com/watch") || videoUrl.contains("youtu.be/")) {
                StreamInfo info = StreamInfo.getInfo(ServiceList.YouTube, videoUrl);
                
                // Try to get the best quality video stream
                if (!info.getVideoStreams().isEmpty()) {
                    // Get the highest quality stream available
                    return info.getVideoStreams().get(0).getUrl();
                } else if (!info.getVideoOnlyStreams().isEmpty()) {
                    // Fallback to video-only streams if regular video streams aren't available
                    return info.getVideoOnlyStreams().get(0).getUrl();
                } else if (!info.getAudioStreams().isEmpty()) {
                    // Last resort: audio only
                    return info.getAudioStreams().get(0).getUrl();
                }
                throw new Exception("No playable streams found for this video");
            } else {
                // For direct video URLs, return as-is
                return videoUrl;
            }
        }
    }
}