package com.nidoham.flowtube.stream;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.nidoham.flowtube.stream.prefs.PrefsHelper;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * MixedStreamDownloader
 * --------------------
 * Specialized stream downloader for retrieving mixed audio-video content streams.
 * This implementation focuses on non-DASH streams that contain both audio and video
 * components in a single container format, providing optimal compatibility and
 * simplified playback for applications requiring unified media streams.
 * 
 * Key Features:
 * - Prioritizes mixed audio-video streams over separate DASH components
 * - Quality-aware selection with fallback mechanisms
 * - Thread-safe operations with comprehensive error handling
 * - Memory-efficient processing with proper resource management
 */
public class MixedStreamDownloader {

    private static final String TAG = "MixedStreamDownloader";
    private static final int YOUTUBE_SERVICE_ID = 0;
    
    // Thread management
    private static ExecutorService sExecutorService;
    private final Handler mainHandler;
    private final Context context;

    /**
     * Listener interface for mixed stream extraction results
     */
    public interface OnMixedStreamExtractListener {
        void onSuccess(@NonNull String streamUrl, @NonNull String resolution, 
                      @NonNull String format, @NonNull String quality);
        void onError(@NonNull Exception error);
        
        // Optional progress notifications
        default void onProgress(@NonNull String status) {}
    }

    /**
     * Data class encapsulating mixed stream information
     */
    public static class MixedStreamInfo {
        public final String url;
        public final String resolution;
        public final String format;
        public final String mimeType;
        public final int resolutionValue;
        public final boolean hasAudio;
        public final boolean hasVideo;

        public MixedStreamInfo(@NonNull String url, @NonNull String resolution, 
                              @NonNull String format, @NonNull String mimeType,
                              int resolutionValue, boolean hasAudio, boolean hasVideo) {
            this.url = url;
            this.resolution = resolution;
            this.format = format;
            this.mimeType = mimeType;
            this.resolutionValue = resolutionValue;
            this.hasAudio = hasAudio;
            this.hasVideo = hasVideo;
        }
    }

    /**
     * Quality preference enumeration for mixed streams
     */
    public enum QualityPreference {
        LOW_QUALITY,        // Targets 360p, fallback to 240p, 144p
        MEDIUM_QUALITY,     // Targets 480p, fallback to 360p or 720p
        HIGH_QUALITY,       // Targets 720p, fallback to highest available
        AUTO_QUALITY        // Uses user preferences from settings
    }

    public MixedStreamDownloader(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
        initializeExecutorService();
    }

    /**
     * Initialize shared thread pool for background stream processing
     */
    private static synchronized void initializeExecutorService() {
        if (sExecutorService == null || sExecutorService.isShutdown()) {
            sExecutorService = Executors.newFixedThreadPool(2);
        }
    }

    /**
     * Retrieve mixed stream using automatic quality detection based on user preferences
     */
    public void loadMixedStream(@NonNull String youtubeUrl, 
                               @NonNull OnMixedStreamExtractListener listener) {
        loadMixedStreamWithQuality(youtubeUrl, QualityPreference.AUTO_QUALITY, listener);
    }

    /**
     * Retrieve mixed stream with specific quality preference
     */
    public void loadMixedStreamWithQuality(@NonNull String youtubeUrl, 
                                          @NonNull QualityPreference qualityPreference,
                                          @NonNull OnMixedStreamExtractListener listener) {
        if (!isValidYouTubeUrl(youtubeUrl)) {
            notifyError(listener, new IllegalArgumentException("Invalid YouTube URL format provided"));
            return;
        }

        sExecutorService.execute(() -> {
            try {
                notifyProgress(listener, "Initializing mixed stream extraction");
                
                StreamingService service = NewPipe.getService(YOUTUBE_SERVICE_ID);
                
                notifyProgress(listener, "Retrieving stream metadata");
                StreamInfo streamInfo = StreamInfo.getInfo(service, youtubeUrl);

                List<VideoStream> videoStreams = streamInfo.getVideoStreams();
                if (videoStreams == null || videoStreams.isEmpty()) {
                    notifyError(listener, new Exception("No mixed streams available for this content"));
                    return;
                }

                notifyProgress(listener, "Analyzing available mixed streams");
                MixedStreamInfo selectedStreamInfo = selectMixedStream(videoStreams, qualityPreference);
                
                if (selectedStreamInfo == null) {
                    notifyError(listener, new Exception("No suitable mixed stream found"));
                    return;
                }

                Log.d(TAG, String.format("Selected mixed stream: %s (%s) - Audio: %s, Video: %s", 
                    selectedStreamInfo.resolution, selectedStreamInfo.format,
                    selectedStreamInfo.hasAudio, selectedStreamInfo.hasVideo));

                notifySuccess(listener, selectedStreamInfo);

            } catch (Exception e) {
                Log.e(TAG, "Error extracting mixed stream from: " + youtubeUrl, e);
                notifyError(listener, e);
            }
        });
    }

    /**
     * Core mixed stream selection algorithm with quality-based filtering
     */
    @Nullable
    private MixedStreamInfo selectMixedStream(@NonNull List<VideoStream> videoStreams, 
                                             @NonNull QualityPreference qualityPreference) {
        if (videoStreams.isEmpty()) {
            return null;
        }

        // Filter streams to include only mixed audio-video content (non-DASH)
        List<VideoStream> mixedStreams = filterMixedStreams(videoStreams);
        
        if (mixedStreams.isEmpty()) {
            Log.w(TAG, "No mixed streams found, falling back to all available streams");
            mixedStreams = videoStreams;
        }

        VideoStream selectedStream = null;

        // Determine quality preference
        QualityPreference effectivePreference = qualityPreference;
        if (qualityPreference == QualityPreference.AUTO_QUALITY) {
            effectivePreference = determineQualityFromPreferences();
        }

        // Select stream based on quality preference
        switch (effectivePreference) {
            case LOW_QUALITY:
                selectedStream = selectLowQualityMixedStream(mixedStreams);
                break;
            case MEDIUM_QUALITY:
                selectedStream = selectMediumQualityMixedStream(mixedStreams);
                break;
            case HIGH_QUALITY:
                selectedStream = selectHighQualityMixedStream(mixedStreams);
                break;
        }

        // Final fallback to any available mixed stream
        if (selectedStream == null) {
            selectedStream = selectAnyValidMixedStream(mixedStreams);
        }

        if (selectedStream == null) {
            return null;
        }

        return createMixedStreamInfo(selectedStream);
    }

    /**
     * Filter video streams to identify mixed audio-video content
     */
    @NonNull
    private List<VideoStream> filterMixedStreams(@NonNull List<VideoStream> videoStreams) {
        return videoStreams.stream()
            .filter(stream -> stream.getUrl() != null && !stream.getUrl().isEmpty())
            .filter(stream -> !stream.isVideoOnly()) // Exclude video-only DASH streams
            .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Select low quality mixed stream with preference hierarchy
     */
    @Nullable
    private VideoStream selectLowQualityMixedStream(@NonNull List<VideoStream> mixedStreams) {
        int[] preferredResolutions = {360, 240, 144};
        
        for (int targetResolution : preferredResolutions) {
            VideoStream stream = findMixedStreamByResolution(mixedStreams, targetResolution);
            if (stream != null) {
                return stream;
            }
        }
        
        return findLowestQualityMixedStream(mixedStreams);
    }

    /**
     * Select medium quality mixed stream with balanced approach
     */
    @Nullable
    private VideoStream selectMediumQualityMixedStream(@NonNull List<VideoStream> mixedStreams) {
        // Primary target: 480p
        VideoStream stream = findMixedStreamByResolution(mixedStreams, 480);
        if (stream != null) {
            return stream;
        }
        
        // Fallback options: 360p or 720p
        stream = findMixedStreamByResolution(mixedStreams, 360);
        if (stream != null) {
            return stream;
        }
        
        stream = findMixedStreamByResolution(mixedStreams, 720);
        if (stream != null) {
            return stream;
        }
        
        return findClosestQualityMixedStream(mixedStreams, 480);
    }

    /**
     * Select high quality mixed stream
     */
    @Nullable
    private VideoStream selectHighQualityMixedStream(@NonNull List<VideoStream> mixedStreams) {
        // Primary target: 720p
        VideoStream stream = findMixedStreamByResolution(mixedStreams, 720);
        if (stream != null) {
            return stream;
        }
        
        return findHighestQualityMixedStream(mixedStreams);
    }

    /**
     * Find mixed stream with specific resolution
     */
    @Nullable
    private VideoStream findMixedStreamByResolution(@NonNull List<VideoStream> mixedStreams, 
                                                   int targetResolution) {
        for (VideoStream stream : mixedStreams) {
            int streamResolution = parseResolutionValue(stream.getResolution());
            if (streamResolution == targetResolution) {
                return stream;
            }
        }
        return null;
    }

    /**
     * Find highest quality mixed stream
     */
    @Nullable
    private VideoStream findHighestQualityMixedStream(@NonNull List<VideoStream> mixedStreams) {
        VideoStream bestStream = null;
        int highestResolution = 0;

        for (VideoStream stream : mixedStreams) {
            int currentResolution = parseResolutionValue(stream.getResolution());
            if (currentResolution > highestResolution) {
                bestStream = stream;
                highestResolution = currentResolution;
            }
        }

        return bestStream;
    }

    /**
     * Find lowest quality mixed stream
     */
    @Nullable
    private VideoStream findLowestQualityMixedStream(@NonNull List<VideoStream> mixedStreams) {
        VideoStream bestStream = null;
        int lowestResolution = Integer.MAX_VALUE;

        for (VideoStream stream : mixedStreams) {
            int currentResolution = parseResolutionValue(stream.getResolution());
            if (currentResolution > 0 && currentResolution < lowestResolution) {
                bestStream = stream;
                lowestResolution = currentResolution;
            }
        }

        return bestStream;
    }

    /**
     * Find mixed stream closest to target resolution
     */
    @Nullable
    private VideoStream findClosestQualityMixedStream(@NonNull List<VideoStream> mixedStreams, 
                                                     int targetResolution) {
        VideoStream bestStream = null;
        int smallestDifference = Integer.MAX_VALUE;

        for (VideoStream stream : mixedStreams) {
            int currentResolution = parseResolutionValue(stream.getResolution());
            if (currentResolution > 0) {
                int difference = Math.abs(currentResolution - targetResolution);
                if (difference < smallestDifference) {
                    bestStream = stream;
                    smallestDifference = difference;
                }
            }
        }

        return bestStream;
    }

    /**
     * Select any valid mixed stream as final fallback
     */
    @Nullable
    private VideoStream selectAnyValidMixedStream(@NonNull List<VideoStream> mixedStreams) {
        for (VideoStream stream : mixedStreams) {
            if (stream.getUrl() != null && !stream.getUrl().isEmpty()) {
                return stream;
            }
        }
        return null;
    }

    /**
     * Create MixedStreamInfo object from VideoStream
     */
    @NonNull
    private MixedStreamInfo createMixedStreamInfo(@NonNull VideoStream stream) {
        String resolution = stream.getResolution() != null ? stream.getResolution() : "unknown";
        String format = stream.getFormat() != null ? stream.getFormat().getName() : "mp4";
        String mimeType = stream.getFormat() != null ? stream.getFormat().getMimeType() : "video/mp4";
        int resolutionValue = parseResolutionValue(resolution);
        boolean hasAudio = !stream.isVideoOnly();
        boolean hasVideo = true; // VideoStream always contains video

        return new MixedStreamInfo(stream.getUrl(), resolution, format, mimeType,
                                  resolutionValue, hasAudio, hasVideo);
    }

    /**
     * Determine quality preference from application settings
     */
    private QualityPreference determineQualityFromPreferences() {
        PrefsHelper.StreamModes modes = PrefsHelper.getModes(context);
        
        if ("high".equals(modes.videoMode)) {
            return QualityPreference.HIGH_QUALITY;
        } else if ("medium".equals(modes.videoMode)) {
            return QualityPreference.MEDIUM_QUALITY;
        } else {
            return QualityPreference.LOW_QUALITY;
        }
    }

    /**
     * Parse resolution string to extract numeric value
     */
    private int parseResolutionValue(@Nullable String resolution) {
        if (resolution == null || resolution.trim().isEmpty()) {
            return 0;
        }

        try {
            String[] parts = resolution.toLowerCase().split("p");
            if (parts.length > 0) {
                String resolutionPart = parts[0].replaceAll("[^0-9]", "");
                if (!resolutionPart.isEmpty()) {
                    return Integer.parseInt(resolutionPart);
                }
            }
            return 0;
        } catch (NumberFormatException e) {
            Log.w(TAG, "Failed to parse resolution: " + resolution, e);
            return 0;
        }
    }

    /**
     * Validate YouTube URL format
     */
    private boolean isValidYouTubeUrl(@Nullable String url) {
        return url != null && 
               !url.trim().isEmpty() && 
               (url.contains("youtube.com") || url.contains("youtu.be"));
    }

    /**
     * Thread-safe success notification
     */
    private void notifySuccess(@NonNull OnMixedStreamExtractListener listener, 
                              @NonNull MixedStreamInfo streamInfo) {
        mainHandler.post(() -> listener.onSuccess(
            streamInfo.url, 
            streamInfo.resolution, 
            streamInfo.format,
            streamInfo.hasAudio && streamInfo.hasVideo ? "mixed" : "partial"
        ));
    }

    /**
     * Thread-safe error notification
     */
    private void notifyError(@NonNull OnMixedStreamExtractListener listener, 
                           @NonNull Exception error) {
        mainHandler.post(() -> listener.onError(error));
    }

    /**
     * Thread-safe progress notification
     */
    private void notifyProgress(@NonNull OnMixedStreamExtractListener listener, 
                              @NonNull String status) {
        mainHandler.post(() -> listener.onProgress(status));
    }

    /**
     * Release resources and shutdown thread pool
     */
    public static void shutdown() {
        if (sExecutorService != null && !sExecutorService.isShutdown()) {
            sExecutorService.shutdown();
        }
    }
}