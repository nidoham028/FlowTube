package com.nidoham.opentube.fragments.list;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
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
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.nidoham.flowtube.PlayerActivity;
import com.nidoham.flowtube.adapter.VideoAdapter;
import com.nidoham.flowtube.databinding.FragmentHomeBinding;
import com.nidoham.flowtube.helper.SearchManager;
import com.nidoham.flowtube.data.TrendingContentManager;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HomeFragment extends Fragment implements VideoAdapter.OnVideoItemClickListener {

    public static final String EXTRA_VIDEO_URL = "video_url";
    public static final String EXTRA_VIDEO_TITLE = "video_title";
    public static final String EXTRA_CHANNEL_NAME = "channel_name";

    private static final String TAG = "HomeFragment";
    private static final int MINIMUM_VIDEO_DURATION = 60;
    private static final int LOAD_MORE_THRESHOLD = 5;
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 2000;
    private static final int TV_GRID_SPAN_COUNT = 3;
    private static final int PHONE_PORTRAIT_SPAN_COUNT = 1;
    private static final int PHONE_LANDSCAPE_SPAN_COUNT = 2;

    private FragmentHomeBinding binding;
    private final List<StreamInfoItem> videoList = Collections.synchronizedList(new ArrayList<>());
    private VideoAdapter videoAdapter;
    private SearchManager searchManager;
    private Handler mainHandler;
    private GridLayoutManager gridLayoutManager;

    private boolean isLoading = false;
    private boolean hasMorePages = true;
    private boolean isInitialLoad = true;
    private int retryAttempts = 0;
    private boolean isAndroidTV = false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mainHandler = new Handler(Looper.getMainLooper());
        detectAndroidTV();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        initializeComponents();
        setupRecyclerView();
        
        if (videoList.isEmpty()) {
            loadTrendingContent();
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (gridLayoutManager != null) {
            updateLayoutManagerSpanCount(newConfig);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (videoList.isEmpty() && !isLoading) {
            loadTrendingContent();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (searchManager != null) {
            searchManager.cancelCurrentSearch();
        }
    }

    @Override
    public void onDestroyView() {
        cleanup();
        binding = null;
        super.onDestroyView();
    }

    private void detectAndroidTV() {
        Context context = getContext();
        if (context != null) {
            isAndroidTV = context.getPackageManager().hasSystemFeature("android.software.leanback");
        }
    }

    private void updateLayoutManagerSpanCount(Configuration config) {
        int newSpanCount = calculateSpanCount(config);
        if (gridLayoutManager.getSpanCount() != newSpanCount) {
            gridLayoutManager.setSpanCount(newSpanCount);
        }
    }

    private int calculateSpanCount(Configuration config) {
        if (isAndroidTV) return TV_GRID_SPAN_COUNT;
        return config.orientation == Configuration.ORIENTATION_LANDSCAPE
                ? PHONE_LANDSCAPE_SPAN_COUNT
                : PHONE_PORTRAIT_SPAN_COUNT;
    }

    private void initializeComponents() {
        searchManager = SearchManager.getInstance();
        
        binding.swipeRefreshLayout.setOnRefreshListener(this::refreshContent);
        binding.swipeRefreshLayout.setColorSchemeResources(
                android.R.color.holo_blue_bright,
                android.R.color.holo_green_light,
                android.R.color.holo_orange_light
        );
        
        if (isAndroidTV) {
            binding.swipeRefreshLayout.setEnabled(false);
        }
    }

    private void setupRecyclerView() {
        videoAdapter = new VideoAdapter(videoList, this);
        
        Configuration config = getResources().getConfiguration();
        int spanCount = calculateSpanCount(config);
        gridLayoutManager = new GridLayoutManager(requireContext(), spanCount);
        
        binding.recyclerViewTrending.setLayoutManager(gridLayoutManager);
        binding.recyclerViewTrending.setAdapter(videoAdapter);
        binding.recyclerViewTrending.setItemAnimator(null);
        
        if (isAndroidTV) {
            binding.recyclerViewTrending.setFocusable(true);
            binding.recyclerViewTrending.setFocusableInTouchMode(false);
        }
        
        binding.recyclerViewTrending.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (shouldLoadMoreContent(dy)) {
                    loadMoreContent();
                }
            }
        });
    }

    private boolean shouldLoadMoreContent(int dy) {
        if (dy <= 0 || isLoading || !hasMorePages || !isAdded()) {
            return false;
        }
        
        int visibleItemCount = gridLayoutManager.getChildCount();
        int totalItemCount = gridLayoutManager.getItemCount();
        int pastVisibleItems = gridLayoutManager.findFirstVisibleItemPosition();
        
        return (visibleItemCount + pastVisibleItems) >= totalItemCount - LOAD_MORE_THRESHOLD;
    }

    private void loadTrendingContent() {
        if (isLoading || !isAdded()) return;
        
        isLoading = true;
        isInitialLoad = true;
        retryAttempts = 0;
        showLoadingIndicator();
        
        performSearch();
    }

    private void performSearch() {
        if (!isAdded()) return;
        
        String query = TrendingContentManager.getTrending("songs");
        SearchResultHandler handler = new SearchResultHandler(this);
        
        if (searchManager != null) {
            searchManager.setSearchResultListener(handler);
            searchManager.searchYouTube(query, handler);
        } else {
            handleSearchError("Search service unavailable", false);
        }
    }

    private void loadMoreContent() {
        if (isLoading || !hasMorePages || !isAdded()) return;
        
        isLoading = true;
        isInitialLoad = false;
        
        if (searchManager != null) {
            searchManager.loadMoreResults();
        }
    }

    private void refreshContent() {
        if (!isAdded()) return;
        
        synchronized (videoList) {
            videoList.clear();
        }
        
        if (videoAdapter != null) {
            runOnUiThread(() -> {
                if (videoAdapter != null && isAdded()) {
                    videoAdapter.notifyDataSetChanged();
                }
            });
        }
        
        hasMorePages = true;
        retryAttempts = 0;
        
        if (searchManager != null) {
            searchManager.clearSearchCache();
        }
        
        loadTrendingContent();
    }

    private void showLoadingIndicator() {
        runOnUiThread(() -> {
            if (binding != null && isAdded()) {
                binding.progressBar.setVisibility(View.VISIBLE);
            }
        });
    }

    private void hideLoadingIndicator() {
        runOnUiThread(() -> {
            if (binding != null && isAdded()) {
                binding.progressBar.setVisibility(View.GONE);
                binding.swipeRefreshLayout.setRefreshing(false);
            }
        });
    }

    private void showError(String message) {
        runOnUiThread(() -> safeToast(message));
    }

    private void safeToast(String msg) {
        if (isAdded() && getContext() != null) {
            Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
        }
    }

    private void runOnUiThread(Runnable action) {
        if (mainHandler != null && isAdded()) {
            mainHandler.post(action);
        }
    }

    private void handleSearchError(String defaultMessage, boolean shouldRetry) {
        isLoading = false;
        hideLoadingIndicator();
        
        if (shouldRetry && retryAttempts < MAX_RETRY_ATTEMPTS && isAdded()) {
            retryAttempts++;
            Log.d(TAG, "Retrying search, attempt " + retryAttempts + "/" + MAX_RETRY_ATTEMPTS);
            
            mainHandler.postDelayed(() -> {
                if (isAdded()) performSearch();
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
        if (!isValidStreamItem(item)) return false;
        long duration = item.getDuration();
        return duration > MINIMUM_VIDEO_DURATION || duration == -1;
    }

    @Override
    public void onVideoItemClick(StreamInfoItem videoItem) {
        if (videoItem == null || videoItem.getUrl() == null || videoItem.getUrl().trim().isEmpty()) {
            showError("Cannot open video");
            return;
        }
        
        Intent intent = new Intent(getContext(), PlayerActivity.class);
        intent.putExtra(EXTRA_VIDEO_URL, videoItem.getUrl());
        intent.putExtra(EXTRA_VIDEO_TITLE, videoItem.getName());
        intent.putExtra(EXTRA_CHANNEL_NAME, videoItem.getUploaderName());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        
        if (isAndroidTV) {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        }
        
        startActivity(intent);
    }

    @Override
    public void onMoreOptionsClick(StreamInfoItem videoItem, int position) {
        if (videoItem != null && isAdded()) {
            String videoTitle = videoItem.getName() != null ? videoItem.getName() : "Video";
            showError("Options for: " + videoTitle);
        }
    }

    private void cleanup() {
        if (searchManager != null) {
            searchManager.cancelCurrentSearch();
            searchManager.setSearchResultListener(null);
        }
        
        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
        }
        
        synchronized (videoList) {
            videoList.clear();
        }
        
        videoAdapter = null;
        gridLayoutManager = null;
    }

    private static class SearchResultHandler implements SearchManager.SearchResultListener {
        private final WeakReference<HomeFragment> fragmentRef;

        SearchResultHandler(HomeFragment fragment) {
            this.fragmentRef = new WeakReference<>(fragment);
        }

        private HomeFragment getFragment() {
            HomeFragment fragment = fragmentRef.get();
            return (fragment != null && fragment.isAdded()) ? fragment : null;
        }

        @Override
        public void onSearchStarted(String query) {
            HomeFragment fragment = getFragment();
            if (fragment != null) {
                Log.d(TAG, "Loading trending content: " + query);
            }
        }

        @Override
        public void onSearchResults(SearchManager.SearchResults results) {
            HomeFragment fragment = getFragment();
            if (fragment == null || results == null) return;
            
            fragment.isLoading = false;
            fragment.hideLoadingIndicator();
            
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
                if (fragment.videoAdapter != null && fragment.isAdded()) {
                    if (fragment.isInitialLoad) {
                        fragment.videoAdapter.notifyDataSetChanged();
                    } else {
                        fragment.videoAdapter.notifyItemRangeInserted(oldSize, validItems.size());
                    }
                }
            });
            
            fragment.hasMorePages = results.hasMorePages;
            Log.d(TAG, "Loaded " + validItems.size() + " videos. Total: " + fragment.videoList.size());
        }

        @Override
        public void onSearchSuggestions(List<String> suggestions) {
            // Not implemented for trending content
        }

        @Override
        public void onSearchError(SearchManager.SearchError error) {
            HomeFragment fragment = getFragment();
            if (fragment == null || error == null) return;
            
            fragment.isLoading = false;
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
            HomeFragment fragment = getFragment();
            if (fragment == null || items == null) return;
            
            fragment.isLoading = false;
            
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
                    if (fragment.videoAdapter != null && fragment.isAdded()) {
                        fragment.videoAdapter.notifyItemRangeInserted(oldSize, validItems.size());
                    }
                });
            }
            
            fragment.hasMorePages = hasMorePages;
            Log.d(TAG, "Loaded " + validItems.size() + " additional videos");
        }
    }
}