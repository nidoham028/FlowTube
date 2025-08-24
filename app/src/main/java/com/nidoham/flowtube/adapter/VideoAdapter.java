package com.nidoham.flowtube.adapter;

import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.nidoham.flowtube.databinding.ItemVideoBinding;
import com.nidoham.flowtube.R;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.text.SimpleDateFormat;

import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.util.image.PicassoHelper;

/**
 * Professional RecyclerView adapter for displaying video content with comprehensive
 * functionality including live stream detection, optimized data binding, proper memory
 * management, and enhanced user experience through efficient image loading.
 */
public class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.VideoViewHolder> {

    private static final String TAG = "VideoAdapter";
    
    private List<StreamInfoItem> videoList;
    private final OnVideoItemClickListener clickListener;
    private Context context;

    /**
     * Interface for handling video item interactions and providing comprehensive
     * callback mechanisms for user interactions and contextual menu operations.
     */
    public interface OnVideoItemClickListener {
        /**
         * Called when a video item is clicked for playback or viewing.
         * 
         * @param videoItem The selected video stream item
         */
        void onVideoItemClick(StreamInfoItem videoItem);
        
        /**
         * Called when the more options menu is requested for a video item.
         * 
         * @param videoItem The video item for which options are requested
         * @param position The adapter position of the video item
         */
        void onMoreOptionsClick(StreamInfoItem videoItem, int position);
    }

    /**
     * Constructs a new VideoAdapter with the specified video list and interaction listener.
     * Uses direct reference to maintain synchronization with fragment updates.
     *
     * @param videoList The initial list of video items to display
     * @param clickListener The listener for handling user interaction events
     */
    public VideoAdapter(@NonNull List<StreamInfoItem> videoList, @NonNull OnVideoItemClickListener clickListener) {
        this.videoList = videoList; // Use direct reference for proper synchronization
        this.clickListener = clickListener;
        setHasStableIds(false);
        
        Log.d(TAG, "VideoAdapter initialized with " + videoList.size() + " items");
    }

    @NonNull
    @Override
    public VideoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        context = parent.getContext();
        LayoutInflater inflater = LayoutInflater.from(context);
        ItemVideoBinding binding = ItemVideoBinding.inflate(inflater, parent, false);
        return new VideoViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull VideoViewHolder holder, int position) {
        if (videoList == null || position < 0 || position >= videoList.size()) {
            Log.w(TAG, "Invalid position or null list: position=" + position + ", list size=" + 
                  (videoList != null ? videoList.size() : "null"));
            holder.clearViewData();
            return;
        }

        StreamInfoItem item = videoList.get(position);
        if (item == null) {
            Log.w(TAG, "Null item at position " + position);
            holder.clearViewData();
            return;
        }

        holder.bind(item, position);
    }

    @Override
    public int getItemCount() {
        int count = videoList != null ? videoList.size() : 0;
        Log.d(TAG, "getItemCount returning: " + count);
        return count;
    }

    /**
     * Updates the entire video list with new data using DiffUtil for optimal performance.
     *
     * @param newVideoList The new list of video items to display
     */
    public void updateVideoList(@Nullable List<StreamInfoItem> newVideoList) {
        if (newVideoList == null) {
            clearVideos();
            return;
        }

        List<StreamInfoItem> oldList = new ArrayList<>(videoList);
        List<StreamInfoItem> newList = new ArrayList<>(newVideoList);
        
        VideoListDiffCallback diffCallback = new VideoListDiffCallback(oldList, newList);
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(diffCallback);
        
        videoList.clear();
        videoList.addAll(newList);
        diffResult.dispatchUpdatesTo(this);
        
        Log.d(TAG, "Video list updated with " + newList.size() + " items");
    }

    /**
     * Appends additional videos to the existing list with optimized insertion notifications.
     *
     * @param newVideos The list of new videos to append to the existing collection
     */
    public void addVideos(@Nullable List<StreamInfoItem> newVideos) {
        if (newVideos == null || newVideos.isEmpty()) {
            Log.d(TAG, "No new videos to add");
            return;
        }
        
        if (videoList == null) {
            videoList = new ArrayList<>();
        }

        int startPosition = videoList.size();
        videoList.addAll(newVideos);
        notifyItemRangeInserted(startPosition, newVideos.size());
        
        Log.d(TAG, "Added " + newVideos.size() + " new videos at position " + startPosition);
    }

    /**
     * Removes all videos from the adapter and updates the display.
     */
    public void clearVideos() {
        if (videoList == null || videoList.isEmpty()) {
            Log.d(TAG, "Video list already empty");
            return;
        }

        int itemCount = videoList.size();
        videoList.clear();
        notifyItemRangeRemoved(0, itemCount);
        
        Log.d(TAG, "Cleared " + itemCount + " videos from adapter");
    }

    /**
     * Removes a specific video item at the given position.
     *
     * @param position The position of the video to remove from the adapter
     */
    public void removeVideo(int position) {
        if (videoList == null || position < 0 || position >= videoList.size()) {
            Log.w(TAG, "Invalid position for video removal: " + position);
            return;
        }

        videoList.remove(position);
        notifyItemRemoved(position);
        
        if (position < videoList.size()) {
            notifyItemRangeChanged(position, videoList.size() - position);
        }
        
        Log.d(TAG, "Removed video at position " + position);
    }

    /**
     * Retrieves the current video list as an immutable copy.
     *
     * @return An immutable copy of the current video list
     */
    @NonNull
    public List<StreamInfoItem> getVideoList() {
        return videoList != null ? new ArrayList<>(videoList) : new ArrayList<>();
    }

    /**
     * DiffUtil callback implementation for efficient list updates.
     */
    private static class VideoListDiffCallback extends DiffUtil.Callback {
        private final List<StreamInfoItem> oldList;
        private final List<StreamInfoItem> newList;

        public VideoListDiffCallback(List<StreamInfoItem> oldList, List<StreamInfoItem> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }

        @Override
        public int getOldListSize() {
            return oldList.size();
        }

        @Override
        public int getNewListSize() {
            return newList.size();
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            StreamInfoItem oldItem = oldList.get(oldItemPosition);
            StreamInfoItem newItem = newList.get(newItemPosition);
            
            if (oldItem == null || newItem == null) {
                return oldItem == newItem;
            }
            
            String oldUrl = oldItem.getUrl();
            String newUrl = newItem.getUrl();
            
            return oldUrl != null && oldUrl.equals(newUrl);
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            StreamInfoItem oldItem = oldList.get(oldItemPosition);
            StreamInfoItem newItem = newList.get(newItemPosition);
            
            if (oldItem == null || newItem == null) {
                return oldItem == newItem;
            }
            
            return compareStreamInfoItems(oldItem, newItem);
        }

        private boolean compareStreamInfoItems(StreamInfoItem item1, StreamInfoItem item2) {
            return TextUtils.equals(item1.getName(), item2.getName()) &&
                   TextUtils.equals(item1.getUploaderName(), item2.getUploaderName()) &&
                   item1.getViewCount() == item2.getViewCount() &&
                   item1.getDuration() == item2.getDuration();
        }
    }

    /**
     * Professional ViewHolder implementation with comprehensive data binding.
     */
    public class VideoViewHolder extends RecyclerView.ViewHolder {

        private final ItemVideoBinding binding;
        
        // Time calculation constants
        private static final long MILLISECONDS_IN_SECOND = 1000L;
        private static final long SECONDS_IN_MINUTE = 60L;
        private static final long MINUTES_IN_HOUR = 60L;
        private static final long HOURS_IN_DAY = 24L;
        private static final long DAYS_IN_WEEK = 7L;
        private static final long DAYS_IN_MONTH = 30L;
        private static final long DAYS_IN_YEAR = 365L;

        // View formatting constants
        private static final long VIEW_COUNT_THOUSAND = 1_000L;
        private static final long VIEW_COUNT_MILLION = 1_000_000L;
        private static final long VIEW_COUNT_BILLION = 1_000_000_000L;

        public VideoViewHolder(@NonNull ItemVideoBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        /**
         * Binds video data to the view holder components.
         *
         * @param videoItem The stream info item containing video metadata
         * @param position The position of this item within the adapter
         */
        public void bind(@NonNull StreamInfoItem videoItem, int position) {
            try {
                Log.d(TAG, "Binding item at position " + position + ": " + videoItem.getName());
                
                setVideoTitle(videoItem);
                setChannelInformation(videoItem);
                configureDurationDisplay(videoItem);
                loadThumbnailImage(videoItem.getThumbnails());
                loadChannelAvatar(videoItem.getUploaderAvatars());
                configureClickHandlers(videoItem, position);
                
            } catch (Exception e) {
                Log.e(TAG, "Error binding video item at position " + position, e);
                // Don't clear data on error, show what we can
                setBasicVideoInfo(videoItem);
            }
        }

        /**
         * Sets basic video information when full binding fails.
         */
        private void setBasicVideoInfo(@NonNull StreamInfoItem videoItem) {
            String title = videoItem.getName();
            if (TextUtils.isEmpty(title)) {
                title = "Video";
            }
            binding.txtTitle.setText(title);
            
            String uploader = videoItem.getUploaderName();
            if (!TextUtils.isEmpty(uploader)) {
                binding.txtInfo.setText(uploader);
            }
            
            configureClickHandlers(videoItem, getAdapterPosition());
        }

        /**
         * Clears all view data when no valid video item is available.
         */
        public void clearViewData() {
            binding.txtTitle.setText("Loading...");
            binding.txtInfo.setText("");
            binding.txtDuration.setText("");
            binding.imgThumb.setImageDrawable(null);
            binding.imgAvatar.setImageDrawable(null);
        }

        /**
         * Sets the video title with proper null handling.
         */
        private void setVideoTitle(@NonNull StreamInfoItem videoItem) {
            String title = videoItem.getName();
            if (TextUtils.isEmpty(title)) {
                title = "Untitled Video";
            }
            binding.txtTitle.setText(title);
        }

        /**
         * Configures comprehensive channel information display.
         */
        private void setChannelInformation(@NonNull StreamInfoItem videoItem) {
            try {
                String channelInfo = buildChannelInfoString(videoItem);
                binding.txtInfo.setText(channelInfo);
            } catch (Exception e) {
                Log.w(TAG, "Error formatting channel information", e);
                String uploader = videoItem.getUploaderName();
                binding.txtInfo.setText(TextUtils.isEmpty(uploader) ? "Unknown Channel" : uploader);
            }
        }

        /**
         * Configures the duration display.
         */
        private void configureDurationDisplay(@NonNull StreamInfoItem videoItem) {
            try {
                if (isCurrentlyLiveStream(videoItem)) {
                    configureLiveStatusDisplay();
                } else {
                    configureRegularDurationDisplay(videoItem);
                }
            } catch (Exception e) {
                Log.w(TAG, "Error configuring duration display", e);
                binding.txtDuration.setText("");
            }
        }

        /**
         * Configures the display for live streams.
         */
        private void configureLiveStatusDisplay() {
            binding.txtDuration.setBackgroundColor(Color.RED);
            binding.txtDuration.setTextColor(Color.WHITE);
            binding.txtDuration.setText("LIVE");
            binding.txtDuration.setPadding(8, 4, 8, 4);
        }

        /**
         * Configures the display for regular video content.
         */
        private void configureRegularDurationDisplay(@NonNull StreamInfoItem videoItem) {
            try {
                binding.txtDuration.setBackgroundResource(R.drawable.duration_background);
            } catch (Exception e) {
                // Fallback if drawable doesn't exist
                binding.txtDuration.setBackgroundColor(Color.parseColor("#80000000"));
            }
            binding.txtDuration.setTextColor(Color.WHITE);
            binding.txtDuration.setPadding(6, 2, 6, 2);
            
            String formattedDuration = formatDuration(videoItem.getDuration());
            binding.txtDuration.setText(formattedDuration);
        }

        /**
         * Determines whether a stream is currently broadcasting live.
         */
        private boolean isCurrentlyLiveStream(@NonNull StreamInfoItem videoItem) {
            StreamType streamType = videoItem.getStreamType();
            return streamType == StreamType.LIVE_STREAM;
        }

        /**
         * Loads thumbnail image using PicassoHelper with error handling.
         */
        private void loadThumbnailImage(@Nullable List<Image> thumbnails) {
            try {
                if (thumbnails == null || thumbnails.isEmpty()) {
                    setPlaceholderThumbnail();
                    return;
                }

                PicassoHelper.loadThumbnail(thumbnails)
                        .fit()
                        .centerCrop()
                        .tag(this)
                        .into(binding.imgThumb);
                        
            } catch (Exception e) {
                Log.w(TAG, "Error loading thumbnail image", e);
                setPlaceholderThumbnail();
            }
        }

        /**
         * Sets a placeholder thumbnail image.
         */
        private void setPlaceholderThumbnail() {
            binding.imgThumb.setBackgroundColor(Color.LTGRAY);
        }

        /**
         * Loads channel avatar using PicassoHelper.
         */
        private void loadChannelAvatar(@Nullable List<Image> avatars) {
            try {
                if (avatars == null || avatars.isEmpty()) {
                    setPlaceholderAvatar();
                    return;
                }

                PicassoHelper.loadAvatar(avatars)
                        .fit()
                        .centerCrop()
                        .tag(this)
                        .into(binding.imgAvatar);
                        
            } catch (Exception e) {
                Log.w(TAG, "Error loading channel avatar", e);
                setPlaceholderAvatar();
            }
        }

        /**
         * Sets a placeholder avatar image.
         */
        private void setPlaceholderAvatar() {
            binding.imgAvatar.setBackgroundColor(Color.LTGRAY);
        }

        /**
         * Configures comprehensive click event handlers.
         */
        private void configureClickHandlers(@NonNull StreamInfoItem videoItem, int position) {
            binding.getRoot().setOnClickListener(view -> {
                if (clickListener != null) {
                    try {
                        clickListener.onVideoItemClick(videoItem);
                    } catch (Exception e) {
                        Log.e(TAG, "Error handling video item click", e);
                    }
                }
            });

            binding.btnMore.setOnClickListener(view -> {
                if (clickListener != null) {
                    try {
                        clickListener.onMoreOptionsClick(videoItem, position);
                    } catch (Exception e) {
                        Log.e(TAG, "Error handling more options click", e);
                    }
                }
            });
        }

        /**
         * Constructs comprehensive channel information string.
         */
        private String buildChannelInfoString(@NonNull StreamInfoItem videoItem) {
            StringBuilder information = new StringBuilder();

            // Uploader name with fallback
            String uploader = videoItem.getUploaderName();
            if (TextUtils.isEmpty(uploader)) {
                uploader = "Unknown Channel";
            }
            information.append(uploader);

            // View count with intelligent formatting
            long viewCount = videoItem.getViewCount();
            if (viewCount >= 0) {
                information.append(" • ").append(formatViewCount(viewCount));
            }

            // Upload date with relative time formatting
            DateWrapper uploadDate = videoItem.getUploadDate();
            if (uploadDate != null && uploadDate.date() != null) {
                long uploadTimeMs = uploadDate.date().getTimeInMillis();
                information.append(" • ").append(formatTimeAgo(uploadTimeMs));
            }

            return information.toString();
        }

        /**
         * Formats upload time into user-friendly relative time representations.
         */
        private String formatTimeAgo(long uploadTimeMs) {
            long currentTimeMs = System.currentTimeMillis();
            long timeDifferenceMs = currentTimeMs - uploadTimeMs;
            
            if (timeDifferenceMs < 0) {
                return "Just now";
            }

            long timeDifferenceSeconds = timeDifferenceMs / MILLISECONDS_IN_SECOND;

            if (timeDifferenceSeconds < SECONDS_IN_MINUTE) {
                return timeDifferenceSeconds <= 5 ? 
                    "Just now" : 
                    timeDifferenceSeconds + " seconds ago";
            }

            long timeDifferenceMinutes = timeDifferenceSeconds / SECONDS_IN_MINUTE;
            if (timeDifferenceMinutes < MINUTES_IN_HOUR) {
                return timeDifferenceMinutes == 1 ? 
                    "1 minute ago" : 
                    timeDifferenceMinutes + " minutes ago";
            }

            long timeDifferenceHours = timeDifferenceMinutes / MINUTES_IN_HOUR;
            if (timeDifferenceHours < HOURS_IN_DAY) {
                return timeDifferenceHours == 1 ? 
                    "1 hour ago" : 
                    timeDifferenceHours + " hours ago";
            }

            long timeDifferenceDays = timeDifferenceHours / HOURS_IN_DAY;
            if (timeDifferenceDays < DAYS_IN_WEEK) {
                return timeDifferenceDays == 1 ? 
                    "1 day ago" : 
                    timeDifferenceDays + " days ago";
            }

            long timeDifferenceWeeks = timeDifferenceDays / DAYS_IN_WEEK;
            if (timeDifferenceDays < DAYS_IN_MONTH) {
                return timeDifferenceWeeks == 1 ? 
                    "1 week ago" : 
                    timeDifferenceWeeks + " weeks ago";
            }

            long timeDifferenceMonths = timeDifferenceDays / DAYS_IN_MONTH;
            if (timeDifferenceDays < DAYS_IN_YEAR) {
                return timeDifferenceMonths == 1 ? 
                    "1 month ago" : 
                    timeDifferenceMonths + " months ago";
            }

            // For very old content, show actual date
            SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
            return dateFormat.format(new Date(uploadTimeMs));
        }

        /**
         * Formats video duration from seconds to human-readable time representation.
         */
        private String formatDuration(long durationInSeconds) {
            if (durationInSeconds <= 0) {
                return "0:00";
            }

            long hours = durationInSeconds / (SECONDS_IN_MINUTE * MINUTES_IN_HOUR);
            long minutes = (durationInSeconds % (SECONDS_IN_MINUTE * MINUTES_IN_HOUR)) / SECONDS_IN_MINUTE;
            long seconds = durationInSeconds % SECONDS_IN_MINUTE;

            if (hours > 0) {
                return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds);
            } else {
                return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
            }
        }

        /**
         * Formats view count numbers into abbreviated representations.
         */
        private String formatViewCount(long viewCount) {
            if (viewCount <= 0) {
                return "No views";
            }

            if (viewCount == 1) {
                return "1 view";
            }

            if (viewCount < VIEW_COUNT_THOUSAND) {
                return viewCount + " views";
            }

            if (viewCount < VIEW_COUNT_MILLION) {
                double thousands = viewCount / 1000.0;
                if (thousands < 10) {
                    return String.format(Locale.getDefault(), "%.1fK views", thousands);
                } else {
                    return String.format(Locale.getDefault(), "%.0fK views", thousands);
                }
            }

            if (viewCount < VIEW_COUNT_BILLION) {
                double millions = viewCount / 1_000_000.0;
                return String.format(Locale.getDefault(), "%.1fM views", millions);
            }

            double billions = viewCount / 1_000_000_000.0;
            return String.format(Locale.getDefault(), "%.1fB views", billions);
        }
    }

    @Override
    public void onViewRecycled(@NonNull VideoViewHolder holder) {
        super.onViewRecycled(holder);
        try {
            PicassoHelper.cancelTag(holder);
            holder.clearViewData();
        } catch (Exception e) {
            Log.w(TAG, "Error during view recycling", e);
        }
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        try {
            if (context != null) {
                PicassoHelper.cancelTag(context);
            }
        } catch (Exception e) {
            Log.w(TAG, "Error during RecyclerView detachment", e);
        }
    }

    @Override
    public boolean onFailedToRecycleView(@NonNull VideoViewHolder holder) {
        Log.w(TAG, "Failed to recycle view holder, clearing data");
        holder.clearViewData();
        return super.onFailedToRecycleView(holder);
    }
}