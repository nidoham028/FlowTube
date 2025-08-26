package com.nidoham.flowtube;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import com.nidoham.flowtube.helper.YouTubeDirectLink;
import com.nidoham.flowtube.ui.viewmodel.PlayerViewModel;

public class PlayerActivity extends AppCompatActivity {

    private ActivityPlayerBinding binding;
    private PlayerViewModel playerViewModel;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isDestroyed = false;
    private boolean isFullscreen = false;

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

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            binding = ActivityPlayerBinding.inflate(getLayoutInflater());
            setContentView(binding.getRoot());
        } catch (Exception e) {
            Toast.makeText(this, "Failed to inflate layout: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setupSystemUI();

        playerViewModel = new ViewModelProvider(this).get(PlayerViewModel.class);

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

        // Always bind player after orientation change
        try {
            binding.playerView.setPlayer(player);
        } catch (Exception e) {
            Toast.makeText(this, "Failed to attach player: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        // Only load video URL ONCE, use saved directVideoUrl after orientation change
        if (isFirstCreate) {
            if (videoUrl != null && !videoUrl.isEmpty() && isYouTubeUrl(videoUrl)) {
                binding.loadingIndicator.setVisibility(View.VISIBLE);
                final long finalPlaybackPosition = playbackPosition;
                YouTubeDirectLink.getDirectLink(this, videoUrl, new YouTubeDirectLink.DirectLinkCallback() {
                    @Override
                    public void onSuccess(String directUrl) {
                        mainHandler.post(() -> {
                            try {
                                if (!isDestroyed && playerViewModel != null) {
                                    directVideoUrl = directUrl;
                                    binding.loadingIndicator.setVisibility(View.GONE);
                                    playerViewModel.loadMedia(directUrl);
                                    if (finalPlaybackPosition > 0) player.seekTo(finalPlaybackPosition);
                                }
                            } catch (Exception e) {
                                Toast.makeText(PlayerActivity.this, "Failed to start video: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                finish();
                            }
                        });
                    }
                    @Override
                    public void onError(Exception e) {
                        mainHandler.post(() -> {
                            binding.loadingIndicator.setVisibility(View.GONE);
                            Toast.makeText(PlayerActivity.this, "Failed to load video: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            finish();
                        });
                    }
                });
            } else if (videoUrl != null && !videoUrl.isEmpty()) {
                binding.loadingIndicator.setVisibility(View.VISIBLE);
                try {
                    directVideoUrl = videoUrl;
                    playerViewModel.loadMedia(videoUrl);
                    if (playbackPosition > 0) player.seekTo(playbackPosition);
                } catch (Exception e) {
                    Toast.makeText(this, "Failed to load video: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    finish();
                }
                binding.loadingIndicator.setVisibility(View.GONE);
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
                Toast.makeText(this, "Failed to restore video position: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }

        // Play/Pause
        safeSetOnClickListener(binding.btnPlayPause, v -> {
            try {
                if (player.isPlaying()) playerViewModel.pause();
                else playerViewModel.play();
            } catch (Exception e) {
                Toast.makeText(this, "Player error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        // Replay 10
        safeSetOnClickListener(binding.btnReplay10, v -> {
            try {
                final long pos = player.getCurrentPosition();
                player.seekTo(Math.max(pos - 10000, 0));
            } catch (Exception e) {
                Toast.makeText(this, "Failed to replay: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        // Forward 10
        safeSetOnClickListener(binding.btnForward10, v -> {
            try {
                final long pos = player.getCurrentPosition();
                final long duration = player.getDuration();
                player.seekTo(Math.min(pos + 10000, duration));
            } catch (Exception e) {
                Toast.makeText(this, "Failed to forward: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        // Orientation Button
        safeSetOnClickListener(binding.btnOrientation, v -> {
            try {
                if (getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                    isFullscreen = false;
                } else {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                    isFullscreen = true;
                    hideSystemUI();
                }
            } catch (Exception e) {
                Toast.makeText(this, "Failed to change orientation: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        // Back Button
        safeSetOnClickListener(binding.btnBack, v -> {
            try {
                finish();
            } catch (Exception e) {
                Toast.makeText(this, "Failed to exit: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        // SeekBar setup
        final SeekBar seekBar = binding.videoProgress;
        seekBar.setMax(1000);

        final ExoPlayer.Listener progressListener = new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                try { updatePlayPauseButton(player.isPlaying()); } catch (Exception ignored) {}
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

        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    updateSeekBar(player, seekBar);
                    if (!isDestroyed) mainHandler.postDelayed(this, 500);
                } catch (Exception ignored) {}
            }
        }, 500);

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            boolean wasPlaying = false;
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                try { wasPlaying = player.isPlaying(); playerViewModel.pause(); } catch (Exception ignored) {}
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                try {
                    final int progress = seekBar.getProgress();
                    final long duration = player.getDuration();
                    if (duration > 0) {
                        long seekTo = (progress * duration) / 1000;
                        player.seekTo(seekTo);
                    }
                    if (wasPlaying) playerViewModel.play();
                } catch (Exception ignored) {}
            }
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { }
        });

        // Metadata display
        safeSetText(binding.txtTitle, videoTitle != null ? videoTitle : "Untitled");
        safeSetText(binding.txtChannelName, channelName != null ? channelName : "");

        // Hide system UI if fullscreen
        if (isFullscreen) hideSystemUI();
    }

    private boolean isYouTubeUrl(String url) {
        try { return url != null && (url.contains("youtube.com") || url.contains("youtu.be")); }
        catch (Exception e) { return false; }
    }

    private void setupSystemUI() {
        try {
            getWindow().setStatusBarColor(ContextCompat.getColor(this, android.R.color.black));
            getWindow().setNavigationBarColor(ContextCompat.getColor(this, android.R.color.black));
        } catch (Exception ignored) {}
    }

    private void updatePlayPauseButton(boolean isPlaying) {
        try {
            int iconRes = isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play;
            binding.btnPlayPause.setImageResource(iconRes);
        } catch (Exception ignored) {}
    }

    private void updateSeekBar(Player player, SeekBar seekBar) {
        try {
            long duration = player.getDuration();
            long position = player.getCurrentPosition();
            if (duration > 0) {
                int progress = (int) ((position * 1000) / duration);
                seekBar.setProgress(progress);
            } else {
                seekBar.setProgress(0);
            }
            safeSetText(binding.txtCurrentTime, formatTime(position));
            safeSetText(binding.txtTotalTime, formatTime(duration));
        } catch (Exception ignored) {}
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
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
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
        } catch (Exception ignored) {}
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { playerViewModel.pause(); } catch (Exception ignored) {}
    }

    @Override
    protected void onResume() {
        super.onResume();
        try { playerViewModel.resume(); } catch (Exception ignored) {}
    }

    @Override
    protected void onDestroy() {
        isDestroyed = true;
        try { playerViewModel.release(); } catch (Exception ignored) {}
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
        } catch (Exception ignored) {}
    }

    // Helper: set text safely
    private void safeSetText(android.widget.TextView tv, String text) {
        try { if (tv != null) tv.setText(text); } catch (Exception ignored) {}
    }
    // Helper: set click listener safely
    private void safeSetOnClickListener(View view, View.OnClickListener listener) {
        try { if (view != null) view.setOnClickListener(listener); } catch (Exception ignored) {}
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
}