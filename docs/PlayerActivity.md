import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.Player;
import androidx.media3.ui.PlayerView;

public class PlayerActivity extends AppCompatActivity {

    private PlayerManager playerManager;
    private PlayerView playerView;
    private PlayerViewModel playerViewModel;
    private Button changeVideoButton;

    private final String[] videoUrls = {
            "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
            "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4"
    };
    private int currentVideoIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        playerView = findViewById(R.id.player_view);
        changeVideoButton = findViewById(R.id.change_video_button);

        playerManager = PlayerManager.getInstance();
        playerManager.initializePlayer(this);

        playerViewModel = new ViewModelProvider(this).get(PlayerViewModel.class);

        playerViewModel.getVideoUrl().observe(this, url -> {
            if (url != null) {
                playerManager.loadMedia(url);
            }
        });

        playerViewModel.getPosition().observe(this, position -> {
            if (position != null) {
                playerManager.getPlayer().seekTo(position);
            }
        });

        playerViewModel.getPlayWhenReady().observe(this, playWhenReady -> {
            if (playWhenReady != null) {
                playerManager.getPlayer().setPlayWhenReady(playWhenReady);
            }
        });

        if (savedInstanceState == null) {
            playerViewModel.setVideoUrl(videoUrls[currentVideoIndex]);
        }

        changeVideoButton.setOnClickListener(v -> {
            currentVideoIndex = (currentVideoIndex + 1) % videoUrls.length;
            playerViewModel.setVideoUrl(videoUrls[currentVideoIndex]);
            playerManager.play();
        });

        playerManager.getPlayer().addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_ENDED) {
                    // Auto-play next video
                    currentVideoIndex = (currentVideoIndex + 1) % videoUrls.length;
                    playerViewModel.setVideoUrl(videoUrls[currentVideoIndex]);
                    playerManager.play();
                }
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        playerView.setPlayer(playerManager.getPlayer());
        playerManager.restoreState();
        playerManager.play();
    }

    @Override
    protected void onResume() {
        super.onResume();
        playerManager.play();
    }

    @Override
    protected void onPause() {
        super.onPause();
        playerManager.pause();
        playerManager.saveState();
        playerViewModel.setPosition(playerManager.getCurrentPosition());
        playerViewModel.setPlayWhenReady(playerManager.getPlayer().getPlayWhenReady());
    }

    @Override
    protected void onStop() {
        super.onStop();
        playerManager.pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        playerManager.releasePlayer();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            enterFullscreen();
        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            exitFullscreen();
        }
    }

    private void enterFullscreen() {
        getSupportActionBar().hide();
        playerView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        );
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    }

    private void exitFullscreen() {
        getSupportActionBar().show();
        playerView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT // Or your desired height
        ));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }
}