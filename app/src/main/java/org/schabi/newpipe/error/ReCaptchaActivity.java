package org.schabi.newpipe.error;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;
import com.nidoham.flowtube.DownloaderImpl;
import com.nidoham.flowtube.databinding.ActivityRecaptchaBinding;
import org.schabi.newpipe.extractor.utils.Utils;

import com.nidoham.flowtube.R;

/**
 * ReCaptchaActivity handles YouTube's CAPTCHA verification.
 * It opens a WebView to allow the user to solve the captcha
 * and then extracts and saves the cookies.
 */
public class ReCaptchaActivity extends AppCompatActivity {

    public static final int RECAPTCHA_REQUEST = 10;
    public static final String RECAPTCHA_URL_EXTRA = "recaptcha_url_extra";
    public static final String YT_URL = "https://www.youtube.com";
    public static final String RECAPTCHA_COOKIES_KEY = "recaptcha_cookies";

    private ActivityRecaptchaBinding recaptchaBinding;
    private String foundCookies = "";

    /**
     * Removes the pbj=1 param which causes YouTube to respond with JSON instead of HTML.
     */
    public static String sanitizeRecaptchaUrl(@Nullable final String url) {
        if (url == null || url.trim().isEmpty()) return YT_URL;
        return url.replace("&pbj=1", "")
                  .replace("pbj=1&", "")
                  .replace("?pbj=1", "");
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        recaptchaBinding = ActivityRecaptchaBinding.inflate(getLayoutInflater());
        setContentView(recaptchaBinding.getRoot());

        final String url = sanitizeRecaptchaUrl(getIntent().getStringExtra(RECAPTCHA_URL_EXTRA));
        setResult(RESULT_CANCELED); // default result

        final WebSettings webSettings = recaptchaBinding.reCaptchaWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setUserAgentString(DownloaderImpl.USER_AGENT);

        recaptchaBinding.reCaptchaWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                handleCookiesFromUrl(request.getUrl().toString());
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                handleCookiesFromUrl(url);
            }
        });

        recaptchaBinding.reCaptchaWebView.clearCache(true);
        recaptchaBinding.reCaptchaWebView.clearHistory();
        CookieManager.getInstance().removeAllCookies(null);

        recaptchaBinding.reCaptchaWebView.loadUrl(url);
    }

    @Override
    public void onBackPressed() {
        saveCookiesAndFinish();
    }

    /**
     * Save collected cookies into preferences and apply them to DownloaderImpl.
     */
    private void saveCookiesAndFinish() {
        handleCookiesFromUrl(recaptchaBinding.reCaptchaWebView.getUrl());

        if (!foundCookies.isEmpty()) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            String key = getApplicationContext().getString(R.string.recaptcha_cookies_key);
            prefs.edit().putString(key, foundCookies).apply();

            DownloaderImpl.getInstance().setCookie(RECAPTCHA_COOKIES_KEY, foundCookies);
            setResult(RESULT_OK);
        }

        recaptchaBinding.reCaptchaWebView.loadUrl("about:blank");
        finish();
    }

    /**
     * Handles both normal cookies and Google Abuse Exemption embedded in URL.
     */
    private void handleCookiesFromUrl(@Nullable final String url) {
        if (url == null) return;

        final String cookies = CookieManager.getInstance().getCookie(url);
        handleCookies(cookies);

        final int abuseStart = url.indexOf("google_abuse=");
        if (abuseStart != -1) {
            final int abuseEnd = url.indexOf("+path");
            try {
                handleCookies(Utils.decodeUrlUtf8(url.substring(abuseStart + 13, abuseEnd)));
            } catch (StringIndexOutOfBoundsException ignored) {
            }
        }
    }

    /**
     * Filters out only valid YouTube cookies.
     */
    private void handleCookies(@Nullable final String cookies) {
        if (cookies == null) return;
        addYoutubeCookies(cookies);
    }

    /**
     * Add only specific YouTube-related cookies.
     */
    private void addYoutubeCookies(@NonNull final String cookies) {
        if (cookies.contains("s_gl=") || cookies.contains("goojf=")
                || cookies.contains("VISITOR_INFO1_LIVE=")
                || cookies.contains("GOOGLE_ABUSE_EXEMPTION=")) {
            addCookie(cookies);
        }
    }

    /**
     * Append new cookies while avoiding duplicates.
     */
    private void addCookie(final String cookie) {
        if (foundCookies.contains(cookie)) return;

        if (foundCookies.isEmpty() || foundCookies.endsWith("; ")) {
            foundCookies += cookie;
        } else if (foundCookies.endsWith(";")) {
            foundCookies += " " + cookie;
        } else {
            foundCookies += "; " + cookie;
        }
    }
}