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

import java.net.URLEncoder;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;

public class SearchManager {
    
    private static final String TAG = "SearchManager";
    private static final int YOUTUBE_SERVICE_ID = ServiceList.YouTube.getServiceId();
    private static final int MAX_QUERY_LENGTH = 1000;
    private static final int MIN_QUERY_LENGTH = 1;
    private static final long CACHE_EXPIRY_TIME = TimeUnit.HOURS.toMillis(2);
    
    private static volatile SearchManager instance;
    private final CompositeDisposable disposables = new CompositeDisposable();
    private final ExecutorService executorService;
    
    private volatile SearchResultListener currentListener;
    private volatile String currentQuery;
    private volatile Page nextPage;
    private volatile boolean isSearching = false;

    static {
        // Initialize RxJava global error handler to prevent crashes
        RxJavaPlugins.setErrorHandler(throwable -> {
            if (throwable instanceof io.reactivex.rxjava3.exceptions.UndeliverableException) {
                Throwable cause = throwable.getCause();
                Log.w(TAG, "RxJava undeliverable exception: " + (cause != null ? cause.getMessage() : throwable.getMessage()));
                
                // Handle specific NewPipe compatibility errors
                if (cause instanceof NoSuchMethodError) {
                    String message = cause.getMessage();
                    if (message != null && (message.contains("toUnmodifiableList") || 
                                          message.contains("Collectors") ||
                                          message.contains("stream"))) {
                        Log.e(TAG, "NewPipe library compatibility issue detected: " + message);
                    }
                }
                return; // Suppress the exception to prevent crash
            }
            
            // Log other unhandled exceptions but don't crash
            Log.e(TAG, "Unhandled RxJava exception", throwable);
        });
    }

    // Java 8 Compatible utility for URL encoding
    public static class UrlEncodingUtils {
        public static String encodeUrl(@NonNull String input) {
            try {
                return URLEncoder.encode(input, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                Log.e(TAG, "UTF-8 encoding not supported", e);
                return input;
            } catch (Exception e) {
                Log.e(TAG, "URL encoding failed", e);
                return input;
            }
        }
        
        // Java 8 compatible safe encoding method using traditional loops
        public static String safeEncodeUrl(@NonNull String input) {
            try {
                byte[] utf8Bytes = input.getBytes(StandardCharsets.UTF_8);
                StringBuilder encoded = new StringBuilder();
                for (int i = 0; i < utf8Bytes.length; i++) {
                    byte b = utf8Bytes[i];
                    if (isUnreserved(b)) {
                        encoded.append((char) b);
                    } else {
                        encoded.append('%');
                        encoded.append(String.format("%02X", b & 0xFF));
                    }
                }
                return encoded.toString();
            } catch (Exception e) {
                Log.e(TAG, "Safe URL encoding failed", e);
                return input.replaceAll("[^a-zA-Z0-9\\-._~]", "");
            }
        }
        
        private static boolean isUnreserved(byte b) {
            return (b >= 'A' && b <= 'Z') ||
                   (b >= 'a' && b <= 'z') ||
                   (b >= '0' && b <= '9') ||
                   b == '-' || b == '_' || b == '.' || b == '~';
        }
    }

    // Java 8 compatible utility class for creating unmodifiable lists
    public static class CollectionUtils {
        // Create unmodifiable list copy - Java 8 compatible
        public static <T> List<T> unmodifiableListCopy(List<T> original) {
            if (original == null || original.isEmpty()) {
                return Collections.emptyList();
            }
            return Collections.unmodifiableList(new ArrayList<>(original));
        }
        
        // Safe filter operation for Java 8 compatibility
        public static <T> List<T> filterByType(List<InfoItem> items, Class<T> clazz) {
            List<T> result = new ArrayList<>();
            for (InfoItem item : items) {
                if (clazz.isInstance(item)) {
                    result.add(clazz.cast(item));
                }
            }
            return Collections.unmodifiableList(result);
        }
    }

    private SearchManager() {
        ThreadFactory threadFactory = new ThreadFactory() {
            private int counter = 0;
            
            @Override
            public Thread newThread(@NonNull Runnable r) {
                Thread thread = new Thread(r, "SearchManager-Thread-" + (++counter));
                thread.setDaemon(true);
                thread.setPriority(Thread.NORM_PRIORITY);
                
                // Set uncaught exception handler for better error handling
                thread.setUncaughtExceptionHandler((t, e) -> {
                    Log.e(TAG, "Uncaught exception in SearchManager thread: " + t.getName(), e);
                });
                
                return thread;
            }
        };
        
        this.executorService = Executors.newCachedThreadPool(threadFactory);
    }

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
            // Use Java 8 compatible method instead of Collections.unmodifiableList(new ArrayList<>(items))
            this.items = CollectionUtils.unmodifiableListCopy(items);
            this.searchSuggestion = searchSuggestion;
            this.isCorrectedSearch = isCorrectedSearch;
            this.hasMorePages = hasMorePages;
            this.totalResults = totalResults;
            this.timestamp = System.currentTimeMillis();
        }

        // Java 8 compatible methods using CollectionUtils
        public List<StreamInfoItem> getStreamItems() {
            return CollectionUtils.filterByType(items, StreamInfoItem.class);
        }

        public List<ChannelInfoItem> getChannelItems() {
            return CollectionUtils.filterByType(items, ChannelInfoItem.class);
        }

        public List<PlaylistInfoItem> getPlaylistItems() {
            return CollectionUtils.filterByType(items, PlaylistInfoItem.class);
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_EXPIRY_TIME;
        }
    }

    public static class SearchError {
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
            COMPATIBILITY_ERROR,
            LIBRARY_VERSION_ERROR,
            UNKNOWN_ERROR
        }

        public SearchError(String query, Throwable exception, ErrorType type, String message) {
            this.query = query;
            this.exception = exception;
            this.type = type;
            this.message = message;
        }
    }

    public void searchYouTube(@NonNull String query, @NonNull SearchResultListener listener) {
        if (!isValidQuery(query)) {
            listener.onSearchError(new SearchError(query, 
                new IllegalArgumentException("Invalid search query"), 
                SearchError.ErrorType.INVALID_QUERY, 
                "Search query must be between " + MIN_QUERY_LENGTH + " and " + MAX_QUERY_LENGTH + " characters"));
            return;
        }

        synchronized (this) {
            cancelCurrentSearch();
            
            currentQuery = query.trim();
            currentListener = listener;
            isSearching = true;
            nextPage = null;
        }

        if (currentListener != null) {
            currentListener.onSearchStarted(currentQuery);
        }

        performNetworkSearch();
    }

    private void performNetworkSearch() {
        try {
            String encodedQuery = UrlEncodingUtils.encodeUrl(currentQuery);
            
            Disposable searchDisposable = io.reactivex.rxjava3.core.Single.fromCallable(() -> {
                try {
                    // Use Java 8 compatible approach - avoid Collections.emptyList() chaining
                    List<String> emptyContentFilters = new ArrayList<>();
                    SearchExtractor extractor = NewPipe.getService(YOUTUBE_SERVICE_ID)
                        .getSearchExtractor(encodedQuery, emptyContentFilters, "");
                    extractor.fetchPage();
                    return SearchInfo.getInfo(extractor);
                    
                } catch (NoSuchMethodError e) {
                    String errorMessage = e.getMessage();
                    if (errorMessage != null && (errorMessage.contains("toUnmodifiableList") || 
                                               errorMessage.contains("Collectors") ||
                                               errorMessage.contains("stream"))) {
                        Log.e(TAG, "NewPipe library version incompatible with Android runtime", e);
                        throw new RuntimeException("Library compatibility error: NewPipe Extractor version requires Java 10+ features not available in Android runtime. Please downgrade NewPipe Extractor to a compatible version (v0.21.x or earlier).", e);
                    }
                    throw new RuntimeException("Method compatibility error", e);
                    
                } catch (ExtractionException e) {
                    Log.w(TAG, "Extraction failed, attempting fallback", e);
                    // Try with alternative encoding as fallback
                    try {
                        String fallbackQuery = UrlEncodingUtils.safeEncodeUrl(currentQuery);
                        List<String> emptyContentFilters = new ArrayList<>();
                        SearchExtractor fallbackExtractor = NewPipe.getService(YOUTUBE_SERVICE_ID)
                            .getSearchExtractor(fallbackQuery, emptyContentFilters, "");
                        fallbackExtractor.fetchPage();
                        return SearchInfo.getInfo(fallbackExtractor);
                    } catch (Exception fallbackEx) {
                        Log.e(TAG, "Fallback search also failed", fallbackEx);
                        throw new RuntimeException("Search extraction failed with both primary and fallback methods", fallbackEx);
                    }
                    
                } catch (Exception e) {
                    Log.e(TAG, "Search operation failed", e);
                    throw new RuntimeException("Search operation failed", e);
                }
            })
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnError(throwable -> {
                // Additional logging for debugging
                Log.e(TAG, "Search operation error in RxJava chain", throwable);
            })
            .subscribe(
                this::handleSearchSuccess,
                this::handleSearchError
            );

            disposables.add(searchDisposable);
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to initiate search operation", e);
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

            if (currentListener != null) {
                currentListener.onSearchResults(results);
            }

            Log.d(TAG, "Search completed successfully for: " + currentQuery + 
                  ", found " + results.totalResults + " items");

        } catch (Exception e) {
            Log.e(TAG, "Error processing search success", e);
            handleSearchError(e);
        }
    }

    private void handleSearchError(Throwable throwable) {
        synchronized (this) {
            isSearching = false;
        }
        
        if (currentListener == null) {
            Log.w(TAG, "Search error occurred but no listener available", throwable);
            return;
        }

        SearchError.ErrorType errorType = determineErrorType(throwable);
        String errorMessage = generateErrorMessage(throwable, errorType);

        SearchError searchError = new SearchError(currentQuery, throwable, errorType, errorMessage);
        currentListener.onSearchError(searchError);

        Log.e(TAG, "Search failed for query: " + currentQuery + " with error type: " + errorType, throwable);
    }

    private SearchError.ErrorType determineErrorType(Throwable throwable) {
        if (throwable.getClass().getSimpleName().equals("NothingFoundException")) {
            return SearchError.ErrorType.NO_RESULTS_FOUND;
        } else if (throwable instanceof NoSuchMethodError) {
            String message = throwable.getMessage();
            if (message != null && (message.contains("toUnmodifiableList") || 
                                  message.contains("Collectors") ||
                                  message.contains("stream"))) {
                return SearchError.ErrorType.LIBRARY_VERSION_ERROR;
            }
            return SearchError.ErrorType.COMPATIBILITY_ERROR;
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
                return "Network connection failed. Please check your internet connection and try again.";
            case EXTRACTION_ERROR:
                return "Failed to extract search results from YouTube. The service may be temporarily unavailable.";
            case RECAPTCHA_REQUIRED:
                return "YouTube has requested verification. Please try again later.";
            case SERVICE_UNAVAILABLE:
                return "YouTube service is currently unavailable. Please try again later.";
            case INVALID_QUERY:
                return "Invalid search query format.";
            case COMPATIBILITY_ERROR:
                return "Device compatibility issue. Please update the app to the latest version.";
            case LIBRARY_VERSION_ERROR:
                return "Library version incompatible with this Android version. Please contact app developer for an update.";
            default:
                String message = throwable.getMessage();
                return "Search operation failed" + (message != null ? ": " + message : ". Please try again.");
        }
    }

    public void loadMoreResults() {
        synchronized (this) {
            if (!Page.isValid(nextPage) || isSearching || currentListener == null) {
                return;
            }
            isSearching = true;
        }

        try {
            String encodedQuery = UrlEncodingUtils.encodeUrl(currentQuery);
            
            Disposable moreResultsDisposable = io.reactivex.rxjava3.core.Single.fromCallable(() -> {
                try {
                    List<String> emptyContentFilters = new ArrayList<>();
                    SearchExtractor extractor = NewPipe.getService(YOUTUBE_SERVICE_ID)
                        .getSearchExtractor(encodedQuery, emptyContentFilters, "");
                    return extractor.getPage(nextPage);
                } catch (NoSuchMethodError e) {
                    Log.w(TAG, "Compatibility issue loading more results", e);
                    String fallbackQuery = UrlEncodingUtils.safeEncodeUrl(currentQuery);
                    List<String> emptyContentFilters = new ArrayList<>();
                    SearchExtractor fallbackExtractor = NewPipe.getService(YOUTUBE_SERVICE_ID)
                        .getSearchExtractor(fallbackQuery, emptyContentFilters, "");
                    return fallbackExtractor.getPage(nextPage);
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

        if (currentListener != null) {
            List<InfoItem> items = new ArrayList<>(result.getItems());
            currentListener.onMoreResultsLoaded(items, Page.isValid(nextPage));
        }

        Log.d(TAG, "Successfully loaded " + result.getItems().size() + " more results for: " + currentQuery);
    }

    public void getSearchSuggestions(@NonNull String query) {
        if (!isValidQuery(query)) {
            if (currentListener != null) {
                currentListener.onSearchSuggestions(Collections.<String>emptyList());
            }
            return;
        }

        String trimmedQuery = query.trim();
        String encodedQuery = UrlEncodingUtils.encodeUrl(trimmedQuery);

        try {
            Disposable suggestionDisposable = io.reactivex.rxjava3.core.Single.fromCallable(() -> {
                try {
                    return NewPipe.getService(YOUTUBE_SERVICE_ID)
                        .getSuggestionExtractor()
                        .suggestionList(encodedQuery);
                } catch (NoSuchMethodError e) {
                    Log.w(TAG, "Suggestions feature incompatible with library version", e);
                    return Collections.<String>emptyList();
                } catch (Exception e) {
                    Log.w(TAG, "Error getting suggestions", e);
                    return Collections.<String>emptyList();
                }
            })
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                suggestions -> {
                    if (currentListener != null) {
                        List<String> safeList = suggestions != null ? suggestions : Collections.<String>emptyList();
                        currentListener.onSearchSuggestions(safeList);
                    }
                },
                throwable -> {
                    Log.w(TAG, "Failed to get suggestions for: " + trimmedQuery, throwable);
                    if (currentListener != null) {
                        currentListener.onSearchSuggestions(Collections.<String>emptyList());
                    }
                }
            );

            disposables.add(suggestionDisposable);
        } catch (Exception e) {
            Log.w(TAG, "Error initiating suggestion search", e);
            if (currentListener != null) {
                currentListener.onSearchSuggestions(Collections.<String>emptyList());
            }
        }
    }

    public void cancelCurrentSearch() {
        synchronized (this) {
            try {
                disposables.clear();
                isSearching = false;
                currentQuery = null;
                nextPage = null;
                Log.d(TAG, "Current search cancelled successfully");
            } catch (Exception e) {
                Log.w(TAG, "Error cancelling current search", e);
            }
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
                future.completeExceptionally(error.exception);
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
                callback.onSearchStarted();
            }

            @Override
            public void onSearchResults(SearchResults results) {
                callback.onResults(results.items);
            }

            @Override
            public void onSearchSuggestions(List<String> suggestions) {
                // Not used in simple callback
            }

            @Override
            public void onSearchError(SearchError error) {
                callback.onError(error.message);
            }

            @Override
            public void onMoreResultsLoaded(List<InfoItem> items, boolean hasMorePages) {
                // Not used in simple callback
            }
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
        try {
            cancelCurrentSearch();
            
            if (executorService != null && !executorService.isShutdown()) {
                executorService.shutdown();
                try {
                    if (!executorService.awaitTermination(2, TimeUnit.SECONDS)) {
                        executorService.shutdownNow();
                        if (!executorService.awaitTermination(1, TimeUnit.SECONDS)) {
                            Log.w(TAG, "ExecutorService did not terminate cleanly");
                        }
                    }
                } catch (InterruptedException e) {
                    executorService.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }

            Log.d(TAG, "SearchManager cleanup completed successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error during SearchManager cleanup", e);
        }
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
}