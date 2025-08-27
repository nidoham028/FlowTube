package com.nidoham.flowtube.ui.player;

import android.content.Context;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.TrackGroup;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.common.Tracks;
import androidx.media3.common.TrackSelectionParameters;
import java.util.ArrayList;
import java.util.List;

public class PlayerManager {
    private static PlayerManager instance;
    private ExoPlayer player;
    private DefaultTrackSelector trackSelector;

    private PlayerManager() {}

    public static PlayerManager getInstance() {
        if (instance == null) {
            instance = new PlayerManager();
        }
        return instance;
    }

    public void initializePlayer(Context context) {
        if (player == null) {
            trackSelector = new DefaultTrackSelector(context);
            player = new ExoPlayer.Builder(context)
                    .setTrackSelector(trackSelector)
                    .build();
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
            trackSelector = null;
        }
    }

    // --- Smart Quality (ABR) ---
    public void setAutoQuality() {
        if (trackSelector != null) {
            TrackSelectionParameters.Builder builder = trackSelector.buildUponParameters();
            builder.clearOverrides();
            builder.clearVideoSizeConstraints();
            trackSelector.setParameters(builder.build());
        }
    }

    // --- Manual Quality ---
    /**
     * Returns a list of available quality heights (e.g. [240, 360, 720, 1080])
     */
    public List<Integer> getAvailableVideoHeights() {
        List<Integer> heights = new ArrayList<>();
        if (player != null) {
            Tracks tracks = player.getCurrentTracks();
            for (Tracks.Group group : tracks.getGroups()) {
                if (group.getType() == C.TRACK_TYPE_VIDEO) {
                    for (int i = 0; i < group.length; i++) {
                        int height = group.getTrackFormat(i).height;
                        if (!heights.contains(height) && height > 0) heights.add(height);
                    }
                }
            }
        }
        return heights;
    }

    /**
     * Selects a specific video height (quality).
     */
    public void selectQuality(int height) {
        if (player == null || trackSelector == null) return;
        
        Tracks tracks = player.getCurrentTracks();
        for (Tracks.Group group : tracks.getGroups()) {
            if (group.getType() == C.TRACK_TYPE_VIDEO) {
                for (int i = 0; i < group.length; i++) {
                    if (group.getTrackFormat(i).height == height) {
                        TrackGroup trackGroup = group.getMediaTrackGroup();
                        
                        // Use the parameters builder approach for track selection
                        TrackSelectionParameters.Builder builder = trackSelector.buildUponParameters();
                        builder.clearOverrides();
                        
                        // Set maximum video size constraints to force selection
                        builder.setMaxVideoSize(Integer.MAX_VALUE, height);
                        builder.setMinVideoSize(0, height);
                        
                        // Set preferred video height
                        builder.setMaxVideoFrameRate(Integer.MAX_VALUE);
                        builder.setMaxVideoBitrate(Integer.MAX_VALUE);
                        
                        trackSelector.setParameters(builder.build());
                        return;
                    }
                }
            }
        }
    }
}