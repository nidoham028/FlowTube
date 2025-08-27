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
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

import com.nidoham.flowtube.databinding.ActivityPlayerBinding;
import com.nidoham.flowtube.tools.YouTubeStreamResolver;
import com.nidoham.flowtube.ui.viewmodel.PlayerViewModel;

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

    private YouTubeStreamResolver resolver;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            binding = ActivityPlayerBinding.inflate(getLayoutInflater());
            setContentView(binding.getRoot());
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to inflate layout: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setupSystemUI();

        // Detect if device is Android TV (compact mode)
        isAndroidTv = isAndroidTvDevice();

        // Determine orientation
        int orientation = getResources().getConfiguration().orientation;
        isLandscape = (orientation == Configuration.ORIENTATION_LANDSCAPE);

        playerViewModel = new ViewModelProvider(this).get(PlayerViewModel.class);
        resolver = new YouTubeStreamResolver();

        // Restore instance state or intent
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

        playerViewModel.initPlayer(this);
        final ExoPlayer player = playerViewModel.getPlayer();

        try {
            if (binding != null && binding.playerView != null) {
                binding.playerView.setPlayer(player);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to attach player: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        if (isFirstCreate) {
            if (videoUrl != null && !videoUrl.isEmpty() && isYouTubeUrl(videoUrl)) {
                showLoading(true);
                final long finalPlaybackPosition = playbackPosition;
                resolver.getWiFiOptimizedDirectLink(videoUrl, new YouTubeStreamResolver.DirectLinkCallback() {
                    @Override
                    public void onSuccess(String directUrl, com.nidoham.flowtube.tools.model.StreamInfo streamInfo) {
                        runOnUiThread(() -> {
                            try {
                                if (!isDestroyed && playerViewModel != null) {
                                    directVideoUrl = directUrl;
                                    showLoading(false);
                                    playerViewModel.loadMedia(directUrl);
                                    if (finalPlaybackPosition > 0) player.seekTo(finalPlaybackPosition);
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                                Toast.makeText(PlayerActivity.this, "Failed to start video: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                finish();
                            }
                        });
                    }

                    @Override
                    public void onError(String error, Exception exception) {
                        Log.e("WiFi", "WiFi optimized extraction failed", exception);
                        runOnUiThread(() -> {
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
                    if (playbackPosition > 0) player.seekTo(playbackPosition);
                } catch (Exception e) {
                    e.printStackTrace();
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
                if (directVideoUrl != null && player.getMediaItemCount() == 0) {
                    playerViewModel.loadMedia(directVideoUrl);
                }
                if (playbackPosition > 0) player.seekTo(playbackPosition);
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "Failed to restore video position: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }

        // Play/Pause Button
        safeSetOnClickListener(binding.btnPlayPause, v -> {
            try {
                if (player.isPlaying()) playerViewModel.pause();
                else playerViewModel.play();
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "Player error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        // Replay 10s Button
        safeSetOnClickListener(binding.btnReplay10, v -> {
            try {
                final long pos = player.getCurrentPosition();
                player.seekTo(Math.max(pos - 10000, 0));
            } catch (Exception e) {
                Toast.makeText(this, "Failed to replay: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        // Forward 10s Button
        safeSetOnClickListener(binding.btnForward10, v -> {
            try {
                final long pos = player.getCurrentPosition();
                final long duration = player.getDuration();
                player.seekTo(Math.min(pos + 10000, duration));
            } catch (Exception e) {
                Toast.makeText(this, "Failed to forward: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        // Orientation Button (different for TV and Phone)
        safeSetOnClickListener(binding.btnOrientation, v -> {
            try {
                if (isAndroidTv) {
                    // TV: Toggle immersive fullscreen, but don't rotate screen
                    isFullscreen = !isFullscreen;
                    if (isFullscreen) hideSystemUI();
                    else showSystemUI();
                } else {
                    // Phone: Use Android 15 recommended orientation APIs if available, else fallback
                    if (Build.VERSION.SDK_INT >= 34 /* Build.VERSION_CODES.UPSIDE_DOWN_CAKE */) { // Android 15
                        int newOrientation = (getDisplay() != null && getDisplay().getRotation() % 2 == 0)
                                ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                        setRequestedOrientation(newOrientation);
                        isFullscreen = (newOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                    } else {
                        int orientation1 = getResources().getConfiguration().orientation;
                        if (orientation1 == Configuration.ORIENTATION_LANDSCAPE) {
                            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                            isFullscreen = false;
                        } else {
                            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                            isFullscreen = true;
                        }
                    }
                    if (isFullscreen) hideSystemUI();
                    else showSystemUI();
                }
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "Failed to change orientation: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        // Back Button
        safeSetOnClickListener(binding.btnBack, v -> {
            try {
                finish();
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "Failed to exit: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        // SeekBar
        final SeekBar seekBar = binding.videoProgress;
        if (seekBar != null) seekBar.setMax(1000);

        // Progress Listener
        final Player.Listener progressListener = new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                try { updatePlayPauseButton(player != null && player.isPlaying()); } catch (Exception ignored) {}
            }
            @Override
            public void onPlaybackStateChanged(int state) {
                try { updateSeekBar(player, seekBar); } catch (Exception ignored) {}
            }
            @Override
            public void onPositionDiscontinuity(Player.PositionInfo oldPosition, Player.PositionInfo newPosition, int reason) {
                try { updateSeekBar(player, seekBar); } catch (Exception ignored) {}
            }
        };
        try { player.addListener(progressListener); } catch (Exception ignored) {}

        // Periodic SeekBar Update
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    updateSeekBar(player, seekBar);
                    if (!isDestroyed) mainHandler.postDelayed(this, 500);
                } catch (Exception ignored) {}
            }
        }, 500);

        // SeekBar Change Listener
        if (seekBar != null) {
            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                boolean wasPlaying = false;
                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    try { wasPlaying = player != null && player.isPlaying(); playerViewModel.pause(); } catch (Exception ignored) {}
                }
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    try {
                        final int progress = seekBar.getProgress();
                        final long duration = player != null ? player.getDuration() : 0;
                        if (duration > 0) {
                            long seekTo = (progress * duration) / 1000;
                            if (player != null) player.seekTo(seekTo);
                        }
                        if (wasPlaying) playerViewModel.play();
                    } catch (Exception ignored) {}
                }
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { }
            });
        }

        safeSetText(binding.txtTitle, videoTitle != null ? videoTitle : "Untitled");
        safeSetText(binding.txtChannelName, channelName != null ? channelName : "");

        // Touch controls to show/hide overlays as requested
        setupControlsOverlayAutoHide();

        // Initial overlays visibility
        if (isLandscape && !isAndroidTv) {
            setAllControlsVisible(false); // landscape phone: only playerView visible
        } else {
            setControlsOverlayVisible(false); // portrait phone: controlsOverlay hidden
        }

        if (isFullscreen) hideSystemUI();
        else showSystemUI();
    }

    private void setupControlsOverlayAutoHide() {
        // Touch listener for the playerView surface
        View touchSurface = binding.playerView;
        if (touchSurface == null) return;

        touchSurface.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                // Cancel any existing hide timer
                if (controlsHideRunnable != null) {
                    mainHandler.removeCallbacks(controlsHideRunnable);
                }

                if (isLandscape && !isAndroidTv) {
                    // Phone landscape: toggle all controls
                    if (areAllControlsVisible()) {
                        // Controls are visible - hide them immediately
                        setAllControlsVisible(false);
                    } else {
                        // Controls are hidden - show them and start timer
                        setAllControlsVisible(true);
                        startHideTimer();
                    }
                } else {
                    // Portrait phone or TV: toggle controls overlay
                    if (isControlsOverlayVisible()) {
                        // Controls are visible - hide them immediately
                        setControlsOverlayVisible(false);
                    } else {
                        // Controls are hidden - show them and start timer
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
        // Clear any existing timer
        if (controlsHideRunnable != null) {
            mainHandler.removeCallbacks(controlsHideRunnable);
        }

        // Create new timer runnable
        controlsHideRunnable = () -> {
            try {
                if (isLandscape && !isAndroidTv) {
                    setAllControlsVisible(false);
                } else {
                    setControlsOverlayVisible(false);
                }
            } catch (Exception ignored) {
                // Handle any potential exceptions during UI updates
            }
        };

        // Start the timer
        mainHandler.postDelayed(controlsHideRunnable, CONTROLS_HIDE_DELAY_MS);
    }

    // For portrait: only controlsOverlay
    private void setControlsOverlayVisible(boolean visible) {
        try {
            if (binding.controlsOverlay != null) {
                binding.controlsOverlay.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
            }
        } catch (Exception ignored) {}
    }
    private boolean isControlsOverlayVisible() {
        try {
            return binding.controlsOverlay != null && binding.controlsOverlay.getVisibility() == View.VISIBLE;
        } catch (Exception ignored) {}
        return false;
    }

    // For landscape: everything except playerView
    private void setAllControlsVisible(boolean visible) {
        try {
            if (binding.controlsOverlay != null) binding.controlsOverlay.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
            // You can add more overlay controls here if you want to also hide/show other views
        } catch (Exception ignored) {}
    }
    private boolean areAllControlsVisible() {
        try {
            return binding.controlsOverlay != null && binding.controlsOverlay.getVisibility() == View.VISIBLE;
        } catch (Exception ignored) {}
        return false;
    }

    private boolean isYouTubeUrl(String url) {
        try { return url != null && (url.contains("youtube.com") || url.contains("youtu.be")); }
        catch (Exception e) { return false; }
    }

    private void setupSystemUI() {
        try {
            getWindow().setStatusBarColor(ContextCompat.getColor(this, android.R.color.black));
            getWindow().setNavigationBarColor(ContextCompat.getColor(this, android.R.color.black));
        } catch (Exception ignored) { ignored.printStackTrace(); }
    }

    private void showLoading(boolean show) {
        try {
            if (binding != null && binding.loadingIndicator != null) {
                binding.loadingIndicator.setVisibility(show ? View.VISIBLE : View.GONE);
            }
        } catch (Exception ignored) { ignored.printStackTrace(); }
    }

    private void updatePlayPauseButton(boolean isPlaying) {
        try {
            if (binding != null && binding.btnPlayPause != null) {
                int iconRes = isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play;
                binding.btnPlayPause.setImageResource(iconRes);
            }
        } catch (Exception ignored) { ignored.printStackTrace(); }
    }

    private void updateSeekBar(Player player, SeekBar seekBar) {
        try {
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
        } catch (Exception ignored) {
            ignored.printStackTrace();
        }
    }

    private String formatTime(long millis) {
        try {
            int totalSeconds = (int) (millis / 1000);
            int minutes = totalSeconds / 60;
            int seconds = totalSeconds % 60;
            return String.format("%02d:%02d", minutes, seconds);
        } catch (Exception e) {
            return "00:00";
        }
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
        } catch (Exception ignored) { ignored.printStackTrace(); }
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
        } catch (Exception ignored) { ignored.printStackTrace(); }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { if (playerViewModel != null) playerViewModel.pause(); } catch (Exception ignored) { ignored.printStackTrace(); }
    }

    @Override
    protected void onResume() {
        super.onResume();
        try { if (playerViewModel != null) playerViewModel.resume(); } catch (Exception ignored) { ignored.printStackTrace(); }
    }

    @Override
    protected void onDestroy() {
        isDestroyed = true;
        try {
            if (playerViewModel != null) playerViewModel.release();
        } catch (Exception ignored) {}
        if (controlsHideRunnable != null) mainHandler.removeCallbacks(controlsHideRunnable);
        binding = null;
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        try { finish(); } catch (Exception ignored) {}
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        try {
            ExoPlayer player = playerViewModel != null ? playerViewModel.getPlayer() : null;
            if (player != null) {
                outState.putLong(EXTRA_PLAYBACK_POSITION, player.getCurrentPosition());
            }
            outState.putString(EXTRA_VIDEO_URL, videoUrl);
            outState.putString(EXTRA_VIDEO_TITLE, videoTitle);
            outState.putString(EXTRA_CHANNEL_NAME, channelName);
            outState.putString(EXTRA_DIRECT_VIDEO_URL, directVideoUrl);
        } catch (Exception ignored) { ignored.printStackTrace(); }
    }

    // Helper: set text safely
    private void safeSetText(android.widget.TextView tv, String text) {
        try { if (tv != null) tv.setText(text); } catch (Exception ignored) { ignored.printStackTrace(); }
    }
    // Helper: set click listener safely
    private void safeSetOnClickListener(View view, View.OnClickListener listener) {
        try { if (view != null) view.setOnClickListener(listener); } catch (Exception ignored) { ignored.printStackTrace(); }
    }
    // Helper: get string extra safely
    private String safeGetString(Bundle bundle, String key, String defaultValue) {
        try { String result = bundle.getString(key); return result != null ? result : defaultValue; }
        catch (Exception e) { return defaultValue; }
    }
    private String safeGetString(Intent intent, String key, String defaultValue) {
        try { String result = intent.getStringExtra(key); return result != null ? result : defaultValue; }
        catch (Exception e) { return defaultValue; }
    }
    // Helper: detect Android TV device
    private boolean isAndroidTvDevice() {
        try {
            UiModeManager uiModeManager = (UiModeManager) getSystemService(Context.UI_MODE_SERVICE);
            return uiModeManager != null && uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
        } catch (Exception e) {
            return false;
        }
    }
}