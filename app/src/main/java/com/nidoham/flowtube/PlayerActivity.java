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
import android.view.WindowManager;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.util.Util;
import com.nidoham.flowtube.databinding.ActivityPlayerBinding;

import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.lang.ref.WeakReference;
import java.util.Formatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PlayerActivity extends AppCompatActivity implements Player.Listener {

    private static final String TAG = "PlayerActivity";

    public static final String EXTRA_VIDEO_URL = "video_url";
    public static final String EXTRA_VIDEO_TITLE = "video_title";
    public static final String EXTRA_VIDEO_VIEWS = "video_views";
    public static final String EXTRA_UPLOAD_TIME = "upload_time";
    public static final String EXTRA_CHANNEL_NAME = "channel_name";
    public static final String EXTRA_SUBSCRIBER_COUNT = "subscriber_count";
    public static final String EXTRA_LIKE_COUNT = "like_count";
    public static final String EXTRA_COMMENT_COUNT = "comment_count";

    private static final int CONTROLS_HIDE_DELAY_MS = 3000;
    private static final int PROGRESS_UPDATE_INTERVAL_MS = 1000;
    private static final int SEEK_INCREMENT_MS = 10000;
    private static final int VIDEO_PLAYER_HEIGHT_DP = 250;

    private ActivityPlayerBinding binding;
    private PlayerManager playerManager;
    private UIController uiController;
    private VideoDataManager videoDataManager;
    
    private StreamExtractor streamExtractor;
    private StreamExtractorInfo streamExtractorInfo;
    
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ExecutorService backgroundExecutor;

    private long playbackPosition = 0;
    private boolean playWhenReady = true;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            playbackPosition = savedInstanceState.getLong("video_position", 0);
            playWhenReady = savedInstanceState.getBoolean("video_playing", true);
        }

        binding = ActivityPlayerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        try {
            initializeActivity();
            setupComponents();
            loadVideoContent();
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize activity", e);
            handleFatalError("Failed to initialize video player");
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (playerManager != null && playerManager.player != null) {
            playbackPosition = playerManager.player.getCurrentPosition();
            playWhenReady = playerManager.player.getPlayWhenReady();
        }
        outState.putLong("video_position", playbackPosition);
        outState.putBoolean("video_playing", playWhenReady);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        playbackPosition = savedInstanceState.getLong("video_position", 0);
        playWhenReady = savedInstanceState.getBoolean("video_playing", true);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (playerManager != null) {
            playerManager.pause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (playerManager != null) {
            playerManager.resume();
        }
    }

    @Override
    protected void onDestroy() {
        cleanupResources();
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (uiController != null) {
            uiController.handleOrientationChange(newConfig);
        }
        binding = ActivityPlayerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setupComponents();
        if (uiController != null) {
            uiController.updateBinding(binding);
        }
        if (playerManager != null) {
            playerManager.seekTo(playbackPosition);
            playerManager.setPlayWhenReady(playWhenReady);
        }
    }

    private void initializeActivity() {
        backgroundExecutor = Executors.newSingleThreadExecutor();
        setupSystemUI();
        setupBackPressHandler();
    }

    private void setupComponents() {
        playerManager = new PlayerManager(this, binding);
        uiController = new UIController(this, binding, mainHandler);
        videoDataManager = new VideoDataManager(binding);
        streamExtractor = new StreamExtractor();

        playerManager.setPlayerListener(this);
        uiController.setPlayerManager(playerManager);

        uiController.setupEventListeners();
        setupRecyclerViews();
    }

    private void loadVideoContent() {
        videoDataManager.loadFromIntent(getIntent());
        String videoUrl = getIntent().getStringExtra(EXTRA_VIDEO_URL);

        if (videoUrl != null && !videoUrl.trim().isEmpty()) {
            loadVideo(videoUrl.trim());
        } else {
            handleError("No video URL provided");
        }
    }

    private void loadVideo(String videoUrl) {
        if (isFinishing() || isDestroyed()) return;
        uiController.showLoadingState();

        backgroundExecutor.submit(() -> {
            try {
                
                StreamInfo stream = streamExtractorInfo.extractInfoStream(videoUrl);
                final String streamUrl = streamExtractor.extractVideoStream(videoUrl);
                
                Intent intent = new Intent();
                intent.putExtra(EXTRA_VIDEO_TITLE, stream.getName().toString());
                intent.putExtra(EXTRA_VIDEO_VIEWS, stream.getViewCount());
                intent.putExtra(EXTRA_UPLOAD_TIME, stream.getUploadDate());
                
                intent.putExtra(EXTRA_CHANNEL_NAME, stream.getUploaderName().toString());
                intent.putExtra(EXTRA_SUBSCRIBER_COUNT, 0);
                intent.putExtra(EXTRA_LIKE_COUNT, stream.getLikeCount());
                
                intent.putExtra(EXTRA_COMMENT_COUNT, 0);
                startActivity(intent);
                
                //String streamTitle = streamExtractor.
                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (streamUrl != null) {
                        playerManager.playVideo(streamUrl, playbackPosition, playWhenReady);
                    } else {
                        handleError("Failed to extract video stream");
                    }
                    uiController.hideLoadingState();
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to load video", e);
                mainHandler.post(() -> handleError("Failed to load video: " + e.getMessage()));
            }
        });
    }

    private void setupSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            int seedColor = ContextCompat.getColor(this, R.color.black);
            getWindow().setStatusBarColor(seedColor);
            getWindow().setNavigationBarColor(seedColor);
        }
    }

    private void setupRecyclerViews() {
       /* if (binding.upNextRecyclerView != null) {
            binding.upNextRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        } */
    }

    private void setupBackPressHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (uiController != null && uiController.isFullscreen()) {
                    uiController.exitFullscreen();
                } else {
                    finish();
                }
            }
        });
    }

    private void handleError(String message) {
        Log.w(TAG, "Handling error: " + message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        uiController.hideLoadingState();
    }

    private void handleFatalError(String message) {
        Log.e(TAG, "Fatal error: " + message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        finish();
    }

    private void cleanupResources() {
        if (playerManager != null) playerManager.cleanup();
        if (uiController != null) uiController.cleanup();
        mainHandler.removeCallbacksAndMessages(null);
        if (backgroundExecutor != null && !backgroundExecutor.isShutdown()) {
            backgroundExecutor.shutdownNow();
        }
    }

    @Override
    public void onPlaybackStateChanged(int playbackState) {
        if (uiController != null) {
            uiController.onPlaybackStateChanged(playbackState);
        }
    }

    @Override
    public void onIsPlayingChanged(boolean isPlaying) {
        if (uiController != null) {
            uiController.onIsPlayingChanged(isPlaying);
        }
    }

    @Override
    public void onPlayerError(@NonNull PlaybackException error) {
        Log.e(TAG, "Player error occurred", error);
        handleError("Video playback error: " + error.getMessage());
        if (uiController != null) {
            uiController.onPlayerError();
        }
    }

    private static class PlayerManager {
        private final ActivityPlayerBinding binding;
        private ExoPlayer player;
        private boolean wasPlayingBeforePause = false;

        public PlayerManager(PlayerActivity activity, ActivityPlayerBinding binding) {
            this.binding = binding;
            initializePlayer(activity);
        }

        private void initializePlayer(PlayerActivity activity) {
            player = new ExoPlayer.Builder(activity).build();
            binding.exoPlayerView.setPlayer(player);
            binding.exoPlayerView.setUseController(false);
        }

        public void setPlayerListener(Player.Listener listener) {
            if (player != null) {
                player.addListener(listener);
            }
        }

        public void playVideo(String streamUrl, long position, boolean playWhenReady) {
            if (player == null) return;
            MediaItem mediaItem = MediaItem.fromUri(streamUrl);
            player.setMediaItem(mediaItem);
            player.prepare();
            player.seekTo(position);
            player.setPlayWhenReady(playWhenReady);
            wasPlayingBeforePause = playWhenReady;
            Log.d(TAG, "Playing video stream: " + streamUrl + " from position: " + position + ", playWhenReady: " + playWhenReady);
        }

        public void pause() {
            if (player != null) {
                wasPlayingBeforePause = player.isPlaying();
                player.pause();
            }
        }

        public void resume() {
            if (player != null && wasPlayingBeforePause) {
                player.play();
            }
        }

        public void togglePlayPause() {
            if (player == null) return;
            if (player.isPlaying()) {
                player.pause();
            } else {
                player.play();
            }
        }

        public void seekTo(long positionMs) {
            if (player != null) player.seekTo(positionMs);
        }

        public void seekBackward() {
            if (player == null) return;
            long newPosition = Math.max(0, player.getCurrentPosition() - SEEK_INCREMENT_MS);
            player.seekTo(newPosition);
        }

        public void seekForward() {
            if (player == null) return;
            long newPosition = Math.min(player.getDuration(), player.getCurrentPosition() + SEEK_INCREMENT_MS);
            player.seekTo(newPosition);
        }

        public boolean isPlaying() {
            return player != null && player.isPlaying();
        }

        public long getCurrentPosition() {
            return player != null ? player.getCurrentPosition() : 0;
        }

        public long getDuration() {
            return player != null ? player.getDuration() : 0;
        }

        public void setPlayWhenReady(boolean playWhenReady) {
            if (player != null) {
                player.setPlayWhenReady(playWhenReady);
            }
        }

        public void cleanup() {
            if (player != null) {
                player.release();
                player = null;
            }
        }
    }

    private static class UIController {
        private final WeakReference<PlayerActivity> activityRef;
        private ActivityPlayerBinding binding;
        private final Handler handler;

        private PlayerManager playerManager;

        private boolean isFullscreen = false;
        private boolean controlsVisible = true;
        private boolean videoEnded = false;

        private Runnable hideControlsRunnable;
        private Runnable updateProgressRunnable;

        public UIController(PlayerActivity activity, ActivityPlayerBinding binding, Handler handler) {
            this.activityRef = new WeakReference<>(activity);
            this.binding = binding;
            this.handler = handler;
            initRunnables();
        }

        public void setPlayerManager(PlayerManager playerManager) {
            this.playerManager = playerManager;
        }

        public boolean isFullscreen() {
            return isFullscreen;
        }

        public void updateBinding(ActivityPlayerBinding newBinding) {
            this.binding = newBinding;
            setupEventListeners();
        }

        private void initRunnables() {
            hideControlsRunnable = () -> {
                if (controlsVisible && !videoEnded) {
                    hideControls();
                }
            };

            updateProgressRunnable = new Runnable() {
                @Override
                public void run() {
                    if (playerManager != null && playerManager.player != null) {
                        long currentPosition = playerManager.getCurrentPosition();
                        long duration = playerManager.getDuration();
                        if (duration > 0) {
                            int progress = (int) ((currentPosition * 100) / duration);
                            if (binding.seekBar != null) {
                                binding.seekBar.setProgress(progress);
                            }
                        }
                        if (binding.timeDisplay != null) {
                            binding.timeDisplay.setText(formatTime(currentPosition) + " / " + formatTime(duration));
                        }
                    }
                    if (playerManager != null && playerManager.isPlaying() && !videoEnded) {
                        handler.postDelayed(this, PROGRESS_UPDATE_INTERVAL_MS);
                    }
                }
            };
        }

        public void setupEventListeners() {
            if (binding.exoPlayerView != null) {
                binding.exoPlayerView.setOnClickListener(v -> toggleControlsVisibility());
            }

            if (binding.centerPlayButton != null) {
                binding.centerPlayButton.setOnClickListener(v -> {
                    if (playerManager != null) {
                        if (videoEnded) {
                            playerManager.seekTo(0);
                            playerManager.player.play();
                            videoEnded = false;
                        } else {
                            playerManager.togglePlayPause();
                        }
                    }
                    resetControlsTimer();
                });
            }

            if (binding.previousButton != null) {
                binding.previousButton.setOnClickListener(v -> {
                    if (playerManager != null) playerManager.seekBackward();
                    resetControlsTimer();
                });
            }

            if (binding.nextButton != null) {
                binding.nextButton.setOnClickListener(v -> {
                    if (playerManager != null) playerManager.seekForward();
                    resetControlsTimer();
                });
            }

            if (binding.fullscreenButton != null) {
                binding.fullscreenButton.setOnClickListener(v -> toggleFullscreen());
            }

            if (binding.expandTitleButton != null) {
                binding.expandTitleButton.setOnClickListener(v -> toggleTitleExpansion());
            }

            if (binding.seekBar != null) {
                binding.seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        if (fromUser && playerManager != null) {
                            long duration = playerManager.getDuration();
                            if (duration > 0) {
                                playerManager.seekTo((duration * progress) / 100);
                                if (videoEnded) {
                                    videoEnded = false;
                                }
                            }
                        }
                    }
                    @Override public void onStartTrackingTouch(SeekBar seekBar) { 
                        stopControlsHideTimer(); 
                    }
                    @Override public void onStopTrackingTouch(SeekBar seekBar) { 
                        resetControlsTimer(); 
                    }
                });
            }

            if (binding.exitFullscreenButton != null) {
                binding.exitFullscreenButton.setOnClickListener(v -> exitFullscreen());
            }
            if (binding.landscapeVideoTitle != null) {
            }
            if (binding.moreOptionsButton != null) {
            }
            if (binding.playbackSpeedButton != null) {
            }
            if (binding.qualityButton != null) {
            }
            if (binding.captionsButton != null) {
            }
            if (binding.settingsButton != null) {
            }
            if (binding.landscapeLikeButton != null) {
            }
            if (binding.landscapeShareButton != null) {
            }
            if (binding.landscapeSaveButton != null) {
            }
        }

        public void handleOrientationChange(Configuration newConfig) {
            PlayerActivity activity = activityRef.get();
            if (activity == null) return;

            if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                enterFullscreen();
            } else {
                exitFullscreen();
            }
        }

        private void toggleFullscreen() {
            PlayerActivity activity = activityRef.get();
            if (activity == null) return;

            if (isFullscreen) {
                exitFullscreen();
            } else {
                enterFullscreen();
            }
        }

        public void enterFullscreen() {
            PlayerActivity activity = activityRef.get();
            if (activity == null) return;

            isFullscreen = true;
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            hideSystemUI(activity);

            if (binding.videoPlayerContainer != null) {
                ViewGroup.LayoutParams params = binding.videoPlayerContainer.getLayoutParams();
                params.width = ViewGroup.LayoutParams.MATCH_PARENT;
                params.height = ViewGroup.LayoutParams.MATCH_PARENT;
                binding.videoPlayerContainer.setLayoutParams(params);
            }

            if (binding.videoDetailsContainer != null) {
                binding.videoDetailsContainer.setVisibility(View.GONE);
            }
            
           /* if (binding.upNextRecyclerView != null) {
                binding.upNextRecyclerView.setVisibility(View.GONE);
            } */

            showControls();
            if (!videoEnded) {
                startControlsHideTimer();
            }
        }

        public void exitFullscreen() {
            PlayerActivity activity = activityRef.get();
            if (activity == null) return;

            isFullscreen = false;
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            showSystemUI(activity);

            if (binding.videoPlayerContainer != null) {
                ViewGroup.LayoutParams params = binding.videoPlayerContainer.getLayoutParams();
                params.width = ViewGroup.LayoutParams.MATCH_PARENT;
                params.height = (int) (VIDEO_PLAYER_HEIGHT_DP * activity.getResources().getDisplayMetrics().density);
                binding.videoPlayerContainer.setLayoutParams(params);
            }

            if (binding.videoDetailsContainer != null) {
                binding.videoDetailsContainer.setVisibility(View.VISIBLE);
            }
            
           /* if (binding.upNextRecyclerView != null) {
                binding.upNextRecyclerView.setVisibility(View.VISIBLE);
            } */

            stopControlsHideTimer();
            showControls();
        }

        private void hideSystemUI(PlayerActivity activity) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                activity.getWindow().setDecorFitsSystemWindows(false);
                WindowInsetsController controller = activity.getWindow().getInsetsController();
                if (controller != null) {
                    controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                    controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                }
            } else {
                activity.getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
            }
        }

        private void showSystemUI(PlayerActivity activity) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                activity.getWindow().setDecorFitsSystemWindows(true);
                WindowInsetsController controller = activity.getWindow().getInsetsController();
                if (controller != null) {
                    controller.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                }
            } else {
                activity.getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
            }
        }

        private void toggleControlsVisibility() {
            if (controlsVisible) {
                if (!videoEnded) {
                    hideControls();
                }
            } else {
                showControls();
                if (!videoEnded) {
                    resetControlsTimer();
                }
            }
        }

        private void showControls() {
            if (binding.videoControlsOverlay != null) {
                binding.videoControlsOverlay.setVisibility(View.VISIBLE);
                controlsVisible = true;
            }
            handler.removeCallbacks(updateProgressRunnable);
            handler.post(updateProgressRunnable);
        }

        private void hideControls() {
            if (binding.videoControlsOverlay != null) {
                binding.videoControlsOverlay.setVisibility(View.GONE);
                controlsVisible = false;
            }
            handler.removeCallbacks(updateProgressRunnable);
        }

        private void startControlsHideTimer() {
            if (!videoEnded) {
                handler.removeCallbacks(hideControlsRunnable);
                handler.postDelayed(hideControlsRunnable, CONTROLS_HIDE_DELAY_MS);
            }
        }

        private void stopControlsHideTimer() {
            handler.removeCallbacks(hideControlsRunnable);
        }

        private void resetControlsTimer() {
            stopControlsHideTimer();
            if (!videoEnded) {
                startControlsHideTimer();
            }
        }

        private void toggleTitleExpansion() {
            if (binding.videoTitle != null) {
                if (binding.videoTitle.getMaxLines() == 2) {
                    binding.videoTitle.setMaxLines(Integer.MAX_VALUE);
                    if (binding.expandTitleButton != null) {
                        binding.expandTitleButton.setImageResource(R.drawable.ic_expand_more);
                    }
                } else {
                    binding.videoTitle.setMaxLines(2);
                    if (binding.expandTitleButton != null) {
                        binding.expandTitleButton.setImageResource(R.drawable.ic_expand_more);
                    }
                }
            }
        }

        public void showLoadingState() {
            if (binding.progressBar != null) {
                binding.progressBar.setVisibility(View.VISIBLE);
            }
        }

        public void hideLoadingState() {
            if (binding.progressBar != null) {
                binding.progressBar.setVisibility(View.GONE);
            }
        }

        public void onPlaybackStateChanged(int playbackState) {
            PlayerActivity activity = activityRef.get();
            if (activity == null) return;

            if (playbackState == Player.STATE_BUFFERING) {
                showLoadingState();
            } else if (playbackState == Player.STATE_READY) {
                hideLoadingState();
                videoEnded = false;
                if (playerManager != null && playerManager.isPlaying()) {
                    startControlsHideTimer();
                    handler.post(updateProgressRunnable);
                }
            } else if (playbackState == Player.STATE_ENDED) {
                hideLoadingState();
                videoEnded = true;
                stopControlsHideTimer();
                showControls();
                if (binding.centerPlayButton != null) {
                    binding.centerPlayButton.setImageResource(R.drawable.ic_replay);
                }
                handler.removeCallbacks(updateProgressRunnable);
            }
        }

        public void onIsPlayingChanged(boolean isPlaying) {
            if (binding.centerPlayButton != null) {
                if (!videoEnded) {
                    binding.centerPlayButton.setImageResource(isPlaying ? R.drawable.ic_pause_circle_filled : R.drawable.ic_play_circle_filled);
                }
            }
            
            if (isPlaying) {
                videoEnded = false;
                if (!videoEnded) {
                    startControlsHideTimer();
                }
                handler.post(updateProgressRunnable);
            } else {
                if (!videoEnded) {
                    stopControlsHideTimer();
                    showControls();
                }
            }
        }

        public void onPlayerError() {
            hideLoadingState();
            videoEnded = false;
            stopControlsHideTimer();
            showControls();
        }

        private String formatTime(long millis) {
            if (millis < 0) millis = 0;
            return new Formatter(new StringBuilder(), Locale.getDefault()).format("%02d:%02d",
                    java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(millis),
                    java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(millis) -
                            java.util.concurrent.TimeUnit.MINUTES.toSeconds(java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(millis)))
                    .toString();
        }

        public void cleanup() {
            handler.removeCallbacks(hideControlsRunnable);
            handler.removeCallbacks(updateProgressRunnable);
        }
    }

    private static class VideoDataManager {
        private final ActivityPlayerBinding binding;

        public VideoDataManager(ActivityPlayerBinding binding) {
            this.binding = binding;
        }

        public void loadFromIntent(Intent intent) {
            if (binding.videoTitle != null) {
                binding.videoTitle.setText(intent.getStringExtra(EXTRA_VIDEO_TITLE));
            }
            if (binding.viewCount != null) {
                binding.viewCount.setText(intent.getStringExtra(EXTRA_VIDEO_VIEWS));
            }
            if (binding.uploadTime != null) {
                binding.uploadTime.setText(intent.getStringExtra(EXTRA_UPLOAD_TIME));
            }
            if (binding.channelName != null) {
                binding.channelName.setText(intent.getStringExtra(EXTRA_CHANNEL_NAME));
            }
            if (binding.subscriberCount != null) {
                binding.subscriberCount.setText(intent.getStringExtra(EXTRA_SUBSCRIBER_COUNT));
            }
            if (binding.likeCount != null) {
                binding.likeCount.setText(intent.getStringExtra(EXTRA_LIKE_COUNT));
            }
            if (binding.commentCount != null) {
                binding.commentCount.setText(intent.getStringExtra(EXTRA_COMMENT_COUNT));
            }

            if (binding.landscapeVideoTitle != null) {
                binding.landscapeVideoTitle.setText(intent.getStringExtra(EXTRA_VIDEO_TITLE));
            }
        }
    }

    private static class StreamExtractor {
        public String extractVideoStream(String videoUrl) throws Exception {
            Log.d(TAG, "Attempting to extract stream from: " + videoUrl);
            if (videoUrl.contains("example.com/stream")) {
                return videoUrl;
            } else if (videoUrl.contains("youtube.com/watch")) {
                StreamInfo info = StreamInfo.getInfo(ServiceList.YouTube, videoUrl);
                if (!info.getVideoStreams().isEmpty()) {
                    return info.getVideoStreams().get(0).getUrl();
                } else if (!info.getAudioStreams().isEmpty()) {
                    return info.getAudioStreams().get(0).getUrl();
                }
                return null;
            } else {
                return videoUrl;
            }
        }
    }
    
    private static class StreamExtractorInfo {
        public StreamInfo extractInfoStream(String videoUrl) throws Exception {
            Log.d(TAG, "Attempting to extract stream from: " + videoUrl);
            if (videoUrl.contains("example.com/stream")) {
                return null;
            } else if (videoUrl.contains("youtube.com/watch")) {
                StreamInfo info = StreamInfo.getInfo(ServiceList.YouTube, videoUrl);
                return info;
            } else {
                return null;
            }
        }
    }
}