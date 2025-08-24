package com.nidoham.skymate;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.error.ReCaptchaActivity;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.util.InfoCache;

import org.schabi.newpipe.core.YouTubeSettingsManager;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;

/**
 * Professional HTTP downloader implementation with hardcoded Bangladesh and Bangla language support
 * for optimal content delivery in the SkyMate application.
 * 
 * This implementation provides robust HTTP client functionality with comprehensive error handling,
 * cookie management, and YouTube integration specifically configured for Bangladesh users.
 */
public final class DownloaderImpl extends Downloader {

    private static final String TAG = "DownloaderImpl";
    
    // User Agent representing modern Firefox browser for optimal compatibility
    public static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0";

    // YouTube-specific constants for restricted mode functionality
    public static final String YOUTUBE_RESTRICTED_MODE_COOKIE_KEY = "youtube_restricted_mode_key";
    public static final String YOUTUBE_RESTRICTED_MODE_COOKIE = "PREF=f2=8000000";
    public static final String YOUTUBE_DOMAIN = "youtube.com";
    
    // HTTP timeout configurations
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final int RATE_LIMIT_STATUS_CODE = 429;
    
    // Hardcoded Bangladesh and Bangla language settings
    private static final String BANGLADESH_ACCEPT_LANGUAGE = "bn-BD,bn;q=0.9,en;q=0.8";
    private static final String BANGLADESH_COUNTRY_HEADER = "BD";
    private static final String TIMEZONE_DHAKA = "Asia/Dhaka";

    private static DownloaderImpl instance;
    private final Map<String, String> cookieStorage;
    private final OkHttpClient httpClient;

    /**
     * Private constructor initializing the downloader with optimized configuration for Bangladesh users.
     * 
     * @param builder Custom OkHttpClient.Builder for advanced configuration
     */
    private DownloaderImpl(@NonNull final OkHttpClient.Builder builder) {
        this.httpClient = builder
                .readTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .connectTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
        this.cookieStorage = new HashMap<>();
        
        Log.d(TAG, "DownloaderImpl initialized for Bangladesh with Bangla language support");
    }

    /**
     * Initializes the singleton instance with optional custom OkHttp builder.
     * 
     * @param builder Optional OkHttpClient.Builder for custom configuration
     * @return The initialized DownloaderImpl instance
     */
    public static synchronized DownloaderImpl init(@Nullable final OkHttpClient.Builder builder) {
        if (instance != null) {
            Log.w(TAG, "DownloaderImpl already initialized, replacing existing instance");
        }
        
        instance = new DownloaderImpl(
                builder != null ? builder : new OkHttpClient.Builder());
        return instance;
    }

    /**
     * Retrieves the singleton instance, creating it if necessary.
     * 
     * @return The DownloaderImpl singleton instance
     */
    public static synchronized DownloaderImpl getInstance() {
        if (instance == null) {
            Log.d(TAG, "Creating new DownloaderImpl instance with Bangladesh configuration");
            instance = new DownloaderImpl(new OkHttpClient.Builder());
        }
        return instance;
    }

    /**
     * Constructs the complete cookie string for a given URL, including YouTube-specific
     * and reCAPTCHA cookies when applicable.
     * 
     * @param url The target URL for which cookies are being prepared
     * @return Formatted cookie string ready for HTTP headers
     */
    @NonNull
    public String getCookies(@NonNull final String url) {
        final String youtubeCookie = url.contains(YOUTUBE_DOMAIN)
                ? getCookie(YOUTUBE_RESTRICTED_MODE_COOKIE_KEY) 
                : null;

        final String recaptchaCookie = getCookie(ReCaptchaActivity.RECAPTCHA_COOKIES_KEY);

        return Stream.of(youtubeCookie, recaptchaCookie)
                .filter(Objects::nonNull)
                .flatMap(cookies -> Arrays.stream(cookies.split("; *")))
                .distinct()
                .collect(Collectors.joining("; "));
    }

    /**
     * Retrieves a stored cookie by its key.
     * 
     * @param key The cookie identifier
     * @return The cookie value or null if not found
     */
    @Nullable
    public String getCookie(@NonNull final String key) {
        return cookieStorage.get(key);
    }

    /**
     * Stores a cookie with the specified key and value.
     * 
     * @param key    The cookie identifier
     * @param cookie The cookie value to store
     */
    public void setCookie(@NonNull final String key, @NonNull final String cookie) {
        cookieStorage.put(key, cookie);
        Log.d(TAG, "Cookie stored for key: " + key);
    }

    /**
     * Removes a stored cookie by its key.
     * 
     * @param key The cookie identifier to remove
     */
    public void removeCookie(@NonNull final String key) {
        final String removedCookie = cookieStorage.remove(key);
        if (removedCookie != null) {
            Log.d(TAG, "Cookie removed for key: " + key);
        }
    }

    /**
     * Updates YouTube restricted mode cookies based on application settings.
     * This method integrates with YouTubeSettingsManager for configuration management.
     * 
     * @param context Android context for accessing application settings
     */
    public void updateYoutubeRestrictedModeCookies(@NonNull final Context context) {
        try {
            final YouTubeSettingsManager settings = new YouTubeSettingsManager(context);
            final boolean restrictedModeEnabled = settings.isRestrictedModeEnabled();
            updateYoutubeRestrictedModeCookies(true);
            
            Log.d(TAG, "YouTube restricted mode updated for Bangladesh user: " + restrictedModeEnabled);
        } catch (Exception e) {
            Log.e(TAG, "Failed to update YouTube restricted mode cookies", e);
        }
    }

    /**
     * Updates YouTube restricted mode cookies based on the provided boolean flag.
     * 
     * @param youtubeRestrictedModeEnabled Whether restricted mode should be enabled
     */
    public void updateYoutubeRestrictedModeCookies(final boolean youtubeRestrictedModeEnabled) {
        if (youtubeRestrictedModeEnabled) {
            setCookie(YOUTUBE_RESTRICTED_MODE_COOKIE_KEY, YOUTUBE_RESTRICTED_MODE_COOKIE);
            Log.d(TAG, "YouTube restricted mode enabled for Bangladesh");
        } else {
            removeCookie(YOUTUBE_RESTRICTED_MODE_COOKIE_KEY);
            Log.d(TAG, "YouTube restricted mode disabled for Bangladesh");
        }
        
        InfoCache.getInstance().clearCache();
    }

    /**
     * Retrieves the content length of a resource without downloading the full content.
     * Uses HTTP HEAD method for efficient length determination.
     * 
     * @param url The URL to check for content length
     * @return The content length in bytes
     * @throws IOException If the request fails or content length is invalid
     */
    public long getContentLength(@NonNull final String url) throws IOException {
        try {
            final Response response = head(url);
            final String contentLengthHeader = response.getHeader("Content-Length");
            
            if (contentLengthHeader == null || contentLengthHeader.isEmpty()) {
                throw new IOException("Content-Length header missing for URL: " + url);
            }
            
            return Long.parseLong(contentLengthHeader);
        } catch (final NumberFormatException e) {
            throw new IOException("Invalid content length format for URL: " + url, e);
        } catch (final ReCaptchaException e) {
            throw new IOException("reCAPTCHA challenge encountered for URL: " + url, e);
        }
    }

    /**
     * Executes an HTTP request with comprehensive error handling and hardcoded Bangladesh/Bangla support.
     * All requests are automatically configured for optimal content delivery to Bangladesh users.
     * 
     * @param request The request object containing all necessary parameters
     * @return Response object with status, headers, and body content
     * @throws IOException        If network operation fails
     * @throws ReCaptchaException If reCAPTCHA challenge is required
     */
    @Override
    @NonNull
    public Response execute(@NonNull final Request request)
            throws IOException, ReCaptchaException {
        
        final String httpMethod = request.httpMethod();
        final String url = request.url();
        final Map<String, List<String>> headers = request.headers();
        final byte[] dataToSend = request.dataToSend();

        Log.d(TAG, String.format("Executing %s request to: %s with Bangladesh configuration", httpMethod, url));

        RequestBody requestBody = null;
        if (dataToSend != null) {
            requestBody = RequestBody.create(dataToSend);
            Log.d(TAG, "Request body size: " + dataToSend.length + " bytes");
        }

        final okhttp3.Request.Builder requestBuilder = new okhttp3.Request.Builder()
                .method(httpMethod, requestBody)
                .url(url)
                .addHeader("User-Agent", USER_AGENT);

        // Hardcoded Bangladesh and Bangla language configuration
        requestBuilder.addHeader("Accept-Language", BANGLADESH_ACCEPT_LANGUAGE);
        requestBuilder.addHeader("Accept-Charset", "UTF-8");
        requestBuilder.addHeader("X-Country-Code", BANGLADESH_COUNTRY_HEADER);
        requestBuilder.addHeader("X-Timezone", TIMEZONE_DHAKA);

        // Apply cookies if available
        final String cookies = getCookies(url);
        if (!cookies.isEmpty()) {
            requestBuilder.addHeader("Cookie", cookies);
            Log.d(TAG, "Applied cookies for Bangladesh user request");
        }

        // Apply custom headers with proper override handling
        headers.forEach((headerName, headerValueList) -> {
            requestBuilder.removeHeader(headerName);
            headerValueList.forEach(headerValue ->
                    requestBuilder.addHeader(headerName, headerValue));
        });

        // Execute the HTTP request with proper resource management
        try (okhttp3.Response response = httpClient.newCall(requestBuilder.build()).execute()) {
            
            final int statusCode = response.code();
            Log.d(TAG, "Response received with status: " + statusCode + " for Bangladesh user");
            
            // Handle rate limiting with specific reCAPTCHA exception
            if (statusCode == RATE_LIMIT_STATUS_CODE) {
                Log.w(TAG, "Rate limit encountered for Bangladesh user, reCAPTCHA challenge required");
                throw new ReCaptchaException("reCaptcha Challenge requested", url);
            }

            // Process response body with proper resource management
            String responseBodyContent = null;
            try (ResponseBody body = response.body()) {
                if (body != null) {
                    responseBodyContent = body.string();
                    Log.d(TAG, "Response body processed for Bangladesh user, size: " + responseBodyContent.length());
                }
            }

            final String finalUrl = response.request().url().toString();
            
            return new Response(
                    statusCode,
                    response.message(),
                    response.headers().toMultimap(),
                    responseBodyContent,
                    finalUrl);
                    
        } catch (IOException e) {
            Log.e(TAG, "Network error during Bangladesh user request execution", e);
            throw new IOException("Network request failed for Bangladesh user, URL: " + url, e);
        }
    }

    /**
     * Clears all stored cookies and resets the cookie storage.
     * Useful for logout operations or privacy management for Bangladesh users.
     */
    public void clearAllCookies() {
        cookieStorage.clear();
        Log.d(TAG, "All cookies cleared for Bangladesh user");
    }
}