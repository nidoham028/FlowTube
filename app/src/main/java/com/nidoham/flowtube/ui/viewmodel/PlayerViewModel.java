package com.nidoham.flowtube.ui.viewmodel;

import android.app.Application;
import android.content.Context;
import androidx.lifecycle.AndroidViewModel;
import androidx.media3.exoplayer.ExoPlayer;
import com.nidoham.flowtube.ui.player.PlayerManager;
import java.util.List;

public class PlayerViewModel extends AndroidViewModel {
    private PlayerManager playerManager;

    public PlayerViewModel(Application application) {
        super(application);
        playerManager = PlayerManager.getInstance();
    }

    public void initPlayer(Context context) {
        playerManager.initializePlayer(context);
    }

    public ExoPlayer getPlayer() {
        return playerManager.getPlayer();
    }

    public void loadMedia(String url) {
        playerManager.loadMedia(url);
    }

    public void play() {
        playerManager.play();
    }

    public void pause() {
        playerManager.pause();
    }

    public void resume() {
        play();
    }

    public void release() {
        playerManager.release();
    }

    // --- Quality APIs ---
    public void setAutoQuality() {
        playerManager.setAutoQuality();
    }

    public List<Integer> getAvailableVideoHeights() {
        return playerManager.getAvailableVideoHeights();
    }

    public void selectQuality(int height) {
        playerManager.selectQuality(height);
    }
}