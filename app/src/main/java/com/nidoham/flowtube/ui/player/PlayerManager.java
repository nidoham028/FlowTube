package com.nidoham.flowtube.ui.player;

import android.content.Context;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.TrackGroup;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.common.Tracks;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.exoplayer.source.MergingMediaSource;
import androidx.media3.datasource.DefaultDataSource;
import java.util.ArrayList;
import java.util.List;

public class PlayerManager {
    private static PlayerManager instance;
    private ExoPlayer player;
    private DefaultTrackSelector trackSelector;
    private DefaultDataSource.Factory dataSourceFactory;

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
            dataSourceFactory = new DefaultDataSource.Factory(context);
            player = new ExoPlayer.Builder(context)
                    .setTrackSelector(trackSelector)
                    .build();
        }
    }

    public ExoPlayer getPlayer() {
        return player;
    }

    /**
     * Loads media from a single URL containing both video and audio (progressive stream).
     * Maintains backward compatibility with existing code.
     */
    public void loadMedia(String url) {
        loadMediaInternal(url);
    }

    /**
     * Internal method for loading media from a single URL.
     */
    private void loadMediaInternal(String url) {
        if (player == null || dataSourceFactory == null) return;
        
        MediaItem mediaItem = MediaItem.fromUri(url);
        MediaSource mediaSource = new ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(mediaItem);
        
        player.setMediaSource(mediaSource);
        player.prepare();
        player.play();
    }

    /**
     * Loads and merges separate video and audio streams using MergingMediaSource.
     * The streams will be synchronized and played together.
     */
    public void loadMediaWithSeparateStreams(String videoUrl, String audioUrl) {
        if (player == null || dataSourceFactory == null) return;
        
        MediaItem videoMediaItem = MediaItem.fromUri(videoUrl);
        MediaItem audioMediaItem = MediaItem.fromUri(audioUrl);
        
        MediaSource videoSource = new ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(videoMediaItem);
        MediaSource audioSource = new ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(audioMediaItem);
        
        MergingMediaSource mergingMediaSource = new MergingMediaSource(videoSource, audioSource);
        
        player.setMediaSource(mergingMediaSource);
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
            dataSourceFactory = null;
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