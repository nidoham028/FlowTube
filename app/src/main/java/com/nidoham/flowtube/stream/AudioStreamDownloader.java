package com.nidoham.flowtube.stream;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.nidoham.flowtube.stream.prefs.PrefsHelper;
import java.util.ArrayList;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AudioStreamDownloader
 * ----------------------
 * Enhanced audio stream loader with optimized performance, intelligent bitrate selection,
 * and comprehensive error handling for YouTube content extraction.
 * 
 * Features:
 * - Advanced bitrate selection algorithms
 * - Thread-safe operations with proper callback handling
 * - Memory-efficient stream processing
 * - Robust error handling and input validation
 * - Configurable quality preferences
 */
public class AudioStreamDownloader {

    private static final String TAG = "AudioStreamDownloader";
    private static final int YOUTUBE_SERVICE_ID = 0;
    
    // Thread management
    private static ExecutorService sExecutorService;
    private final Handler mainHandler;
    private final Context context;

    // Bitrate quality thresholds
    private static final int HIGH_QUALITY_MIN_BITRATE = 128;
    private static final int LOW_QUALITY_MAX_BITRATE = 96;
    private static final int FALLBACK_BITRATE = 64;

    /**
     * Enhanced listener interface for audio extraction results
     */
    public interface OnAudioExtractListener {
        void onSuccess(@NonNull String audioUrl, int bitrate, @NonNull String format);
        void onError(@NonNull Exception e);
        
        // Optional progress callback for extraction status updates
        default void onProgress(@NonNull String status) {}
    }

    /**
     * Data class representing extracted audio stream information
     */
    public static class AudioStreamInfo {
        public final String url;
        public final int bitrate;
        public final String format;
        public final boolean isAdaptive;

        public AudioStreamInfo(@NonNull String url, int bitrate, 
                             @NonNull String format, boolean isAdaptive) {
            this.url = url;
            this.bitrate = bitrate;
            this.format = format;
            this.isAdaptive = isAdaptive;
        }

        public boolean isHighQuality() {
            return bitrate >= HIGH_QUALITY_MIN_BITRATE;
        }

        public boolean isLowQuality() {
            return bitrate <= LOW_QUALITY_MAX_BITRATE;
        }
    }

    public AudioStreamDownloader(@NonNull Context context) {
        this.context = context.getApplicationContext(); // Prevent memory leaks
        this.mainHandler = new Handler(Looper.getMainLooper());
        initializeExecutorService();
    }

    /**
     * Initialize thread pool for background operations with controlled concurrency
     */
    private static synchronized void initializeExecutorService() {
        if (sExecutorService == null || sExecutorService.isShutdown()) {
            sExecutorService = Executors.newFixedThreadPool(2); // Limit concurrent extractions
        }
    }

    /**
     * Load audio stream based on user preferences from SharedPreferences
     */
    public void loadDefaultAudio(@NonNull String youtubeUrl, @NonNull OnAudioExtractListener listener) {
        if (!isValidYouTubeUrl(youtubeUrl)) {
            notifyError(listener, new IllegalArgumentException("Invalid YouTube URL provided"));
            return;
        }

        PrefsHelper.StreamModes modes = PrefsHelper.getModes(context);
        boolean isHighQuality = "high".equals(modes.audioMode);
        
        extractAudioStream(youtubeUrl, isHighQuality, listener);
    }

    /**
     * Load data-saving low quality audio stream optimized for bandwidth conservation
     */
    public void loadDataSaverAudio(@NonNull String youtubeUrl, @NonNull OnAudioExtractListener listener) {
        if (!isValidYouTubeUrl(youtubeUrl)) {
            notifyError(listener, new IllegalArgumentException("Invalid YouTube URL provided"));
            return;
        }
        extractAudioStream(youtubeUrl, false, listener);
    }

    /**
     * Load high quality audio stream for optimal listening experience
     */
    public void loadHighQualityAudio(@NonNull String youtubeUrl, @NonNull OnAudioExtractListener listener) {
        if (!isValidYouTubeUrl(youtubeUrl)) {
            notifyError(listener, new IllegalArgumentException("Invalid YouTube URL provided"));
            return;
        }
        extractAudioStream(youtubeUrl, true, listener);
    }

    /**
     * Enhanced core extraction method with intelligent stream selection
     */
    private void extractAudioStream(@NonNull String youtubeUrl, boolean highQuality, 
                                  @NonNull OnAudioExtractListener listener) {
        
        sExecutorService.execute(() -> {
            try {
                notifyProgress(listener, "Initializing audio extraction...");
                
                StreamingService service = NewPipe.getService(YOUTUBE_SERVICE_ID);
                
                notifyProgress(listener, "Fetching stream information...");
                StreamInfo streamInfo = StreamInfo.getInfo(service, youtubeUrl);

                List<AudioStream> audioStreams = streamInfo.getAudioStreams();
                if (audioStreams == null || audioStreams.isEmpty()) {
                    notifyError(listener, new Exception("No audio streams available for this content"));
                    return;
                }

                notifyProgress(listener, "Selecting optimal audio quality...");
                AudioStreamInfo selectedStreamInfo = selectOptimalAudioStream(audioStreams, highQuality);
                
                if (selectedStreamInfo == null) {
                    notifyError(listener, new Exception("Unable to find suitable audio stream"));
                    return;
                }

                Log.d(TAG, String.format("Selected audio stream: %d kbps (%s, %s quality)", 
                    selectedStreamInfo.bitrate, selectedStreamInfo.format, 
                    highQuality ? "high" : "low"));

                notifySuccess(listener, selectedStreamInfo);

            } catch (Exception e) {
                Log.e(TAG, "Error extracting audio stream from: " + youtubeUrl, e);
                notifyError(listener, e);
            }
        });
    }

    /**
     * Advanced audio stream selection with bitrate-based quality matching
     */
    @Nullable
    private AudioStreamInfo selectOptimalAudioStream(@NonNull List<AudioStream> audioStreams, boolean highQuality) {
        if (audioStreams.isEmpty()) {
            return null;
        }

        // Create mutable copy for sorting
        List<AudioStream> sortedStreams = new ArrayList<>(audioStreams);
        
        // Sort streams by bitrate in descending order
        Collections.sort(sortedStreams, new Comparator<AudioStream>() {
            @Override
            public int compare(AudioStream stream1, AudioStream stream2) {
                int bitrate1 = getBitrateValue(stream1);
                int bitrate2 = getBitrateValue(stream2);
                return Integer.compare(bitrate2, bitrate1); // Descending order
            }
        });

        AudioStream selectedStream = null;

        if (highQuality) {
            // High quality: Find stream with bitrate >= HIGH_QUALITY_MIN_BITRATE
            for (AudioStream stream : sortedStreams) {
                if (isValidStream(stream) && getBitrateValue(stream) >= HIGH_QUALITY_MIN_BITRATE) {
                    selectedStream = stream;
                    break;
                }
            }
            
            // Fallback to highest available bitrate if no high-quality stream found
            if (selectedStream == null) {
                for (AudioStream stream : sortedStreams) {
                    if (isValidStream(stream)) {
                        selectedStream = stream;
                        break;
                    }
                }
            }
        } else {
            // Low quality: Find stream with bitrate <= LOW_QUALITY_MAX_BITRATE
            for (int i = sortedStreams.size() - 1; i >= 0; i--) {
                AudioStream stream = sortedStreams.get(i);
                if (isValidStream(stream) && getBitrateValue(stream) <= LOW_QUALITY_MAX_BITRATE) {
                    selectedStream = stream;
                    break;
                }
            }
            
            // Fallback to lowest available bitrate if no low-quality stream found
            if (selectedStream == null) {
                for (int i = sortedStreams.size() - 1; i >= 0; i--) {
                    AudioStream stream = sortedStreams.get(i);
                    if (isValidStream(stream)) {
                        selectedStream = stream;
                        break;
                    }
                }
            }
        }

        if (selectedStream == null) {
            return null;
        }

        return new AudioStreamInfo(
            selectedStream.getUrl(),
            getBitrateValue(selectedStream),
            getFormatString(selectedStream),
            isAdaptiveStream(selectedStream)
        );
    }

    /**
     * Extract bitrate value from AudioStream with fallback handling
     */
    private int getBitrateValue(@NonNull AudioStream stream) {
        try {
            int bitrate = stream.getAverageBitrate();
            return bitrate > 0 ? bitrate : FALLBACK_BITRATE;
        } catch (Exception e) {
            Log.w(TAG, "Unable to get bitrate for stream, using fallback", e);
            return FALLBACK_BITRATE;
        }
    }

    /**
     * Get format string with safe fallback handling
     */
    @NonNull
    private String getFormatString(@NonNull AudioStream stream) {
        try {
            return stream.getFormat() != null ? stream.getFormat().getMimeType() : "audio/mp4";
        } catch (Exception e) {
            Log.w(TAG, "Unable to get format for stream, using fallback", e);
            return "audio/mp4";
        }
    }

    /**
     * Check if stream is adaptive with safe error handling
     */
    private boolean isAdaptiveStream(@NonNull AudioStream stream) {
        try {
            // AudioStream doesn't have isVideoOnly() method
            // Return false as audio streams are typically not adaptive in the same way as video
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Validate audio stream availability and accessibility
     */
    private boolean isValidStream(@Nullable AudioStream stream) {
        return stream != null && 
               stream.getUrl() != null && 
               !stream.getUrl().isEmpty() &&
               getBitrateValue(stream) > 0;
    }

    /**
     * Validate YouTube URL format with comprehensive checking
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
     * Thread-safe success notification on main thread
     */
    private void notifySuccess(@NonNull OnAudioExtractListener listener, @NonNull AudioStreamInfo streamInfo) {
        mainHandler.post(() -> listener.onSuccess(streamInfo.url, streamInfo.bitrate, streamInfo.format));
    }

    /**
     * Thread-safe error notification on main thread
     */
    private void notifyError(@NonNull OnAudioExtractListener listener, @NonNull Exception error) {
        mainHandler.post(() -> listener.onError(error));
    }

    /**
     * Thread-safe progress notification on main thread
     */
    private void notifyProgress(@NonNull OnAudioExtractListener listener, @NonNull String status) {
        mainHandler.post(() -> listener.onProgress(status));
    }

    /**
     * Get available audio quality options for user interface display
     */
    public void getAvailableQualities(@NonNull String youtubeUrl, @NonNull OnQualityListListener listener) {
        sExecutorService.execute(() -> {
            try {
                StreamingService service = NewPipe.getService(YOUTUBE_SERVICE_ID);
                StreamInfo streamInfo = StreamInfo.getInfo(service, youtubeUrl);
                
                List<AudioStream> audioStreams = streamInfo.getAudioStreams();
                if (audioStreams == null || audioStreams.isEmpty()) {
                    mainHandler.post(() -> listener.onError(new Exception("No audio streams available")));
                    return;
                }

                List<String> qualities = new ArrayList<>();
                for (AudioStream stream : audioStreams) {
                    if (isValidStream(stream)) {
                        String quality = getBitrateValue(stream) + " kbps";
                        if (!qualities.contains(quality)) {
                            qualities.add(quality);
                        }
                    }
                }

                mainHandler.post(() -> listener.onSuccess(qualities));

            } catch (Exception e) {
                mainHandler.post(() -> listener.onError(e));
            }
        });
    }

    /**
     * Interface for receiving available quality information
     */
    public interface OnQualityListListener {
        void onSuccess(@NonNull List<String> availableQualities);
        void onError(@NonNull Exception e);
    }

    /**
     * Clean up resources when downloader is no longer needed
     */
    public static void shutdown() {
        if (sExecutorService != null && !sExecutorService.isShutdown()) {
            sExecutorService.shutdown();
        }
    }
}