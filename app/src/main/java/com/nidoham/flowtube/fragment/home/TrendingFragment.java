package com.nidoham.flowtube.fragment.home;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.nidoham.flowtube.PlayerActivity;
import com.nidoham.flowtube.adapter.VideoAdapter;
import com.nidoham.flowtube.databinding.FragmentTrendingBinding;
import com.nidoham.flowtube.helper.SearchManager;
import com.nidoham.flowtube.data.TrendingContentManager;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class TrendingFragment extends Fragment implements VideoAdapter.OnVideoItemClickListener {

    private static final String TAG = "TrendingFragment";
    private static final int MINIMUM_VIDEO_DURATION = 60; // seconds
    private static final int LOAD_MORE_THRESHOLD = 3; // items before end to trigger loading
    
    private FragmentTrendingBinding binding;
    private final List<StreamInfoItem> videoList = new ArrayList<>();
    private VideoAdapter videoAdapter;
    private SearchManager searchManager;
    
    private final AtomicBoolean isLoading = new AtomicBoolean(false);
    private boolean hasMorePages = true;
    private boolean isInitialLoad = true;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentTrendingBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        initializeComponents();
        setupRecyclerView();
        loadTrendingContent();
    }

    private void initializeComponents() {
        searchManager = SearchManager.getInstance();
        
        binding.swipeRefreshLayout.setOnRefreshListener(this::refreshContent);
        binding.swipeRefreshLayout.setColorSchemeResources(
            android.R.color.holo_blue_bright,
            android.R.color.holo_green_light,
            android.R.color.holo_orange_light
        );
    }

    private void setupRecyclerView() {
        videoAdapter = new VideoAdapter(videoList, this);
        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        binding.recyclerViewTrending.setLayoutManager(layoutManager);
        binding.recyclerViewTrending.setAdapter(videoAdapter);

        binding.recyclerViewTrending.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (shouldLoadMoreContent(layoutManager, dy)) {
                    loadMoreContent();
                }
            }
        });
    }

    private boolean shouldLoadMoreContent(LinearLayoutManager layoutManager, int dy) {
        if (dy <= 0 || isLoading.get() || !hasMorePages) {
            return false;
        }
        
        int visibleItemCount = layoutManager.getChildCount();
        int totalItemCount = layoutManager.getItemCount();
        int pastVisibleItems = layoutManager.findFirstVisibleItemPosition();

        return (visibleItemCount + pastVisibleItems) >= totalItemCount - LOAD_MORE_THRESHOLD;
    }

    private void loadTrendingContent() {
        if (isLoading.get() || !isAdded()) {
            return;
        }

        isLoading.set(true);
        isInitialLoad = true;
        showLoadingIndicator();
        
        try {
            String selectedQuery = TrendingContentManager.getTrending("songs");
            SearchResultHandler handler = new SearchResultHandler();
            searchManager.setSearchResultListener(handler);
            searchManager.searchYouTube(selectedQuery, handler);
        } catch (Exception e) {
            Log.e(TAG, "Error initiating trending content search", e);
            handleSearchError("Failed to load trending content");
        }
    }

    private void loadMoreContent() {
        if (isLoading.get() || !hasMorePages || !isAdded()) {
            return;
        }
        isLoading.set(true);
        isInitialLoad = false;
        
        try {
            searchManager.loadMoreResults();
        } catch (Exception e) {
            Log.e(TAG, "Error loading more content", e);
            isLoading.set(false);
        }
    }

    private void refreshContent() {
        videoList.clear();
        if (videoAdapter != null) {
            videoAdapter.notifyDataSetChanged();
        }
        hasMorePages = true;
        if (searchManager != null) {
            searchManager.clearSearchCache();
        }
        loadTrendingContent();
    }

    private void showLoadingIndicator() {
        if (binding != null) {
            binding.progressBar.setVisibility(View.VISIBLE);
        }
    }

    private void hideLoadingIndicator() {
        if (binding != null) {
            binding.progressBar.setVisibility(View.GONE);
            binding.swipeRefreshLayout.setRefreshing(false);
        }
    }

    private void showError(String message) {
        if (isAdded() && getContext() != null) {
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
        }
    }

    private void handleSearchError(String defaultMessage) {
        isLoading.set(false);
        hideLoadingIndicator();
        showError(defaultMessage);
    }

    private boolean isValidStreamItem(StreamInfoItem item) {
        return item != null && 
               item.getUrl() != null && 
               !item.getUrl().isEmpty() &&
               item.getName() != null && 
               !item.getName().trim().isEmpty() &&
               item.getDuration() != -1;
    }

    private boolean isAcceptableVideo(StreamInfoItem item) {
        if (!isValidStreamItem(item)) {
            return false;
        }
        
        // Accept videos longer than minimum duration (filters out very short content)
        long duration = item.getDuration();
        return duration > MINIMUM_VIDEO_DURATION || duration == -1; // -1 indicates live stream
    }

    @Override
    public void onVideoItemClick(StreamInfoItem videoItem) {
        if (videoItem == null || videoItem.getUrl() == null || videoItem.getUrl().isEmpty()) {
            showError("Cannot open video");
            return;
        }

        try {
            Intent intent = new Intent(getContext(), PlayerActivity.class);
            intent.putExtra("video_url", videoItem.getUrl());
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error opening video: " + videoItem.getUrl(), e);
            showError("Cannot open video");
        }
    }

    @Override
    public void onMoreOptionsClick(StreamInfoItem videoItem, int position) {
        if (videoItem != null && isAdded()) {
            String videoTitle = videoItem.getName() != null ? videoItem.getName() : "Video";
            Toast.makeText(requireContext(), "Options for: " + videoTitle, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (videoList.isEmpty() && !isLoading.get()) {
            loadTrendingContent();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (searchManager != null) {
            searchManager.cancelCurrentSearch();
            searchManager.setSearchResultListener(null);
        }
        binding = null;
    }

    private class SearchResultHandler implements SearchManager.SearchResultListener {

        @Override
        public void onSearchStarted(String query) {
            Log.d(TAG, "Loading trending content: " + query);
        }

        @Override
        public void onSearchResults(SearchManager.SearchResults results) {
            isLoading.set(false);
            hideLoadingIndicator();

            if (!isAdded() || results == null) {
                return;
            }

            try {
                if (isInitialLoad) {
                    videoList.clear();
                }

                List<StreamInfoItem> validItems = new ArrayList<>();
                for (StreamInfoItem item : results.getStreamItems()) {
                    if (isAcceptableVideo(item)) {
                        validItems.add(item);
                    }
                }

                if (validItems.isEmpty() && isInitialLoad) {
                    showError("No trending videos available");
                    return;
                }

                int oldSize = videoList.size();
                videoList.addAll(validItems);
                
                if (videoAdapter != null) {
                    if (isInitialLoad) {
                        videoAdapter.notifyDataSetChanged();
                    } else {
                        videoAdapter.notifyItemRangeInserted(oldSize, validItems.size());
                    }
                }

                hasMorePages = results.hasMorePages;
                Log.d(TAG, "Loaded " + validItems.size() + " videos. Total: " + videoList.size());

            } catch (Exception e) {
                Log.e(TAG, "Error processing search results", e);
                showError("Error loading videos");
            }
        }

        @Override
        public void onSearchSuggestions(List<String> suggestions) {
            // Not implemented for trending content
        }

        @Override
        public void onSearchError(SearchManager.SearchError error) {
            isLoading.set(false);
            hideLoadingIndicator();

            if (!isAdded() || error == null) {
                return;
            }

            Log.e(TAG, "Search error: " + error.message, error.exception);
            
            String errorMessage;
            switch (error.type) {
                case NO_RESULTS_FOUND:
                    errorMessage = isInitialLoad ? "No trending videos found" : null;
                    break;
                case NETWORK_ERROR:
                    errorMessage = "Network error. Check your connection.";
                    break;
                case RECAPTCHA_REQUIRED:
                    errorMessage = "Verification required. Try again later.";
                    break;
                default:
                    errorMessage = "Failed to load videos";
                    break;
            }
            
            if (errorMessage != null) {
                showError(errorMessage);
            }
        }

        @Override
        public void onMoreResultsLoaded(List<InfoItem> items, boolean hasMorePages) {
            isLoading.set(false);
            
            if (!isAdded() || items == null) {
                return;
            }

            try {
                List<StreamInfoItem> validItems = new ArrayList<>();
                for (InfoItem item : items) {
                    if (item instanceof StreamInfoItem && isAcceptableVideo((StreamInfoItem) item)) {
                        validItems.add((StreamInfoItem) item);
                    }
                }

                if (!validItems.isEmpty()) {
                    int oldSize = videoList.size();
                    videoList.addAll(validItems);
                    if (videoAdapter != null) {
                        videoAdapter.notifyItemRangeInserted(oldSize, validItems.size());
                    }
                }

                TrendingFragment.this.hasMorePages = hasMorePages;
                Log.d(TAG, "Loaded " + validItems.size() + " additional videos");

            } catch (Exception e) {
                Log.e(TAG, "Error processing additional results", e);
            }
        }
    }
}