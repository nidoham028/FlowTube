package com.nidoham.flowtube;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
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
import android.view.animation.AccelerateDecelerateInterpolator;
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
import com.nidoham.flowtube.stream.extractor.InformationExtractor;
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
    private static final int ANIMATION_DURATION_MS = 300;

    private ActivityPlayerBinding binding;
    private PlayerViewModel playerViewModel;
    private StreamExtractor streamExtractor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable hideControlsRunnable;
    private Runnable progressUpdateRunnable;

    private String videoUrl = "";
    private String videoTitle = "";
    private String channelName = "";
    private long playbackPosition = 0L;
    private boolean isFullscreen = false;
    private boolean areControlsVisible = true;
    private boolean isUserSeeking = false;

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
        
        // Initialize controls visibility
        binding.controlsOverlay.setAlpha(1.0f);
        binding.controlsOverlay.setVisibility(View.VISIBLE);
        showControlsTemporarily();
    }

    private void updateVideoInfo() {
        binding.txtTitle.setText(videoTitle);
        binding.txtChannelName.setText(channelName);
    }

    private void updateStreamInfo(StreamInfo streamInfo) {
        InformationExtractor info = new InformationExtractor(streamInfo);
        
        binding.txtTitle.setText(streamInfo.getName());
        binding.txtChannelName.setText(streamInfo.getUploaderName());
        
        binding.txtMeta.setText(info.getTextMeta());
        binding.txtLikeCount.setText(info.getFormattedLikeCount());
        binding.txtSubscriberCount.setText(info.getFormattedSubscriptionCount());
    }

    private void setupControls() {
        ExoPlayer player = playerViewModel.getExoPlayer();

        binding.btnPlayPause.setOnClickListener(v -> {
            if (player != null) {
                animateButtonPress(v);
                playerViewModel.togglePlayPause();
                showControlsTemporarily();
            }
        });

        binding.btnReplay10.setOnClickListener(v -> {
            animateButtonPress(v);
            seekRelative(-SEEK_INCREMENT_MS);
            showControlsTemporarily();
        });
        
        binding.btnForward10.setOnClickListener(v -> {
            animateButtonPress(v);
            seekRelative(SEEK_INCREMENT_MS);
            showControlsTemporarily();
        });
        
        binding.btnOrientation.setOnClickListener(v -> {
            animateButtonPress(v);
            toggleOrientation();
        });
        
        binding.btnBack.setOnClickListener(v -> {
            animateButtonPress(v);
            finish();
        });

        // Setup other control buttons with animations
        setupControlButton(binding.btnCast);
        setupControlButton(binding.btnCC);
        setupControlButton(binding.btnSettings);
        setupControlButton(binding.btnLike);
        setupControlButton(binding.btnDislike);
        setupControlButton(binding.btnComments);
        setupControlButton(binding.btnSave);
        setupControlButton(binding.btnShare);
        setupControlButton(binding.btnMore);
    }

    private void setupControlButton(View button) {
        if (button != null) {
            button.setOnClickListener(v -> {
                animateButtonPress(v);
                showControlsTemporarily();
            });
        }
    }

    private void animateButtonPress(View button) {
        ObjectAnimator scaleDown = ObjectAnimator.ofFloat(button, "scaleX", 1.0f, 0.9f);
        scaleDown.setDuration(100);
        scaleDown.setInterpolator(new AccelerateDecelerateInterpolator());

        ObjectAnimator scaleUp = ObjectAnimator.ofFloat(button, "scaleX", 0.9f, 1.0f);
        scaleUp.setDuration(100);
        scaleUp.setInterpolator(new AccelerateDecelerateInterpolator());

        ObjectAnimator scaleDownY = ObjectAnimator.ofFloat(button, "scaleY", 1.0f, 0.9f);
        scaleDownY.setDuration(100);
        scaleDownY.setInterpolator(new AccelerateDecelerateInterpolator());

        ObjectAnimator scaleUpY = ObjectAnimator.ofFloat(button, "scaleY", 0.9f, 1.0f);
        scaleUpY.setDuration(100);
        scaleUpY.setInterpolator(new AccelerateDecelerateInterpolator());

        scaleDown.start();
        scaleDownY.start();

        scaleDown.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                scaleUp.start();
                scaleUpY.start();
            }
        });
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
                isUserSeeking = true;
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
                isUserSeeking = false;
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
        if (areControlsVisible) {
            hideControlsWithAnimation();
        } else {
            showControlsWithAnimation();
        }
    }

    private void showControlsWithAnimation() {
        if (areControlsVisible) return;

        areControlsVisible = true;
        binding.controlsOverlay.setVisibility(View.VISIBLE);
        
        ObjectAnimator fadeIn = ObjectAnimator.ofFloat(binding.controlsOverlay, "alpha", 0.0f, 1.0f);
        fadeIn.setDuration(ANIMATION_DURATION_MS);
        fadeIn.setInterpolator(new AccelerateDecelerateInterpolator());
        fadeIn.start();
        
        startHideTimer();
    }

    private void hideControlsWithAnimation() {
        if (!areControlsVisible) return;

        areControlsVisible = false;
        
        ObjectAnimator fadeOut = ObjectAnimator.ofFloat(binding.controlsOverlay, "alpha", 1.0f, 0.0f);
        fadeOut.setDuration(ANIMATION_DURATION_MS);
        fadeOut.setInterpolator(new AccelerateDecelerateInterpolator());
        
        fadeOut.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                binding.controlsOverlay.setVisibility(View.INVISIBLE);
            }
        });
        
        fadeOut.start();
        cancelHideTimer();
    }

    private void showControlsTemporarily() {
        showControlsWithAnimation();
    }

    private void startHideTimer() {
        cancelHideTimer();
        hideControlsRunnable = this::hideControlsWithAnimation;
        mainHandler.postDelayed(hideControlsRunnable, CONTROLS_HIDE_DELAY_MS);
    }

    private void cancelHideTimer() {
        if (hideControlsRunnable != null) {
            mainHandler.removeCallbacks(hideControlsRunnable);
        }
    }

    private void startProgressUpdates() {
        progressUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isUserSeeking) {
                    updateProgress();
                }
                mainHandler.postDelayed(this, 500);
            }
        };
        mainHandler.post(progressUpdateRunnable);
    }

    private void stopProgressUpdates() {
        if (progressUpdateRunnable != null) {
            mainHandler.removeCallbacks(progressUpdateRunnable);
        }
    }

    private void updateProgress() {
        ExoPlayer player = playerViewModel.getExoPlayer();
        if (player == null || player.getPlaybackState() == Player.STATE_IDLE) return;

        long position = player.getCurrentPosition();
        long duration = player.getDuration();
        long bufferedPosition = player.getBufferedPosition();

        if (duration > 0 && !isUserSeeking) {
            int progress = (int) ((position * 1000) / duration);
            int bufferedProgress = (int) ((bufferedPosition * 1000) / duration);
            
            binding.videoProgress.setProgress(progress);
            binding.videoProgress.setSecondaryProgress(bufferedProgress);
        }

        binding.txtCurrentTime.setText(formatTime(position));
        binding.txtTotalTime.setText(formatTime(duration));
    }

    private void updatePlayPauseButton(boolean isPlaying) {
        int iconRes = isPlaying ? R.drawable.ic_pause : R.drawable.ic_play_arrow;
        binding.btnPlayPause.setImageResource(iconRes);
        
        // Add rotation animation for visual feedback
        ObjectAnimator rotateAnimation = ObjectAnimator.ofFloat(binding.btnPlayPause, "rotation", 0f, 360f);
        rotateAnimation.setDuration(200);
        rotateAnimation.setInterpolator(new AccelerateDecelerateInterpolator());
        rotateAnimation.start();
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
            if (show) {
                binding.loadingIndicator.setVisibility(View.VISIBLE);
                ObjectAnimator fadeIn = ObjectAnimator.ofFloat(binding.loadingIndicator, "alpha", 0.0f, 1.0f);
                fadeIn.setDuration(200);
                fadeIn.start();
            } else {
                ObjectAnimator fadeOut = ObjectAnimator.ofFloat(binding.loadingIndicator, "alpha", 1.0f, 0.0f);
                fadeOut.setDuration(200);
                fadeOut.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        binding.loadingIndicator.setVisibility(View.GONE);
                    }
                });
                fadeOut.start();
            }
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
        cancelHideTimer();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (areControlsVisible) {
            startHideTimer();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (playerViewModel != null) {
            playerViewModel.pause();
        }
        stopProgressUpdates();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelHideTimer();
        stopProgressUpdates();
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