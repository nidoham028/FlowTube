package com.nidoham.flowtube.tools;

import com.nidoham.flowtube.tools.QualityStreamHelper;
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
 * Enhanced with quality-based stream selection capabilities.
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

        // Add video streams with audio
        for (VideoStream videoStream : info.getVideoStreams()) {
            streamInfos.add(new StreamInfo(
                videoStream.getResolution(),
                videoStream.getFormat().getName(),
                videoStream.getUrl()
            ));
        }

        // Add video-only streams
        for (VideoStream videoStream : info.getVideoOnlyStreams()) {
            streamInfos.add(new StreamInfo(
                videoStream.getResolution(),
                videoStream.getFormat().getName(),
                videoStream.getUrl()
            ));
        }

        // Add audio streams
        for (AudioStream audioStream : info.getAudioStreams()) {
            streamInfos.add(new StreamInfo(
                audioStream.getAverageBitrate() + "kbps",
                "audio",
                audioStream.getUrl()
            ));
        }

        return streamInfos;
    }

    /**
     * Retrieves the direct playable URL from a StreamInfo object.
     *
     * @param stream The StreamInfo object containing the URL.
     * @return The direct URL, or null if the stream is null.
     */
    public String getDirectLink(StreamInfo stream) {
        return stream != null ? stream.getUrl() : null;
    }

    /**
     * Gets the highest quality video stream from the available streams.
     * This method maintains backward compatibility with existing implementations.
     *
     * @param streams List of available streams to search through.
     * @return The highest quality video stream, or null if no video streams are found.
     */
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

    /**
     * Retrieves a stream matching the specified quality tier preference.
     * Uses the QualityStreamHelper to find streams within quality ranges.
     *
     * @param streams List of available streams to search through.
     * @param qualityTier The desired quality tier (LOW, NORMAL, HIGH, HD).
     * @return The best stream matching the quality tier, or null if none found.
     */
    public StreamInfo getStreamByQuality(List<StreamInfo> streams, QualityStreamHelper.QualityTier qualityTier) {
        return QualityStreamHelper.getStreamByQuality(streams, qualityTier);
    }

    /**
     * Retrieves a stream with fallback logic to ensure playability.
     * If the preferred quality is not available, automatically selects the next best option.
     *
     * @param streams List of available streams to search through.
     * @param preferredQuality The preferred quality tier.
     * @return The best available stream with fallback, or null if no video streams exist.
     */
    public StreamInfo getBestAvailableQuality(List<StreamInfo> streams, QualityStreamHelper.QualityTier preferredQuality) {
        return QualityStreamHelper.getBestAvailableQuality(streams, preferredQuality);
    }

    /**
     * Organizes all streams by their quality tiers for comprehensive quality-based access.
     *
     * @param streams List of streams to organize.
     * @return QualityStreams object containing streams grouped by quality tier.
     */
    public QualityStreamHelper.QualityStreams organizeStreamsByQuality(List<StreamInfo> streams) {
        return QualityStreamHelper.organizeByQuality(streams);
    }

    /**
     * Filters streams to only include audio streams.
     *
     * @param streams List of streams to filter.
     * @return List containing only audio streams.
     */
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

    /**
     * Retrieves a stream with intelligent quality recommendation based on device and network conditions.
     * This method provides smart optimization for the best user experience.
     *
     * @param streams List of available streams to search through.
     * @param preferredQuality The preferred quality tier.
     * @param networkStrength Network strength indicator (1-5, where 5 is strongest).
     * @param deviceCapability Device capability level (1-5, where 5 is highest).
     * @return The intelligently recommended stream for optimal performance.
     */
    public StreamInfo getSmartRecommendedStream(List<StreamInfo> streams,
                                               QualityStreamHelper.QualityTier preferredQuality,
                                               int networkStrength,
                                               int deviceCapability) {
        return QualityStreamHelper.getSmartRecommendedStream(streams, preferredQuality, networkStrength, deviceCapability);
    }

    /**
     * Retrieves the optimal stream for mobile devices with automatic optimization.
     * This method considers typical mobile device capabilities and network conditions.
     *
     * @param streams List of available streams to optimize for mobile viewing.
     * @return The mobile-optimized stream for the best mobile experience.
     */
    public StreamInfo getMobileOptimizedStream(List<StreamInfo> streams) {
        return QualityStreamHelper.getMobileOptimizedStream(streams);
    }

    /**
     * Retrieves streams matching the very low quality tier (144p) for minimal bandwidth usage.
     *
     * @param streams List of available streams to filter.
     * @return The best 144p stream available, or null if none exists.
     */
    public StreamInfo getVeryLowQualityStream(List<StreamInfo> streams) {
        return QualityStreamHelper.getStreamByQuality(streams, QualityStreamHelper.QualityTier.VERY_LOW);
    }

    /**
     * Filters streams to only include video streams (excludes audio-only streams).
     *
     * @param streams List of streams to filter.
     * @return List containing only video streams.
     */
    public List<StreamInfo> getVideoStreams(List<StreamInfo> streams) {
        List<StreamInfo> videoStreams = new ArrayList<>();
        if (streams != null) {
            for (StreamInfo stream : streams) {
                if (!"audio".equalsIgnoreCase(stream.getFormat())) {
                    videoStreams.add(stream);
                }
            }
        }
        return videoStreams;
    }

    /**
     * Callback interface for asynchronous direct link retrieval operations.
     * Provides structured response handling for URL extraction processes.
     */
    public interface DirectLinkCallback {
        /**
         * Called when direct link retrieval succeeds.
         *
         * @param directUrl The extracted direct playable URL
         * @param streamInfo The associated stream information
         */
        void onSuccess(String directUrl, StreamInfo streamInfo);

        /**
         * Called when direct link retrieval fails.
         *
         * @param error The error message describing the failure
         * @param exception The underlying exception if available
         */
        void onError(String error, Exception exception);
    }

    /**
     * Retrieves the direct link for the overall best quality stream available.
     * This method selects the highest quality stream regardless of network conditions.
     *
     * @param videoUrl The YouTube video URL to process
     * @param callback The callback interface for handling the result
     */
    public void getOverallBestDirectLink(String videoUrl, DirectLinkCallback callback) {
        new Thread(() -> {
            try {
                List<StreamInfo> streams = getAvailableStreams(videoUrl);
                StreamInfo bestStream = getBestQuality(streams);

                if (bestStream != null) {
                    String directUrl = getDirectLink(bestStream);
                    callback.onSuccess(directUrl, bestStream);
                } else {
                    callback.onError("No suitable stream found for overall best quality", null);
                }
            } catch (Exception e) {
                callback.onError("Failed to retrieve overall best quality stream: " + e.getMessage(), e);
            }
        }).start();
    }

    /**
     * Parses quality labels to extract numeric resolution values.
     *
     * @param qualityLabel The quality label (e.g., "720p", "1080p").
     * @return The numeric quality value, or 0 if parsing fails.
     */
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

    /**
     * Retrieves the direct link optimized for WiFi network conditions.
     * This method prioritizes high-quality streams suitable for stable broadband connections.
     *
     * @param videoUrl The YouTube video URL to process
     * @param callback The callback interface for handling the result
     */
    public void getWiFiOptimizedDirectLink(String videoUrl, DirectLinkCallback callback) {
        new Thread(() -> {
            try {
                List<StreamInfo> streams = getAvailableStreams(videoUrl);
                StreamInfo wifiStream = getSmartRecommendedStream(
                        streams,
                        QualityStreamHelper.QualityTier.HIGH,
                        5, // networkStrength
                        4  // deviceCapability
                );

                if (wifiStream != null) {
                    String directUrl = getDirectLink(wifiStream);
                    callback.onSuccess(directUrl, wifiStream);
                } else {
                    callback.onError("No suitable stream found for WiFi optimization", null);
                }
            } catch (Exception e) {
                callback.onError("Failed to retrieve WiFi optimized stream: " + e.getMessage(), e);
            }
        }).start();
    }

    /**
     * Retrieves the direct link optimized for general mobile device usage.
     * This method balances quality and performance for typical mobile viewing scenarios.
     *
     * @param videoUrl The YouTube video URL to process
     * @param callback The callback interface for handling the result
     */
    public void getMobileOptimizedDirectLink(String videoUrl, DirectLinkCallback callback) {
        new Thread(() -> {
            try {
                List<StreamInfo> streams = getAvailableStreams(videoUrl);
                StreamInfo mobileStream = getMobileOptimizedStream(streams);

                if (mobileStream != null) {
                    String directUrl = getDirectLink(mobileStream);
                    callback.onSuccess(directUrl, mobileStream);
                } else {
                    callback.onError("No suitable stream found for mobile optimization", null);
                }
            } catch (Exception e) {
                callback.onError("Failed to retrieve mobile optimized stream: " + e.getMessage(), e);
            }
        }).start();
    }

    /**
     * Retrieves the direct link optimized for data saver mode.
     * This method prioritizes minimal bandwidth usage while maintaining acceptable quality.
     *
     * @param videoUrl The YouTube video URL to process
     * @param callback The callback interface for handling the result
     */
    public void getDataSaverDirectLink(String videoUrl, DirectLinkCallback callback) {
        new Thread(() -> {
            try {
                List<StreamInfo> streams = getAvailableStreams(videoUrl);
                StreamInfo dataSaverStream = getVeryLowQualityStream(streams);

                // Fallback to low quality if very low is not available
                if (dataSaverStream == null) {
                    dataSaverStream = getStreamByQuality(streams, QualityStreamHelper.QualityTier.LOW);
                }

                if (dataSaverStream != null) {
                    String directUrl = getDirectLink(dataSaverStream);
                    callback.onSuccess(directUrl, dataSaverStream);
                } else {
                    callback.onError("No suitable stream found for data saver mode", null);
                }
            } catch (Exception e) {
                callback.onError("Failed to retrieve data saver stream: " + e.getMessage(), e);
            }
        }).start();
    }

    /**
     * Retrieves the direct link optimized for mobile data network conditions.
     * This method considers limited bandwidth and potential data usage constraints.
     *
     * @param videoUrl The YouTube video URL to process
     * @param callback The callback interface for handling the result
     */
    public void getMobileDataDirectLink(String videoUrl, DirectLinkCallback callback) {
        new Thread(() -> {
            try {
                List<StreamInfo> streams = getAvailableStreams(videoUrl);
                StreamInfo mobileDataStream = getSmartRecommendedStream(
                        streams,
                        QualityStreamHelper.QualityTier.NORMAL,
                        2, // networkStrength
                        3  // deviceCapability
                );

                if (mobileDataStream != null) {
                    String directUrl = getDirectLink(mobileDataStream);
                    callback.onSuccess(directUrl, mobileDataStream);
                } else {
                    callback.onError("No suitable stream found for mobile data optimization", null);
                }
            } catch (Exception e) {
                callback.onError("Failed to retrieve mobile data optimized stream: " + e.getMessage(), e);
            }
        }).start();
    }
}