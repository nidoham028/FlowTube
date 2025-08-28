package com.nidoham.flowtube.stream;

import android.util.Log;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.stream.StreamInfo;

/**
 * A utility class for fetching YouTube video information using the NewPipe extractor library.
 * Provides asynchronous methods to extract video metadata such as title, description, uploader details,
 * duration, view count, and thumbnail information.
 */
public class InfoStreamDownloader {
    
    private static final String TAG = "InfoStreamDownloader";

    /**
     * Fetches stream information asynchronously from a YouTube URL using callback pattern.
     * This method extracts only the metadata information without processing stream URLs.
     * 
     * @param youtubeUrl The YouTube video URL to extract information from
     * @param callback The callback interface to handle success or error responses
     */
    public static void getStreamInfo(String youtubeUrl, StreamInfoCallback callback) {
        new Thread(() -> {
            try {
                if (NewPipe.getDownloader() == null) {
                    callback.onError(new IllegalStateException("NewPipe not initialized. Call initNewPipe() first."));
                    return;
                }

                if (youtubeUrl == null || youtubeUrl.trim().isEmpty()) {
                    callback.onError(new IllegalArgumentException("YouTube URL cannot be null or empty"));
                    return;
                }

                StreamInfo streamInfo = StreamInfo.getInfo(ServiceList.YouTube, youtubeUrl);

                if (streamInfo != null) {
                    callback.onSuccess(streamInfo);
                } else {
                    callback.onError(new ExtractionException("StreamInfo is null"));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error extracting StreamInfo from URL: " + youtubeUrl, e);
                callback.onError(e);
            }
        }).start();
    }

    /**
     * Validates whether the provided URL is a valid YouTube URL
     * 
     * @param url The URL to validate
     * @return true if the URL is a valid YouTube URL, false otherwise
     */
    public static boolean isValidYouTubeUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }
        
        try {
            // Simple URL validation for YouTube URLs
            String lowerUrl = url.toLowerCase().trim();
            return lowerUrl.contains("youtube.com/watch") || 
                   lowerUrl.contains("youtu.be/") || 
                   lowerUrl.contains("youtube.com/embed/") ||
                   lowerUrl.contains("m.youtube.com/watch");
        } catch (Exception e) {
            Log.w(TAG, "Error validating YouTube URL: " + url, e);
            return false;
        }
    }

    /**
     * Callback interface for handling StreamInfo extraction results.
     * This interface provides methods for both successful extraction and error handling scenarios.
     */
    public interface StreamInfoCallback {
        /**
         * Called when stream information is successfully extracted.
         * The StreamInfo object contains video metadata including title, description, uploader,
         * duration, view count, upload date, thumbnail URLs, and other video information.
         * 
         * @param info The extracted StreamInfo object containing video metadata
         */
        void onSuccess(StreamInfo info);

        /**
         * Called when an error occurs during stream information extraction
         * 
         * @param e The exception that occurred during the extraction process
         */
        void onError(Exception e);
    }
}