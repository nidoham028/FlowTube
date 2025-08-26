package com.nidoham.flowtube;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.nidoham.flowtube.adapter.UnifiedSearchAdapter;
import com.nidoham.flowtube.databinding.ActivitySearchBinding;
import com.nidoham.flowtube.helper.SearchManager;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.lang.ref.WeakReference;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Professional 3-in-1 search activity for the FlowTube application.
 *
 * This activity provides a comprehensive search experience using a single RecyclerView to display:
 * 1.  Recent search history on focus.
 * 2.  Live search suggestions while typing.
 * 3.  Video search results after submission.
 *
 * Features include debouncing, infinite scroll, state preservation, and robust error handling.
 *
 * @author FlowTube Team
 * @version 3.1 (Complete & Fixed)
 * @since API level 21
 */
public class SearchActivity extends AppCompatActivity implements UnifiedSearchAdapter.SearchItemClickListener {

    private static final String TAG = "SearchActivity";
    private static final String CLASS_NAME = SearchActivity.class.getSimpleName();

    // Configuration Constants
    private static final long SUGGESTION_DELAY_MS = 300L;
    private static final int MIN_QUERY_LENGTH = 2;
    private static final int MAX_QUERY_LENGTH = 100;
    private static final int LOAD_MORE_THRESHOLD = 3;
    private static final int MAX_SUGGESTIONS = 8;
    private static final int MAX_HISTORY_ITEMS = 15;

    // State Keys
    private static final String SAVED_QUERY = "saved_query";
    private static final String SAVED_SCROLL_POSITION = "saved_scroll_position";
    private static final String PREFS_NAME = "SearchPrefs";
    private static final String KEY_SEARCH_HISTORY = "search_history";


    // UI Components
    private ActivitySearchBinding binding;

    // Core Components
    private SearchManager searchManager;
    private SearchResultHandler searchResultListener;
    private UnifiedSearchAdapter unifiedAdapter;
    private LinearLayoutManager layoutManager;
    private SearchHistoryManager historyManager;

    // Data Collections
    private final List<Object> currentDisplayItems = Collections.synchronizedList(new ArrayList<>());

    // State Management
    private final AtomicBoolean isLoadingMore = new AtomicBoolean(false);
    private final AtomicBoolean hasMorePages = new AtomicBoolean(false);
    private final AtomicBoolean isActivityDestroyed = new AtomicBoolean(false);

    private enum SearchState { HISTORY, SUGGESTIONS, RESULTS }
    private SearchState currentState = SearchState.HISTORY;

    // Threading
    private final Handler suggestionHandler = new Handler(Looper.getMainLooper());
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

            binding = ActivitySearchBinding.inflate(getLayoutInflater());
            setContentView(binding.getRoot());

            setupStatusBar();
            initializeComponents();
            setupViews();
            restoreSavedState(savedInstanceState);
            handleSearchIntent();

            if (currentQuery.isEmpty()) {
                displaySearchHistory();
            }

            Log.i(TAG, CLASS_NAME + " successfully initialized");

        } catch (Exception e) {
            Log.e(TAG, "Critical error during onCreate()", e);
            handleCriticalError("Failed to initialize search", e);
        }
    }

    private void initializeComponents() {
        searchManager = SearchManager.getInstance();
        searchResultListener = new SearchResultHandler(this);
        searchManager.setSearchResultListener(searchResultListener);
        historyManager = new SearchHistoryManager(this);
    }

    private void setupViews() {
        setupSearchInput();
        setupRecyclerView();
        setupClickListeners();
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
    
    private void setupSearchInput() {
        binding.etSearch.addTextChangedListener(new TextWatcher() {
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
            public void afterTextChanged(Editable s) {}
        });

        binding.etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                performSearch();
                return true;
            }
            return false;
        });

        binding.etSearch.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && binding.etSearch.getText().toString().isEmpty()) {
                displaySearchHistory();
            }
        });
    }

    private void setupRecyclerView() {
        unifiedAdapter = new UnifiedSearchAdapter(currentDisplayItems, this);
        layoutManager = new LinearLayoutManager(this);
        binding.recyclerView.setLayoutManager(layoutManager);
        binding.recyclerView.setAdapter(unifiedAdapter);
        binding.recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (dy > 0 && currentState == SearchState.RESULTS && shouldLoadMoreResults()) {
                    loadMoreResults();
                }
            }
        });
    }
    
    private void setupClickListeners() {
        binding.ivBack.setOnClickListener(v -> finish());
        binding.ivClear.setOnClickListener(v -> {
            binding.etSearch.setText("");
            // When cleared, give focus back to the EditText and show history
            binding.etSearch.requestFocus();
            showKeyboard();
            displaySearchHistory();
        });
        binding.correctSuggestion.setOnClickListener(v -> {
            String suggestionText = binding.correctSuggestion.getText().toString();
            if (suggestionText.startsWith("Showing results for ")) {
                String correctedQuery = suggestionText.substring("Showing results for ".length());
                binding.etSearch.setText(correctedQuery);
                binding.etSearch.setSelection(correctedQuery.length());
                performSearch();
            }
        });
    }

    private void handleQueryChange(@NonNull String query) {
        cancelPendingSuggestions();
        if (isValidQuery(query)) {
            suggestionRunnable = () -> {
                if (!isActivityDestroyed.get() && currentQuery.equals(query)) {
                    searchManager.getSearchSuggestions(query);
                }
            };
            suggestionHandler.postDelayed(suggestionRunnable, SUGGESTION_DELAY_MS);
        } else {
            // If query is empty or too short, show history
            displaySearchHistory();
        }
    }

    private void cancelPendingSuggestions() {
        if (suggestionRunnable != null) {
            suggestionHandler.removeCallbacks(suggestionRunnable);
            suggestionRunnable = null;
        }
    }
    
    @UiThread
    public void performSearch() {
        String query = sanitizeQuery(currentQuery);
        if (!validateSearchQuery(query)) {
            return;
        }

        hideKeyboard();
        if (query.equals(lastSearchedQuery) && !currentDisplayItems.isEmpty() && currentState == SearchState.RESULTS) {
            Log.d(TAG, "Skipping duplicate search for: " + query);
            return;
        }

        lastSearchedQuery = query;
        historyManager.addSearchTerm(query);
        startSearch(query);
    }

    private void startSearch(@NonNull String query) {
        if (searchManager.isSearching() || isActivityDestroyed.get()) {
            Log.w(TAG, "Cannot start search, already searching or activity is destroyed.");
            return;
        }
        showLoadingState();
        searchManager.searchYouTube(query, searchResultListener);
        Log.i(TAG, "Started search for query: " + query);
    }
    
    private void loadMoreResults() {
        if (isLoadingMore.get() || !hasMorePages.get() || searchManager.isSearching() || isActivityDestroyed.get()) {
            return;
        }
        isLoadingMore.set(true);
        showUserMessage("Loading more...");
        searchManager.loadMoreResults();
        Log.d(TAG, "Requesting more search results.");
    }
    
    private boolean shouldLoadMoreResults() {
        int visibleItemCount = layoutManager.getChildCount();
        int totalItemCount = layoutManager.getItemCount();
        int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();

        return (visibleItemCount + firstVisibleItemPosition) >= totalItemCount - LOAD_MORE_THRESHOLD && firstVisibleItemPosition >= 0;
    }
    
    // --- UI State Management ---

    private void showLoadingState() {
        runOnUiThread(() -> {
            if (isActivityDestroyed.get()) return;
            binding.loadingProgressBar.setVisibility(View.VISIBLE);
            binding.recyclerView.setVisibility(View.GONE);
            binding.emptyStateView.setVisibility(View.GONE);
            binding.correctSuggestion.setVisibility(View.GONE);
        });
    }

    private void showResultsState() {
        runOnUiThread(() -> {
            if (isActivityDestroyed.get()) return;
            binding.loadingProgressBar.setVisibility(View.GONE);
            binding.recyclerView.setVisibility(View.VISIBLE);
            binding.emptyStateView.setVisibility(View.GONE);
            if (savedScrollPosition > 0) {
                binding.recyclerView.scrollToPosition(savedScrollPosition);
                savedScrollPosition = 0;
            }
        });
    }

    private void showEmptyState(String title, String description) {
        runOnUiThread(() -> {
            if (isActivityDestroyed.get()) return;
            binding.loadingProgressBar.setVisibility(View.GONE);
            binding.recyclerView.setVisibility(View.GONE);
            binding.emptyStateView.setVisibility(View.VISIBLE);
            binding.emptyStateTitle.setText(title);
            binding.emptyStateDescription.setText(description);
        });
    }

    private void showCorrectionSuggestion(String suggestion) {
        runOnUiThread(() -> {
            if (isActivityDestroyed.get() || suggestion == null || suggestion.isEmpty()) {
                binding.correctSuggestion.setVisibility(View.GONE);
                return;
            }
            binding.correctSuggestion.setText("Showing results for " + suggestion);
            binding.correctSuggestion.setVisibility(View.VISIBLE);
        });
    }
    
    // --- Data Display Logic ---

    private void displaySearchHistory() {
        if (isActivityDestroyed.get()) return;
        currentState = SearchState.HISTORY;
        List<String> history = historyManager.getSearchHistory();
        
        runOnUiThread(() -> {
            if (isActivityDestroyed.get()) return;
            synchronized (currentDisplayItems) {
                currentDisplayItems.clear();
                currentDisplayItems.addAll(history);
            }
            unifiedAdapter.notifyDataSetChanged();
            if (history.isEmpty()) {
                showEmptyState("No Search History", "Your recent searches will appear here.");
            } else {
                showResultsState(); // Re-use this method to show the RecyclerView
            }
        });
    }

    private void displaySuggestions(List<String> suggestions) {
        if (isActivityDestroyed.get() || !binding.etSearch.hasFocus()) return;
        currentState = SearchState.SUGGESTIONS;

        runOnUiThread(() -> {
            synchronized (currentDisplayItems) {
                currentDisplayItems.clear();
                currentDisplayItems.addAll(suggestions);
            }
            unifiedAdapter.notifyDataSetChanged();
            if (!suggestions.isEmpty()) {
                showResultsState();
            }
        });
    }

    private void displayResults(List<StreamInfoItem> results, boolean hasMore) {
        if (isActivityDestroyed.get()) return;
        currentState = SearchState.RESULTS;
        hasMorePages.set(hasMore);

        runOnUiThread(() -> {
            synchronized (currentDisplayItems) {
                currentDisplayItems.clear();
                currentDisplayItems.addAll(results);
            }
            unifiedAdapter.notifyDataSetChanged();
            binding.recyclerView.scrollToPosition(0);
            showResultsState();
        });
    }

    private void appendResults(List<StreamInfoItem> moreResults, boolean hasMore) {
        if (isActivityDestroyed.get() || moreResults.isEmpty()) return;
        hasMorePages.set(hasMore);
        
        runOnUiThread(() -> {
            int oldSize;
            synchronized (currentDisplayItems) {
                oldSize = currentDisplayItems.size();
                currentDisplayItems.addAll(moreResults);
            }
            unifiedAdapter.notifyItemRangeInserted(oldSize, moreResults.size());
        });
    }

    // --- Lifecycle and Interface Callbacks ---
    
    @Override
    public void onVideoItemClick(StreamInfoItem videoItem) {
        if (videoItem == null || videoItem.getUrl() == null || videoItem.getUrl().trim().isEmpty()) {
            showUserMessage("Cannot open video: invalid data");
            return;
        }
        try {
            // Assuming PlayerActivity exists
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
    public void onSuggestionItemClick(String suggestion) {
        binding.etSearch.setText(suggestion);
        binding.etSearch.setSelection(suggestion.length());
        performSearch();
    }
    
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(SAVED_QUERY, currentQuery);
        if (layoutManager != null) {
            outState.putInt(SAVED_SCROLL_POSITION, layoutManager.findFirstVisibleItemPosition());
        }
    }

    @Override
    protected void onDestroy() {
        isActivityDestroyed.set(true);
        cancelPendingSuggestions();
        if (searchManager != null) {
            searchManager.setSearchResultListener(null);
            searchManager.cancelCurrentSearch();
        }
        binding = null;
        super.onDestroy();
    }
    
    // --- Utility Methods ---

    private void hideKeyboard() {
        View view = getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void showKeyboard() {
        View view = getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
        }
    }
    
    private void restoreSavedState(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            String savedQuery = savedInstanceState.getString(SAVED_QUERY);
            if (savedQuery != null) {
                binding.etSearch.setText(savedQuery);
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
                binding.etSearch.setText(query);
                currentQuery = query.trim();
                performSearch();
            }
        }
    }
    
    @NonNull
    private String sanitizeQuery(@Nullable String query) {
        return (query == null) ? "" : query.trim().replaceAll("\\s+", " ");
    }

    private boolean isValidQuery(@Nullable String query) {
        return query != null && !query.isEmpty() && query.length() >= MIN_QUERY_LENGTH && query.length() <= MAX_QUERY_LENGTH;
    }

    private boolean validateSearchQuery(@Nullable String query) {
        if (query == null || query.isEmpty()) {
            Toast.makeText(this, "Please enter a search term", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (query.length() < MIN_QUERY_LENGTH) {
            Toast.makeText(this, "Search term must be at least " + MIN_QUERY_LENGTH + " characters", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
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

    /**
     * Handles callbacks from the SearchManager and updates the UI accordingly.
     * Uses a WeakReference to the activity to prevent memory leaks.
     */
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
            // This method is required by the interface.
            // UI is already in a loading state from performSearch().
            Log.i(TAG, "Search has started for query: " + query);
        }

        @Override
        public void onSearchResults(@Nullable SearchManager.SearchResults results) {
            SearchActivity activity = getActivity();
            if (activity == null || results == null || !results.query.equals(activity.lastSearchedQuery)) {
                return; // Ignore stale results
            }

            List<StreamInfoItem> streamItems = extractStreamItems(results.items);
            if (streamItems.isEmpty()) {
                activity.showEmptyState("No Results Found", "Try different search terms or check for typos.");
            } else {
                activity.displayResults(streamItems, results.hasMorePages);
            }

            if (results.isCorrectedSearch && results.searchSuggestion != null) {
                activity.showCorrectionSuggestion(results.searchSuggestion);
            } else {
                activity.showCorrectionSuggestion(null); // Hide it if no correction
            }
        }

        @Override
        public void onSearchError(@Nullable SearchManager.SearchError error) {
            SearchActivity activity = getActivity();
            if (activity == null || error == null || !error.query.equals(activity.lastSearchedQuery)) {
                return; // Ignore stale errors
            }
            activity.showEmptyState("Search Error", "An unexpected error occurred. Please try again.");
            Log.e(TAG, "Search error [" + error.type + "]: " + error.message, error.exception);
        }

        @Override
        public void onMoreResultsLoaded(@Nullable List<InfoItem> items, boolean hasMorePages) {
            SearchActivity activity = getActivity();
            if (activity == null) return;
            
            activity.isLoadingMore.set(false);
            if (items != null && !items.isEmpty()) {
                activity.appendResults(extractStreamItems(items), hasMorePages);
            } else {
                activity.hasMorePages.set(false);
            }
        }

        @Override
        public void onSearchSuggestions(@Nullable List<String> suggestionsList) {
            SearchActivity activity = getActivity();
            if (activity == null || suggestionsList == null || !activity.binding.etSearch.hasFocus()) {
                return;
            }
            List<String> limitedSuggestions = suggestionsList.stream().limit(MAX_SUGGESTIONS).collect(Collectors.toList());
            activity.displaySuggestions(limitedSuggestions);
        }

        @NonNull
        private List<StreamInfoItem> extractStreamItems(@NonNull List<InfoItem> items) {
            return items.stream()
                        .filter(StreamInfoItem.class::isInstance)
                        .map(StreamInfoItem.class::cast)
                        .collect(Collectors.toList());
        }
    }

    /**
     * Manages saving and retrieving search history using SharedPreferences.
     */
    private static class SearchHistoryManager {
        private final SharedPreferences prefs;
        private final Gson gson = new Gson();

        SearchHistoryManager(Context context) {
            this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        }

        List<String> getSearchHistory() {
            String json = prefs.getString(KEY_SEARCH_HISTORY, "[]");
            Type type = new TypeToken<ArrayList<String>>() {}.getType();
            return gson.fromJson(json, type);
        }

        void addSearchTerm(String term) {
            if (term == null || term.isEmpty()) return;
            List<String> history = getSearchHistory();
            history.remove(term); // Remove old entry to move it to the top
            history.add(0, term); // Add new term to the beginning
            if (history.size() > MAX_HISTORY_ITEMS) {
                history = history.subList(0, MAX_HISTORY_ITEMS);
            }
            saveSearchHistory(history);
        }

        private void saveSearchHistory(List<String> history) {
            prefs.edit().putString(KEY_SEARCH_HISTORY, gson.toJson(history)).apply();
        }
    }
}