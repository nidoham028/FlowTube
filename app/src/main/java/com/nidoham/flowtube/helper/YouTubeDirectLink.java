package com.nidoham.flowtube.helper;

import android.content.Context;
import android.util.Log;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.stream.StreamExtractor;
import org.schabi.newpipe.extractor.stream.VideoStream;
import java.io.IOException;
import java.util.List;

public class YouTubeDirectLink {
    private static final String TAG = "YouTubeDirectLink";

    /**
     * Fetches the direct video stream URL from a YouTube URL.
     *
     * @param context   The Android context (e.g., Activity or Application context).
     * @param youtubeUrl The YouTube video URL (e.g., https://www.youtube.com/watch?v=VIDEO_ID).
     * @param callback  Callback to return the direct URL or error.
     */
    public static void getDirectLink(Context context, String youtubeUrl, DirectLinkCallback callback) {
        // Run extraction in a background thread
        new Thread(() -> {
            try {
                // Ensure NewPipe is initialized
                if (NewPipe.getDownloader() == null) {
                    throw new IllegalStateException("NewPipe not initialized. Call NewPipe.init() first.");
                }

                // Get the stream extractor
                StreamExtractor extractor = ServiceList.YouTube.getStreamExtractor(youtubeUrl);
                extractor.fetchPage();

                // Get video streams
                List<VideoStream> videoStreams = extractor.getVideoStreams();

                // Select the first available video stream
                if (!videoStreams.isEmpty()) {
                    String directUrl = videoStreams.get(0).getUrl();
                    callback.onSuccess(directUrl);
                } else {
                    callback.onError(new ExtractionException("No video streams found"));
                }
            } catch (ExtractionException | IOException e) {
                Log.e(TAG, "Error extracting URL: " + e.getMessage());
                callback.onError(e);
            }
        }).start();
    }

    /**
     * Callback interface for handling the result of the direct link extraction.
     */
    public interface DirectLinkCallback {
        void onSuccess(String directUrl);
        void onError(Exception e);
    }
}