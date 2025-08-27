package com.nidoham.flowtube.tools.model;

import java.util.Objects;

/**
 * Data class representing a stream (video or audio) from YouTube.
 */
public class StreamInfo {
    private String quality; // e.g., "720p", "128kbps", "audio"
    private String format;  // e.g., "mp4", "webm", "audio"
    private String url;     // Direct playable URL

    public StreamInfo() {
    }

    public StreamInfo(String quality, String format, String url) {
        this.quality = quality;
        this.format = format;
        this.url = url;
    }

    public String getQuality() {
        return quality;
    }

    public void setQuality(String quality) {
        this.quality = quality;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    // Optional: For better usability in collections
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StreamInfo)) return false;
        StreamInfo that = (StreamInfo) o;
        return Objects.equals(quality, that.quality) &&
                Objects.equals(format, that.format) &&
                Objects.equals(url, that.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(quality, format, url);
    }

    @Override
    public String toString() {
        return "StreamInfo{" +
                "quality='" + quality + '\'' +
                ", format='" + format + '\'' +
                ", url='" + url + '\'' +
                '}';
    }
}