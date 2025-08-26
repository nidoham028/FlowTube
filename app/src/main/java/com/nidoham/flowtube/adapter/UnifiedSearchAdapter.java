package com.nidoham.flowtube.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.nidoham.flowtube.R;
import com.squareup.picasso.Picasso;
import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import java.util.List;

public class UnifiedSearchAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_SUGGESTION = 0;
    private static final int TYPE_VIDEO = 1;

    private final List<Object> items;
    private final SearchItemClickListener listener;

    public interface SearchItemClickListener {
        void onSuggestionItemClick(String suggestion);
        void onVideoItemClick(StreamInfoItem videoItem);
    }

    public UnifiedSearchAdapter(List<Object> items, SearchItemClickListener listener) {
        this.items = items;
        this.listener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        if (items.get(position) instanceof String) {
            return TYPE_SUGGESTION;
        } else if (items.get(position) instanceof StreamInfoItem) {
            return TYPE_VIDEO;
        }
        return -1;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_SUGGESTION) {
            View view = inflater.inflate(R.layout.item_search_suggestion, parent, false);
            return new SuggestionViewHolder(view);
        } else { // TYPE_VIDEO
            View view = inflater.inflate(R.layout.item_video_result, parent, false);
            return new VideoViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (getItemViewType(position) == TYPE_SUGGESTION) {
            SuggestionViewHolder suggestionHolder = (SuggestionViewHolder) holder;
            String suggestion = (String) items.get(position);
            suggestionHolder.bind(suggestion, listener);
        } else {
            VideoViewHolder videoHolder = (VideoViewHolder) holder;
            StreamInfoItem videoItem = (StreamInfoItem) items.get(position);
            videoHolder.bind(videoItem, listener);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class SuggestionViewHolder extends RecyclerView.ViewHolder {
        TextView suggestionText;
        SuggestionViewHolder(View itemView) {
            super(itemView);
            suggestionText = itemView.findViewById(R.id.suggestion_text);
        }

        void bind(final String suggestion, final SearchItemClickListener listener) {
            suggestionText.setText(suggestion);
            itemView.setOnClickListener(v -> listener.onSuggestionItemClick(suggestion));
        }
    }

    static class VideoViewHolder extends RecyclerView.ViewHolder {
        ImageView thumbnail;
        TextView title, uploader;

        VideoViewHolder(View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.video_thumbnail);
            title = itemView.findViewById(R.id.video_title);
            uploader = itemView.findViewById(R.id.video_uploader);
        }

        void bind(final StreamInfoItem item, final SearchItemClickListener listener) {
            title.setText(item.getName());
            uploader.setText(item.getUploaderName());

            // ** FIXED CODE **
            // Use the getThumbnails() method which returns a List<Image>.
            List<Image> thumbnails = item.getThumbnails();
            String thumbnailUrl = null;

            // Check if the list of thumbnails is not empty and get the URL from the first one.
            if (thumbnails != null && !thumbnails.isEmpty()) {
                thumbnailUrl = thumbnails.get(0).getUrl();
            }

            if (thumbnailUrl != null && !thumbnailUrl.isEmpty()) {
                Picasso.get()
                        .load(thumbnailUrl)
                        .placeholder(R.color.seed)
                        .error(R.color.seed)
                        .into(thumbnail);
            } else {
                // Set a default image if no thumbnail is available
                thumbnail.setImageResource(R.color.seed);
            }

            itemView.setOnClickListener(v -> listener.onVideoItemClick(item));
        }
    }
}