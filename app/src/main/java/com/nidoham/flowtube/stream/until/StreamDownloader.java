package com.nidoham.flowtube.stream.until;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.nidoham.flowtube.stream.AudioStreamDownloader;
import com.nidoham.flowtube.stream.VideoStreamDownloader;
import com.nidoham.flowtube.stream.MixedStreamDownloader;
import com.nidoham.flowtube.stream.prefs.PrefsHelper;

/**
 * StreamDownloader
 * ----------------
 * Unified stream downloader that orchestrates audio, video, and mixed stream extraction
 * for YouTube content. This class provides a single interface for all streaming operations
 * while delegating to specialized downloaders based on content type and user preferences.
 * 
 * Features:
 * - Unified interface for all stream types (DASH and mixed)
 * - Intelligent stream type selection based on user preferences
 * - Comprehensive error handling with fallback mechanisms
 * - Thread-safe operations with proper lifecycle management
 * - Support for both separate (DASH) and combined (mixed) stream formats
 */
public class StreamDownloader {

    private static final String TAG = "StreamDownloader";
    
    private final Context context;
    private final Handler mainHandler;
    
    // Specialized downloaders for different stream types
    private final AudioStreamDownloader audioDownloader;
    private final VideoStreamDownloader videoDownloader;
    private final MixedStreamDownloader mixedDownloader;

    /**
     * Stream type enumeration for download strategy selection
     */
    public enum StreamType {
        AUDIO_ONLY,         // Audio stream only
        VIDEO_ONLY,         // Video stream only (DASH)
        MIXED_STREAM,       // Combined audio-video stream
        DASH_STREAMS,       // Separate audio and video streams
        AUTO_DETECT         // Automatically determine best stream type
    }

    /**
     * Quality preference for unified stream selection
     */
    public enum QualityMode {
        DATA_SAVER,         // Low quality, bandwidth optimized
        STANDARD,           // Medium quality, balanced approach
        HIGH_QUALITY,       // High quality, best experience
        USER_PREFERENCE     // Use settings from SharedPreferences
    }

    /**
     * Comprehensive listener interface for stream extraction results
     */
    public interface OnStreamExtractListener {
        void onAudioStreamReady(@NonNull String audioUrl, int bitrate, @NonNull String format);
        void onVideoStreamReady(@NonNull String videoUrl, @NonNull String resolution, @NonNull String format);
        void onMixedStreamReady(@NonNull String streamUrl, @NonNull String resolution, 
                               @NonNull String format, @NonNull String quality);
        void onError(@NonNull Exception error);
        
        // Optional callbacks for enhanced user experience
        default void onProgress(@NonNull String status) {}
        default void onStreamTypeDetected(@NonNull StreamType streamType) {}
    }

    /**
     * Specialized listener for DASH stream extraction (separate audio and video)
     */
    public interface OnDashStreamListener {
        void onStreamsReady(@NonNull String audioUrl, int audioBitrate, @NonNull String audioFormat,
                           @NonNull String videoUrl, @NonNull String videoResolution, @NonNull String videoFormat);
        void onError(@NonNull Exception error);
        default void onProgress(@NonNull String status) {}
    }

    /**
     * Data class containing comprehensive stream information
     */
    public static class StreamResult {
        public final StreamType streamType;
        public final String audioUrl;
        public final String videoUrl;
        public final String mixedUrl;
        public final int audioBitrate;
        public final String videoResolution;
        public final String audioFormat;
        public final String videoFormat;
        public final String mixedFormat;

        public StreamResult(@NonNull StreamType streamType, @Nullable String audioUrl, 
                           @Nullable String videoUrl, @Nullable String mixedUrl,
                           int audioBitrate, @Nullable String videoResolution,
                           @Nullable String audioFormat, @Nullable String videoFormat,
                           @Nullable String mixedFormat) {
            this.streamType = streamType;
            this.audioUrl = audioUrl;
            this.videoUrl = videoUrl;
            this.mixedUrl = mixedUrl;
            this.audioBitrate = audioBitrate;
            this.videoResolution = videoResolution;
            this.audioFormat = audioFormat;
            this.videoFormat = videoFormat;
            this.mixedFormat = mixedFormat;
        }
    }

    public StreamDownloader(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
        
        // Initialize specialized downloaders
        this.audioDownloader = new AudioStreamDownloader(context);
        this.videoDownloader = new VideoStreamDownloader(context);
        this.mixedDownloader = new MixedStreamDownloader(context);
    }

    /**
     * Download streams with automatic type detection based on user preferences
     */
    public void downloadStream(@NonNull String youtubeUrl, @NonNull OnStreamExtractListener listener) {
        downloadStreamWithQuality(youtubeUrl, QualityMode.USER_PREFERENCE, StreamType.AUTO_DETECT, listener);
    }

    /**
     * Download streams with specified quality and type preferences
     */
    public void downloadStreamWithQuality(@NonNull String youtubeUrl, @NonNull QualityMode qualityMode,
                                         @NonNull StreamType streamType, @NonNull OnStreamExtractListener listener) {
        if (!isValidYouTubeUrl(youtubeUrl)) {
            listener.onError(new IllegalArgumentException("Invalid YouTube URL provided"));
            return;
        }

        StreamType effectiveStreamType = determineEffectiveStreamType(streamType);
        listener.onStreamTypeDetected(effectiveStreamType);

        switch (effectiveStreamType) {
            case AUDIO_ONLY:
                extractAudioOnly(youtubeUrl, qualityMode, listener);
                break;
            case VIDEO_ONLY:
                extractVideoOnly(youtubeUrl, qualityMode, listener);
                break;
            case MIXED_STREAM:
                extractMixedStream(youtubeUrl, qualityMode, listener);
                break;
            case DASH_STREAMS:
                extractDashStreams(youtubeUrl, qualityMode, listener);
                break;
            default:
                listener.onError(new IllegalStateException("Unsupported stream type: " + effectiveStreamType));
        }
    }

    /**
     * Download DASH streams (separate audio and video) with synchronized completion
     */
    public void downloadDashStreams(@NonNull String youtubeUrl, @NonNull QualityMode qualityMode,
                                   @NonNull OnDashStreamListener listener) {
        if (!isValidYouTubeUrl(youtubeUrl)) {
            listener.onError(new IllegalArgumentException("Invalid YouTube URL provided"));
            return;
        }

        DashStreamCoordinator coordinator = new DashStreamCoordinator(listener);
        
        listener.onProgress("Extracting DASH streams...");
        
        // Extract audio stream
        extractAudioWithCoordinator(youtubeUrl, qualityMode, coordinator);
        
        // Extract video stream
        extractVideoWithCoordinator(youtubeUrl, qualityMode, coordinator);
    }

    /**
     * Extract audio-only stream based on quality preference
     */
    private void extractAudioOnly(@NonNull String youtubeUrl, @NonNull QualityMode qualityMode,
                                 @NonNull OnStreamExtractListener listener) {
        listener.onProgress("Extracting audio stream...");
        
        AudioStreamDownloader.OnAudioExtractListener audioListener = new AudioStreamDownloader.OnAudioExtractListener() {
            @Override
            public void onSuccess(@NonNull String audioUrl, int bitrate, @NonNull String format) {
                listener.onAudioStreamReady(audioUrl, bitrate, format);
            }

            @Override
            public void onError(@NonNull Exception e) {
                listener.onError(e);
            }

            @Override
            public void onProgress(@NonNull String status) {
                listener.onProgress(status);
            }
        };

        switch (qualityMode) {
            case DATA_SAVER:
                audioDownloader.loadDataSaverAudio(youtubeUrl, audioListener);
                break;
            case HIGH_QUALITY:
                audioDownloader.loadHighQualityAudio(youtubeUrl, audioListener);
                break;
            case USER_PREFERENCE:
            case STANDARD:
            default:
                audioDownloader.loadDefaultAudio(youtubeUrl, audioListener);
                break;
        }
    }

    /**
     * Extract video-only stream based on quality preference
     */
    private void extractVideoOnly(@NonNull String youtubeUrl, @NonNull QualityMode qualityMode,
                                 @NonNull OnStreamExtractListener listener) {
        listener.onProgress("Extracting video stream...");
        
        VideoStreamDownloader.OnVideoExtractListener videoListener = new VideoStreamDownloader.OnVideoExtractListener() {
            @Override
            public void onSuccess(@NonNull String videoUrl, @NonNull String resolution, @NonNull String format) {
                listener.onVideoStreamReady(videoUrl, resolution, format);
            }

            @Override
            public void onError(@NonNull Exception e) {
                listener.onError(e);
            }

            @Override
            public void onProgress(@NonNull String status) {
                listener.onProgress(status);
            }
        };

        switch (qualityMode) {
            case DATA_SAVER:
                videoDownloader.loadDataSaverVideo(youtubeUrl, videoListener);
                break;
            case HIGH_QUALITY:
                videoDownloader.loadHighQualityVideo(youtubeUrl, videoListener);
                break;
            case USER_PREFERENCE:
            case STANDARD:
            default:
                videoDownloader.loadDefaultVideo(youtubeUrl, videoListener);
                break;
        }
    }

    /**
     * Extract mixed audio-video stream
     */
    private void extractMixedStream(@NonNull String youtubeUrl, @NonNull QualityMode qualityMode,
                                   @NonNull OnStreamExtractListener listener) {
        listener.onProgress("Extracting mixed stream...");
        
        MixedStreamDownloader.QualityPreference preference = convertQualityMode(qualityMode);
        
        MixedStreamDownloader.OnMixedStreamExtractListener mixedListener = new MixedStreamDownloader.OnMixedStreamExtractListener() {
            @Override
            public void onSuccess(@NonNull String streamUrl, @NonNull String resolution, 
                                 @NonNull String format, @NonNull String quality) {
                listener.onMixedStreamReady(streamUrl, resolution, format, quality);
            }

            @Override
            public void onError(@NonNull Exception error) {
                listener.onError(error);
            }

            @Override
            public void onProgress(@NonNull String status) {
                listener.onProgress(status);
            }
        };

        mixedDownloader.loadMixedStreamWithQuality(youtubeUrl, preference, mixedListener);
    }

    /**
     * Extract both audio and video streams for DASH playback
     */
    private void extractDashStreams(@NonNull String youtubeUrl, @NonNull QualityMode qualityMode,
                                   @NonNull OnStreamExtractListener listener) {
        DashStreamCoordinator coordinator = new DashStreamCoordinator(new OnDashStreamListener() {
            @Override
            public void onStreamsReady(@NonNull String audioUrl, int audioBitrate, @NonNull String audioFormat,
                                      @NonNull String videoUrl, @NonNull String videoResolution, @NonNull String videoFormat) {
                // Notify both streams are ready
                listener.onAudioStreamReady(audioUrl, audioBitrate, audioFormat);
                listener.onVideoStreamReady(videoUrl, videoResolution, videoFormat);
            }

            @Override
            public void onError(@NonNull Exception error) {
                listener.onError(error);
            }

            @Override
            public void onProgress(@NonNull String status) {
                listener.onProgress(status);
            }
        });

        listener.onProgress("Extracting DASH streams...");
        
        extractAudioWithCoordinator(youtubeUrl, qualityMode, coordinator);
        extractVideoWithCoordinator(youtubeUrl, qualityMode, coordinator);
    }

    /**
     * Extract audio stream for DASH coordination
     */
    private void extractAudioWithCoordinator(@NonNull String youtubeUrl, @NonNull QualityMode qualityMode,
                                            @NonNull DashStreamCoordinator coordinator) {
        AudioStreamDownloader.OnAudioExtractListener audioListener = new AudioStreamDownloader.OnAudioExtractListener() {
            @Override
            public void onSuccess(@NonNull String audioUrl, int bitrate, @NonNull String format) {
                coordinator.setAudioStream(audioUrl, bitrate, format);
            }

            @Override
            public void onError(@NonNull Exception e) {
                coordinator.handleError(e);
            }

            @Override
            public void onProgress(@NonNull String status) {
                coordinator.updateProgress("Audio: " + status);
            }
        };

        switch (qualityMode) {
            case DATA_SAVER:
                audioDownloader.loadDataSaverAudio(youtubeUrl, audioListener);
                break;
            case HIGH_QUALITY:
                audioDownloader.loadHighQualityAudio(youtubeUrl, audioListener);
                break;
            case USER_PREFERENCE:
            case STANDARD:
            default:
                audioDownloader.loadDefaultAudio(youtubeUrl, audioListener);
                break;
        }
    }

    /**
     * Extract video stream for DASH coordination
     */
    private void extractVideoWithCoordinator(@NonNull String youtubeUrl, @NonNull QualityMode qualityMode,
                                            @NonNull DashStreamCoordinator coordinator) {
        VideoStreamDownloader.OnVideoExtractListener videoListener = new VideoStreamDownloader.OnVideoExtractListener() {
            @Override
            public void onSuccess(@NonNull String videoUrl, @NonNull String resolution, @NonNull String format) {
                coordinator.setVideoStream(videoUrl, resolution, format);
            }

            @Override
            public void onError(@NonNull Exception e) {
                coordinator.handleError(e);
            }

            @Override
            public void onProgress(@NonNull String status) {
                coordinator.updateProgress("Video: " + status);
            }
        };

        switch (qualityMode) {
            case DATA_SAVER:
                videoDownloader.loadDataSaverVideo(youtubeUrl, videoListener);
                break;
            case HIGH_QUALITY:
                videoDownloader.loadHighQualityVideo(youtubeUrl, videoListener);
                break;
            case USER_PREFERENCE:
            case STANDARD:
            default:
                videoDownloader.loadDefaultVideo(youtubeUrl, videoListener);
                break;
        }
    }

    /**
     * Determine effective stream type based on preferences and availability
     */
    private StreamType determineEffectiveStreamType(@NonNull StreamType requestedType) {
        if (requestedType != StreamType.AUTO_DETECT) {
            return requestedType;
        }

        PrefsHelper.StreamModes modes = PrefsHelper.getModes(context);
        
        // Logic to determine stream type based on user preferences
        // Priority: Mixed streams for compatibility, DASH for quality control
        boolean preferHighQuality = "high".equals(modes.videoMode) || "high".equals(modes.audioMode);

        if (preferHighQuality) {
            return StreamType.DASH_STREAMS; // High quality users get separate streams for better control
        } else {
            return StreamType.MIXED_STREAM; // Default to mixed streams for compatibility
        }
    }

    /**
     * Convert QualityMode to MixedStreamDownloader.QualityPreference
     */
    private MixedStreamDownloader.QualityPreference convertQualityMode(@NonNull QualityMode qualityMode) {
        switch (qualityMode) {
            case DATA_SAVER:
                return MixedStreamDownloader.QualityPreference.LOW_QUALITY;
            case HIGH_QUALITY:
                return MixedStreamDownloader.QualityPreference.HIGH_QUALITY;
            case STANDARD:
                return MixedStreamDownloader.QualityPreference.MEDIUM_QUALITY;
            case USER_PREFERENCE:
            default:
                return MixedStreamDownloader.QualityPreference.AUTO_QUALITY;
        }
    }

    /**
     * Validate YouTube URL format
     */
    private boolean isValidYouTubeUrl(@Nullable String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }
        
        String trimmedUrl = url.trim().toLowerCase();
        return trimmedUrl.contains("youtube.com") || 
               trimmedUrl.contains("youtu.be") ||
               trimmedUrl.contains("m.youtube.com");
    }

    /**
     * Get available stream information for user interface display
     */
    public void getStreamInformation(@NonNull String youtubeUrl, @NonNull OnStreamInfoListener listener) {
        if (!isValidYouTubeUrl(youtubeUrl)) {
            listener.onError(new IllegalArgumentException("Invalid YouTube URL provided"));
            return;
        }

        listener.onProgress("Analyzing available streams...");

        StreamInfoCollector collector = new StreamInfoCollector(listener);
        
        // Collect audio quality information
        audioDownloader.getAvailableQualities(youtubeUrl, new AudioStreamDownloader.OnQualityListListener() {
            @Override
            public void onSuccess(@NonNull java.util.List<String> availableQualities) {
                collector.setAudioQualities(availableQualities);
            }

            @Override
            public void onError(@NonNull Exception e) {
                collector.handleError(e);
            }
        });
    }

    /**
     * Interface for receiving comprehensive stream information
     */
    public interface OnStreamInfoListener {
        void onStreamInfoReady(@NonNull java.util.List<String> audioQualities, 
                              @NonNull java.util.List<String> videoQualities,
                              boolean supportsMixed, boolean supportsDash);
        void onError(@NonNull Exception error);
        default void onProgress(@NonNull String status) {}
    }

    /**
     * Convenience method for audio-only extraction
     */
    public void extractAudioStream(@NonNull String youtubeUrl, @NonNull QualityMode qualityMode,
                                  @NonNull AudioStreamDownloader.OnAudioExtractListener listener) {
        if (!isValidYouTubeUrl(youtubeUrl)) {
            listener.onError(new IllegalArgumentException("Invalid YouTube URL provided"));
            return;
        }

        switch (qualityMode) {
            case DATA_SAVER:
                audioDownloader.loadDataSaverAudio(youtubeUrl, listener);
                break;
            case HIGH_QUALITY:
                audioDownloader.loadHighQualityAudio(youtubeUrl, listener);
                break;
            case USER_PREFERENCE:
            case STANDARD:
            default:
                audioDownloader.loadDefaultAudio(youtubeUrl, listener);
                break;
        }
    }

    /**
     * Convenience method for video-only extraction
     */
    public void extractVideoStream(@NonNull String youtubeUrl, @NonNull QualityMode qualityMode,
                                  @NonNull VideoStreamDownloader.OnVideoExtractListener listener) {
        if (!isValidYouTubeUrl(youtubeUrl)) {
            listener.onError(new IllegalArgumentException("Invalid YouTube URL provided"));
            return;
        }

        switch (qualityMode) {
            case DATA_SAVER:
                videoDownloader.loadDataSaverVideo(youtubeUrl, listener);
                break;
            case HIGH_QUALITY:
                videoDownloader.loadHighQualityVideo(youtubeUrl, listener);
                break;
            case USER_PREFERENCE:
            case STANDARD:
            default:
                videoDownloader.loadDefaultVideo(youtubeUrl, listener);
                break;
        }
    }

    /**
     * Convenience method for mixed stream extraction
     */
    public void extractMixedStream(@NonNull String youtubeUrl, @NonNull QualityMode qualityMode,
                                  @NonNull MixedStreamDownloader.OnMixedStreamExtractListener listener) {
        if (!isValidYouTubeUrl(youtubeUrl)) {
            listener.onError(new IllegalArgumentException("Invalid YouTube URL provided"));
            return;
        }

        MixedStreamDownloader.QualityPreference preference = convertQualityMode(qualityMode);
        mixedDownloader.loadMixedStreamWithQuality(youtubeUrl, preference, listener);
    }

    /**
     * Internal coordinator class for synchronizing DASH stream extraction
     */
    private class DashStreamCoordinator {
        private final OnDashStreamListener listener;
        private String audioUrl;
        private int audioBitrate;
        private String audioFormat;
        private String videoUrl;
        private String videoResolution;
        private String videoFormat;
        private boolean audioReady = false;
        private boolean videoReady = false;
        private boolean errorOccurred = false;

        public DashStreamCoordinator(@NonNull OnDashStreamListener listener) {
            this.listener = listener;
        }

        public synchronized void setAudioStream(@NonNull String url, int bitrate, @NonNull String format) {
            if (errorOccurred) return;
            
            this.audioUrl = url;
            this.audioBitrate = bitrate;
            this.audioFormat = format;
            this.audioReady = true;
            
            checkCompletion();
        }

        public synchronized void setVideoStream(@NonNull String url, @NonNull String resolution, @NonNull String format) {
            if (errorOccurred) return;
            
            this.videoUrl = url;
            this.videoResolution = resolution;
            this.videoFormat = format;
            this.videoReady = true;
            
            checkCompletion();
        }

        public synchronized void handleError(@NonNull Exception error) {
            if (!errorOccurred) {
                errorOccurred = true;
                listener.onError(error);
            }
        }

        public void updateProgress(@NonNull String status) {
            if (!errorOccurred) {
                listener.onProgress(status);
            }
        }

        private void checkCompletion() {
            if (audioReady && videoReady && !errorOccurred) {
                listener.onStreamsReady(audioUrl, audioBitrate, audioFormat, 
                                       videoUrl, videoResolution, videoFormat);
            }
        }
    }

    /**
     * Internal collector class for gathering stream information
     */
    private class StreamInfoCollector {
        private final OnStreamInfoListener listener;
        private java.util.List<String> audioQualities;
        private boolean audioInfoReady = false;
        private boolean errorOccurred = false;

        public StreamInfoCollector(@NonNull OnStreamInfoListener listener) {
            this.listener = listener;
        }

        public synchronized void setAudioQualities(@NonNull java.util.List<String> qualities) {
            if (errorOccurred) return;
            
            this.audioQualities = qualities;
            this.audioInfoReady = true;
            
            // For now, we'll provide basic information
            // In a full implementation, you would also collect video quality information
            java.util.List<String> videoQualities = java.util.Arrays.asList("720p", "480p", "360p", "240p", "144p");
            
            listener.onStreamInfoReady(audioQualities, videoQualities, true, true);
        }

        public synchronized void handleError(@NonNull Exception error) {
            if (!errorOccurred) {
                errorOccurred = true;
                listener.onError(error);
            }
        }
    }

    /**
     * Clean up all resources and shutdown thread pools
     */
    public void shutdown() {
        AudioStreamDownloader.shutdown();
        VideoStreamDownloader.shutdown();
        MixedStreamDownloader.shutdown();
    }

    /**
     * Static method for global cleanup
     */
    public static void shutdownAll() {
        AudioStreamDownloader.shutdown();
        VideoStreamDownloader.shutdown();
        MixedStreamDownloader.shutdown();
    }
}