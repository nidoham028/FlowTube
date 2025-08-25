package com.nidoham.flowtube.fragment.home;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.nidoham.flowtube.PlayerActivity;
import com.nidoham.flowtube.adapter.VideoAdapter;
import com.nidoham.flowtube.databinding.FragmentTrendingBinding;
import com.nidoham.flowtube.helper.SearchManager;
import com.nidoham.flowtube.data.TrendingContentManager;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class TrendingFragment extends Fragment implements VideoAdapter.OnVideoItemClickListener {

    private static final String TAG = "TrendingFragment";
    private static final int MINIMUM_VIDEO_DURATION = 60; // seconds
    private static final int LOAD_MORE_THRESHOLD = 5; // items before end to trigger loading
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 2000;
    private static final int TV_GRID_SPAN_COUNT = 3;
    private static final int PHONE_PORTRAIT_SPAN_COUNT = 1;
    private static final int PHONE_LANDSCAPE_SPAN_COUNT = 2;
    
    private FragmentTrendingBinding binding;
    private final List<StreamInfoItem> videoList = Collections.synchronizedList(new ArrayList<>());
    private VideoAdapter videoAdapter;
    private SearchManager searchManager;
    private Handler mainHandler;
    
    private final AtomicBoolean isLoading = new AtomicBoolean(false);
    private final AtomicBoolean isDestroyed = new AtomicBoolean(false);
    private boolean hasMorePages = true;
    private boolean isInitialLoad = true;
    private int retryAttempts = 0;
    private String lastQuery = "";
    
    // Android TV detection and layout management
    private boolean isAndroidTV = false;
    private RecyclerView.LayoutManager layoutManager;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mainHandler = new Handler(Looper.getMainLooper());
        detectAndroidTV();
        
        // Add lifecycle observer for proper cleanup
        getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override
            public void onDestroy(@NonNull LifecycleOwner owner) {
                cleanup();
            }
        });
    }

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
        
        if (isDestroyed.get()) {
            return;
        }
        
        initializeComponents();
        setupRecyclerView();
        loadTrendingContent();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (binding != null && !isDestroyed.get()) {
            updateLayoutForConfiguration(newConfig);
        }
    }

    private void detectAndroidTV() {
        Context context = getContext();
        if (context != null) {
            isAndroidTV = context.getPackageManager().hasSystemFeature("android.software.leanback");
        }
    }

    private void updateLayoutForConfiguration(Configuration config) {
        if (layoutManager instanceof GridLayoutManager) {
            GridLayoutManager gridLayoutManager = (GridLayoutManager) layoutManager;
            
            if (isAndroidTV) {
                gridLayoutManager.setSpanCount(TV_GRID_SPAN_COUNT);
            } else {
                int spanCount = config.orientation == Configuration.ORIENTATION_LANDSCAPE 
                    ? PHONE_LANDSCAPE_SPAN_COUNT : PHONE_PORTRAIT_SPAN_COUNT;
                gridLayoutManager.setSpanCount(spanCount);
            }
        }
    }

    private void initializeComponents() {
        try {
            searchManager = SearchManager.getInstance();
            
            if (binding != null) {
                binding.swipeRefreshLayout.setOnRefreshListener(this::refreshContent);
                binding.swipeRefreshLayout.setColorSchemeResources(
                    android.R.color.holo_blue_bright,
                    android.R.color.holo_green_light,
                    android.R.color.holo_orange_light
                );
                
                // Enhance for Android TV
                if (isAndroidTV) {
                    binding.swipeRefreshLayout.setEnabled(false); // Disable swipe refresh on TV
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing components", e);
        }
    }

    private void setupRecyclerView() {
        if (binding == null || isDestroyed.get()) {
            return;
        }

        try {
            videoAdapter = new VideoAdapter(videoList, this);
            
            // Determine appropriate layout manager based on device type and orientation
            if (isAndroidTV) {
                layoutManager = new GridLayoutManager(requireContext(), TV_GRID_SPAN_COUNT);
            } else {
                Configuration config = getResources().getConfiguration();
                int spanCount = config.orientation == Configuration.ORIENTATION_LANDSCAPE 
                    ? PHONE_LANDSCAPE_SPAN_COUNT : PHONE_PORTRAIT_SPAN_COUNT;
                layoutManager = new GridLayoutManager(requireContext(), spanCount);
            }
            
            binding.recyclerViewTrending.setLayoutManager(layoutManager);
            binding.recyclerViewTrending.setAdapter(videoAdapter);
            
            // Optimize for Android TV navigation
            if (isAndroidTV) {
                binding.recyclerViewTrending.setFocusable(true);
                binding.recyclerViewTrending.setFocusableInTouchMode(false);
            }

            binding.recyclerViewTrending.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    super.onScrolled(recyclerView, dx, dy);
                    if (!isDestroyed.get() && shouldLoadMoreContent(dy)) {
                        loadMoreContent();
                    }
                }
            });
            
            // Set item animator for smoother transitions
            binding.recyclerViewTrending.setItemAnimator(null); // Disable for performance on TV
            
        } catch (Exception e) {
            Log.e(TAG, "Error setting up RecyclerView", e);
        }
    }

    private boolean shouldLoadMoreContent(int dy) {
        if (dy <= 0 || isLoading.get() || !hasMorePages || isDestroyed.get()) {
            return false;
        }
        
        try {
            int visibleItemCount, totalItemCount, pastVisibleItems;
            
            if (layoutManager instanceof GridLayoutManager) {
                GridLayoutManager gridLayoutManager = (GridLayoutManager) layoutManager;
                visibleItemCount = gridLayoutManager.getChildCount();
                totalItemCount = gridLayoutManager.getItemCount();
                pastVisibleItems = gridLayoutManager.findFirstVisibleItemPosition();
            } else if (layoutManager instanceof LinearLayoutManager) {
                LinearLayoutManager linearLayoutManager = (LinearLayoutManager) layoutManager;
                visibleItemCount = linearLayoutManager.getChildCount();
                totalItemCount = linearLayoutManager.getItemCount();
                pastVisibleItems = linearLayoutManager.findFirstVisibleItemPosition();
            } else {
                return false;
            }

            return (visibleItemCount + pastVisibleItems) >= totalItemCount - LOAD_MORE_THRESHOLD;
        } catch (Exception e) {
            Log.e(TAG, "Error checking scroll position", e);
            return false;
        }
    }

    private void loadTrendingContent() {
        if (isLoading.get() || isDestroyed.get()) {
            return;
        }

        isLoading.set(true);
        isInitialLoad = true;
        retryAttempts = 0;
        showLoadingIndicator();
        
        performSearch();
    }

    private void performSearch() {
        if (isDestroyed.get()) {
            return;
        }

        try {
            lastQuery = TrendingContentManager.getTrending("songs");
            SearchResultHandler handler = new SearchResultHandler(this);
            
            if (searchManager != null) {
                searchManager.setSearchResultListener(handler);
                searchManager.searchYouTube(lastQuery, handler);
            } else {
                handleSearchError("Search service unavailable", false);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initiating trending content search", e);
            handleSearchError("Failed to load trending content", true);
        }
    }

    private void loadMoreContent() {
        if (isLoading.get() || !hasMorePages || isDestroyed.get()) {
            return;
        }
        
        isLoading.set(true);
        isInitialLoad = false;
        
        try {
            if (searchManager != null) {
                searchManager.loadMoreResults();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading more content", e);
            isLoading.set(false);
        }
    }

    private void refreshContent() {
        if (isDestroyed.get()) {
            return;
        }

        synchronized (videoList) {
            videoList.clear();
        }
        
        if (videoAdapter != null && !isDestroyed.get()) {
            runOnUiThread(() -> {
                if (videoAdapter != null && !isDestroyed.get()) {
                    videoAdapter.notifyDataSetChanged();
                }
            });
        }
        
        hasMorePages = true;
        retryAttempts = 0;
        
        if (searchManager != null) {
            try {
                searchManager.clearSearchCache();
            } catch (Exception e) {
                Log.w(TAG, "Error clearing search cache", e);
            }
        }
        
        loadTrendingContent();
    }

    private void showLoadingIndicator() {
        runOnUiThread(() -> {
            if (binding != null && !isDestroyed.get()) {
                binding.progressBar.setVisibility(View.VISIBLE);
            }
        });
    }

    private void hideLoadingIndicator() {
        runOnUiThread(() -> {
            if (binding != null && !isDestroyed.get()) {
                binding.progressBar.setVisibility(View.GONE);
                binding.swipeRefreshLayout.setRefreshing(false);
            }
        });
    }

    private void showError(String message) {
        runOnUiThread(() -> {
            if (isAdded() && getContext() != null && !isDestroyed.get()) {
                try {
                    Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Log.w(TAG, "Error showing toast", e);
                }
            }
        });
    }

    private void runOnUiThread(Runnable action) {
        if (mainHandler != null && !isDestroyed.get()) {
            mainHandler.post(action);
        }
    }

    private void handleSearchError(String defaultMessage, boolean shouldRetry) {
        isLoading.set(false);
        hideLoadingIndicator();
        
        if (shouldRetry && retryAttempts < MAX_RETRY_ATTEMPTS && !isDestroyed.get()) {
            retryAttempts++;
            Log.d(TAG, "Retrying search, attempt " + retryAttempts + "/" + MAX_RETRY_ATTEMPTS);
            
            mainHandler.postDelayed(() -> {
                if (!isDestroyed.get()) {
                    performSearch();
                }
            }, RETRY_DELAY_MS);
        } else {
            showError(defaultMessage);
        }
    }

    private boolean isValidStreamItem(StreamInfoItem item) {
        return item != null && 
               item.getUrl() != null && 
               !item.getUrl().trim().isEmpty() &&
               item.getName() != null && 
               !item.getName().trim().isEmpty();
    }

    private boolean isAcceptableVideo(StreamInfoItem item) {
        if (!isValidStreamItem(item)) {
            return false;
        }
        
        long duration = item.getDuration();
        return duration > MINIMUM_VIDEO_DURATION || duration == -1; // -1 indicates live stream
    }

    @Override
    public void onVideoItemClick(StreamInfoItem videoItem) {
        if (videoItem == null || videoItem.getUrl() == null || videoItem.getUrl().trim().isEmpty()) {
            showError("Cannot open video");
            return;
        }

        try {
            Intent intent = new Intent(getContext(), PlayerActivity.class);
            intent.putExtra("video_url", videoItem.getUrl());
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            // Add Android TV specific flags
            if (isAndroidTV) {
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            }
            
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error opening video: " + videoItem.getUrl(), e);
            showError("Cannot open video");
        }
    }

    @Override
    public void onMoreOptionsClick(StreamInfoItem videoItem, int position) {
        if (videoItem != null && isAdded() && !isDestroyed.get()) {
            String videoTitle = videoItem.getName() != null ? videoItem.getName() : "Video";
            showError("Options for: " + videoTitle);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isDestroyed.get() && videoList.isEmpty() && !isLoading.get()) {
            loadTrendingContent();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (searchManager != null) {
            try {
                searchManager.cancelCurrentSearch();
            } catch (Exception e) {
                Log.w(TAG, "Error cancelling search on pause", e);
            }
        }
    }

    @Override
    public void onDestroyView() {
        isDestroyed.set(true);
        cleanup();
        binding = null;
        super.onDestroyView();
    }

    private void cleanup() {
        if (searchManager != null) {
            try {
                searchManager.cancelCurrentSearch();
                searchManager.setSearchResultListener(null);
            } catch (Exception e) {
                Log.w(TAG, "Error during search manager cleanup", e);
            }
        }
        
        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
        }
        
        synchronized (videoList) {
            videoList.clear();
        }
        
        videoAdapter = null;
        layoutManager = null;
    }

    private static class SearchResultHandler implements SearchManager.SearchResultListener {
        private final WeakReference<TrendingFragment> fragmentRef;

        SearchResultHandler(TrendingFragment fragment) {
            this.fragmentRef = new WeakReference<>(fragment);
        }

        private TrendingFragment getFragment() {
            TrendingFragment fragment = fragmentRef.get();
            return (fragment != null && !fragment.isDestroyed.get()) ? fragment : null;
        }

        @Override
        public void onSearchStarted(String query) {
            TrendingFragment fragment = getFragment();
            if (fragment != null) {
                Log.d(TAG, "Loading trending content: " + query);
            }
        }

        @Override
        public void onSearchResults(SearchManager.SearchResults results) {
            TrendingFragment fragment = getFragment();
            if (fragment == null || results == null) {
                return;
            }

            fragment.isLoading.set(false);
            fragment.hideLoadingIndicator();

            try {
                if (fragment.isInitialLoad) {
                    synchronized (fragment.videoList) {
                        fragment.videoList.clear();
                    }
                }

                List<StreamInfoItem> validItems = new ArrayList<>();
                for (StreamInfoItem item : results.getStreamItems()) {
                    if (fragment.isAcceptableVideo(item)) {
                        validItems.add(item);
                    }
                }

                if (validItems.isEmpty() && fragment.isInitialLoad) {
                    fragment.showError("No trending videos available");
                    return;
                }

                final int oldSize;
                synchronized (fragment.videoList) {
                    oldSize = fragment.videoList.size();
                    fragment.videoList.addAll(validItems);
                }
                
                fragment.runOnUiThread(() -> {
                    if (fragment.videoAdapter != null && !fragment.isDestroyed.get()) {
                        if (fragment.isInitialLoad) {
                            fragment.videoAdapter.notifyDataSetChanged();
                        } else {
                            fragment.videoAdapter.notifyItemRangeInserted(oldSize, validItems.size());
                        }
                    }
                });

                fragment.hasMorePages = results.hasMorePages;
                Log.d(TAG, "Loaded " + validItems.size() + " videos. Total: " + fragment.videoList.size());

            } catch (Exception e) {
                Log.e(TAG, "Error processing search results", e);
                fragment.showError("Error loading videos");
            }
        }

        @Override
        public void onSearchSuggestions(List<String> suggestions) {
            // Not implemented for trending content
        }

        @Override
        public void onSearchError(SearchManager.SearchError error) {
            TrendingFragment fragment = getFragment();
            if (fragment == null || error == null) {
                return;
            }

            fragment.isLoading.set(false);
            fragment.hideLoadingIndicator();

            Log.e(TAG, "Search error: " + error.message, error.exception);
            
            String errorMessage;
            boolean shouldRetry = false;
            
            switch (error.type) {
                case NO_RESULTS_FOUND:
                    errorMessage = fragment.isInitialLoad ? "No trending videos found" : null;
                    break;
                case NETWORK_ERROR:
                    errorMessage = "Network error. Check your connection.";
                    shouldRetry = true;
                    break;
                case RECAPTCHA_REQUIRED:
                    errorMessage = "Verification required. Try again later.";
                    shouldRetry = true;
                    break;
                default:
                    errorMessage = "Failed to load videos";
                    shouldRetry = true;
                    break;
            }
            
            if (errorMessage != null) {
                fragment.handleSearchError(errorMessage, shouldRetry);
            }
        }

        @Override
        public void onMoreResultsLoaded(List<InfoItem> items, boolean hasMorePages) {
            TrendingFragment fragment = getFragment();
            if (fragment == null || items == null) {
                return;
            }

            fragment.isLoading.set(false);
            
            try {
                List<StreamInfoItem> validItems = new ArrayList<>();
                for (InfoItem item : items) {
                    if (item instanceof StreamInfoItem && fragment.isAcceptableVideo((StreamInfoItem) item)) {
                        validItems.add((StreamInfoItem) item);
                    }
                }

                if (!validItems.isEmpty()) {
                    final int oldSize;
                    synchronized (fragment.videoList) {
                        oldSize = fragment.videoList.size();
                        fragment.videoList.addAll(validItems);
                    }
                    
                    fragment.runOnUiThread(() -> {
                        if (fragment.videoAdapter != null && !fragment.isDestroyed.get()) {
                            fragment.videoAdapter.notifyItemRangeInserted(oldSize, validItems.size());
                        }
                    });
                }

                fragment.hasMorePages = hasMorePages;
                Log.d(TAG, "Loaded " + validItems.size() + " additional videos");

            } catch (Exception e) {
                Log.e(TAG, "Error processing additional results", e);
            }
        }
    }
}