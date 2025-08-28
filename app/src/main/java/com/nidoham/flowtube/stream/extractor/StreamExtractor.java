package com.nidoham.flowtube.stream.extractor;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.nidoham.flowtube.stream.InfoStreamDownloader;
import com.nidoham.flowtube.stream.until.StreamDownloader.QualityMode;

import org.schabi.newpipe.extractor.stream.StreamInfo;

/**
 * StreamExtractor
 * ---------------
 * Comprehensive extraction utility that consolidates audio, video, and metadata extraction
 * from YouTube content into a unified interface. This class integrates InfoStreamDownloader
 * and AudioVideoExtractor to provide complete stream processing capabilities through a
 * single, cohesive callback mechanism.
 * 
 * This implementation provides efficient resource management and robust error handling
 * while maintaining separation of concerns between different extraction operations.
 */
public class StreamExtractor {

    private static final String TAG = "StreamExtractor";
    
    private final AudioVideoExtractor audioVideoExtractor;
    private final Context context;

    /**
     * Comprehensive callback interface for unified stream extraction operations
     */
    public interface OnStreamExtractionListener {
        /**
         * Called when video stream URL is successfully extracted
         * @param videoUrl The direct video stream URL
         */
        void onVideoReady(@NonNull String videoUrl);

        /**
         * Called when audio stream URL is successfully extracted  
         * @param audioUrl The direct audio stream URL
         */
        void onAudioReady(@NonNull String audioUrl);

        /**
         * Called when video information metadata is successfully extracted
         * @param streamInfo Complete video information including title, description, duration, etc.
         */
        void onInformationReady(@NonNull StreamInfo streamInfo);

        /**
         * Called when any extraction operation encounters an error
         * @param error The exception that occurred during extraction
         * @param operationType Description of which operation failed (e.g., "Video URL", "Audio URL", "Information")
         */
        void onExtractionError(@NonNull Exception error, @NonNull String operationType);
    }

    /**
     * Initialize StreamExtractor with application context
     * @param context Application context for resource access and initialization
     */
    public StreamExtractor(@NonNull Context context) {
        this.context = context;
        this.audioVideoExtractor = new AudioVideoExtractor(context);
    }

    /**
     * Execute comprehensive stream extraction with default quality preferences
     * This method performs parallel extraction of video URLs, audio URLs, and metadata information
     * 
     * @param youtubeUrl The YouTube URL to process for complete extraction
     * @param listener Callback interface for receiving all extraction results and errors
     */
    public void extractAll(@NonNull String youtubeUrl, @NonNull OnStreamExtractionListener listener) {
        extractAllWithQuality(youtubeUrl, QualityMode.USER_PREFERENCE, listener);
    }

    /**
     * Execute comprehensive stream extraction with specified quality configuration
     * This method coordinates multiple extraction operations while maintaining consistent error handling
     * 
     * @param youtubeUrl The YouTube URL to process for complete extraction
     * @param qualityMode Quality preference for audio and video stream selection
     * @param listener Callback interface for receiving all extraction results and errors
     */
    public void extractAllWithQuality(@NonNull String youtubeUrl, @NonNull QualityMode qualityMode, 
                                    @NonNull OnStreamExtractionListener listener) {
        if (!isValidYouTubeUrl(youtubeUrl)) {
            listener.onExtractionError(new IllegalArgumentException("Invalid YouTube URL format"), "URL Validation");
            return;
        }

        // Extract stream information metadata
        extractStreamInformation(youtubeUrl, listener);
        
        // Extract audio and video URLs with specified quality
        extractAudioVideoUrls(youtubeUrl, qualityMode, listener);
    }

    /**
     * Extract only video stream information without audio/video URLs
     * 
     * @param youtubeUrl The YouTube URL to process for information extraction
     * @param listener Callback interface for receiving information results
     */
    public void extractInformationOnly(@NonNull String youtubeUrl, @NonNull OnStreamExtractionListener listener) {
        if (!isValidYouTubeUrl(youtubeUrl)) {
            listener.onExtractionError(new IllegalArgumentException("Invalid YouTube URL format"), "URL Validation");
            return;
        }

        extractStreamInformation(youtubeUrl, listener);
    }

    /**
     * Extract only audio and video URLs without metadata information
     * 
     * @param youtubeUrl The YouTube URL to process for stream URL extraction
     * @param qualityMode Quality preference for stream selection
     * @param listener Callback interface for receiving stream URL results
     */
    public void extractUrlsOnly(@NonNull String youtubeUrl, @NonNull QualityMode qualityMode, 
                               @NonNull OnStreamExtractionListener listener) {
        if (!isValidYouTubeUrl(youtubeUrl)) {
            listener.onExtractionError(new IllegalArgumentException("Invalid YouTube URL format"), "URL Validation");
            return;
        }

        extractAudioVideoUrls(youtubeUrl, qualityMode, listener);
    }

    /**
     * Internal method for stream information extraction using InfoStreamDownloader
     */
    private void extractStreamInformation(@NonNull String youtubeUrl, @NonNull OnStreamExtractionListener listener) {
        InfoStreamDownloader.getStreamInfo(youtubeUrl, new InfoStreamDownloader.StreamInfoCallback() {
            @Override
            public void onSuccess(StreamInfo info) {
                try {
                    listener.onInformationReady(info);
                } catch (Exception e) {
                    Log.e(TAG, "Error processing StreamInfo callback", e);
                    listener.onExtractionError(e, "Information Processing");
                }
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Information extraction failed for URL: " + youtubeUrl, e);
                listener.onExtractionError(e, "Information Extraction");
            }
        });
    }

    /**
     * Internal method for audio and video URL extraction using AudioVideoExtractor
     */
    private void extractAudioVideoUrls(@NonNull String youtubeUrl, @NonNull QualityMode qualityMode, 
                                     @NonNull OnStreamExtractionListener listener) {
        audioVideoExtractor.extractUrlsWithQuality(youtubeUrl, qualityMode, new AudioVideoExtractor.OnExtractionListener() {
            @Override
            public void onVideoUrlReady(@NonNull String videoUrl) {
                try {
                    listener.onVideoReady(videoUrl);
                } catch (Exception e) {
                    Log.e(TAG, "Error processing video URL callback", e);
                    listener.onExtractionError(e, "Video URL Processing");
                }
            }

            @Override
            public void onAudioUrlReady(@NonNull String audioUrl) {
                try {
                    listener.onAudioReady(audioUrl);
                } catch (Exception e) {
                    Log.e(TAG, "Error processing audio URL callback", e);
                    listener.onExtractionError(e, "Audio URL Processing");
                }
            }

            @Override
            public void onExtractionError(@NonNull String error) {
                listener.onExtractionError(new RuntimeException(error), "Stream URL Extraction");
            }
        });
    }

    /**
     * Validate YouTube URL format for extraction compatibility
     * This method provides comprehensive URL validation to ensure extraction success
     */
    private boolean isValidYouTubeUrl(@Nullable String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }
        
        try {
            String normalizedUrl = url.trim().toLowerCase();
            return normalizedUrl.contains("youtube.com/watch") || 
                   normalizedUrl.contains("youtu.be/") || 
                   normalizedUrl.contains("youtube.com/embed/") ||
                   normalizedUrl.contains("m.youtube.com/watch");
        } catch (Exception e) {
            Log.w(TAG, "URL validation error for: " + url, e);
            return false;
        }
    }

    /**
     * Release all resources and perform cleanup operations
     * This method ensures proper resource management and prevents memory leaks
     */
    public void cleanup() {
        try {
            if (audioVideoExtractor != null) {
                audioVideoExtractor.cleanup();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error during cleanup operations", e);
        }
    }

    /**
     * Check if the extractor has been properly initialized and is ready for operations
     * @return true if the extractor is ready for use, false otherwise
     */
    public boolean isReady() {
        return context != null && audioVideoExtractor != null;
    }
}