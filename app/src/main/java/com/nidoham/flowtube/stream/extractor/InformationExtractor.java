package com.nidoham.flowtube.stream.extractor;

import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.stream.StreamInfo;

import java.text.DecimalFormat;
import java.util.concurrent.TimeUnit;

public class InformationExtractor {
    private static final String VIEW_LABEL = "views";
    private static final String SUBSCRIPTION_LABEL = "subscribers";
    private static final String LIKE_LABEL = "likes";

    private final StreamInfo info;

    public InformationExtractor(StreamInfo info) {
        this.info = info;
    }

    public String getTextMeta() {
        StringBuilder information = new StringBuilder();

        long viewCount = info.getViewCount();
        if (viewCount > 0) {
            information.append(formatViewCount(viewCount));
        }

        DateWrapper uploadDate = info.getUploadDate();
        if (uploadDate != null && uploadDate.date() != null) {
            long uploadTimeMs = uploadDate.date().getTimeInMillis();
            String timeAgo = formatTimeAgo(uploadTimeMs);

            if (information.length() > 0 && !timeAgo.isEmpty()) {
                information.append(" • ");
            }
            information.append(timeAgo);
        }

        return information.toString();
    }

    public String getFormattedSubscriptionCount() {
        long count = getSubscriptionCount();
        return formatCount(count, SUBSCRIPTION_LABEL);
    }

    public String getFormattedLikeCount() {
        long count = getLikeCount();
        return formatCount(count, "");
    }

    // Helper method to get subscription count
    private long getSubscriptionCount() {
        try {
            return info.getUploaderSubscriberCount();
        } catch (NoSuchMethodError | NullPointerException e) {
            return 0;
        }
    }

    // Helper method to get like count
    private long getLikeCount() {
        try {
            return info.getLikeCount();
        } catch (NoSuchMethodError | NullPointerException e) {
            return 0;
        }
    }

    private String formatCount(long count, String label) {
        if (count <= 0) {
            return "0 " + label;
        }
        if (count < 1000) {
            return count + " " + label;
        }

        final String[] units = new String[]{"K", "M", "B", "T"};
        double value = count;
        int unitIndex = -1;

        while (value >= 1000 && unitIndex < units.length - 1) {
            value /= 1000;
            unitIndex++;
        }

        DecimalFormat decimalFormat = new DecimalFormat(value % 1 == 0 ? "#" : "#.#");
        return decimalFormat.format(value) + units[unitIndex] + " " + label;
    }

    private String formatViewCount(long count) {
        return formatCount(count, VIEW_LABEL);
    }

    private String formatTimeAgo(long timeMs) {
        long currentTimeMs = System.currentTimeMillis();
        long diffMs = currentTimeMs - timeMs;

        if (diffMs < 0) {
            return ""; // future date? ignore
        }

        long minutes = TimeUnit.MILLISECONDS.toMinutes(diffMs);
        long hours = TimeUnit.MILLISECONDS.toHours(diffMs);
        long days = TimeUnit.MILLISECONDS.toDays(diffMs);
        long weeks = days / 7;
        long months = days / 30;
        long years = days / 365;

        if (years > 0) return years + (years == 1 ? " year ago" : " years ago");
        if (months > 0) return months + (months == 1 ? " month ago" : " months ago");
        if (weeks > 0) return weeks + (weeks == 1 ? " week ago" : " weeks ago");
        if (days > 0) return days + (days == 1 ? " day ago" : " days ago");
        if (hours > 0) return hours + (hours == 1 ? " hour ago" : " hours ago");
        if (minutes > 0) return minutes + (minutes == 1 ? " minute ago" : " minutes ago");

        return "just now";
    }
}