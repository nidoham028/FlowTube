package com.nidoham.flowtube.tools;

import com.nidoham.flowtube.tools.model.StreamInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Helper class for filtering and selecting streams based on quality preferences.
 * Provides convenient methods to retrieve streams matching specific quality tiers.
 */
public class QualityStreamHelper {
    
    /**
     * Quality tiers for stream selection
     */
    public enum QualityTier {
        VERY_LOW, // 144p
        LOW,      // <= 360p
        NORMAL,   // 480p
        HIGH,     // 720p  
        HD        // <= 1080p
    }

    /**
     * Gets the best available stream for the specified quality tier with smart selection logic.
     * 
     * @param streams List of available streams to filter from
     * @param qualityTier The desired quality tier
     * @return The best matching stream for the quality tier, or null if none found
     */
    public static StreamInfo getStreamByQuality(List<StreamInfo> streams, QualityTier qualityTier) {
        if (streams == null || streams.isEmpty()) {
            return null;
        }

        List<StreamInfo> filteredStreams = filterStreamsByQuality(streams, qualityTier);
        
        if (filteredStreams.isEmpty()) {
            return null;
        }

        // Sort by quality descending and return the best match within the tier
        Collections.sort(filteredStreams, new Comparator<StreamInfo>() {
            @Override
            public int compare(StreamInfo o1, StreamInfo o2) {
                return parseQuality(o2.getQuality()) - parseQuality(o1.getQuality());
            }
        });

        return filteredStreams.get(0);
    }

    /**
     * Retrieves the optimal stream link based on device capabilities and network conditions.
     * Uses smart selection algorithm to recommend the best quality for the given context.
     * 
     * @param streams List of available streams
     * @param preferredTier The preferred quality tier
     * @param networkStrength Network strength indicator (1-5, where 5 is strongest)
     * @param deviceCapability Device capability level (1-5, where 5 is highest)
     * @return The recommended optimal stream
     */
    public static StreamInfo getSmartRecommendedStream(List<StreamInfo> streams, 
                                                      QualityTier preferredTier, 
                                                      int networkStrength, 
                                                      int deviceCapability) {
        if (streams == null || streams.isEmpty()) {
            return null;
        }

        // Calculate smart quality recommendation
        QualityTier recommendedTier = calculateOptimalQuality(preferredTier, networkStrength, deviceCapability);
        
        // Try to get the recommended quality first
        StreamInfo result = getStreamByQuality(streams, recommendedTier);
        
        // If not available, use fallback logic
        if (result == null) {
            result = getBestAvailableQuality(streams, recommendedTier);
        }
        
        return result;
    }

    /**
     * Gets the most suitable stream link for mobile devices with automatic optimization.
     * 
     * @param streams List of available streams
     * @return The optimal stream for mobile viewing
     */
    public static StreamInfo getMobileOptimizedStream(List<StreamInfo> streams) {
        // Mobile devices typically perform well with NORMAL quality (480p)
        return getSmartRecommendedStream(streams, QualityTier.NORMAL, 3, 3);
    }

    /**
     * Filters streams to match the specified quality tier requirements.
     * 
     * @param streams List of streams to filter
     * @param qualityTier The quality tier to filter by
     * @return List of streams matching the quality tier
     */
    public static List<StreamInfo> filterStreamsByQuality(List<StreamInfo> streams, QualityTier qualityTier) {
        List<StreamInfo> filteredStreams = new ArrayList<>();
        
        if (streams == null) {
            return filteredStreams;
        }

        for (StreamInfo stream : streams) {
            if (isVideoStream(stream) && matchesQualityTier(stream, qualityTier)) {
                filteredStreams.add(stream);
            }
        }

        return filteredStreams;
    }

    /**
     * Gets all available streams organized by quality tier.
     * 
     * @param streams List of streams to organize
     * @return QualityStreams object containing streams organized by tier
     */
    public static QualityStreams organizeByQuality(List<StreamInfo> streams) {
        QualityStreams qualityStreams = new QualityStreams();
        
        if (streams != null) {
            for (StreamInfo stream : streams) {
                if (isVideoStream(stream)) {
                    int quality = parseQuality(stream.getQuality());
                    
                    if (quality == 144) {
                        qualityStreams.veryLowQuality.add(stream);
                    } else if (quality <= 360 && quality > 144) {
                        qualityStreams.lowQuality.add(stream);
                    } else if (quality == 480) {
                        qualityStreams.normalQuality.add(stream);
                    } else if (quality == 720) {
                        qualityStreams.highQuality.add(stream);
                    } else if (quality <= 1080 && quality > 720) {
                        qualityStreams.hdQuality.add(stream);
                    }
                }
            }
        }
        
        return qualityStreams;
    }

    /**
     * Gets the best available quality stream, falling back to lower qualities if needed.
     * 
     * @param streams List of available streams
     * @param preferredQuality The preferred quality tier
     * @return The best available stream, with fallback to lower qualities
     */
    public static StreamInfo getBestAvailableQuality(List<StreamInfo> streams, QualityTier preferredQuality) {
        StreamInfo result = getStreamByQuality(streams, preferredQuality);
        
        if (result != null) {
            return result;
        }
        
        // Fallback logic: try lower quality tiers
        QualityTier[] fallbackOrder = getFallbackOrder(preferredQuality);
        
        for (QualityTier fallbackQuality : fallbackOrder) {
            result = getStreamByQuality(streams, fallbackQuality);
            if (result != null) {
                return result;
            }
        }
        
        return null;
    }

    private static boolean matchesQualityTier(StreamInfo stream, QualityTier qualityTier) {
        int quality = parseQuality(stream.getQuality());
        
        switch (qualityTier) {
            case VERY_LOW:
                return quality == 144;
            case LOW:
                return quality <= 360 && quality > 144;
            case NORMAL:
                return quality == 480;
            case HIGH:
                return quality == 720;
            case HD:
                return quality <= 1080 && quality > 720;
            default:
                return false;
        }
    }

    private static boolean isVideoStream(StreamInfo stream) {
        return stream != null && 
               stream.getFormat() != null && 
               !"audio".equalsIgnoreCase(stream.getFormat());
    }

    private static int parseQuality(String qualityLabel) {
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

    private static QualityTier[] getFallbackOrder(QualityTier preferredQuality) {
        switch (preferredQuality) {
            case HD:
                return new QualityTier[]{QualityTier.HIGH, QualityTier.NORMAL, QualityTier.LOW, QualityTier.VERY_LOW};
            case HIGH:
                return new QualityTier[]{QualityTier.NORMAL, QualityTier.HD, QualityTier.LOW, QualityTier.VERY_LOW};
            case NORMAL:
                return new QualityTier[]{QualityTier.LOW, QualityTier.HIGH, QualityTier.VERY_LOW, QualityTier.HD};
            case LOW:
                return new QualityTier[]{QualityTier.VERY_LOW, QualityTier.NORMAL, QualityTier.HIGH, QualityTier.HD};
            case VERY_LOW:
                return new QualityTier[]{QualityTier.LOW, QualityTier.NORMAL, QualityTier.HIGH, QualityTier.HD};
            default:
                return new QualityTier[0];
        }
    }

    /**
     * Calculates the optimal quality tier based on network and device capabilities.
     */
    private static QualityTier calculateOptimalQuality(QualityTier preferredTier, int networkStrength, int deviceCapability) {
        // Combine network and device factors to determine optimal quality
        int qualityScore = (networkStrength + deviceCapability) / 2;
        
        // If network or device is weak, downgrade quality
        if (networkStrength <= 2 || deviceCapability <= 2) {
            // Weak conditions - prefer lower quality for stability
            switch (preferredTier) {
                case HD:
                case HIGH:
                    return QualityTier.NORMAL;
                case NORMAL:
                    return QualityTier.LOW;
                case LOW:
                    return QualityTier.VERY_LOW;
                default:
                    return preferredTier;
            }
        } else if (qualityScore >= 4 && (networkStrength >= 4 || deviceCapability >= 4)) {
            // Strong conditions - can handle preferred quality or better
            return preferredTier;
        } else {
            // Moderate conditions - balance quality and performance
            switch (preferredTier) {
                case HD:
                    return QualityTier.HIGH;
                case HIGH:
                case NORMAL:
                    return QualityTier.NORMAL;
                default:
                    return preferredTier;
            }
        }
    }

    /**
     * Container class for streams organized by quality tier
     */
    public static class QualityStreams {
        private final List<StreamInfo> veryLowQuality = new ArrayList<>();
        private final List<StreamInfo> lowQuality = new ArrayList<>();
        private final List<StreamInfo> normalQuality = new ArrayList<>();
        private final List<StreamInfo> highQuality = new ArrayList<>();
        private final List<StreamInfo> hdQuality = new ArrayList<>();

        public List<StreamInfo> getVeryLowQuality() {
            return new ArrayList<>(veryLowQuality);
        }

        public List<StreamInfo> getLowQuality() {
            return new ArrayList<>(lowQuality);
        }

        public List<StreamInfo> getNormalQuality() {
            return new ArrayList<>(normalQuality);
        }

        public List<StreamInfo> getHighQuality() {
            return new ArrayList<>(highQuality);
        }

        public List<StreamInfo> getHdQuality() {
            return new ArrayList<>(hdQuality);
        }

        public boolean hasVeryLowQuality() {
            return !veryLowQuality.isEmpty();
        }

        public boolean hasLowQuality() {
            return !lowQuality.isEmpty();
        }

        public boolean hasNormalQuality() {
            return !normalQuality.isEmpty();
        }

        public boolean hasHighQuality() {
            return !highQuality.isEmpty();
        }

        public boolean hasHdQuality() {
            return !hdQuality.isEmpty();
        }
    }
}