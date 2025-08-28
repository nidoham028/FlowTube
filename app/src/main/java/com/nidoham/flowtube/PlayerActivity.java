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

public class PlayerActivity extends AppCompatActivity {

    private ActivityPlayerBinding binding;
    private PlayerViewModel playerViewModel;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isDestroyed = false;
    private boolean isFullscreen = false;
    private boolean isLandscape = false;
    private boolean isAndroidTv = false;

    private Runnable controlsHideRunnable;
    private static final int CONTROLS_HIDE_DELAY_MS = 2000;

    public static final String EXTRA_VIDEO_URL = "video_url";
    public static final String EXTRA_VIDEO_TITLE = "video_title";
    public static final String EXTRA_CHANNEL_NAME = "channel_name";
    public static final String EXTRA_PLAYBACK_POSITION = "playback_position";
    public static final String EXTRA_DIRECT_VIDEO_URL = "direct_video_url";

    private String videoUrl = "";
    private String videoTitle = "";
    private String channelName = "";
    private long playbackPosition = 0;
    private String directVideoUrl = null;
    private boolean isFirstCreate = true;

    private static final String TAG = "PlayerActivity";

    // Stream extraction features
    private StreamExtractor streamExtractor;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            binding = ActivityPlayerBinding.inflate(getLayoutInflater());
            setContentView(binding.getRoot());
        } catch (Exception e) {
            Log.e(TAG, "Failed to inflate layout", e);
            Toast.makeText(this, "Failed to inflate layout: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setupSystemUI();

        isAndroidTv = isAndroidTvDevice();

        int orientation = getResources().getConfiguration().orientation;
        isLandscape = (orientation == Configuration.ORIENTATION_LANDSCAPE);

        playerViewModel = new ViewModelProvider(this).get(PlayerViewModel.class);

        // Initialize StreamExtractor
        streamExtractor = new StreamExtractor(this);

        if (savedInstanceState != null) {
            videoUrl = safeGetString(savedInstanceState, EXTRA_VIDEO_URL, "");
            videoTitle = safeGetString(savedInstanceState, EXTRA_VIDEO_TITLE, "");
            channelName = safeGetString(savedInstanceState, EXTRA_CHANNEL_NAME, "");
            playbackPosition = savedInstanceState.getLong(EXTRA_PLAYBACK_POSITION, 0);
            directVideoUrl = safeGetString(savedInstanceState, EXTRA_DIRECT_VIDEO_URL, null);
            isFirstCreate = false;
        } else {
            Intent intent = getIntent();
            if (intent != null) {
                videoUrl = safeGetString(intent, EXTRA_VIDEO_URL, "");
                videoTitle = safeGetString(intent, EXTRA_VIDEO_TITLE, "");
                channelName = safeGetString(intent, EXTRA_CHANNEL_NAME, "");
            }
            playbackPosition = 0;
            directVideoUrl = null;
            isFirstCreate = true;
        }

        playerViewModel.initializePlayer(this);
        final ExoPlayer player = playerViewModel.getExoPlayer();

        try {
            if (binding != null && binding.playerView != null) {
                binding.playerView.setPlayer(player);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to attach player", e);
            Toast.makeText(this, "Failed to attach player: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        // Feature: Use StreamExtractor for YouTube URLs
        if (isFirstCreate) {
            if (videoUrl != null && !videoUrl.isEmpty() && isYouTubeUrl(videoUrl)) {
                showLoading(true);
                streamExtractor.extractAll(videoUrl, new StreamExtractor.OnStreamExtractionListener() {
                    @Override
                    public void onVideoReady(@NonNull String extractedVideoUrl) {
                        runOnUiThread(() -> {
                            Log.d(TAG, "Extracted video URL: " + extractedVideoUrl);
                            directVideoUrl = extractedVideoUrl;
                            playerViewModel.loadMedia(extractedVideoUrl);
                            if (playbackPosition > 0 && player != null) {
                                player.seekTo(playbackPosition);
                            }
                        });
                    }

                    @Override
                    public void onAudioReady(@NonNull String extractedAudioUrl) {
                        runOnUiThread(() -> {
                            Log.d(TAG, "Extracted audio URL: " + extractedAudioUrl);
                            // You can process or display the audio URL as needed
                        });
                    }

                    @Override
                    public void onInformationReady(@NonNull StreamInfo streamInfo) {
                        runOnUiThread(() -> {
                            Log.d(TAG, "Extracted info: " + streamInfo.getName());
                            updateStreamInfoUI(streamInfo);
                        });
                    }

                    @Override
                    public void onExtractionError(@NonNull Exception error, @NonNull String operationType) {
                        runOnUiThread(() -> {
                            Log.e(TAG, "Extraction error: " + operationType, error);
                            Toast.makeText(PlayerActivity.this, "Failed to load video: " + error.getMessage(), Toast.LENGTH_LONG).show();
                            showLoading(false);
                            finish();
                        });
                    }
                });
            } else if (videoUrl != null && !videoUrl.isEmpty()) {
                showLoading(true);
                try {
                    directVideoUrl = videoUrl;
                    playerViewModel.loadMedia(videoUrl);
                    if (playbackPosition > 0 && player != null) {
                        player.seekTo(playbackPosition);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to load video", e);
                    Toast.makeText(this, "Failed to load video: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    finish();
                }
                showLoading(false);
            } else {
                Toast.makeText(this, "Invalid video URL", Toast.LENGTH_LONG).show();
                finish();
            }
        } else {
            try {
                if (directVideoUrl != null && player != null && player.getMediaItemCount() == 0) {
                    playerViewModel.loadMedia(directVideoUrl);
                }
                if (playbackPosition > 0 && player != null) {
                    player.seekTo(playbackPosition);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to restore video position", e);
                Toast.makeText(this, "Failed to restore video position: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }

        setupPlayerControls(player);
        setupSeekBarAndProgress(player);
        setupUI();
        setupControlsOverlayAutoHide();

        if (isLandscape && !isAndroidTv) {
            setAllControlsVisible(false);
        } else {
            setControlsOverlayVisible(false);
        }

        if (isFullscreen) {
            hideSystemUI();
        } else {
            showSystemUI();
        }
    }

    private void updateStreamInfoUI(StreamInfo streamInfo) {
        // Example: update title and channel name from extracted info
        if (streamInfo != null) {
            safeSetText(binding.txtTitle, streamInfo.getName());
            safeSetText(binding.txtChannelName, streamInfo.getUploaderName());
            // You can add more info updates here, e.g. description, duration, etc.
        }
    }

    private void setupPlayerControls(ExoPlayer player) {
        safeSetOnClickListener(binding.btnPlayPause, v -> {
            try {
                if (player != null && player.isPlaying()) {
                    playerViewModel.pause();
                } else {
                    playerViewModel.play();
                }
            } catch (Exception e) {
                Log.e(TAG, "Player error", e);
                Toast.makeText(this, "Player error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        safeSetOnClickListener(binding.btnReplay10, v -> {
            try {
                if (player != null) {
                    long pos = player.getCurrentPosition();
                    player.seekTo(Math.max(pos - 10000, 0));
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to replay", e);
                Toast.makeText(this, "Failed to replay: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        safeSetOnClickListener(binding.btnForward10, v -> {
            try {
                if (player != null) {
                    long pos = player.getCurrentPosition();
                    long duration = player.getDuration();
                    player.seekTo(Math.min(pos + 10000, duration));
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to forward", e);
                Toast.makeText(this, "Failed to forward: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        safeSetOnClickListener(binding.btnOrientation, v -> {
            try {
                if (isAndroidTv) {
                    isFullscreen = !isFullscreen;
                    if (isFullscreen) {
                        hideSystemUI();
                    } else {
                        showSystemUI();
                    }
                } else {
                    int orientation = getResources().getConfiguration().orientation;
                    if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                        isFullscreen = false;
                    } else {
                        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                        isFullscreen = true;
                    }
                    if (isFullscreen) {
                        hideSystemUI();
                    } else {
                        showSystemUI();
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to change orientation", e);
                Toast.makeText(this, "Failed to change orientation: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        safeSetOnClickListener(binding.btnBack, v -> {
            try {
                finish();
            } catch (Exception e) {
                Log.e(TAG, "Failed to exit", e);
                Toast.makeText(this, "Failed to exit: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupSeekBarAndProgress(ExoPlayer player) {
        final SeekBar seekBar = binding.videoProgress;
        if (seekBar != null) {
            seekBar.setMax(1000);
        }

        final Player.Listener progressListener = new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                updatePlayPauseButton(player != null && player.isPlaying());
            }

            @Override
            public void onPlaybackStateChanged(int state) {
                updateSeekBar(player, seekBar);
            }

            @Override
            public void onPositionDiscontinuity(Player.PositionInfo oldPosition, Player.PositionInfo newPosition, int reason) {
                updateSeekBar(player, seekBar);
            }
        };

        if (player != null) {
            player.addListener(progressListener);
        }

        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                updateSeekBar(player, seekBar);
                if (!isDestroyed) {
                    mainHandler.postDelayed(this, 500);
                }
            }
        }, 500);

        if (seekBar != null) {
            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                boolean wasPlaying = false;

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    wasPlaying = player != null && player.isPlaying();
                    playerViewModel.pause();
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    int progress = seekBar.getProgress();
                    long duration = player != null ? player.getDuration() : 0;
                    if (duration > 0 && player != null) {
                        long seekTo = (progress * duration) / 1000;
                        player.seekTo(seekTo);
                    }
                    if (wasPlaying) {
                        playerViewModel.play();
                    }
                }

                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { }
            });
        }
    }

    private void setupUI() {
        safeSetText(binding.txtTitle, videoTitle != null ? videoTitle : "Untitled");
        safeSetText(binding.txtChannelName, channelName != null ? channelName : "");
    }

    private void setupControlsOverlayAutoHide() {
        View touchSurface = binding.playerView;
        if (touchSurface == null) return;

        touchSurface.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (controlsHideRunnable != null) {
                    mainHandler.removeCallbacks(controlsHideRunnable);
                }

                if (isLandscape && !isAndroidTv) {
                    if (areAllControlsVisible()) {
                        setAllControlsVisible(false);
                    } else {
                        setAllControlsVisible(true);
                        startHideTimer();
                    }
                } else {
                    if (isControlsOverlayVisible()) {
                        setControlsOverlayVisible(false);
                    } else {
                        setControlsOverlayVisible(true);
                        startHideTimer();
                    }
                }
                return true;
            }
            return false;
        });
    }

    private void startHideTimer() {
        if (controlsHideRunnable != null) {
            mainHandler.removeCallbacks(controlsHideRunnable);
        }

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
        return binding.controlsOverlay != null && binding.controlsOverlay.getVisibility() == View.VISIBLE;
    }

    private void setAllControlsVisible(boolean visible) {
        if (binding.controlsOverlay != null) {
            binding.controlsOverlay.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
        }
    }

    private boolean areAllControlsVisible() {
        return binding.controlsOverlay != null && binding.controlsOverlay.getVisibility() == View.VISIBLE;
    }

    private void setupSystemUI() {
        try {
            getWindow().setStatusBarColor(ContextCompat.getColor(this, android.R.color.black));
            getWindow().setNavigationBarColor(ContextCompat.getColor(this, android.R.color.black));
        } catch (Exception e) {
            Log.e(TAG, "Error setting up system UI", e);
        }
    }

    private void showLoading(boolean show) {
        if (binding != null && binding.loadingIndicator != null) {
            binding.loadingIndicator.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private void updatePlayPauseButton(boolean isPlaying) {
        if (binding != null && binding.btnPlayPause != null) {
            int iconRes = isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play;
            binding.btnPlayPause.setImageResource(iconRes);
        }
    }

    private void updateSeekBar(Player player, SeekBar seekBar) {
        long duration = player != null ? player.getDuration() : 0;
        long position = player != null ? player.getCurrentPosition() : 0;
        if (seekBar != null) {
            if (duration > 0) {
                int progress = (int) ((position * 1000) / duration);
                seekBar.setProgress(progress);
            } else {
                seekBar.setProgress(0);
            }
        }
        safeSetText(binding != null ? binding.txtCurrentTime : null, formatTime(position));
        safeSetText(binding != null ? binding.txtTotalTime : null, formatTime(duration));
    }

    private String formatTime(long millis) {
        int totalSeconds = (int) (millis / 1000);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private void hideSystemUI() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowInsetsController controller = getWindow().getInsetsController();
                if (controller != null) {
                    controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                    controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                }
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
        } catch (Exception e) {
            Log.e(TAG, "Error hiding system UI", e);
        }
    }

    private void showSystemUI() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowInsetsController controller = getWindow().getInsetsController();
                if (controller != null) {
                    controller.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                }
            } else {
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error showing system UI", e);
        }
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
    }

    @Override
    protected void onDestroy() {
        isDestroyed = true;
        if (controlsHideRunnable != null) {
            mainHandler.removeCallbacks(controlsHideRunnable);
            controlsHideRunnable = null;
        }
        if (streamExtractor != null) {
            streamExtractor.cleanup();
        }
        binding = null;
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        ExoPlayer player = playerViewModel != null ? playerViewModel.getExoPlayer() : null;
        if (player != null) {
            outState.putLong(EXTRA_PLAYBACK_POSITION, player.getCurrentPosition());
        }
        outState.putString(EXTRA_VIDEO_URL, videoUrl);
        outState.putString(EXTRA_VIDEO_TITLE, videoTitle);
        outState.putString(EXTRA_CHANNEL_NAME, channelName);
        outState.putString(EXTRA_DIRECT_VIDEO_URL, directVideoUrl);
    }

    private void safeSetText(TextView tv, String text) {
        if (tv != null) {
            tv.setText(text);
        }
    }

    private void safeSetOnClickListener(View view, View.OnClickListener listener) {
        if (view != null) {
            view.setOnClickListener(listener);
        }
    }

    private String safeGetString(Bundle bundle, String key, String defaultValue) {
        String result = bundle.getString(key);
        return result != null ? result : defaultValue;
    }

    private String safeGetString(Intent intent, String key, String defaultValue) {
        String result = intent.getStringExtra(key);
        return result != null ? result : defaultValue;
    }

    private boolean isYouTubeUrl(String url) {
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

    private boolean isAndroidTvDevice() {
        UiModeManager uiModeManager = (UiModeManager) getSystemService(Context.UI_MODE_SERVICE);
        return uiModeManager != null && uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
    }
}