package com.nidoham.flowtube.helper;

import android.util.Log;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.stream.StreamInfo;

public class YouTubeDirectLink {

    private static final String TAG = "YouTubeDirectLink";

    // শুধুমাত্র StreamInfo রিটার্ন করার মেথড
    public static void getStreamInfo(String youtubeUrl, StreamInfoCallback callback) {
        new Thread(() -> {
            try {
                if (NewPipe.getDownloader() == null) {
                    callback.onError(new IllegalStateException("NewPipe not initialized. Call initNewPipe() first."));
                    return;
                }

                StreamInfo streamInfo = StreamInfo.getInfo(ServiceList.YouTube, youtubeUrl);

                if (streamInfo != null) {
                    callback.onSuccess(streamInfo);
                } else {
                    callback.onError(new ExtractionException("StreamInfo is null"));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error extracting StreamInfo", e);
                callback.onError(e);
            }
        }).start();
    }

    // Callback interface for StreamInfo results
    public interface StreamInfoCallback {
        void onSuccess(StreamInfo info);
        void onError(Exception e);
    }
}