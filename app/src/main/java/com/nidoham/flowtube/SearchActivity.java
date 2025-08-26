package com.nidoham.flowtube;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.nidoham.flowtube.adapter.VideoAdapter;
import com.nidoham.flowtube.adapter.SearchSuggestionsAdapter;
import com.nidoham.flowtube.databinding.ActivitySearchBinding;
import com.nidoham.flowtube.helper.SearchManager;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Professional search activity for FlowTube application.
 *
 * This activity provides comprehensive search functionality including:
 * - Real-time search suggestions with debouncing
 * - Infinite scroll pagination for search results
 * - State preservation across configuration changes
 * - Proper memory management and lifecycle handling
 * - Professional error handling and user feedback
 *
 * @author FlowTube Team
 * @version 2.2 (Corrected)
 * @since API level 21
 */
public class SearchActivity extends AppCompatActivity implements
        VideoAdapter.OnVideoItemClickListener,
        SearchSuggestionsAdapter.OnSuggestionClickListener {

    private static final String TAG = "SearchActivity";
    private static final String CLASS_NAME = SearchActivity.class.getSimpleName();

    // Configuration Constants
    private static final long SUGGESTION_DELAY_MS = 300L;
    private static final int MIN_QUERY_LENGTH = 2;
    private static final int MAX_QUERY_LENGTH = 100;
    private static final int LOAD_MORE_THRESHOLD = 3;
    private static final int MAX_SUGGESTIONS = 10;

    // State Keys
    private static final String SAVED_QUERY = "saved_query";
    private static final String SAVED_SCROLL_POSITION = "saved_scroll_position";

    // UI Components
    private ActivitySearchBinding binding;

    // Core Components
    private SearchManager searchManager;
    private SearchManager.SearchResultListener searchResultListener;
    private VideoAdapter videoAdapter;
    private SearchSuggestionsAdapter suggestionsAdapter;
    private LinearLayoutManager resultsLayoutManager;

    // Data Collections - Thread-safe
    private final List<StreamInfoItem> searchResults = Collections.synchronizedList(new ArrayList<>());
    private final List<String> suggestions = Collections.synchronizedList(new ArrayList<>());

    // State Management - Thread-safe atomics
    private final AtomicBoolean isLoadingMore = new AtomicBoolean(false);
    private final AtomicBoolean hasMorePages = new AtomicBoolean(false);
    private final AtomicBoolean isActivityDestroyed = new AtomicBoolean(false);

    // Threading
    private Handler suggestionHandler;
    private Runnable suggestionRunnable;

    // Search State
    private String currentQuery = "";
    private String lastSearchedQuery = "";
    private int savedScrollPosition = 0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            Log.d(TAG, CLASS_NAME + " onCreate() started");

            initializeBinding();
            setupStatusBar();
            initializeComponents();
            setupViews();
            restoreSavedState(savedInstanceState);
            handleSearchIntent();

            Log.i(TAG, CLASS_NAME + " successfully initialized");

        } catch (Exception e) {
            Log.e(TAG, "Critical error during onCreate()", e);
            handleCriticalError("Failed to initialize search", e);
        }
    }

    private void initializeBinding() {
        binding = ActivitySearchBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
    }

    private void setupStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                int seedColor = ContextCompat.getColor(this, R.color.seed);
                getWindow().setStatusBarColor(seedColor);
                getWindow().setNavigationBarColor(seedColor);
            } catch (Exception e) {
                Log.w(TAG, "Failed to setup status bar", e);
            }
        }
    }

    private void initializeComponents() {
        suggestionHandler = new Handler(Looper.getMainLooper());
        searchManager = SearchManager.getInstance();
        searchResultListener = new SearchResultHandler(this);
        searchManager.setSearchResultListener(searchResultListener);
    }

    private void setupViews() {
        setupSearchInput();
        setupRecyclerViews();
        setupClickListeners();
    }

    private void setupSearchInput() {
        binding.searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String newQuery = sanitizeQuery(s.toString());
                if (!newQuery.equals(currentQuery)) {
                    currentQuery = newQuery;
                    handleQueryChange(currentQuery);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (binding.searchInputLayout.getError() != null) {
                    binding.searchInputLayout.setError(null);
                }
            }
        });

        binding.searchEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                performSearch();
                return true;
            }
            return false;
        });

        binding.searchEditText.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && isValidQuery(currentQuery) && !suggestions.isEmpty()) {
                showSuggestionsPanel();
            } else if (!hasFocus) {
                // Delay hiding to allow suggestion clicks
                new Handler(Looper.getMainLooper()).postDelayed(this::hideSuggestionsPanel, 150);
            }
        });
    }

    private void setupRecyclerViews() {
        videoAdapter = new VideoAdapter(searchResults, this);
        resultsLayoutManager = new LinearLayoutManager(this);
        binding.searchResultsRecyclerView.setLayoutManager(resultsLayoutManager);
        binding.searchResultsRecyclerView.setAdapter(videoAdapter);
        binding.searchResultsRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (dy > 0 && shouldLoadMoreResults()) {
                    loadMoreResults();
                }
            }
        });

        suggestionsAdapter = new SearchSuggestionsAdapter(suggestions, this);
        binding.suggestionsList.setLayoutManager(new LinearLayoutManager(this));
        binding.suggestionsList.setAdapter(suggestionsAdapter);
    }

    private void setupClickListeners() {
        binding.searchButton.setOnClickListener(v -> performSearch());
        binding.correctSuggestion.setOnClickListener(v -> {
            String suggestionText = binding.correctSuggestion.getText().toString();
            if (suggestionText.startsWith("Showing results for ")) {
                String correctedQuery = suggestionText.substring("Showing results for ".length());
                if (isValidQuery(correctedQuery)) {
                    binding.searchEditText.setText(correctedQuery);
                    performSearch();
                }
            }
        });
    }

    private void handleQueryChange(@NonNull String query) {
        cancelPendingSuggestions();
        if (isValidQuery(query)) {
            suggestionRunnable = () -> {
                if (!isActivityDestroyed.get() && currentQuery.equals(query)) {
                    loadSuggestions(query);
                }
            };
            suggestionHandler.postDelayed(suggestionRunnable, SUGGESTION_DELAY_MS);
        } else {
            hideSuggestionsPanel();
        }
    }

    private void cancelPendingSuggestions() {
        if (suggestionRunnable != null) {
            suggestionHandler.removeCallbacks(suggestionRunnable);
            suggestionRunnable = null;
        }
    }

    private void loadSuggestions(@NonNull String query) {
        if (isActivityDestroyed.get()) return;
        // The listener is already set on the SearchManager instance.
        // We just need to call the method.
        searchManager.getSearchSuggestions(query);
        Log.d(TAG, "Requesting suggestions for query: " + query);
    }

    @UiThread
    public void performSearch() {
        String query = sanitizeQuery(currentQuery);
        ValidationResult validation = validateSearchQuery(query);
        if (!validation.isValid) {
            binding.searchInputLayout.setError(validation.errorMessage);
            return;
        }

        binding.searchInputLayout.setError(null);
        hideKeyboard();
        hideSuggestionsPanel();

        if (query.equals(lastSearchedQuery) && !searchResults.isEmpty()) {
            Log.d(TAG, "Skipping duplicate search for: " + query);
            return;
        }

        lastSearchedQuery = query;
        clearSearchResults();
        startSearch(query);
    }

    private void startSearch(@NonNull String query) {
        if (searchManager.isSearching() || isActivityDestroyed.get()) {
            Log.w(TAG, "Cannot start search, already searching or activity is destroyed.");
            return;
        }
        showLoadingState();
        hideEmptyState();
        searchManager.searchYouTube(query, searchResultListener);
        Log.i(TAG, "Started search for query: " + query);
    }

    private void loadMoreResults() {
        if (isLoadingMore.get() || !hasMorePages.get() || searchManager.isSearching() || isActivityDestroyed.get()) {
            return;
        }
        isLoadingMore.set(true);
        // Add a visual indicator for loading more
        showUserMessage("Loading more...");
        searchManager.loadMoreResults();
        Log.d(TAG, "Requesting more search results.");
    }

    private boolean shouldLoadMoreResults() {
        if (isLoadingMore.get() || !hasMorePages.get()) {
            return false;
        }
        int visibleItemCount = resultsLayoutManager.getChildCount();
        int totalItemCount = resultsLayoutManager.getItemCount();
        int firstVisibleItemPosition = resultsLayoutManager.findFirstVisibleItemPosition();

        return (visibleItemCount + firstVisibleItemPosition) >= totalItemCount - LOAD_MORE_THRESHOLD && firstVisibleItemPosition >= 0;
    }

    private void clearSearchResults() {
        runOnUiThread(() -> {
            if (isActivityDestroyed.get()) return;
            synchronized (searchResults) {
                searchResults.clear();
            }
            videoAdapter.notifyDataSetChanged();
        });
    }

    private void showLoadingState() {
        runOnUiThread(() -> {
            if (isActivityDestroyed.get()) return;
            binding.loadingProgressBar.setVisibility(View.VISIBLE);
            binding.searchResultsRecyclerView.setVisibility(View.GONE);
            binding.emptyStateView.setVisibility(View.GONE);
            binding.correctSuggestion.setVisibility(View.GONE);
        });
    }

    private void showResultsState() {
        runOnUiThread(() -> {
            if (isActivityDestroyed.get()) return;
            binding.loadingProgressBar.setVisibility(View.GONE);
            binding.searchResultsRecyclerView.setVisibility(View.VISIBLE);
            binding.emptyStateView.setVisibility(View.GONE);
            if (savedScrollPosition > 0) {
                binding.searchResultsRecyclerView.scrollToPosition(savedScrollPosition);
                savedScrollPosition = 0;
            }
        });
    }

    private void showEmptyState(String title, String description) {
        runOnUiThread(() -> {
            if (isActivityDestroyed.get()) return;
            binding.loadingProgressBar.setVisibility(View.GONE);
            binding.searchResultsRecyclerView.setVisibility(View.GONE);
            binding.emptyStateView.setVisibility(View.VISIBLE);
            binding.emptyStateTitle.setText(title);
            binding.emptyStateDescription.setText(description);
        });
    }

    private void hideEmptyState() {
        runOnUiThread(() -> {
            if (isActivityDestroyed.get()) return;
            binding.emptyStateView.setVisibility(View.GONE);
        });
    }

    private void showSuggestionsPanel() {
        runOnUiThread(() -> {
            if (isActivityDestroyed.get() || suggestions.isEmpty()) return;
            binding.suggestionsPanel.setVisibility(View.VISIBLE);
        });
    }

    private void hideSuggestionsPanel() {
        runOnUiThread(() -> {
            if (isActivityDestroyed.get()) return;
            binding.suggestionsPanel.setVisibility(View.GONE);
        });
    }

    private void showCorrectionSuggestion(String suggestion) {
        runOnUiThread(() -> {
            if (isActivityDestroyed.get() || suggestion == null || suggestion.isEmpty()) return;
            binding.correctSuggestion.setText("Showing results for " + suggestion);
            binding.correctSuggestion.setVisibility(View.VISIBLE);
        });
    }

    private void hideKeyboard() {
        View view = getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void restoreSavedState(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            String savedQuery = savedInstanceState.getString(SAVED_QUERY);
            if (savedQuery != null) {
                binding.searchEditText.setText(savedQuery);
                currentQuery = savedQuery;
            }
            savedScrollPosition = savedInstanceState.getInt(SAVED_SCROLL_POSITION, 0);
        }
    }

    private void handleSearchIntent() {
        Intent intent = getIntent();
        if (intent != null && Intent.ACTION_SEARCH.equals(intent.getAction())) {
            String query = intent.getStringExtra("query");
            if (query != null && !query.trim().isEmpty()) {
                binding.searchEditText.setText(query);
                currentQuery = query.trim();
                performSearch();
            }
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(SAVED_QUERY, currentQuery);
        if (resultsLayoutManager != null) {
            outState.putInt(SAVED_SCROLL_POSITION, resultsLayoutManager.findFirstVisibleItemPosition());
        }
    }

    @Override
    public void onVideoItemClick(StreamInfoItem videoItem) {
        openVideo(videoItem);
    }
    
    @Override
    public void onMoreOptionsClick(StreamInfoItem videoItem, int position) {
        showUserMessage("More options for: " + videoItem.getName());
    }

    @Override
    public void onSuggestionClick(String suggestion) {
        binding.searchEditText.setText(suggestion);
        binding.searchEditText.setSelection(suggestion.length());
        performSearch();
    }

    private void openVideo(StreamInfoItem videoItem) {
        if (videoItem == null || videoItem.getUrl() == null || videoItem.getUrl().trim().isEmpty()) {
            showUserMessage("Cannot open video: invalid data");
            return;
        }
        try {
            Intent intent = new Intent(this, PlayerActivity.class);
            intent.putExtra("video_url", videoItem.getUrl());
            intent.putExtra("video_title", videoItem.getName());
            intent.putExtra("video_uploader", videoItem.getUploaderName());
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error opening video: " + videoItem.getUrl(), e);
            showUserMessage("Cannot open video");
        }
    }

    @Override
    protected void onDestroy() {
        isActivityDestroyed.set(true);
        cleanup();
        super.onDestroy();
    }

    private void cleanup() {
        cancelPendingSuggestions();
        if (searchManager != null) {
            // Unregister listener to prevent memory leaks
            searchManager.setSearchResultListener(null);
            searchManager.cancelCurrentSearch();
        }
        binding = null;
    }

    @NonNull
    private String sanitizeQuery(@Nullable String query) {
        if (query == null) return "";
        return query.trim().replaceAll("\\s+", " ");
    }

    private boolean isValidQuery(@Nullable String query) {
        return query != null && !query.isEmpty() && query.length() >= MIN_QUERY_LENGTH && query.length() <= MAX_QUERY_LENGTH;
    }

    @NonNull
    private ValidationResult validateSearchQuery(@Nullable String query) {
        if (query == null || query.isEmpty()) {
            return new ValidationResult(false, "Please enter a search term");
        }
        if (query.length() < MIN_QUERY_LENGTH) {
            return new ValidationResult(false, "Search term must be at least " + MIN_QUERY_LENGTH + " characters");
        }
        if (query.length() > MAX_QUERY_LENGTH) {
            return new ValidationResult(false, "Search term is too long (max " + MAX_QUERY_LENGTH + " characters)");
        }
        return new ValidationResult(true, null);
    }

    private void handleCriticalError(@NonNull String message, @NonNull Exception exception) {
        Log.e(TAG, "Critical error in " + CLASS_NAME + ": " + message, exception);
        runOnUiThread(() -> {
            if (!isActivityDestroyed.get()) {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }

    private void showUserMessage(@NonNull String message) {
        runOnUiThread(() -> {
            if (!isActivityDestroyed.get()) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private static class ValidationResult {
        final boolean isValid;
        final String errorMessage;
        ValidationResult(boolean isValid, @Nullable String errorMessage) {
            this.isValid = isValid;
            this.errorMessage = errorMessage;
        }
    }

    private static class SearchResultHandler implements SearchManager.SearchResultListener {
        private final WeakReference<SearchActivity> activityRef;

        SearchResultHandler(@NonNull SearchActivity activity) {
            this.activityRef = new WeakReference<>(activity);
        }

        @Nullable
        private SearchActivity getActivity() {
            SearchActivity activity = activityRef.get();
            return (activity != null && !activity.isActivityDestroyed.get()) ? activity : null;
        }

        @Override
        public void onSearchStarted(@NonNull String query) {
            Log.i(TAG, "Search has started for query: " + query);
        }

        @Override
        public void onSearchResults(@Nullable SearchManager.SearchResults results) {
            SearchActivity activity = getActivity();
            if (activity == null || results == null) return;
            
            // Prevent old search results from overwriting new ones
            if (!results.query.equals(activity.lastSearchedQuery)) {
                Log.w(TAG, "Ignoring stale search results for query: " + results.query);
                return;
            }

            if (results.items.isEmpty()) {
                activity.showEmptyState("No Results Found", "Try different search terms or check for typos.");
                return;
            }

            List<StreamInfoItem> streamItems = extractStreamItems(results.items);
            synchronized (activity.searchResults) {
                activity.searchResults.clear();
                activity.searchResults.addAll(streamItems);
            }

            activity.hasMorePages.set(results.hasMorePages);
            activity.videoAdapter.notifyDataSetChanged();
            activity.showResultsState();

            if (results.isCorrectedSearch && results.searchSuggestion != null) {
                activity.showCorrectionSuggestion(results.searchSuggestion);
            }
            Log.i(TAG, "Search success: " + streamItems.size() + " videos found. More pages: " + results.hasMorePages);
        }

        @Override
        public void onSearchError(@Nullable SearchManager.SearchError error) {
            SearchActivity activity = getActivity();
            if (activity == null || error == null) return;
            
            // Prevent old search errors from appearing over new results
            if (!error.query.equals(activity.lastSearchedQuery)) {
                Log.w(TAG, "Ignoring stale search error for query: " + error.query);
                return;
            }

            ErrorInfo errorInfo = getErrorInfo(error);
            activity.showEmptyState(errorInfo.title, errorInfo.description);
            Log.e(TAG, "Search error [" + error.type + "]: " + error.message, error.exception);
        }

        @Override
        public void onMoreResultsLoaded(@Nullable List<InfoItem> items, boolean hasMorePages) {
            SearchActivity activity = getActivity();
            if (activity == null) return;
            
            activity.isLoadingMore.set(false);

            if (items == null || items.isEmpty()) {
                activity.hasMorePages.set(false);
                Log.d(TAG, "No more results to load.");
                return;
            }

            List<StreamInfoItem> streamItems = extractStreamItems(items);
            int oldSize;
            synchronized (activity.searchResults) {
                oldSize = activity.searchResults.size();
                activity.searchResults.addAll(streamItems);
            }
            activity.hasMorePages.set(hasMorePages);
            activity.videoAdapter.notifyItemRangeInserted(oldSize, streamItems.size());
            Log.i(TAG, "Loaded " + streamItems.size() + " more results. More pages: " + hasMorePages);
        }

        @Override
        public void onSearchSuggestions(@Nullable List<String> suggestionsList) {
            SearchActivity activity = getActivity();
            if (activity == null || suggestionsList == null) return;
            
            // Ensure suggestions are for the current text in the search box
            if (!activity.binding.searchEditText.hasFocus()) {
                 activity.hideSuggestionsPanel();
                 return;
            }

            List<String> limitedSuggestions = suggestionsList.stream().limit(MAX_SUGGESTIONS).collect(Collectors.toList());
            synchronized (activity.suggestions) {
                activity.suggestions.clear();
                activity.suggestions.addAll(limitedSuggestions);
            }
            activity.suggestionsAdapter.notifyDataSetChanged();
            
            if (!limitedSuggestions.isEmpty()) {
                activity.showSuggestionsPanel();
            } else {
                activity.hideSuggestionsPanel();
            }
        }

        @NonNull
        private List<StreamInfoItem> extractStreamItems(@NonNull List<InfoItem> items) {
            List<StreamInfoItem> streamItems = new ArrayList<>();
            for (InfoItem item : items) {
                if (item instanceof StreamInfoItem) {
                    streamItems.add((StreamInfoItem) item);
                }
            }
            return streamItems;
        }

        @NonNull
        private ErrorInfo getErrorInfo(@NonNull SearchManager.SearchError error) {
            switch (error.type) {
                case NO_RESULTS_FOUND:
                    return new ErrorInfo("No Results Found", "Try different search terms or check spelling");
                case NETWORK_ERROR:
                    return new ErrorInfo("Network Error", "Please check your internet connection and try again");
                case RECAPTCHA_REQUIRED:
                    return new ErrorInfo("Verification Required", "Please try again in a few minutes");
                case SERVICE_UNAVAILABLE:
                    return new ErrorInfo("Service Unavailable", "The service is temporarily unavailable");
                default:
                    return new ErrorInfo("Search Error", "An unexpected error occurred. Please try again.");
            }
        }

        private static class ErrorInfo {
            final String title;
            final String description;
            ErrorInfo(@NonNull String title, @NonNull String description) {
                this.title = title;
                this.description = description;
            }
        }
    }
}