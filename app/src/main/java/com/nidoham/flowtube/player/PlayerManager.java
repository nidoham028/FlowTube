package com.nidoham.flowtube.player;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.LoadControl;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.MergingMediaSource;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Advanced Media3 PlayerManager with comprehensive media handling capabilities.
 * 
 * Features:
 * - Adaptive streaming support (Progressive, HLS, DASH)
 * - Intelligent caching with configurable policies
 * - Advanced error handling and recovery
 * - Quality selection and ABR management
 * - Audio language and subtitle management
 * - Thread-safe operations
 * - Comprehensive listener management
 * - Memory-efficient resource handling
 */
public class PlayerManager {

    private static final String TAG = "PlayerManager";
    private static final long DEFAULT_CACHE_SIZE = 512L * 1024L * 1024L; // 512 MB
    private static final String USER_AGENT_PREFIX = "FlowTube";
    
    // Singleton components
    private static volatile PlayerManager instance;
    private static SimpleCache mediaCache;
    private static final Object CACHE_LOCK = new Object();
    
    // Core components
    private ExoPlayer player;
    private DefaultTrackSelector trackSelector;
    private DataSource.Factory dataSourceFactory;
    private DefaultMediaSourceFactory mediaSourceFactory;
    private LoadControl loadControl;
    private StandaloneDatabaseProvider databaseProvider;
    
    // State management
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);
    private final AtomicBoolean isReleased = new AtomicBoolean(false);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    // Error handling
    private volatile PlaybackException lastError;
    private int consecutiveErrors = 0;
    private static final int MAX_RETRY_COUNT = 3;
    
    // Listener management
    private final Set<PlayerEventListener> eventListeners = ConcurrentHashMap.newKeySet();
    private final Set<Player.Listener> playerListeners = ConcurrentHashMap.newKeySet();
    
    /**
     * Interface for handling player events with enhanced error information.
     */
    public interface PlayerEventListener {
        default void onPlayerReady() {}
        default void onPlayerError(@NonNull PlaybackException error, boolean canRetry) {}
        default void onPlaybackStateChanged(@Player.State int state) {}
        default void onTracksChanged(@NonNull Tracks tracks) {}
        default void onQualityChanged(int height, long bitrate) {}
    }
    
    /**
     * Configuration class for player initialization.
     */
    public static class PlayerConfig {
        public long cacheSize = DEFAULT_CACHE_SIZE;
        public boolean enableCache = true;
        public boolean enableAdaptiveBitrate = true;
        public int bufferDurationMs = DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS;
        public int minBufferMs = DefaultLoadControl.DEFAULT_MIN_BUFFER_MS;
        public int maxBufferMs = DefaultLoadControl.DEFAULT_MAX_BUFFER_MS;
        public String userAgent = null;
        
        public PlayerConfig setCacheSize(long bytes) {
            this.cacheSize = bytes;
            return this;
        }
        
        public PlayerConfig setBufferConfiguration(int minMs, int maxMs, int playbackMs) {
            this.minBufferMs = minMs;
            this.maxBufferMs = maxMs;
            this.bufferDurationMs = playbackMs;
            return this;
        }
        
        public PlayerConfig setUserAgent(@Nullable String agent) {
            this.userAgent = agent;
            return this;
        }
    }
    
    private PlayerManager() {
        // Private constructor for singleton
    }
    
    /**
     * Thread-safe singleton instance retrieval.
     */
    public static PlayerManager getInstance() {
        if (instance == null) {
            synchronized (PlayerManager.class) {
                if (instance == null) {
                    instance = new PlayerManager();
                }
            }
        }
        return instance;
    }
    
    /**
     * Initialize player with default configuration.
     */
    public synchronized void initialize(@NonNull Context context) {
        initialize(context, new PlayerConfig());
    }
    
    /**
     * Initialize player with custom configuration.
     */
    public synchronized void initialize(@NonNull Context context, @NonNull PlayerConfig config) {
        if (isInitialized.get() || isReleased.get()) {
            Log.w(TAG, "Player already initialized or released");
            return;
        }
        
        try {
            setupCache(context, config);
            setupDataSourceFactory(context, config);
            setupTrackSelector(context, config);
            setupLoadControl(config);
            setupPlayer(context);
            
            isInitialized.set(true);
            Log.i(TAG, "Player initialized successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize player", e);
            cleanup();
            throw new RuntimeException("Player initialization failed", e);
        }
    }
    
    private void setupCache(@NonNull Context context, @NonNull PlayerConfig config) {
        if (!config.enableCache) return;
        
        synchronized (CACHE_LOCK) {
            if (mediaCache == null) {
                File cacheDirectory = new File(context.getCacheDir(), "media3_cache");
                databaseProvider = new StandaloneDatabaseProvider(context);
                
                mediaCache = new SimpleCache(
                    cacheDirectory,
                    new LeastRecentlyUsedCacheEvictor(config.cacheSize),
                    databaseProvider
                );
            }
        }
    }
    
    private void setupDataSourceFactory(@NonNull Context context, @NonNull PlayerConfig config) {
        String userAgent = config.userAgent != null ? 
            config.userAgent : Util.getUserAgent(context, USER_AGENT_PREFIX);
            
        DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(DefaultHttpDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS)
            .setReadTimeoutMs(DefaultHttpDataSource.DEFAULT_READ_TIMEOUT_MILLIS);
        
        DefaultDataSource.Factory upstreamFactory = new DefaultDataSource.Factory(context, httpFactory);
        
        if (config.enableCache && mediaCache != null) {
            dataSourceFactory = new CacheDataSource.Factory()
                .setCache(mediaCache)
                .setUpstreamDataSourceFactory(upstreamFactory)
                .setFlags(CacheDataSource.FLAG_BLOCK_ON_CACHE | CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
        } else {
            dataSourceFactory = upstreamFactory;
        }
        
        // FIX: Set load error handling policy on mediaSourceFactory, not ExoPlayer.Builder
        mediaSourceFactory = new DefaultMediaSourceFactory(dataSourceFactory)
            .setLoadErrorHandlingPolicy(new DefaultLoadErrorHandlingPolicy());
    }
    
    private void setupTrackSelector(@NonNull Context context, @NonNull PlayerConfig config) {
        trackSelector = new DefaultTrackSelector(context);
        
        TrackSelectionParameters.Builder parametersBuilder = trackSelector.buildUponParameters()
            .setForceHighestSupportedBitrate(!config.enableAdaptiveBitrate);
            
        trackSelector.setParameters(parametersBuilder.build());
    }
    
    private void setupLoadControl(@NonNull PlayerConfig config) {
        loadControl = new DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                config.minBufferMs,
                config.maxBufferMs,
                config.bufferDurationMs,
                config.bufferDurationMs
            )
            .build();
    }
    
    private void setupPlayer(@NonNull Context context) {
        player = new ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(mediaSourceFactory)
            .build();
        
        // Add comprehensive event listener
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                notifyPlaybackStateChanged(playbackState);
                if (playbackState == Player.STATE_READY) {
                    consecutiveErrors = 0;
                }
            }
            
            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                handlePlayerError(error);
            }
            
            @Override
            public void onTracksChanged(@NonNull Tracks tracks) {
                notifyTracksChanged(tracks);
                detectQualityChange(tracks);
            }
        });
    }
    
    private void handlePlayerError(@NonNull PlaybackException error) {
        lastError = error;
        consecutiveErrors++;
        
        Log.e(TAG, "Player error occurred (attempt " + consecutiveErrors + ")", error);
        
        boolean canRetry = consecutiveErrors < MAX_RETRY_COUNT && 
                          (error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                           error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT);
        
        notifyPlayerError(error, canRetry);
        
        if (canRetry) {
            mainHandler.postDelayed(this::attemptRecovery, 1000 * consecutiveErrors);
        }
    }
    
    private void attemptRecovery() {
        if (player != null && !isReleased.get()) {
            Log.i(TAG, "Attempting player recovery");
            player.prepare();
        }
    }
    
    /**
     * Load media from URL with automatic format detection.
     */
    public void loadMedia(@NonNull String url) {
        loadMedia(url, null);
    }
    
    /**
     * Load media with optional subtitle configuration.
     */
    public void loadMedia(@NonNull String url, @Nullable SubtitleConfiguration subtitleConfig) {
        if (!validatePlayerState()) return;
        
        try {
            MediaItem.Builder builder = new MediaItem.Builder().setUri(Uri.parse(url));
            
            if (subtitleConfig != null) {
                MediaItem.SubtitleConfiguration subConfig = new MediaItem.SubtitleConfiguration.Builder(
                    Uri.parse(subtitleConfig.url))
                    .setMimeType(subtitleConfig.mimeType)
                    .setLanguage(subtitleConfig.language)
                    .setSelectionFlags(subtitleConfig.isDefault ? C.SELECTION_FLAG_DEFAULT : 0)
                    .build();
                
                builder.setSubtitleConfigurations(Collections.singletonList(subConfig));
            }
            
            player.setMediaItem(builder.build());
            player.prepare();
            consecutiveErrors = 0;
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to load media: " + url, e);
        }
    }
    
    /**
     * Load media with separate video and audio streams.
     */
    public void loadMediaWithSeparateStreams(@NonNull String videoUrl, @NonNull String audioUrl) {
        if (!validatePlayerState()) return;
        
        try {
            MediaSource videoSource = mediaSourceFactory.createMediaSource(MediaItem.fromUri(videoUrl));
            MediaSource audioSource = mediaSourceFactory.createMediaSource(MediaItem.fromUri(audioUrl));
            MergingMediaSource mergedSource = new MergingMediaSource(videoSource, audioSource);
            
            player.setMediaSource(mergedSource);
            player.prepare();
            consecutiveErrors = 0;
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to load separate streams", e);
        }
    }
    
    /**
     * Subtitle configuration helper class.
     */
    public static class SubtitleConfiguration {
        public final String url;
        public final String mimeType;
        public final String language;
        public final boolean isDefault;
        
        public SubtitleConfiguration(@NonNull String url, @NonNull String mimeType, 
                                   @Nullable String language, boolean isDefault) {
            this.url = url;
            this.mimeType = mimeType;
            this.language = language;
            this.isDefault = isDefault;
        }
        
        public static SubtitleConfiguration createVtt(@NonNull String url, @Nullable String language) {
            return new SubtitleConfiguration(url, MimeTypes.TEXT_VTT, language, true);
        }
    }
    
    /**
     * Quality information container.
     */
    public static class QualityInfo {
        public final int height;
        public final int width;
        public final long bitrate;
        public final String codecs;
        
        public QualityInfo(int height, int width, long bitrate, @Nullable String codecs) {
            this.height = height;
            this.width = width;
            this.bitrate = bitrate;
            this.codecs = codecs;
        }
        
        @Override
        public String toString() {
            return height + "p (" + (bitrate / 1000) + " kbps)";
        }
    }
    
    /**
     * Get comprehensive quality information for available video tracks.
     */
    public List<QualityInfo> getAvailableQualities() {
        if (player == null) return new ArrayList<>();
        
        List<QualityInfo> qualities = new ArrayList<>();
        Set<Integer> seenHeights = new LinkedHashSet<>();
        
        Tracks tracks = player.getCurrentTracks();
        for (Tracks.Group group : tracks.getGroups()) {
            if (group.getType() != C.TRACK_TYPE_VIDEO) continue;
            
            for (int i = 0; i < group.length; i++) {
                Format format = group.getTrackFormat(i);
                if (format.height > 0 && !seenHeights.contains(format.height)) {
                    seenHeights.add(format.height);
                    qualities.add(new QualityInfo(
                        format.height, 
                        format.width, 
                        format.bitrate,
                        format.codecs
                    ));
                }
            }
        }
        
        qualities.sort((a, b) -> Integer.compare(b.height, a.height)); // Descending order
        return qualities;
    }
    
    /**
     * Enable automatic quality selection.
     */
    public void enableAdaptiveQuality() {
        if (trackSelector == null) return;
        
        TrackSelectionParameters params = trackSelector.buildUponParameters()
            .clearOverrides()
            .clearVideoSizeConstraints()
            .setMaxVideoBitrate(Integer.MAX_VALUE)
            .setForceHighestSupportedBitrate(false)
            .build();
            
        trackSelector.setParameters(params);
    }
    
    /**
     * Select specific video quality by height.
     */
    public boolean selectQuality(int targetHeight) {
        if (player == null || trackSelector == null) return false;
        
        Tracks tracks = player.getCurrentTracks();
        TrackSelectionParameters.Builder builder = trackSelector.buildUponParameters();
        builder.clearOverrides();
        
        boolean qualityFound = false;
        
        for (Tracks.Group group : tracks.getGroups()) {
            if (group.getType() != C.TRACK_TYPE_VIDEO) continue;
            
            TrackGroup trackGroup = group.getMediaTrackGroup();
            List<Integer> targetIndices = new ArrayList<>();
            
            for (int i = 0; i < group.length; i++) {
                Format format = group.getTrackFormat(i);
                if (format.height == targetHeight) {
                    targetIndices.add(i);
                }
            }
            
            if (!targetIndices.isEmpty()) {
                // Use setTrackTypeDisabled(false) and manual override approach for compatibility
                builder.setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false);
                qualityFound = true;
                
                // Apply video size constraints as alternative to track selection override
                builder.setMaxVideoSize(Integer.MAX_VALUE, targetHeight)
                       .setMinVideoSize(0, targetHeight);
            }
        }
        
        if (qualityFound) {
            builder.setMaxVideoSize(Integer.MAX_VALUE, targetHeight)
                   .setMinVideoSize(0, targetHeight);
            trackSelector.setParameters(builder.build());
        }
        
        return qualityFound;
    }
    
    /**
     * Get available audio languages.
     */
    public List<String> getAvailableAudioLanguages() {
        if (player == null) return new ArrayList<>();
        
        List<String> languages = new ArrayList<>();
        Set<String> uniqueLanguages = new LinkedHashSet<>();
        
        Tracks tracks = player.getCurrentTracks();
        for (Tracks.Group group : tracks.getGroups()) {
            if (group.getType() != C.TRACK_TYPE_AUDIO) continue;
            
            for (int i = 0; i < group.length; i++) {
                String language = group.getTrackFormat(i).language;
                if (language != null && uniqueLanguages.add(language)) {
                    languages.add(language);
                }
            }
        }
        
        return languages;
    }
    
    /**
     * Select preferred audio language.
     */
    public void selectAudioLanguage(@NonNull String languageCode) {
        if (trackSelector == null) return;
        
        TrackSelectionParameters params = trackSelector.buildUponParameters()
            .setPreferredAudioLanguage(languageCode)
            .build();
            
        trackSelector.setParameters(params);
    }
    
    /**
     * Select preferred subtitle language.
     */
    public void selectSubtitleLanguage(@NonNull String languageCode) {
        if (trackSelector == null) return;
        
        TrackSelectionParameters params = trackSelector.buildUponParameters()
            .setPreferredTextLanguage(languageCode)
            .setIgnoredTextSelectionFlags(0)
            .build();
            
        trackSelector.setParameters(params);
    }
    
    /**
     * Disable all subtitle tracks.
     */
    public void disableSubtitles() {
        if (trackSelector == null) return;
        
        TrackSelectionParameters params = trackSelector.buildUponParameters()
            .setPreferredTextLanguage(null)
            .build();
            
        trackSelector.setParameters(params);
    }
    
    // Listener management
    public void addListener(@NonNull PlayerEventListener listener) {
        eventListeners.add(listener);
    }
    
    public void removeListener(@NonNull PlayerEventListener listener) {
        eventListeners.remove(listener);
    }
    
    public void addPlayerListener(@NonNull Player.Listener listener) {
        playerListeners.add(listener);
        if (player != null) {
            player.addListener(listener);
        }
    }
    
    public void removePlayerListener(@NonNull Player.Listener listener) {
        playerListeners.remove(listener);
        if (player != null) {
            player.removeListener(listener);
        }
    }
    
    // Notification methods
    private void notifyPlaybackStateChanged(@Player.State int state) {
        for (PlayerEventListener listener : eventListeners) {
            try {
                listener.onPlaybackStateChanged(state);
            } catch (Exception e) {
                Log.e(TAG, "Error in listener callback", e);
            }
        }
        
        if (state == Player.STATE_READY) {
            for (PlayerEventListener listener : eventListeners) {
                try {
                    listener.onPlayerReady();
                } catch (Exception e) {
                    Log.e(TAG, "Error in listener callback", e);
                }
            }
        }
    }
    
    private void notifyPlayerError(@NonNull PlaybackException error, boolean canRetry) {
        for (PlayerEventListener listener : eventListeners) {
            try {
                listener.onPlayerError(error, canRetry);
            } catch (Exception e) {
                Log.e(TAG, "Error in listener callback", e);
            }
        }
    }
    
    private void notifyTracksChanged(@NonNull Tracks tracks) {
        for (PlayerEventListener listener : eventListeners) {
            try {
                listener.onTracksChanged(tracks);
            } catch (Exception e) {
                Log.e(TAG, "Error in listener callback", e);
            }
        }
    }
    
    private void detectQualityChange(@NonNull Tracks tracks) {
        for (Tracks.Group group : tracks.getGroups()) {
            if (group.getType() != C.TRACK_TYPE_VIDEO || !group.isSelected()) continue;
            
            for (int i = 0; i < group.length; i++) {
                if (group.isTrackSelected(i)) {
                    Format format = group.getTrackFormat(i);
                    for (PlayerEventListener listener : eventListeners) {
                        try {
                            listener.onQualityChanged(format.height, format.bitrate);
                        } catch (Exception e) {
                            Log.e(TAG, "Error in listener callback", e);
                        }
                    }
                    return;
                }
            }
        }
    }
    
    // Control methods
    public void play() {
        if (validatePlayerState()) {
            player.play();
        }
    }
    
    public void pause() {
        if (validatePlayerState()) {
            player.pause();
        }
    }
    
    public void stop() {
        if (validatePlayerState()) {
            player.stop();
        }
    }
    
    public void seekTo(long positionMs) {
        if (validatePlayerState()) {
            player.seekTo(Math.max(0, positionMs));
        }
    }
    
    public void setPlaybackSpeed(float speed) {
        if (validatePlayerState()) {
            player.setPlaybackSpeed(Math.max(0.1f, Math.min(3.0f, speed)));
        }
    }
    
    // State queries
    public boolean isPlaying() {
        return player != null && player.isPlaying();
    }
    
    public long getCurrentPosition() {
        return player != null ? player.getCurrentPosition() : 0L;
    }
    
    public long getDuration() {
        return player != null ? player.getDuration() : C.TIME_UNSET;
    }
    
    @Player.State
    public int getPlaybackState() {
        return player != null ? player.getPlaybackState() : Player.STATE_IDLE;
    }
    
    public float getPlaybackSpeed() {
        return player != null ? player.getPlaybackParameters().speed : 1.0f;
    }
    
    @Nullable
    public PlaybackException getLastError() {
        return lastError;
    }
    
    public ExoPlayer getPlayer() {
        return player;
    }
    
    public boolean isInitialized() {
        return isInitialized.get() && !isReleased.get();
    }
    
    private boolean validatePlayerState() {
        if (!isInitialized.get() || isReleased.get() || player == null) {
            Log.w(TAG, "Player not properly initialized or already released");
            return false;
        }
        return true;
    }
    
    private void cleanup() {
        isInitialized.set(false);
        
        if (player != null) {
            // Remove all registered listeners
            for (Player.Listener listener : playerListeners) {
                player.removeListener(listener);
            }
            player.release();
            player = null;
        }
        
        trackSelector = null;
        mediaSourceFactory = null;
        dataSourceFactory = null;
        loadControl = null;
        
        eventListeners.clear();
        playerListeners.clear();
    }
    
    /**
     * Release all resources. Should be called when the player is no longer needed.
     */
    public synchronized void release() {
        if (isReleased.get()) return;
        
        Log.i(TAG, "Releasing player resources");
        isReleased.set(true);
        cleanup();
    }
    
    /**
     * Release cache resources. Should be called during application shutdown.
     */
    public static synchronized void releaseCache() {
        synchronized (CACHE_LOCK) {
            if (mediaCache != null) {
                try {
                    mediaCache.release();
                    mediaCache = null;
                    Log.i(TAG, "Media cache released");
                } catch (Exception e) {
                    Log.e(TAG, "Error releasing cache", e);
                }
            }
        }
    }
}