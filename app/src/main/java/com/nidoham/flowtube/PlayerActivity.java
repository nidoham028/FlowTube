package com.nidoham.flowtube;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

import com.nidoham.flowtube.databinding.ActivityPlayerBinding;
import com.nidoham.flowtube.ui.viewmodel.PlayerViewModel;
import com.nidoham.flowtube.helper.YouTubeDirectLink;

public class PlayerActivity extends AppCompatActivity {

    private ActivityPlayerBinding binding;
    private PlayerViewModel playerViewModel;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isDestroyed = false;
    private boolean isFullscreen = false;

    // Intent extras
    public static final String EXTRA_VIDEO_URL = "video_url";
    public static final String EXTRA_VIDEO_TITLE = "video_title";
    public static final String EXTRA_CHANNEL_NAME = "channel_name";

    // UI/Player State
    private String videoUrl = "";
    private String videoTitle = "";
    private String channelName = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityPlayerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupSystemUI();

        // ViewModel initialization
        playerViewModel = new ViewModelProvider(this).get(PlayerViewModel.class);

        // Extract Intent data
        Intent intent = getIntent();
        if (intent != null) {
            videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL);
            videoTitle = intent.getStringExtra(EXTRA_VIDEO_TITLE);
            channelName = intent.getStringExtra(EXTRA_CHANNEL_NAME);
        }

        // PlayerManager setup
        playerViewModel.initPlayer(this); // Context pass
        ExoPlayer player = playerViewModel.getPlayer();
        binding.playerView.setPlayer(player);

        // Check if the URL is a YouTube URL and fetch direct link
        if (videoUrl != null && !videoUrl.isEmpty() && isYouTubeUrl(videoUrl)) {
            
            YouTubeDirectLink.getDirectLink(this, videoUrl, new YouTubeDirectLink.DirectLinkCallback() {
                @Override
                public void onSuccess(String directUrl) {
                    mainHandler.post(() -> {
                        if (!isDestroyed) {
                            
                            playerViewModel.loadMedia(directUrl);
                        }
                    });
                }

                @Override
                public void onError(Exception e) {
                    mainHandler.post(() -> {
                        if (!isDestroyed) {
                            
                            Toast.makeText(PlayerActivity.this, "Failed to load video: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            finish();
                        }
                    });
                }
            });
        } else if (videoUrl != null && !videoUrl.isEmpty()) {
            // Non-YouTube URL, load directly
            playerViewModel.loadMedia(videoUrl);
        } else {
            Toast.makeText(this, "Invalid video URL", Toast.LENGTH_LONG).show();
            finish();
        }

        // Play/Pause Button
        binding.btnPlayPause.setOnClickListener(v -> {
            if (player.isPlaying()) {
                playerViewModel.pause();
            } else {
                playerViewModel.play();
            }
        });

        // Orientation Button
        binding.btnOrientation.setOnClickListener(v -> {
            if (isFullscreen) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            } else {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            }
            isFullscreen = !isFullscreen;
        });

        // Back Button
        binding.btnBack.setOnClickListener(v -> finish());

        // Observe playback state
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (!isDestroyed) {
                    updatePlayPauseButton(player.isPlaying());
                }
            }
        });

        // Metadata display
        binding.txtTitle.setText(videoTitle != null ? videoTitle : "Untitled");
        binding.txtChannelName.setText(channelName != null ? channelName : "");

        // Hide system UI if fullscreen
        if (isFullscreen) hideSystemUI();
    }

    private boolean isYouTubeUrl(String url) {
        return url != null && (url.contains("youtube.com") || url.contains("youtu.be"));
    }

    private void setupSystemUI() {
        getWindow().setStatusBarColor(ContextCompat.getColor(this, android.R.color.black));
        getWindow().setNavigationBarColor(ContextCompat.getColor(this, android.R.color.black));
    }

    private void updatePlayPauseButton(boolean isPlaying) {
        int iconRes = isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play;
        binding.btnPlayPause.setImageResource(iconRes);
    }

    private void hideSystemUI() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
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

    @Override
    protected void onPause() {
        super.onPause();
        playerViewModel.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        playerViewModel.resume();
    }

    @Override
    protected void onDestroy() {
        isDestroyed = true;
        playerViewModel.release();
        binding = null;
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        finish();
    }
}