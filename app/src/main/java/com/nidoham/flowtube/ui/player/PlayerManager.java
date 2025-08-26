package com.nidoham.flowtube.ui.player;

import android.content.Context;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.common.MediaItem;

public class PlayerManager {
    private static PlayerManager instance;
    private ExoPlayer player;

    private PlayerManager() {}

    public static PlayerManager getInstance() {
        if (instance == null) {
            instance = new PlayerManager();
        }
        return instance;
    }

    public void initializePlayer(Context context) {
        if (player == null) {
            player = new ExoPlayer.Builder(context).build();
        }
    }

    public ExoPlayer getPlayer() {
        return player;
    }

    public void loadMedia(String url) {
        MediaItem mediaItem = MediaItem.fromUri(url);
        player.setMediaItem(mediaItem);
        player.prepare();
        player.play();
    }

    public void play() {
        if (player != null) player.play();
    }

    public void pause() {
        if (player != null) player.pause();
    }

    public void release() {
        if (player != null) {
            player.release();
            player = null;
        }
    }
}