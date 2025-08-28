package com.nidoham.flowtube.stream.extractor;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;

import com.nidoham.flowtube.stream.until.StreamDownloader;
import com.nidoham.flowtube.stream.until.StreamDownloader.QualityMode;
import com.nidoham.flowtube.stream.until.StreamDownloader.OnDashStreamListener;

/**
 * AudioVideoExtractor
 * -------------------
 * Streamlined extractor class that retrieves direct audio and video URLs from YouTube content
 * using DASH stream extraction. This implementation provides a simplified interface with
 * consolidated callback mechanism for efficient URL retrieval operations.
 */
public class AudioVideoExtractor {

    private static final String TAG = "AudioVideoExtractor";
    
    private final StreamDownloader streamDownloader;

    /**
     * Unified callback interface for audio and video URL extraction
     */
    public interface OnExtractionListener {
        void onVideoUrlReady(@NonNull String videoUrl);
        void onAudioUrlReady(@NonNull String audioUrl);
        void onExtractionError(@NonNull String error);
    }

    public AudioVideoExtractor(@NonNull Context context) {
        this.streamDownloader = new StreamDownloader(context);
    }

    /**
     * Extract audio and video URLs from YouTube content with user preference quality settings
     * 
     * @param youtubeUrl The YouTube URL to process for stream extraction
     * @param listener Callback interface for receiving extraction results
     */
    public void extractUrls(@NonNull String youtubeUrl, @NonNull OnExtractionListener listener) {
        extractUrlsWithQuality(youtubeUrl, QualityMode.USER_PREFERENCE, listener);
    }

    /**
     * Extract audio and video URLs with specified quality configuration
     * 
     * @param youtubeUrl The YouTube URL to process for stream extraction
     * @param qualityMode Quality preference for stream selection
     * @param listener Callback interface for receiving extraction results
     */
    public void extractUrlsWithQuality(@NonNull String youtubeUrl, @NonNull QualityMode qualityMode, 
                                      @NonNull OnExtractionListener listener) {
        if (!isValidYouTubeUrl(youtubeUrl)) {
            listener.onExtractionError("Invalid YouTube URL provided");
            return;
        }

        streamDownloader.downloadDashStreams(youtubeUrl, qualityMode, new OnDashStreamListener() {
            @Override
            public void onStreamsReady(@NonNull String audioUrl, int audioBitrate, @NonNull String audioFormat,
                                      @NonNull String videoUrl, @NonNull String videoResolution, @NonNull String videoFormat) {
                listener.onVideoUrlReady(videoUrl);
                listener.onAudioUrlReady(audioUrl);
            }

            @Override
            public void onError(@NonNull Exception error) {
                Log.e(TAG, "Stream extraction failed for URL: " + youtubeUrl, error);
                listener.onExtractionError(error.getMessage());
            }
        });
    }

    /**
     * Validate YouTube URL format for extraction compatibility
     */
    private boolean isValidYouTubeUrl(@NonNull String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }
        
        String trimmedUrl = url.trim().toLowerCase();
        return trimmedUrl.contains("youtube.com") || 
               trimmedUrl.contains("youtu.be") ||
               trimmedUrl.contains("m.youtube.com");
    }

    /**
     * Release StreamDownloader resources and perform cleanup operations
     */
    public void cleanup() {
        if (streamDownloader != null) {
            streamDownloader.shutdown();
        }
    }
}