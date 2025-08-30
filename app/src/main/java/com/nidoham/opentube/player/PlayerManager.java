package com.nidoham.opentube.player;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.LoadControl;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.MergingMediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.exoplayer.dash.DashMediaSource;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.upstream.Allocator;
import androidx.media3.exoplayer.upstream.DefaultAllocator;

import com.nidoham.flowtube.App;

public class PlayerManager {
    private static final String TAG = "PlayerManager";
    private static PlayerManager instance;
    private static final Object lock = new Object();

    private ExoPlayer exoPlayer;
    private final Context applicationContext;
    private DefaultDataSource.Factory dataSourceFactory;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private PlayerManager(Context context) {
        this.applicationContext = context.getApplicationContext();
        initializeDataSourceFactory();
        initializePlayer();
    }

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

    private void initializeDataSourceFactory() {
        DefaultHttpDataSource.Factory httpDataSourceFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent(Util.getUserAgent(applicationContext, App.USER_AGENT))
                .setConnectTimeoutMs(DefaultHttpDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS)
                .setReadTimeoutMs(DefaultHttpDataSource.DEFAULT_READ_TIMEOUT_MILLIS)
                .setAllowCrossProtocolRedirects(true);
        dataSourceFactory = new DefaultDataSource.Factory(applicationContext, httpDataSourceFactory);
    }

    private void initializePlayer() {
        if (exoPlayer == null) {
            LoadControl loadControl = new CustomLoadControl();

            exoPlayer = new ExoPlayer.Builder(applicationContext)
                    .setLoadControl(loadControl)
                    .build();

            Log.i(TAG, "Media3 ExoPlayer initialized with CustomLoadControl");
        }
    }

    // Custom LoadControl implementing YouTube-like buffering (updated for Media3)
    private static class CustomLoadControl extends DefaultLoadControl {
        private static final int MIN_BUFFER_MS = 30000; // 30 seconds
        private static final int MAX_BUFFER_MS = 50000; // 50 seconds
        private static final int BUFFER_FOR_PLAYBACK_MS = 1500; // 1.5 seconds
        private static final int BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 3000; // 3 seconds

        public CustomLoadControl() {
            super(new DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE),
                    MIN_BUFFER_MS,
                    MAX_BUFFER_MS,
                    BUFFER_FOR_PLAYBACK_MS,
                    BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
                    C.LENGTH_UNSET,
                    false,
                    C.DEFAULT_BUFFER_SEGMENT_SIZE,
                    false);
        }
    }

    public ExoPlayer getExoPlayer() {
        if (exoPlayer == null) {
            initializePlayer();
        }
        return exoPlayer;
    }

    public void playStream(String audioUrl, String videoUrl) {
        if (exoPlayer == null) {
            initializePlayer();
        }
        try {
            MediaSource audioSource = createMediaSource(audioUrl);
            MediaSource videoSource = createMediaSource(videoUrl);

            MergingMediaSource mergingMediaSource = new MergingMediaSource(videoSource, audioSource);

            exoPlayer.setMediaSource(mergingMediaSource);
            exoPlayer.prepare();
            forceBufferDownload();
            exoPlayer.setPlayWhenReady(true);

            Log.i(TAG, "Playing merged stream: Audio=" + audioUrl + ", Video=" + videoUrl);
        } catch (Exception e) {
            Log.e(TAG, "Error playing merged stream", e);
        }
    }

    public void playMedia(String mediaUrl) {
        if (exoPlayer == null) {
            initializePlayer();
        }
        try {
            MediaSource mediaSource = createMediaSource(mediaUrl);
            exoPlayer.setMediaSource(mediaSource);
            exoPlayer.prepare();
            forceBufferDownload();
            exoPlayer.setPlayWhenReady(true);

            Log.i(TAG, "Playing media: " + mediaUrl);
        } catch (Exception e) {
            Log.e(TAG, "Error playing media", e);
        }
    }

    private MediaSource createMediaSource(String url) {
        Uri uri = Uri.parse(url);
        int contentType = Util.inferContentType(uri);
        switch (contentType) {
            case C.CONTENT_TYPE_DASH:
                return new DashMediaSource.Factory(dataSourceFactory).createMediaSource(MediaItem.fromUri(uri));
            case C.CONTENT_TYPE_HLS:
                return new HlsMediaSource.Factory(dataSourceFactory).createMediaSource(MediaItem.fromUri(uri));
            case C.CONTENT_TYPE_OTHER:
            default:
                return new ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(MediaItem.fromUri(uri));
        }
    }

    // Force buffer download by periodically checking buffered duration
    private final long FORCE_BUFFER_MIN_MS = 30000;  // 30 seconds minimum buffer
    private final long FORCE_BUFFER_CHECK_INTERVAL_MS = 1000; // 1 second

    private final Runnable bufferForceRunnable = new Runnable() {
        @Override
        public void run() {
            if (exoPlayer == null) return;

            long bufferedMs = exoPlayer.getBufferedPosition() - exoPlayer.getCurrentPosition();
            if (bufferedMs < FORCE_BUFFER_MIN_MS) {
                if (!exoPlayer.getPlayWhenReady()) {
                    exoPlayer.setPlayWhenReady(true);
                }
            }
            mainHandler.postDelayed(this, FORCE_BUFFER_CHECK_INTERVAL_MS);
        }
    };

    // Start force buffer download
    public void forceBufferDownload() {
        mainHandler.removeCallbacks(bufferForceRunnable);
        mainHandler.post(bufferForceRunnable);
    }

    // Stop force buffer download
    public void stopForceBufferDownload() {
        mainHandler.removeCallbacks(bufferForceRunnable);
    }

    public void pause() {
        if (exoPlayer != null) {
            exoPlayer.setPlayWhenReady(false);
            stopForceBufferDownload();
        }
    }

    public void play() {
        if (exoPlayer != null) {
            exoPlayer.setPlayWhenReady(true);
            forceBufferDownload();
        }
    }

    public void stop() {
        if (exoPlayer != null) {
            exoPlayer.stop();
            stopForceBufferDownload();
        }
    }

    public void seekTo(long positionMs) {
        if (exoPlayer != null) {
            exoPlayer.seekTo(positionMs);
        }
    }

    public void releasePlayer() {
        if (exoPlayer != null) {
            exoPlayer.release();
            exoPlayer = null;
            stopForceBufferDownload();
            Log.i(TAG, "Media3 ExoPlayer released");
        }
    }

    public static void releaseInstance() {
        synchronized (lock) {
            if (instance != null) {
                instance.releasePlayer();
                instance = null;
            }
        }
    }
}