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
    private static final int CONTROLS_HIDE_DELAY_MS = 3000;
    private static final int PROGRESS_UPDATE_INTERVAL_MS = 1000;
    private static final int SEEK_INCREMENT_MS = 10000;
    private static final int VIDEO_HEIGHT_DP = 250;
    
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
    private boolean controlsVisible = true;
    
    // UI runnables
    private final Runnable hideControlsRunnable = this::hideControls;
    private final Runnable updateProgressRunnable = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            if (isPlaying()) {
                mainHandler.postDelayed(this, PROGRESS_UPDATE_INTERVAL_MS);
            }
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initializeActivity(savedInstanceState);
        setupUI();
        loadVideoContent();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        savePlayerState(outState);
    }

    @Override
    protected void onPause() {
        super.onPause();
        pausePlayer();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumePlayer();
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
        handleOrientationChange(newConfig);
        recreateUI();
    }

    // Initialization methods
    private void initializeActivity(@Nullable Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            restorePlayerState(savedInstanceState);
        }
        
        binding = ActivityPlayerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        backgroundExecutor = Executors.newSingleThreadExecutor();
        streamExtractor = new StreamExtractor();
        
        setupSystemUI();
        setupBackPressHandler();
    }

    private void setupUI() {
        initializePlayer();
        setupEventListeners();
        showControls();
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
        player = new ExoPlayer.Builder(this).build();
        binding.playerView.setPlayer(player);
        binding.playerView.setUseController(false);
        player.addListener(this);
    }

    private void playVideo(String streamUrl) {
        if (player == null || streamUrl == null) return;
        
        MediaItem mediaItem = MediaItem.fromUri(streamUrl);
        player.setMediaItem(mediaItem);
        player.prepare();
        player.seekTo(playbackPosition);
        player.setPlayWhenReady(playWhenReady);
        
        Log.d(TAG, "Playing video: " + streamUrl);
    }

    private void togglePlayPause() {
        if (player == null) return;
        
        if (player.isPlaying()) {
            player.pause();
        } else {
            player.play();
        }
        resetControlsTimer();
    }

    private void seekTo(long positionMs) {
        if (player != null) {
            player.seekTo(positionMs);
        }
    }

    private void seekBackward() {
        if (player == null) return;
        long newPosition = Math.max(0, player.getCurrentPosition() - SEEK_INCREMENT_MS);
        player.seekTo(newPosition);
        resetControlsTimer();
    }

    private void seekForward() {
        if (player == null) return;
        long duration = player.getDuration();
        long newPosition = Math.min(duration, player.getCurrentPosition() + SEEK_INCREMENT_MS);
        player.seekTo(newPosition);
        resetControlsTimer();
    }

    private boolean isPlaying() {
        return player != null && player.isPlaying();
    }

    private void pausePlayer() {
        if (player != null && player.isPlaying()) {
            player.pause();
        }
    }

    private void resumePlayer() {
        if (player != null && playWhenReady) {
            player.play();
        }
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
    }

    private void restorePlayerState(Bundle savedInstanceState) {
        playbackPosition = savedInstanceState.getLong("video_position", 0);
        playWhenReady = savedInstanceState.getBoolean("video_playing", true);
    }

    // Video loading
    private void loadVideoContent() {
        loadVideoData();
        String videoUrl = getIntent().getStringExtra(EXTRA_VIDEO_URL);
        
        if (videoUrl != null && !videoUrl.trim().isEmpty()) {
            loadVideo(videoUrl.trim());
        } else {
            showError("No video URL provided");
        }
    }

    private void loadVideoData() {
        Intent intent = getIntent();
        String title = intent.getStringExtra(EXTRA_VIDEO_TITLE);
        String channelName = intent.getStringExtra(EXTRA_CHANNEL_NAME);
        
        // Only set from intent if values exist, otherwise they'll be set from extracted data
        if (title != null && !title.isEmpty()) {
            setTextIfNotNull(binding.txtTitle, title);
        }
        if (channelName != null && !channelName.isEmpty()) {
            setTextIfNotNull(binding.txtChannelName, channelName);
        }
    }

    private void populateVideoMetadata(StreamInfo streamInfo) {
        try {
            // Update title if not already set from intent
            if (streamInfo.getName() != null && !streamInfo.getName().isEmpty()) {
                setTextIfNotNull(binding.txtTitle, streamInfo.getName());
            }

            // Update channel name if available
            if (streamInfo.getUploaderName() != null && !streamInfo.getUploaderName().isEmpty()) {
                setTextIfNotNull(binding.txtChannelName, streamInfo.getUploaderName());
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
                setTextIfNotNull(binding.txtMeta, metaText);
            }

            // Update like count if available
            if (streamInfo.getLikeCount() > 0) {
                setTextIfNotNull(binding.txtLikeCount, formatViewCount(streamInfo.getLikeCount()));
            }

            Log.d(TAG, "Video metadata populated successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error populating video metadata", e);
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

        // For very old content, show actual date
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
        setClickListenerIfNotNull(binding.playerView, v -> toggleControlsVisibility());
        setClickListenerIfNotNull(binding.btnPlayPause, v -> togglePlayPause());
        setClickListenerIfNotNull(binding.btnReplay10, v -> seekBackward());
        setClickListenerIfNotNull(binding.btnForward10, v -> seekForward());
        setClickListenerIfNotNull(binding.btnOrientation, v -> toggleFullscreen());
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
                    if (fromUser && player != null) {
                        long duration = player.getDuration();
                        if (duration > 0) {
                            seekTo((duration * progress) / 100);
                        }
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    stopControlsHideTimer();
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    resetControlsTimer();
                }
            });
        }
    }

    // Fullscreen management
    private void handleOrientationChange(Configuration newConfig) {
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            enterFullscreen();
        } else {
            exitFullscreen();
        }
    }

    private void toggleFullscreen() {
        if (isFullscreen) {
            exitFullscreen();
        } else {
            enterFullscreen();
        }
    }

    private void enterFullscreen() {
        isFullscreen = true;
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        hideSystemUI();
        adjustVideoPlayerLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        hideVideoDetails();
        showControls();
        resetControlsTimer();
    }

    private void exitFullscreen() {
        isFullscreen = false;
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        showSystemUI();
        adjustVideoPlayerLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        showVideoDetails();
        showControls();
        stopControlsHideTimer();
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
        // In landscape mode, hide all content below the player
        setVisibilityIfNotNull(binding.videoProgress, View.GONE);
        setVisibilityIfNotNull(binding.txtTitle, View.GONE);
        setVisibilityIfNotNull(binding.txtMeta, View.GONE);
        setVisibilityIfNotNull(binding.rowChannel, View.GONE);
        setVisibilityIfNotNull(binding.rowActionsScroll, View.GONE);
        setVisibilityIfNotNull(binding.txtCommentsCount, View.GONE);
        setVisibilityIfNotNull(binding.btnCommentsExpand, View.GONE);
        setVisibilityIfNotNull(binding.rvRelated, View.GONE);
    }

    private void showVideoDetails() {
        // In portrait mode, show all content below the player
        setVisibilityIfNotNull(binding.videoProgress, View.VISIBLE);
        setVisibilityIfNotNull(binding.txtTitle, View.VISIBLE);
        setVisibilityIfNotNull(binding.txtMeta, View.VISIBLE);
        setVisibilityIfNotNull(binding.rowChannel, View.VISIBLE);
        setVisibilityIfNotNull(binding.rowActionsScroll, View.VISIBLE);
        setVisibilityIfNotNull(binding.txtCommentsCount, View.VISIBLE);
        setVisibilityIfNotNull(binding.btnCommentsExpand, View.VISIBLE);
        setVisibilityIfNotNull(binding.rvRelated, View.VISIBLE);
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

    // Controls visibility
    private void toggleControlsVisibility() {
        if (controlsVisible) {
            hideControls();
        } else {
            showControls();
            resetControlsTimer();
        }
    }

    private void showControls() {
        // Show player controls overlay
        View playerViewOverlay = binding.playerView.getOverlayFrameLayout();
        if (playerViewOverlay != null) {
            playerViewOverlay.setVisibility(View.VISIBLE);
        }
        controlsVisible = true;
        mainHandler.removeCallbacks(updateProgressRunnable);
        mainHandler.post(updateProgressRunnable);
    }

    private void hideControls() {
        // Hide player controls overlay
        View playerViewOverlay = binding.playerView.getOverlayFrameLayout();
        if (playerViewOverlay != null) {
            playerViewOverlay.setVisibility(View.GONE);
        }
        controlsVisible = false;
        mainHandler.removeCallbacks(updateProgressRunnable);
    }

    private void startControlsHideTimer() {
        mainHandler.removeCallbacks(hideControlsRunnable);
        mainHandler.postDelayed(hideControlsRunnable, CONTROLS_HIDE_DELAY_MS);
    }

    private void stopControlsHideTimer() {
        mainHandler.removeCallbacks(hideControlsRunnable);
    }

    private void resetControlsTimer() {
        stopControlsHideTimer();
        if (isPlaying()) {
            startControlsHideTimer();
        }
    }

    // Progress updates
    private void updateProgress() {
        if (player == null || binding == null) return;
        
        long currentPosition = player.getCurrentPosition();
        long duration = player.getDuration();
        
        if (duration > 0 && binding.videoProgress != null) {
            int progress = (int) ((currentPosition * 100) / duration);
            binding.videoProgress.setProgress(progress);
        }
        
        // Update time displays
        if (binding.txtCurrentTime != null) {
            binding.txtCurrentTime.setText(formatTime(currentPosition));
        }
        
        if (binding.txtRemainingTime != null && duration > 0) {
            long remainingTime = duration - currentPosition;
            binding.txtRemainingTime.setText("-" + formatTime(remainingTime));
        }
    }

    private String formatTime(long millis) {
        if (millis < 0) millis = 0;
        
        return new Formatter(new StringBuilder(), Locale.getDefault())
            .format("%02d:%02d",
                java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(millis),
                java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(millis) -
                java.util.concurrent.TimeUnit.MINUTES.toSeconds(
                    java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(millis)))
            .toString();
    }

    // Loading states
    private void showLoadingState() {
        // Show loading indicator - using the progress bar from layout
        setVisibilityIfNotNull(binding.videoProgress, View.VISIBLE);
    }

    private void hideLoadingState() {
        // Loading is handled by progress updates, so no specific action needed here
    }

    // Error handling
    private void showError(String message) {
        Log.w(TAG, "Error: " + message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        hideLoadingState();
    }

    // UI recreation after configuration change
    private void recreateUI() {
        try {
            binding = ActivityPlayerBinding.inflate(getLayoutInflater());
            setContentView(binding.getRoot());
            setupUI();
            loadVideoData();
            
            if (player != null) {
                binding.playerView.setPlayer(player);
                seekTo(playbackPosition);
                player.setPlayWhenReady(playWhenReady);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error recreating UI", e);
            showError("Failed to recreate interface");
        }
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
                if (isPlaying()) {
                    resetControlsTimer();
                }
                break;
            case Player.STATE_ENDED:
                hideLoadingState();
                stopControlsHideTimer();
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
            resetControlsTimer();
        } else {
            stopControlsHideTimer();
            showControls();
        }
    }

    @Override
    public void onPlayerError(@NonNull PlaybackException error) {
        Log.e(TAG, "Player error", error);
        showError("Playback error: " + error.getMessage());
        hideLoadingState();
        stopControlsHideTimer();
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