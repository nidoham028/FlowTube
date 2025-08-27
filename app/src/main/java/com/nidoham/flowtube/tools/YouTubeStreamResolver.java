package com.nidoham.flowtube.tools;

import com.nidoham.flowtube.tools.model.StreamInfo;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.VideoStream;

// NOTE: Don't import org.schabi.newpipe.extractor.stream.StreamInfo here to avoid name clash

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Logic class for extracting and resolving YouTube video and audio streams using NewPipe Extractor.
 * This class is UI-agnostic and can be used on any Java platform.
 */
public class YouTubeStreamResolver {

    public YouTubeStreamResolver() {
        // No explicit initialization needed for NewPipe Extractor.
    }

    /**
     * Fetches all available streams (video and audio) for the given YouTube video URL.
     * The returned list contains video and audio stream info with direct URLs.
     *
     * @param videoUrl The full URL to the YouTube video.
     * @return List of available streams for the video.
     * @throws Exception if extraction fails.
     */
    public List<StreamInfo> getAvailableStreams(String videoUrl) throws Exception {
        List<StreamInfo> streamInfos = new ArrayList<>();

        org.schabi.newpipe.extractor.stream.StreamInfo info;
        try {
            info = org.schabi.newpipe.extractor.stream.StreamInfo.getInfo(ServiceList.YouTube, videoUrl);
        } catch (ExtractionException e) {
            throw new Exception("Failed to extract streams: " + e.getMessage(), e);
        }

        // Add video streams
        for (VideoStream videoStream : info.getVideoStreams()) {
            streamInfos.add(new StreamInfo(
                videoStream.getQuality(),
                videoStream.getFormat().getName(),
                videoStream.getUrl()
            ));
        }
        
        // Add video streams
        for (VideoStream videoStream : info.getVideoOnlyStreams()) {
            streamInfos.add(new StreamInfo(
                videoStream.getQuality(),
                videoStream.getFormat().getName(),
                videoStream.getUrl()
            ));
        }

        return streamInfos;
    }

    public String getDirectLink(StreamInfo stream) {
        return stream != null ? stream.getUrl() : null;
    }

    public StreamInfo getBestQuality(List<StreamInfo> streams) {
        if (streams == null || streams.isEmpty()) {
            return null;
        }
        List<StreamInfo> videoStreams = new ArrayList<>();
        for (StreamInfo stream : streams) {
            if (!"audio".equalsIgnoreCase(stream.getFormat())) {
                videoStreams.add(stream);
            }
        }
        if (videoStreams.isEmpty()) {
            return null;
        }
        Collections.sort(videoStreams, new Comparator<StreamInfo>() {
            @Override
            public int compare(StreamInfo o1, StreamInfo o2) {
                return parseQuality(o2.getQuality()) - parseQuality(o1.getQuality());
            }
        });
        return videoStreams.get(0);
    }

    public List<StreamInfo> getAudioStreams(List<StreamInfo> streams) {
        List<StreamInfo> audioStreams = new ArrayList<>();
        if (streams != null) {
            for (StreamInfo s : streams) {
                if ("audio".equalsIgnoreCase(s.getFormat())) {
                    audioStreams.add(s);
                }
            }
        }
        return audioStreams;
    }

    private int parseQuality(String qualityLabel) {
        if (qualityLabel == null) return 0;
        try {
            if (qualityLabel.endsWith("p")) {
                return Integer.parseInt(qualityLabel.replace("p", ""));
            }
            return 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}