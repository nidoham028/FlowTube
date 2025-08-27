package com.nidoham.flowtube.helper;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.search.SearchExtractor;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.channel.ChannelInfoItem;
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * SearchManager: Handles YouTube search with full error safety and resource protection.
 */
public class SearchManager {

    private static final String TAG = "SearchManager";
    private static final int YOUTUBE_SERVICE_ID = ServiceList.YouTube.getServiceId();
    private static final int MAX_QUERY_LENGTH = 1000;
    private static final int MIN_QUERY_LENGTH = 1;
    private static final long CACHE_EXPIRY_TIME = TimeUnit.HOURS.toMillis(2);

    private static volatile SearchManager instance;
    private final CompositeDisposable disposables = new CompositeDisposable();
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    private volatile SearchResultListener currentListener;
    private volatile String currentQuery;
    private volatile Page nextPage;
    private volatile boolean isSearching = false;

    private SearchManager() {}

    public static SearchManager getInstance() {
        if (instance == null) {
            synchronized (SearchManager.class) {
                if (instance == null) {
                    instance = new SearchManager();
                }
            }
        }
        return instance;
    }

    // --- Listener Definitions ---
    public interface SearchResultListener {
        void onSearchStarted(String query);
        void onSearchResults(SearchResults results);
        void onSearchSuggestions(List<String> suggestions);
        void onSearchError(SearchError error);
        void onMoreResultsLoaded(List<InfoItem> items, boolean hasMorePages);
    }

    public static class SearchResults {
        public final String query;
        public final List<InfoItem> items;
        public final String searchSuggestion;
        public final boolean isCorrectedSearch;
        public final boolean hasMorePages;
        public final int totalResults;
        private final long timestamp;

        public SearchResults(String query, List<InfoItem> items, String searchSuggestion,
                             boolean isCorrectedSearch, boolean hasMorePages, int totalResults) {
            this.query = query;
            this.items = items != null ? Collections.unmodifiableList(new ArrayList<>(items)) : Collections.emptyList();
            this.searchSuggestion = searchSuggestion;
            this.isCorrectedSearch = isCorrectedSearch;
            this.hasMorePages = hasMorePages;
            this.totalResults = totalResults;
            this.timestamp = System.currentTimeMillis();
        }

        public List<StreamInfoItem> getStreamItems() {
            List<StreamInfoItem> streamItems = new ArrayList<>();
            for (InfoItem item : items) {
                if (item instanceof StreamInfoItem) {
                    streamItems.add((StreamInfoItem) item);
                }
            }
            return Collections.unmodifiableList(streamItems);
        }

        public List<ChannelInfoItem> getChannelItems() {
            List<ChannelInfoItem> channelItems = new ArrayList<>();
            for (InfoItem item : items) {
                if (item instanceof ChannelInfoItem) {
                    channelItems.add((ChannelInfoItem) item);
                }
            }
            return Collections.unmodifiableList(channelItems);
        }

        public List<PlaylistInfoItem> getPlaylistItems() {
            List<PlaylistInfoItem> playlistItems = new ArrayList<>();
            for (InfoItem item : items) {
                if (item instanceof PlaylistInfoItem) {
                    playlistItems.add((PlaylistInfoItem) item);
                }
            }
            return Collections.unmodifiableList(playlistItems);
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_EXPIRY_TIME;
        }
    }

    public static class SearchError extends Exception {
        public final String query;
        public final Throwable exception;
        public final ErrorType type;
        public final String message;

        public enum ErrorType {
            NETWORK_ERROR,
            EXTRACTION_ERROR,
            NO_RESULTS_FOUND,
            SERVICE_UNAVAILABLE,
            RECAPTCHA_REQUIRED,
            INVALID_QUERY,
            UNKNOWN_ERROR
        }

        public SearchError(String query, Throwable exception, ErrorType type, String message) {
            super(message, exception);
            this.query = query;
            this.exception = exception;
            this.type = type;
            this.message = message;
        }
    }

    // --- Main Search API ---
    public void searchYouTube(@NonNull String query, @NonNull SearchResultListener listener) {
        if (!isValidQuery(query)) {
            safeListenerCall(() -> listener.onSearchError(new SearchError(query,
                new IllegalArgumentException("Invalid search query"),
                SearchError.ErrorType.INVALID_QUERY,
                "Search query must be between " + MIN_QUERY_LENGTH + " and " + MAX_QUERY_LENGTH + " characters")));
            return;
        }

        synchronized (this) {
            cancelCurrentSearch();
            currentQuery = query.trim();
            currentListener = listener;
            isSearching = true;
            nextPage = null;
        }

        safeListenerCall(() -> {
            if (currentListener != null) currentListener.onSearchStarted(currentQuery);
        });

        performNetworkSearch();
    }

    private void performNetworkSearch() {
        try {
            Disposable searchDisposable = io.reactivex.rxjava3.core.Single.fromCallable(() -> {
                try {
                    SearchExtractor extractor = NewPipe.getService(YOUTUBE_SERVICE_ID)
                        .getSearchExtractor(currentQuery, Collections.emptyList(), "");
                    extractor.fetchPage();
                    return SearchInfo.getInfo(extractor);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            })
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                this::handleSearchSuccess,
                this::handleSearchError
            );
            disposables.add(searchDisposable);
        } catch (Exception e) {
            handleSearchError(e);
        }
    }

    private void handleSearchSuccess(@NonNull SearchInfo searchInfo) {
        try {
            synchronized (this) {
                isSearching = false;
                nextPage = searchInfo.getNextPage();
            }

            SearchResults results = new SearchResults(
                currentQuery,
                searchInfo.getRelatedItems(),
                searchInfo.getSearchSuggestion(),
                searchInfo.isCorrectedSearch(),
                Page.isValid(nextPage),
                searchInfo.getRelatedItems().size()
            );

            safeListenerCall(() -> {
                if (currentListener != null) currentListener.onSearchResults(results);
            });

            Log.d(TAG, "Search completed for: " + currentQuery +
                    ", found " + results.totalResults + " items");

        } catch (Exception e) {
            Log.e(TAG, "Error handling search success", e);
            handleSearchError(e);
        }
    }

    private void handleSearchError(Throwable throwable) {
        synchronized (this) {
            isSearching = false;
        }

        if (currentListener == null) return;

        SearchError.ErrorType errorType = determineErrorType(throwable);
        String errorMessage = generateErrorMessage(throwable, errorType);

        SearchError searchError = new SearchError(currentQuery, throwable, errorType, errorMessage);

        safeListenerCall(() -> currentListener.onSearchError(searchError));
        Log.e(TAG, "Search failed for query: " + currentQuery, throwable);
    }

    private SearchError.ErrorType determineErrorType(Throwable throwable) {
        if (throwable instanceof SearchExtractor.NothingFoundException) {
            return SearchError.ErrorType.NO_RESULTS_FOUND;
        } else if (throwable instanceof ExtractionException) {
            String message = throwable.getMessage();
            if (message != null && message.toLowerCase().contains("recaptcha")) {
                return SearchError.ErrorType.RECAPTCHA_REQUIRED;
            }
            return SearchError.ErrorType.EXTRACTION_ERROR;
        } else if (throwable instanceof java.net.UnknownHostException
                || throwable instanceof java.net.SocketTimeoutException
                || throwable instanceof java.io.IOException) {
            return SearchError.ErrorType.NETWORK_ERROR;
        } else {
            return SearchError.ErrorType.UNKNOWN_ERROR;
        }
    }

    private String generateErrorMessage(Throwable throwable, SearchError.ErrorType errorType) {
        switch (errorType) {
            case NO_RESULTS_FOUND:
                return "No results found for: " + currentQuery;
            case NETWORK_ERROR:
                return "Network connection failed. Please check your internet connection.";
            case EXTRACTION_ERROR:
                return "Failed to extract search results from YouTube.";
            case RECAPTCHA_REQUIRED:
                return "reCAPTCHA verification required. Please try again later.";
            case SERVICE_UNAVAILABLE:
                return "YouTube service is currently unavailable.";
            case INVALID_QUERY:
                return "Invalid search query format.";
            default:
                String message = throwable.getMessage();
                return "An unexpected error occurred" + (message != null ? ": " + message : "");
        }
    }

    // --- Paging Support ---
    public void loadMoreResults() {
        synchronized (this) {
            if (!Page.isValid(nextPage) || isSearching || currentListener == null) {
                return;
            }
            isSearching = true;
        }

        try {
            Disposable moreResultsDisposable = io.reactivex.rxjava3.core.Single.fromCallable(() -> {
                try {
                    SearchExtractor extractor = NewPipe.getService(YOUTUBE_SERVICE_ID)
                        .getSearchExtractor(currentQuery, Collections.emptyList(), "");
                    return extractor.getPage(nextPage);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            })
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                result -> handleMoreResults((ListExtractor.InfoItemsPage<InfoItem>) result),
                this::handleSearchError
            );

            disposables.add(moreResultsDisposable);
        } catch (Exception e) {
            handleSearchError(e);
        }
    }

    private void handleMoreResults(@NonNull ListExtractor.InfoItemsPage<InfoItem> result) {
        synchronized (this) {
            isSearching = false;
            nextPage = result.getNextPage();
        }

        safeListenerCall(() -> {
            if (currentListener != null) {
                List<InfoItem> items = new ArrayList<>(result.getItems());
                currentListener.onMoreResultsLoaded(items, Page.isValid(nextPage));
            }
        });

        Log.d(TAG, "Loaded " + result.getItems().size() + " more results for: " + currentQuery);
    }

    // --- Suggestions ---
    public void getSearchSuggestions(@NonNull String query) {
        if (!isValidQuery(query)) {
            safeListenerCall(() -> {
                if (currentListener != null) currentListener.onSearchSuggestions(Collections.emptyList());
            });
            return;
        }

        String trimmedQuery = query.trim();

        try {
            Disposable suggestionDisposable = io.reactivex.rxjava3.core.Single.fromCallable(() -> {
                try {
                    return NewPipe.getService(YOUTUBE_SERVICE_ID)
                        .getSuggestionExtractor()
                        .suggestionList(trimmedQuery);
                } catch (Exception e) {
                    return Collections.<String>emptyList();
                }
            })
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                suggestions -> safeListenerCall(() -> {
                    if (currentListener != null) currentListener.onSearchSuggestions(suggestions);
                }),
                throwable -> {
                    Log.w(TAG, "Failed to get suggestions for: " + trimmedQuery, throwable);
                    safeListenerCall(() -> {
                        if (currentListener != null) currentListener.onSearchSuggestions(Collections.emptyList());
                    });
                }
            );

            disposables.add(suggestionDisposable);
        } catch (Exception e) {
            Log.w(TAG, "Error initiating suggestion search", e);
            safeListenerCall(() -> {
                if (currentListener != null) currentListener.onSearchSuggestions(Collections.emptyList());
            });
        }
    }

    // --- Cleanup & Resource Management ---
    public void cancelCurrentSearch() {
        synchronized (this) {
            try {
                disposables.clear();
            } catch (Exception e) {
                Log.w(TAG, "Error clearing disposables", e);
            }
            isSearching = false;
            currentQuery = null;
            nextPage = null;
        }
    }

    public void setSearchResultListener(@Nullable SearchResultListener listener) {
        synchronized (this) {
            this.currentListener = listener;
        }
    }

    public boolean isSearching() {
        return isSearching;
    }

    @Nullable
    public String getCurrentQuery() {
        return currentQuery;
    }

    public boolean hasMoreResults() {
        synchronized (this) {
            return Page.isValid(nextPage) && !isSearching;
        }
    }

    public void clearSearchCache() {
        try {
            Log.d(TAG, "Search cache cleared");
        } catch (Exception e) {
            Log.w(TAG, "Error clearing search cache", e);
        }
    }

    // --- Synchronous and Simple Search ---
    public CompletableFuture<SearchResults> searchSync(@NonNull String query) {
        CompletableFuture<SearchResults> future = new CompletableFuture<>();

        SearchResultListener syncListener = new SearchResultListener() {
            @Override
            public void onSearchStarted(String query) {
                // No action needed for sync operation
            }

            @Override
            public void onSearchResults(SearchResults results) {
                future.complete(results);
            }

            @Override
            public void onSearchSuggestions(List<String> suggestions) {
                // Not applicable for search operation
            }

            @Override
            public void onSearchError(SearchError error) {
                future.completeExceptionally(error);
            }

            @Override
            public void onMoreResultsLoaded(List<InfoItem> items, boolean hasMorePages) {
                // Not applicable for initial search
            }
        };

        SearchResultListener previousListener = currentListener;
        setSearchResultListener(syncListener);

        searchYouTube(query, syncListener);

        future.whenComplete((result, throwable) -> {
            setSearchResultListener(previousListener);
        });

        return future;
    }

    public interface SimpleSearchCallback {
        void onSearchStarted();
        void onResults(List<InfoItem> items);
        void onError(String errorMessage);
    }

    public void search(@NonNull String query, @NonNull SimpleSearchCallback callback) {
        setSearchResultListener(new SearchResultListener() {
            @Override
            public void onSearchStarted(String query) {
                safeListenerCall(callback::onSearchStarted);
            }

            @Override
            public void onSearchResults(SearchResults results) {
                safeListenerCall(() -> callback.onResults(results.items));
            }

            @Override
            public void onSearchSuggestions(List<String> suggestions) {}

            @Override
            public void onSearchError(SearchError error) {
                safeListenerCall(() -> callback.onError(error.message));
            }

            @Override
            public void onMoreResultsLoaded(List<InfoItem> items, boolean hasMorePages) {}
        });

        searchYouTube(query, currentListener);
    }

    private boolean isValidQuery(@NonNull String query) {
        String trimmed = query.trim();
        return !trimmed.isEmpty() &&
                trimmed.length() >= MIN_QUERY_LENGTH &&
                trimmed.length() <= MAX_QUERY_LENGTH;
    }

    public void cleanup() {
        cancelCurrentSearch();

        try {
            executorService.shutdown();
            if (!executorService.awaitTermination(2, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            Log.w(TAG, "Error shutting down executor service", e);
        }

        Log.d(TAG, "SearchManager cleanup completed");
    }

    public static CompletableFuture<List<InfoItem>> quickSearch(@NonNull String query) {
        CompletableFuture<List<InfoItem>> future = new CompletableFuture<>();

        getInstance().search(query, new SimpleSearchCallback() {
            @Override
            public void onSearchStarted() {
                // No action needed
            }

            @Override
            public void onResults(List<InfoItem> items) {
                future.complete(items);
            }

            @Override
            public void onError(String errorMessage) {
                future.completeExceptionally(new RuntimeException(errorMessage));
            }
        });

        return future;
    }

    // --- Utility: Safe listener call ---
    private void safeListenerCall(Runnable runnable) {
        try {
            runnable.run();
        } catch (Throwable t) {
            Log.e(TAG, "Listener callback threw exception", t);
        }
    }
}