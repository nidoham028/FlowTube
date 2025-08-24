package com.nidoham.flowtube;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.error.ReCaptchaActivity;
import org.schabi.newpipe.core.YouTubeSettingsManager;
import org.schabi.newpipe.util.InfoCache;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;

public final class DownloaderImpl extends Downloader {

    private static final String TAG = "DownloaderImpl";
    
    public static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0";

    public static final String YOUTUBE_RESTRICTED_MODE_COOKIE_KEY =
            "youtube_restricted_mode_key";
    public static final String YOUTUBE_RESTRICTED_MODE_COOKIE = "PREF=f2=8000000";
    public static final String YOUTUBE_DOMAIN = "youtube.com";
    
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final int HTTP_TOO_MANY_REQUESTS = 429;
    private static final String CONTENT_LENGTH_HEADER = "Content-Length";
    private static final String COOKIE_SEPARATOR = "; ";

    private static volatile DownloaderImpl instance;
    private final Map<String, String> mCookies;
    private final OkHttpClient client;

    private DownloaderImpl(final OkHttpClient.Builder builder) {
        this.client = builder
                .readTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .connectTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();
        this.mCookies = new HashMap<>();
        
        Log.d(TAG, "DownloaderImpl initialized with timeout: " + DEFAULT_TIMEOUT_SECONDS + "s");
    }

    public static DownloaderImpl init(@Nullable final OkHttpClient.Builder builder) {
        if (instance != null) {
            Log.w(TAG, "DownloaderImpl already initialized, replacing existing instance");
        }
        
        instance = new DownloaderImpl(
                builder != null ? builder : new OkHttpClient.Builder());
        return instance;
    }

    public static DownloaderImpl getInstance() {
        if (instance == null) {
            synchronized (DownloaderImpl.class) {
                if (instance == null) {
                    Log.d(TAG, "Creating new DownloaderImpl instance");
                    instance = new DownloaderImpl(new OkHttpClient.Builder());
                }
            }
        }
        return instance;
    }

    public String getCookies(final String url) {
        if (url == null || url.isEmpty()) {
            return "";
        }

        final List<String> cookieList = new ArrayList<>();
        
        // Add YouTube-specific cookies if applicable
        if (url.contains(YOUTUBE_DOMAIN)) {
            final String youtubeCookie = getCookie(YOUTUBE_RESTRICTED_MODE_COOKIE_KEY);
            if (youtubeCookie != null && !youtubeCookie.isEmpty()) {
                cookieList.add(youtubeCookie);
            }
        }

        // Add reCAPTCHA cookies if present
        final String recaptchaCookie = getCookie(ReCaptchaActivity.RECAPTCHA_COOKIES_KEY);
        if (recaptchaCookie != null && !recaptchaCookie.isEmpty()) {
            cookieList.add(recaptchaCookie);
        }

        // Combine and deduplicate cookies
        return combineCookies(cookieList);
    }

    private String combineCookies(final List<String> cookieList) {
        final Set<String> uniqueCookies = new HashSet<>();
        
        for (final String cookieString : cookieList) {
            if (cookieString != null && !cookieString.trim().isEmpty()) {
                final String[] cookies = cookieString.split(COOKIE_SEPARATOR);
                for (final String cookie : cookies) {
                    final String trimmedCookie = cookie.trim();
                    if (!trimmedCookie.isEmpty()) {
                        uniqueCookies.add(trimmedCookie);
                    }
                }
            }
        }

        final StringBuilder combinedCookies = new StringBuilder();
        boolean first = true;
        for (final String cookie : uniqueCookies) {
            if (!first) {
                combinedCookies.append(COOKIE_SEPARATOR);
            }
            combinedCookies.append(cookie);
            first = false;
        }

        return combinedCookies.toString();
    }

    @Nullable
    public String getCookie(final String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        return mCookies.get(key);
    }

    public void setCookie(final String key, final String cookie) {
        if (key == null || key.isEmpty()) {
            Log.w(TAG, "Attempted to set cookie with null or empty key");
            return;
        }
        
        if (cookie == null) {
            removeCookie(key);
            return;
        }
        
        mCookies.put(key, cookie);
        Log.d(TAG, "Cookie set for key: " + key);
    }

    public void removeCookie(final String key) {
        if (key == null || key.isEmpty()) {
            return;
        }
        
        final String removed = mCookies.remove(key);
        if (removed != null) {
            Log.d(TAG, "Cookie removed for key: " + key);
        }
    }

    public void updateYoutubeRestrictedModeCookies(final Context context) {
        if (context == null) {
            Log.w(TAG, "Context is null, cannot update YouTube restricted mode cookies");
            return;
        }

        try {
            final YouTubeSettingsManager settings = new YouTubeSettingsManager(context);
            final boolean restrictedModeEnabled = settings.isRestrictedModeEnabled();
            updateYoutubeRestrictedModeCookies(restrictedModeEnabled);
        } catch (final Exception e) {
            Log.e(TAG, "Error updating YouTube restricted mode cookies", e);
        }
    }

    public void updateYoutubeRestrictedModeCookies(final boolean youtubeRestrictedModeEnabled) {
        try {
            if (youtubeRestrictedModeEnabled) {
                setCookie(YOUTUBE_RESTRICTED_MODE_COOKIE_KEY, YOUTUBE_RESTRICTED_MODE_COOKIE);
                Log.d(TAG, "YouTube restricted mode enabled");
            } else {
                removeCookie(YOUTUBE_RESTRICTED_MODE_COOKIE_KEY);
                Log.d(TAG, "YouTube restricted mode disabled");
            }
            
            InfoCache.getInstance().clearCache();
        } catch (final Exception e) {
            Log.e(TAG, "Error updating YouTube restricted mode cookies", e);
        }
    }

    public long getContentLength(final String url) throws IOException {
        if (url == null || url.isEmpty()) {
            throw new IOException("URL cannot be null or empty");
        }

        try {
            final Response response = head(url);
            final String contentLengthHeader = response.getHeader(CONTENT_LENGTH_HEADER);
            
            if (contentLengthHeader == null || contentLengthHeader.isEmpty()) {
                throw new IOException("Content-Length header not present in response");
            }
            
            return Long.parseLong(contentLengthHeader);
        } catch (final NumberFormatException e) {
            throw new IOException("Invalid content length format", e);
        } catch (final ReCaptchaException e) {
            throw new IOException("reCAPTCHA challenge encountered", e);
        }
    }

    @Override
    public Response execute(@NonNull final Request request)
            throws IOException, ReCaptchaException {
        
        if (request == null) {
            throw new IllegalArgumentException("Request cannot be null");
        }

        final String httpMethod = request.httpMethod();
        final String url = request.url();
        final Map<String, List<String>> headers = request.headers();
        final byte[] dataToSend = request.dataToSend();

        if (url == null || url.isEmpty()) {
            throw new IOException("Request URL cannot be null or empty");
        }

        RequestBody requestBody = null;
        if (dataToSend != null && dataToSend.length > 0) {
            requestBody = RequestBody.create(dataToSend);
        }

        final okhttp3.Request.Builder requestBuilder = new okhttp3.Request.Builder()
                .method(httpMethod, requestBody)
                .url(url)
                .addHeader("User-Agent", USER_AGENT);

        // Add cookies if available
        final String cookies = getCookies(url);
        if (!cookies.isEmpty()) {
            requestBuilder.addHeader("Cookie", cookies);
        }

        // Add custom headers
        if (headers != null) {
            for (final Map.Entry<String, List<String>> headerEntry : headers.entrySet()) {
                final String headerName = headerEntry.getKey();
                final List<String> headerValues = headerEntry.getValue();
                
                if (headerName != null && headerValues != null) {
                    requestBuilder.removeHeader(headerName);
                    for (final String headerValue : headerValues) {
                        if (headerValue != null) {
                            requestBuilder.addHeader(headerName, headerValue);
                        }
                    }
                }
            }
        }

        // Execute the request
        try (okhttp3.Response response = client.newCall(requestBuilder.build()).execute()) {
            
            if (response.code() == HTTP_TOO_MANY_REQUESTS) {
                throw new ReCaptchaException("reCaptcha Challenge requested", url);
            }

            String responseBodyToReturn = null;
            try (ResponseBody body = response.body()) {
                if (body != null) {
                    responseBodyToReturn = body.string();
                }
            }

            final String latestUrl = response.request().url().toString();
            
            Log.d(TAG, "Request completed: " + response.code() + " for " + url);
            
            return new Response(
                    response.code(),
                    response.message(),
                    response.headers().toMultimap(),
                    responseBodyToReturn,
                    latestUrl);
                    
        } catch (final IOException e) {
            Log.e(TAG, "Network request failed for URL: " + url, e);
            throw e;
        } catch (final Exception e) {
            Log.e(TAG, "Unexpected error during request execution", e);
            throw new IOException("Request execution failed", e);
        }
    }

    /**
     * Clears all stored cookies
     */
    public void clearAllCookies() {
        final int cookieCount = mCookies.size();
        mCookies.clear();
        Log.d(TAG, "Cleared " + cookieCount + " cookies");
    }

    /**
     * Gets the current OkHttpClient instance
     */
    public OkHttpClient getClient() {
        return client;
    }

    /**
     * Gets cookie count for monitoring purposes
     */
    public int getCookieCount() {
        return mCookies.size();
    }

    /**
     * Validates if a URL is supported for requests
     */
    public boolean isUrlSupported(final String url) {
        return url != null && 
               !url.trim().isEmpty() && 
               (url.startsWith("http://") || url.startsWith("https://"));
    }
}