package com.nidoham.flowtube;

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
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

import com.nidoham.flowtube.databinding.ActivityPlayerBinding;
import com.nidoham.flowtube.stream.extractor.StreamExtractor;
import com.nidoham.opentube.player.PlayerViewModel;
import org.schabi.newpipe.extractor.stream.StreamInfo;

public class PlayerActivity extends AppCompatActivity {

    private static final String TAG = "PlayerActivity";

    public static final String EXTRA_VIDEO_URL = "video_url";
    public static final String EXTRA_VIDEO_TITLE = "video_title";
    public static final String EXTRA_CHANNEL_NAME = "channel_name";
    public static final String EXTRA_PLAYBACK_POSITION = "playback_position";

    private static final int CONTROLS_HIDE_DELAY_MS = 3000;
    private static final int SEEK_INCREMENT_MS = 10000;
    private static final int INITIAL_BUFFERING_DELAY_MS = 1000;

    private ActivityPlayerBinding binding;
    private PlayerViewModel playerViewModel;
    private StreamExtractor streamExtractor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable hideControlsRunnable;

    private String videoUrl = "";
    private String videoTitle = "";
    private String channelName = "";
    private long playbackPosition = 0L;
    private boolean isFullscreen = false;

    // Flag to ensure initial buffering delay only once
    private boolean initialBufferingHandled = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityPlayerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupSystemUI();
        initializeComponents();
        extractIntentData(savedInstanceState);
        setupPlayer();
        loadContent();
        setupUI();
    }

    private void initializeComponents() {
        playerViewModel = new ViewModelProvider(this).get(PlayerViewModel.class);
        streamExtractor = new StreamExtractor(this);
    }

    private void extractIntentData(@Nullable Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            videoUrl = savedInstanceState.getString(EXTRA_VIDEO_URL, "");
            videoTitle = savedInstanceState.getString(EXTRA_VIDEO_TITLE, "");
            channelName = savedInstanceState.getString(EXTRA_CHANNEL_NAME, "");
            playbackPosition = savedInstanceState.getLong(EXTRA_PLAYBACK_POSITION, 0L);
        } else {
            Intent intent = getIntent();
            if (intent != null) {
                videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL);
                videoTitle = intent.getStringExtra(EXTRA_VIDEO_TITLE);
                channelName = intent.getStringExtra(EXTRA_CHANNEL_NAME);
            }
        }

        videoUrl = videoUrl != null ? videoUrl : "";
        videoTitle = videoTitle != null ? videoTitle : "Untitled";
        channelName = channelName != null ? channelName : "";
    }

    private void setupPlayer() {
        ExoPlayer player = playerViewModel.getExoPlayer();
        binding.playerView.setPlayer(player);

        player.addListener(new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                updatePlayPauseButton(isPlaying);
            }

            @Override
            public void onPlaybackStateChanged(int state) {
                updateProgress();

                if (state == Player.STATE_BUFFERING) {
                    if (!initialBufferingHandled) {
                        // Initial buffering - pause playback and delay start
                        initialBufferingHandled = true;
                        playerViewModel.pause();
                        mainHandler.postDelayed(() -> {
                            if (player.getPlaybackState() == Player.STATE_BUFFERING) {
                                playerViewModel.play();
                            }
                        }, INITIAL_BUFFERING_DELAY_MS);
                    } else {
                        showLoading(true);
                    }
                } else if (state == Player.STATE_READY) {
                    showLoading(false);
                } else {
                    // For other states like ENDED or IDLE
                    showLoading(false);
                }
            }
        });
    }

    private void loadContent() {
        if (videoUrl.isEmpty()) {
            showError("Invalid video URL");
            return;
        }

        showLoading(true);

        if (isYouTubeUrl(videoUrl)) {
            extractYouTubeStreams();
        } else {
            loadDirectVideo();
        }
    }

    private void extractYouTubeStreams() {
        streamExtractor.extractAll(videoUrl, new StreamExtractor.OnStreamExtractionListener() {
            private String extractedVideoUrl = "";
            private String extractedAudioUrl = "";

            @Override
            public void onVideoReady(@NonNull String url) {
                runOnUiThread(() -> {
                    extractedVideoUrl = url;
                    if (!extractedAudioUrl.isEmpty()) {
                        loadMergedStreams(extractedVideoUrl, extractedAudioUrl);
                    }
                });
            }

            @Override
            public void onAudioReady(@NonNull String url) {
                runOnUiThread(() -> {
                    extractedAudioUrl = url;
                    if (!extractedVideoUrl.isEmpty()) {
                        loadMergedStreams(extractedVideoUrl, extractedAudioUrl);
                    }
                });
            }

            @Override
            public void onInformationReady(@NonNull StreamInfo streamInfo) {
                runOnUiThread(() -> updateStreamInfo(streamInfo));
            }

            @Override
            public void onExtractionError(@NonNull Exception error, @NonNull String operationType) {
                runOnUiThread(() -> showError("Failed to load video: " + error.getMessage()));
            }
        });
    }

    private void loadMergedStreams(String videoUrl, String audioUrl) {
        try {
            playerViewModel.playStream(audioUrl, videoUrl);
            seekToPosition();
            showLoading(false);
            showControlsTemporarily();
            initialBufferingHandled = false; // reset flag on new video load
        } catch (Exception e) {
            showError("Failed to load streams: " + e.getMessage());
        }
    }

    private void loadDirectVideo() {
        try {
            playerViewModel.playMedia(videoUrl);
            seekToPosition();
            showLoading(false);
            showControlsTemporarily();
            initialBufferingHandled = false; // reset flag on new video load
        } catch (Exception e) {
            showError("Failed to load video: " + e.getMessage());
        }
    }

    private void seekToPosition() {
        if (playbackPosition > 0) {
            ExoPlayer player = playerViewModel.getExoPlayer();
            if (player != null) {
                player.seekTo(playbackPosition);
            }
        }
    }

    private void setupUI() {
        updateVideoInfo();
        setupControls();
        setupSeekBar();
        setupTouchHandling();
        startProgressUpdates();
    }

    private void updateVideoInfo() {
        binding.txtTitle.setText(videoTitle);
        binding.txtChannelName.setText(channelName);
    }

    private void updateStreamInfo(StreamInfo streamInfo) {
        binding.txtTitle.setText(streamInfo.getName());
        binding.txtChannelName.setText(streamInfo.getUploaderName());
    }

    private void setupControls() {
        ExoPlayer player = playerViewModel.getExoPlayer();

        binding.btnPlayPause.setOnClickListener(v -> {
            if (player != null) {
                playerViewModel.togglePlayPause();
                showControlsTemporarily();
            }
        });

        binding.btnReplay10.setOnClickListener(v -> {
            seekRelative(-SEEK_INCREMENT_MS);
            showControlsTemporarily();
        });
        binding.btnForward10.setOnClickListener(v -> {
            seekRelative(SEEK_INCREMENT_MS);
            showControlsTemporarily();
        });
        binding.btnOrientation.setOnClickListener(v -> toggleOrientation());
        binding.btnBack.setOnClickListener(v -> finish());
    }

    private void seekRelative(long deltaMs) {
        ExoPlayer player = playerViewModel.getExoPlayer();
        if (player != null) {
            long currentPos = player.getCurrentPosition();
            long duration = player.getDuration();
            long newPos = Math.max(0, Math.min(currentPos + deltaMs, duration));
            player.seekTo(newPos);
        }
    }

    private void toggleOrientation() {
        int orientation = getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            isFullscreen = false;
            showSystemUI();
            showControlsTemporarily();
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            isFullscreen = true;
            hideSystemUI();
            showControlsTemporarily();
        }
    }

    private void setupSeekBar() {
        binding.videoProgress.setMax(1000);
        binding.videoProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            private boolean wasPlaying = false;

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                ExoPlayer player = playerViewModel.getExoPlayer();
                wasPlaying = player != null && player.isPlaying();
                if (wasPlaying) playerViewModel.pause();
                showControlsTemporarily();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                ExoPlayer player = playerViewModel.getExoPlayer();
                if (player != null) {
                    long duration = player.getDuration();
                    if (duration > 0) {
                        long seekTo = (seekBar.getProgress() * duration) / 1000;
                        player.seekTo(seekTo);
                    }
                }
                if (wasPlaying) playerViewModel.play();
                showControlsTemporarily();
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    ExoPlayer player = playerViewModel.getExoPlayer();
                    if (player != null) {
                        long duration = player.getDuration();
                        if (duration > 0) {
                            long newPosition = (duration * progress) / 1000L;
                            binding.txtCurrentTime.setText(formatTime(newPosition));
                        }
                    }
                    showControlsTemporarily();
                }
            }
        });
    }

    private void setupTouchHandling() {
        binding.playerView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                toggleControlsVisibility();
                return true;
            }
            return false;
        });
    }

    private void toggleControlsVisibility() {
        boolean isVisible = binding.controlsOverlay.getVisibility() == View.VISIBLE;
        if (isVisible) {
            binding.controlsOverlay.setVisibility(View.INVISIBLE);
            cancelHideTimer();
        } else {
            binding.controlsOverlay.setVisibility(View.VISIBLE);
            startHideTimer();
        }
    }

    private void showControlsTemporarily() {
        binding.controlsOverlay.setVisibility(View.VISIBLE);
        startHideTimer();
    }

    private void startHideTimer() {
        cancelHideTimer();
        hideControlsRunnable = () -> binding.controlsOverlay.setVisibility(View.INVISIBLE);
        mainHandler.postDelayed(hideControlsRunnable, CONTROLS_HIDE_DELAY_MS);
    }

    private void cancelHideTimer() {
        if (hideControlsRunnable != null) {
            mainHandler.removeCallbacks(hideControlsRunnable);
        }
    }

    private void startProgressUpdates() {
        Runnable updateRunnable = new Runnable() {
            @Override
            public void run() {
                updateProgress();
                mainHandler.postDelayed(this, 500);
            }
        };
        mainHandler.post(updateRunnable);
    }

    private void updateProgress() {
        ExoPlayer player = playerViewModel.getExoPlayer();
        if (player == null || player.getPlaybackState() == Player.STATE_IDLE) return;

        long position = player.getCurrentPosition();
        long duration = player.getDuration();

        if (duration > 0) {
            int progress = (int) ((position * 1000) / duration);
            binding.videoProgress.setProgress(progress);
        } else {
            binding.videoProgress.setProgress(0);
        }

        binding.txtCurrentTime.setText(formatTime(position));
        binding.txtTotalTime.setText(formatTime(duration));
    }

    private void updatePlayPauseButton(boolean isPlaying) {
        int iconRes = isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play;
        binding.btnPlayPause.setImageResource(iconRes);
    }

    private String formatTime(long millis) {
        if (millis < 0) millis = 0;
        long totalSeconds = millis / 1000;
        long seconds = totalSeconds % 60;
        long minutes = (totalSeconds / 60) % 60;
        long hours = totalSeconds / 3600;

        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d", minutes, seconds);
        }
    }

    private void hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    private void showSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        }
    }

    private void setupSystemUI() {
        getWindow().setStatusBarColor(ContextCompat.getColor(this, android.R.color.black));
        getWindow().setNavigationBarColor(ContextCompat.getColor(this, android.R.color.black));
    }

    private void showLoading(boolean show) {
        if (binding.loadingIndicator != null) {
            binding.loadingIndicator.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private void showError(String message) {
        Log.e(TAG, message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        showLoading(false);
    }

    private boolean isYouTubeUrl(String url) {
        if (url == null || url.trim().isEmpty()) return false;
        String normalizedUrl = url.trim().toLowerCase();
        return normalizedUrl.contains("youtube.com/watch") ||
            normalizedUrl.contains("youtu.be/") ||
            normalizedUrl.contains("youtube.com/embed/");
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (playerViewModel != null) {
            playerViewModel.pause();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (playerViewModel != null) {
            playerViewModel.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelHideTimer();
        if (streamExtractor != null) {
            streamExtractor.cleanup();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        ExoPlayer player = playerViewModel != null ? playerViewModel.getExoPlayer() : null;
        if (player != null) {
            outState.putLong(EXTRA_PLAYBACK_POSITION, player.getCurrentPosition());
        }
        outState.putString(EXTRA_VIDEO_URL, videoUrl);
        outState.putString(EXTRA_VIDEO_TITLE, videoTitle);
        outState.putString(EXTRA_CHANNEL_NAME, channelName);
    }

    @Override
    public void onBackPressed() {
        if (isFullscreen) {
            toggleOrientation();
        } else {
            super.onBackPressed();
        }
    }
}
