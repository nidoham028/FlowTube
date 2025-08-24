package com.nidoham.flowtube;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
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

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Formatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PlayerActivity extends AppCompatActivity implements Player.Listener {

    private static final String TAG = "PlayerActivity";

    private ActivityPlayerBinding binding;
    private ExoPlayer player;
    private ExecutorService backgroundExecutor;

    private boolean isFullscreen = false;
    private boolean isPlaying = false;
    private boolean isLiked = false;
    private boolean isDisliked = false;
    private boolean controlsVisible = true;

    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable hideControlsRunnable;
    private Runnable updateProgressRunnable;

    private String videoUrl;

    private static final int CONTROLS_HIDE_DELAY = 3000;
    private static final int PROGRESS_UPDATE_INTERVAL = 1000;
    private static final int SEEK_INCREMENT = 10000;

    private static boolean newPipeInitialized = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityPlayerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        backgroundExecutor = Executors.newSingleThreadExecutor();

        initializeSystemUI();
        initializePlayer();
        setupEventListeners();
        setupRecyclerViews();
        loadVideoData();
        handleOrientationChange();

        setupBackPressHandler();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (player != null && !player.isPlaying()) {
            player.play();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (player != null && !player.isPlaying()) {
            player.play();
        }
    }

    @Override
    protected void onPause() {
        if (player != null) {
            player.pause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releasePlayer();
        cleanupResources();
    }



    private void initializeSystemUI() {
        int seedColor = ContextCompat.getColor(this, R.color.black);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(seedColor);
            getWindow().setNavigationBarColor(seedColor);
        }
    }

    private void initializePlayer() {
        player = new ExoPlayer.Builder(this).build();
        binding.exoPlayerView.setPlayer(player);
        player.addListener(this);

        videoUrl = getIntent().getStringExtra("video_url");

        if (videoUrl != null && !videoUrl.trim().isEmpty()) {
            fetchAndPlayFromYouTube(videoUrl.trim());
        } else {
            Toast.makeText(this, "No video URL provided", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupRecyclerViews() {
        binding.upNextRecyclerView.setLayoutManager(new LinearLayoutManager(this));
    }

    private void setupEventListeners() {
        setupPlaybackControls();
        setupActionButtons();
        setupCommentInput();
        setupSeekBar();
    }

    private void setupBackPressHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isFullscreen) {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                } else {
                    setEnabled(false);
                    PlayerActivity.super.onBackPressed();
                }
            }
        });
    }

    private void fetchAndPlayFromYouTube(String youtubeUrl) {
        backgroundExecutor.execute(() -> {
            try {
                StreamInfo info = StreamInfo.getInfo(ServiceList.YouTube, youtubeUrl);

                String bestProgressiveUrl = findBestVideoStream(info.getVideoStreams());

                if (bestProgressiveUrl != null) {
                    runOnUiThread(() -> playVideo(bestProgressiveUrl));
                } else {
                    runOnUiThread(() -> showError("No playable video stream found"));
                }
            } catch (Exception e) {
                Log.e(TAG, "Stream extraction failed", e);
                runOnUiThread(() -> showError("Failed to load video"));
            }
        });
    }

    private String findBestVideoStream(List<VideoStream> videoStreams) {
        String bestUrl = null;
        int bestHeight = -1;

        for (VideoStream stream : videoStreams) {
            if (stream.isVideoOnly()) continue;

            String format = stream.getFormat() != null ? stream.getFormat().getSuffix() : "";
            int height = parseResolutionHeight(stream.getResolution());

            boolean isBetter = bestUrl == null ||
                    (isMp4Format(format) && !isMp4Format(getBestFormat(bestUrl))) ||
                    (height > bestHeight);

            if (isBetter) {
                bestUrl = stream.getUrl();
                bestHeight = height;
            }
        }

        if (bestUrl == null && !videoStreams.isEmpty()) {
            bestUrl = videoStreams.get(0).getUrl();
            Log.w(TAG, "Using fallback video stream");
        }

        return bestUrl;
    }

    private void playVideo(String streamUrl) {
        try {
            MediaItem mediaItem = MediaItem.fromUri(streamUrl);
            player.setMediaItem(mediaItem);
            player.prepare();
            player.play();
            Log.d(TAG, "Playing video stream");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start playback", e);
            showError("Failed to start video playback");
        }
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private int parseResolutionHeight(String resolution) {
        if (resolution == null || resolution.isEmpty()) {
            return -1;
        }
        
        // Extract numeric part from resolution string (e.g., "720p" -> 720)
        try {
            String numericPart = resolution.replaceAll("[^0-9]", "");
            return Integer.parseInt(numericPart);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private boolean isMp4Format(@Nullable String format) {
        return format != null && format.toLowerCase(Locale.ROOT).contains("mp4");
    }

    private String getBestFormat(String url) {
        if (url == null) return "";
        
        int queryIndex = url.indexOf('?');
        String baseUrl = queryIndex >= 0 ? url.substring(0, queryIndex) : url;
        int dotIndex = baseUrl.lastIndexOf('.');
        
        return dotIndex >= 0 && dotIndex < baseUrl.length() - 1 
            ? baseUrl.substring(dotIndex + 1) 
            : "";
    }

    private void setupPlaybackControls() {
        binding.centerPlayButton.setOnClickListener(v -> {
            togglePlayPause();
            resetControlsTimer();
        });

        binding.previousButton.setOnClickListener(v -> {
            seekBackward();
            resetControlsTimer();
        });

        binding.nextButton.setOnClickListener(v -> {
            seekForward();
            resetControlsTimer();
        });

        binding.fullscreenButton.setOnClickListener(v -> toggleFullscreen());
        binding.videoControlsOverlay.setOnClickListener(v -> toggleControlsVisibility());
    }

    private void setupActionButtons() {
        binding.expandTitleButton.setOnClickListener(v -> toggleTitleExpansion());
        binding.likeButton.setOnClickListener(v -> handleLikeAction());
        binding.dislikeButton.setOnClickListener(v -> handleDislikeAction());
        binding.shareButton.setOnClickListener(v -> shareVideo());
        binding.downloadButton.setOnClickListener(v -> initiateDownload());
    }

    private void setupCommentInput() {
        binding.commentEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void setupSeekBar() {
        binding.seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override 
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && player != null && player.getDuration() > 0) {
                    long targetPosition = (player.getDuration() * progress) / 100;
                    player.seekTo(targetPosition);
                }
            }
            
            @Override public void onStartTrackingTouch(SeekBar seekBar) { stopControlsHideTimer(); }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { resetControlsTimer(); }
        });
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        handleOrientationChange();
    }

    private void handleOrientationChange() {
        boolean isLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        if (isLandscape) {
            enterLandscapeMode();
        } else {
            enterPortraitMode();
        }
    }

    private void enterLandscapeMode() {
        isFullscreen = true;
        enableImmersiveMode();
        hideNonEssentialViews();
        adjustVideoPlayerForLandscape();
        startControlsHideTimer();
    }

    private void enterPortraitMode() {
        isFullscreen = false;
        disableImmersiveMode();
        showAllViews();
        adjustVideoPlayerForPortrait();
        stopControlsHideTimer();
        showControls();
    }

    private void enableImmersiveMode() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(android.view.WindowInsets.Type.statusBars() | 
                              android.view.WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
        }
    }

    private void disableImmersiveMode() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.show(android.view.WindowInsets.Type.statusBars() | 
                              android.view.WindowInsets.Type.navigationBars());
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        }
    }

    private void hideNonEssentialViews() {
        ScrollView scrollView = (ScrollView) binding.getRoot();
        LinearLayout mainContainer = (LinearLayout) scrollView.getChildAt(0);
        
        for (int i = 1; i < mainContainer.getChildCount(); i++) {
            View child = mainContainer.getChildAt(i);
            child.setVisibility(View.GONE);
        }
    }

    private void showAllViews() {
        ScrollView scrollView = (ScrollView) binding.getRoot();
        LinearLayout mainContainer = (LinearLayout) scrollView.getChildAt(0);
        
        for (int i = 1; i < mainContainer.getChildCount(); i++) {
            View child = mainContainer.getChildAt(i);
            child.setVisibility(View.VISIBLE);
        }
    }

    private void adjustVideoPlayerForLandscape() {
        ViewGroup.LayoutParams params = binding.videoPlayerContainer.getLayoutParams();
        params.height = ViewGroup.LayoutParams.MATCH_PARENT;
        binding.videoPlayerContainer.setLayoutParams(params);
    }

    private void adjustVideoPlayerForPortrait() {
        ViewGroup.LayoutParams params = binding.videoPlayerContainer.getLayoutParams();
        params.height = (int) (250 * getResources().getDisplayMetrics().density);
        binding.videoPlayerContainer.setLayoutParams(params);
    }

    private void toggleFullscreen() {
        if (isFullscreen) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
    }

    private void toggleControlsVisibility() {
        if (isFullscreen) {
            if (controlsVisible) {
                hideControls();
            } else {
                showControls();
                startControlsHideTimer();
            }
        }
    }

    private void showControls() {
        binding.videoControlsOverlay.setVisibility(View.VISIBLE);
        controlsVisible = true;
    }

    private void hideControls() {
        binding.videoControlsOverlay.setVisibility(View.GONE);
        controlsVisible = false;
    }

    private void startControlsHideTimer() {
        if (!isFullscreen) return;
        
        stopControlsHideTimer();
        hideControlsRunnable = () -> {
            if (isFullscreen && controlsVisible && isPlaying) {
                hideControls();
            }
        };
        handler.postDelayed(hideControlsRunnable, CONTROLS_HIDE_DELAY);
    }

    private void stopControlsHideTimer() {
        if (hideControlsRunnable != null) {
            handler.removeCallbacks(hideControlsRunnable);
            hideControlsRunnable = null;
        }
    }

    private void resetControlsTimer() {
        if (isFullscreen) {
            showControls();
            startControlsHideTimer();
        }
    }

    private void loadVideoData() {
        String title = getIntent().getStringExtra("video_title");
        String views = getIntent().getStringExtra("video_views");
        String time = getIntent().getStringExtra("upload_time");
        String channel = getIntent().getStringExtra("channel_name");
        String subscribers = getIntent().getStringExtra("subscriber_count");
        int likes = getIntent().getIntExtra("like_count", 0);
        int comments = getIntent().getIntExtra("comment_count", 0);

        binding.videoTitle.setText(title != null ? title : "Video Title");
        binding.viewCount.setText(views != null ? views : "0 views");
        binding.uploadTime.setText(time != null ? time : "Just now");
        binding.channelName.setText(channel != null ? channel : "Channel Name");
        binding.subscriberCount.setText(subscribers != null ? subscribers : "0 subscribers");
        binding.likeCount.setText(String.valueOf(likes));
        binding.commentCount.setText(" " + comments);
    }

    private void togglePlayPause() {
        if (player == null) return;
        
        if (player.isPlaying()) {
            player.pause();
        } else {
            player.play();
        }
    }

    private void seekBackward() {
        if (player == null) return;
        
        long currentPosition = player.getCurrentPosition();
        long targetPosition = Math.max(0, currentPosition - SEEK_INCREMENT);
        player.seekTo(targetPosition);
    }

    private void seekForward() {
        if (player == null) return;
        
        long duration = player.getDuration();
        long currentPosition = player.getCurrentPosition();
        long targetPosition = Math.min(duration, currentPosition + SEEK_INCREMENT);
        player.seekTo(targetPosition);
    }

    private void toggleTitleExpansion() {
        boolean isExpanded = binding.videoTitle.getMaxLines() == Integer.MAX_VALUE;
        binding.videoTitle.setMaxLines(isExpanded ? 2 : Integer.MAX_VALUE);
        binding.expandTitleButton.setRotation(isExpanded ? 0 : 180);
    }

    private void handleLikeAction() {
        if (isLiked) {
            isLiked = false;
            binding.likeButton.setAlpha(1.0f);
            updateLikeCount(-1);
        } else {
            isLiked = true;
            if (isDisliked) {
                isDisliked = false;
                binding.dislikeButton.setAlpha(1.0f);
            }
            binding.likeButton.setAlpha(0.6f);
            updateLikeCount(1);
        }
    }

    private void handleDislikeAction() {
        if (isDisliked) {
            isDisliked = false;
            binding.dislikeButton.setAlpha(1.0f);
        } else {
            isDisliked = true;
            if (isLiked) {
                isLiked = false;
                binding.likeButton.setAlpha(1.0f);
                updateLikeCount(-1);
            }
            binding.dislikeButton.setAlpha(0.6f);
        }
    }

    private void updateLikeCount(int change) {
        try {
            String currentText = binding.likeCount.getText().toString();
            int currentLikes = Integer.parseInt(currentText);
            int newLikes = Math.max(0, currentLikes + change);
            binding.likeCount.setText(String.valueOf(newLikes));
        } catch (NumberFormatException e) {
            Log.w(TAG, "Failed to parse like count", e);
            binding.likeCount.setText("0");
        }
    }

    private void shareVideo() {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        String shareText = String.format("Check out this video: %s\n%s", 
                binding.videoTitle.getText().toString(),
                videoUrl != null ? videoUrl : "");
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, binding.videoTitle.getText().toString());
        
        try {
            startActivity(Intent.createChooser(shareIntent, "Share video"));
        } catch (Exception e) {
            Log.e(TAG, "Failed to share video", e);
            Toast.makeText(this, "Failed to share video", Toast.LENGTH_SHORT).show();
        }
    }

    private void initiateDownload() {
        Toast.makeText(this, "Download feature will be available soon", Toast.LENGTH_SHORT).show();
    }

    private void startProgressUpdates() {
        stopProgressUpdates();
        updateProgressRunnable = new Runnable() {
            @Override
            public void run() {
                if (player != null && isPlaying) {
                    updatePlaybackProgress();
                    handler.postDelayed(this, PROGRESS_UPDATE_INTERVAL);
                }
            }
        };
        handler.post(updateProgressRunnable);
    }

    private void stopProgressUpdates() {
        if (updateProgressRunnable != null) {
            handler.removeCallbacks(updateProgressRunnable);
            updateProgressRunnable = null;
        }
    }

    private void updatePlaybackProgress() {
        if (player == null || player.getDuration() <= 0) return;
        
        long currentPosition = player.getCurrentPosition();
        long duration = player.getDuration();
        int progress = (int) ((currentPosition * 100) / duration);
        
        binding.seekBar.setProgress(progress);

        StringBuilder timeBuilder = new StringBuilder();
        Formatter formatter = new Formatter(timeBuilder, Locale.getDefault());
        
        String currentTime = Util.getStringForTime(timeBuilder, formatter, currentPosition);
        timeBuilder.setLength(0);
        String totalTime = Util.getStringForTime(timeBuilder, formatter, duration);
        
        String timeDisplay = String.format("%s / %s", currentTime, totalTime);
        binding.timeDisplay.setText(timeDisplay);
        
        formatter.close();
    }

    private void updatePlayButtonState() {
        int iconResource = isPlaying 
                ? android.R.drawable.ic_media_pause 
                : android.R.drawable.ic_media_play;
        binding.centerPlayButton.setImageResource(iconResource);
    }

    @Override
    public void onPlaybackStateChanged(int playbackState) {
        Player.Listener.super.onPlaybackStateChanged(playbackState);
        
        if (playbackState == Player.STATE_READY && player != null && player.isPlaying()) {
            startProgressUpdates();
        } else {
            stopProgressUpdates();
        }
        
        if (playbackState == Player.STATE_ENDED) {
            binding.centerPlayButton.setImageResource(android.R.drawable.ic_media_play);
        }
    }

    @Override
    public void onIsPlayingChanged(boolean isPlaying) {
        Player.Listener.super.onIsPlayingChanged(isPlaying);
        
        this.isPlaying = isPlaying;
        updatePlayButtonState();
        
        if (isPlaying) {
            startProgressUpdates();
        } else {
            stopProgressUpdates();
        }
    }

    @Override
    public void onPlayerError(PlaybackException error) {
        Player.Listener.super.onPlayerError(error);
        
        Log.e(TAG, "Player error occurred", error);
        Toast.makeText(this, "Video playback error: " + error.getMessage(), Toast.LENGTH_LONG).show();
        
        binding.centerPlayButton.setImageResource(android.R.drawable.ic_media_play);
        stopProgressUpdates();
    }

    private void releasePlayer() {
        if (player != null) {
            player.removeListener(this);
            player.release();
            player = null;
        }
    }

    private void cleanupResources() {
        stopProgressUpdates();
        stopControlsHideTimer();
        
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
        
        if (backgroundExecutor != null && !backgroundExecutor.isShutdown()) {
            backgroundExecutor.shutdown();
        }
    }
}