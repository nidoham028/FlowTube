package com.nidoham.opentube.player;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.media3.common.C; // Import the C class
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.MergingMediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.exoplayer.dash.DashMediaSource;
import androidx.media3.exoplayer.hls.HlsMediaSource;

/**
 * Singleton PlayerManager class that manages a single ExoPlayer instance for the entire app.
 * Handles merging of separate audio and video streams for DASH/HLS content.
 * Uses Media3 ExoPlayer APIs.
 */
public class PlayerManager {
    private static final String TAG = "PlayerManager";
    private static PlayerManager instance;
    private static final Object lock = new Object();
    
    private ExoPlayer exoPlayer;
    private Context applicationContext;
    private DefaultDataSource.Factory dataSourceFactory;
    
    private PlayerManager(Context context) {
        this.applicationContext = context.getApplicationContext();
        initializeDataSourceFactory();
        initializePlayer();
    }
    
    /**
     * Get the singleton instance of PlayerManager
     */
    public static PlayerManager getInstance(Context context) {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new PlayerManager(context);
                }
            }
        }
        return instance;
    }
    
    /**
     * Initialize the data source factory for network requests
     */
    private void initializeDataSourceFactory() {
        DefaultHttpDataSource.Factory httpDataSourceFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent(Util.getUserAgent(applicationContext, "YourAppName"))
                .setConnectTimeoutMs(DefaultHttpDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS)
                .setReadTimeoutMs(DefaultHttpDataSource.DEFAULT_READ_TIMEOUT_MILLIS)
                .setAllowCrossProtocolRedirects(true);
        
        dataSourceFactory = new DefaultDataSource.Factory(applicationContext, httpDataSourceFactory);
    }
    
    /**
     * Initialize the ExoPlayer instance
     */
    private void initializePlayer() {
        if (exoPlayer == null) {
            exoPlayer = new ExoPlayer.Builder(applicationContext)
                    .build();
            
            Log.d(TAG, "Media3 ExoPlayer initialized");
        }
    }
    
    /**
     * Get the ExoPlayer instance
     */
    public ExoPlayer getExoPlayer() {
        if (exoPlayer == null) {
            initializePlayer();
        }
        return exoPlayer;
    }
    
    /**
     * Play stream by merging separate audio and video URLs
     * Supports DASH, HLS, and progressive streams
     * 
     * @param audioUrl URL of the audio stream
     * @param videoUrl URL of the video stream
     */
    public void playStream(String audioUrl, String videoUrl) {
        if (exoPlayer == null) {
            initializePlayer();
        }
        
        try {
            MediaSource audioSource = createMediaSource(audioUrl);
            MediaSource videoSource = createMediaSource(videoUrl);
            
            // Merge audio and video sources
            MergingMediaSource mergingMediaSource = new MergingMediaSource(videoSource, audioSource);
            
            // Prepare and play
            exoPlayer.setMediaSource(mergingMediaSource);
            exoPlayer.prepare();
            exoPlayer.setPlayWhenReady(true);
            
            Log.d(TAG, "Playing merged stream - Audio: " + audioUrl + ", Video: " + videoUrl);
            
        } catch (Exception e) {
            Log.e(TAG, "Error playing stream", e);
        }
    }
    
    /**
     * Play a single media source
     * 
     * @param mediaUrl URL of the media to play
     */
    public void playMedia(String mediaUrl) {
        if (exoPlayer == null) {
            initializePlayer();
        }
        
        try {
            MediaSource mediaSource = createMediaSource(mediaUrl);
            exoPlayer.setMediaSource(mediaSource);
            exoPlayer.prepare();
            exoPlayer.setPlayWhenReady(true);
            
            Log.d(TAG, "Playing media: " + mediaUrl);
            
        } catch (Exception e) {
            Log.e(TAG, "Error playing media", e);
        }
    }
    
    /**
     * Create appropriate MediaSource based on URL type
     */
    private MediaSource createMediaSource(String url) {
        Uri uri = Uri.parse(url);
        int contentType = Util.inferContentType(uri);
        
        switch (contentType) {
            case C.TYPE_DASH: // FIXED
                return new DashMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(MediaItem.fromUri(uri));
            case C.TYPE_HLS: // FIXED
                return new HlsMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(MediaItem.fromUri(uri));
            case C.TYPE_OTHER: // FIXED
            default:
                return new ProgressiveMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(MediaItem.fromUri(uri));
        }
    }
    
    /**
     * Pause playback
     */
    public void pause() {
        if (exoPlayer != null) {
            exoPlayer.setPlayWhenReady(false);
        }
    }
    
    /**
     * Resume playback
     */
    public void play() {
        if (exoPlayer != null) {
            exoPlayer.setPlayWhenReady(true);
        }
    }
    
    /**
     * Stop playback
     */
    public void stop() {
        if (exoPlayer != null) {
            exoPlayer.stop();
        }
    }
    
    /**
     * Seek to specific position
     * 
     * @param positionMs Position in milliseconds
     */
    public void seekTo(long positionMs) {
        if (exoPlayer != null) {
            exoPlayer.seekTo(positionMs);
        }
    }
    
    /**
     * Release the player and clean up resources
     * Call this when the app is being destroyed
     */
    public void releasePlayer() {
        if (exoPlayer != null) {
            exoPlayer.release();
            exoPlayer = null;
            Log.d(TAG, "Media3 ExoPlayer released");
        }
    }
    
    /**
     * Release the singleton instance
     * Call this when the app is being destroyed
     */
    public static void releaseInstance() {
        synchronized (lock) {
            if (instance != null) {
                instance.releasePlayer();
                instance = null;
            }
        }
    }
}