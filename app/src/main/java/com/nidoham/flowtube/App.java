package com.nidoham.flowtube;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.nidoham.strivo.settings.ApplicationSettings;
import com.nidoham.flowtube.core.language.AppLanguage;
import org.schabi.newpipe.error.ReCaptchaActivity;
import com.nidoham.strivo.Localization.Localizations;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.util.InfoCache;
import org.schabi.newpipe.util.image.PicassoHelper;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class App extends Application {

    public static final boolean DEBUG = BuildConfig.DEBUG;
    private static final String TAG = "FlowTubeApp";
    private static final String CRASH_LOG_KEY = "crash_log";
    private static final String CRASH_COUNT_KEY = "crash_count";
    private static final String LAST_CRASH_TIME_KEY = "last_crash_time";
    private static final long CRASH_RESET_INTERVAL = 24 * 60 * 60 * 1000L; // 24 hours
    private static final int MAX_CRASHES_PER_DAY = 5;

    private static volatile App instance;
    private DownloaderImpl downloaderImpl;
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);
    private Thread.UncaughtExceptionHandler defaultExceptionHandler;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        
        // Store the default exception handler before setting our custom one
        defaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(this::handleUncaughtException);

        performInitialization();
    }

    private void performInitialization() {
        try {
            initializeBasicComponents();
            initializeNewPipeServices();
            setupApplicationLocalization();
            
            isInitialized.set(true);
            Log.i(TAG, "FlowTube application initialized successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "Critical initialization failure", e);
            handleInitializationFailure(e);
        }
    }

    private void initializeBasicComponents() {
        try {
            ApplicationSettings.getInstance(this);
            AppLanguage.getInstance(this).initialize();
            PicassoHelper.init(this);
            
            if (DEBUG) {
                Log.d(TAG, "Basic application components initialized");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing basic components", e);
            throw new RuntimeException("Failed to initialize core application components", e);
        }
    }

    private void initializeNewPipeServices() throws ExtractionException {
        try {
            downloaderImpl = DownloaderImpl.init(null);
            if (downloaderImpl == null) {
                throw new ExtractionException("DownloaderImpl initialization returned null");
            }
            
            applyStoredCookies(downloaderImpl);
            NewPipe.init(downloaderImpl);
            
            // Clear cache to ensure fresh start
            InfoCache.getInstance().clearCache();
            
            if (DEBUG) {
                Log.d(TAG, "NewPipe services initialized successfully");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "NewPipe initialization failed", e);
            throw new ExtractionException("Failed to initialize NewPipe services", e);
        }
    }

    private void applyStoredCookies(@NonNull DownloaderImpl downloader) {
        final SharedPreferences preferences = getDefaultSharedPreferences();
        
        // Apply reCAPTCHA cookies if available
        try {
            final String cookieKey = getResources().getString(R.string.recaptcha_cookies_key);
            final String storedCookies = preferences.getString(cookieKey, null);
            
            if (storedCookies != null && !storedCookies.trim().isEmpty()) {
                downloader.setCookie(ReCaptchaActivity.RECAPTCHA_COOKIES_KEY, storedCookies);
                if (DEBUG) {
                    Log.d(TAG, "Applied stored reCAPTCHA cookies");
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "reCAPTCHA cookie resource not available or malformed", e);
        }

        // Apply YouTube restricted mode settings
        try {
            downloader.updateYoutubeRestrictedModeCookies(this);
            if (DEBUG) {
                Log.d(TAG, "YouTube restricted mode cookies applied");
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to apply YouTube restricted mode settings", e);
        }
    }

    private void setupApplicationLocalization() {
        try {
            Localizations.applySettingsLocale(this);
            if (DEBUG) {
                Log.d(TAG, "Application localization configured");
            }
        } catch (Exception e) {
            Log.w(TAG, "Error configuring application localization", e);
        }
    }

    private void handleInitializationFailure(@NonNull Exception e) {
        final String errorMessage = "Critical application initialization failure";
        Log.e(TAG, errorMessage, e);
        
        // Save initialization error details
        saveErrorDetails(errorMessage, e);
        
        // Attempt to launch debug activity
        try {
            launchDebugActivity(errorMessage, e);
        } catch (Exception debugException) {
            Log.e(TAG, "Failed to launch debug activity after initialization failure", debugException);
        }
        
        // Terminate application gracefully
        terminateApplication();
    }

    private void handleUncaughtException(@NonNull Thread thread, @NonNull Throwable throwable) {
        Log.e(TAG, "Uncaught exception in thread: " + thread.getName(), throwable);
        
        // Check crash frequency to prevent crash loops
        if (shouldPreventCrashLoop()) {
            Log.w(TAG, "Too many crashes detected, delegating to system handler");
            if (defaultExceptionHandler != null) {
                defaultExceptionHandler.uncaughtException(thread, throwable);
            }
            return;
        }
        
        recordCrashOccurrence();
        
        final String crashDetails = generateCrashReport(throwable, thread);
        saveErrorDetails("Application crash", throwable);
        
        try {
            launchDebugActivity(crashDetails, throwable);
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch debug activity after crash", e);
        }
        
        terminateApplication();
    }

    private boolean shouldPreventCrashLoop() {
        final SharedPreferences preferences = getDefaultSharedPreferences();
        final long currentTime = System.currentTimeMillis();
        final long lastCrashTime = preferences.getLong(LAST_CRASH_TIME_KEY, 0);
        final int crashCount = preferences.getInt(CRASH_COUNT_KEY, 0);
        
        // Reset crash count if enough time has passed
        if (currentTime - lastCrashTime > CRASH_RESET_INTERVAL) {
            preferences.edit()
                    .putInt(CRASH_COUNT_KEY, 0)
                    .putLong(LAST_CRASH_TIME_KEY, currentTime)
                    .apply();
            return false;
        }
        
        return crashCount >= MAX_CRASHES_PER_DAY;
    }

    private void recordCrashOccurrence() {
        final SharedPreferences preferences = getDefaultSharedPreferences();
        final int currentCrashCount = preferences.getInt(CRASH_COUNT_KEY, 0);
        
        preferences.edit()
                .putInt(CRASH_COUNT_KEY, currentCrashCount + 1)
                .putLong(LAST_CRASH_TIME_KEY, System.currentTimeMillis())
                .apply();
    }

    private void saveErrorDetails(@NonNull String description, @NonNull Throwable throwable) {
        try {
            final String crashReport = generateCrashReport(throwable, Thread.currentThread());
            final long timestamp = System.currentTimeMillis();
            
            getDefaultSharedPreferences().edit()
                    .putString(CRASH_LOG_KEY, crashReport)
                    .putLong(CRASH_LOG_KEY + "_time", timestamp)
                    .putString(CRASH_LOG_KEY + "_description", description)
                    .apply();
                    
        } catch (Exception e) {
            Log.w(TAG, "Failed to save error details", e);
        }
    }

    private String generateCrashReport(@NonNull Throwable throwable, @NonNull Thread thread) {
        final StringBuilder reportBuilder = new StringBuilder();
        final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        
        reportBuilder.append("=== FlowTube Crash Report ===\n");
        reportBuilder.append("Time: ").append(dateFormat.format(new Date())).append("\n");
        reportBuilder.append("Thread: ").append(thread.getName()).append("\n");
        reportBuilder.append("Exception: ").append(throwable.getClass().getSimpleName()).append("\n");
        reportBuilder.append("Message: ").append(throwable.getMessage()).append("\n");
        reportBuilder.append("Stack Trace:\n");
        
        final StringWriter stringWriter = new StringWriter();
        final PrintWriter printWriter = new PrintWriter(stringWriter);
        throwable.printStackTrace(printWriter);
        reportBuilder.append(stringWriter.toString());
        
        return reportBuilder.toString();
    }

    private void launchDebugActivity(@NonNull String errorMessage, @NonNull Throwable throwable) {
        final Intent debugIntent = new Intent(this, DebugActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | 
                         Intent.FLAG_ACTIVITY_CLEAR_TOP | 
                         Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra("error_message", errorMessage)
                .putExtra("stack_trace", Log.getStackTraceString(throwable))
                .putExtra("crash_time", System.currentTimeMillis())
                .putExtra("thread_name", Thread.currentThread().getName());

        final Runnable launchRunnable = () -> {
            try {
                startActivity(debugIntent);
            } catch (Exception e) {
                Log.e(TAG, "Failed to launch debug activity", e);
            }
        };

        if (Looper.myLooper() == Looper.getMainLooper()) {
            launchRunnable.run();
        } else {
            new Handler(Looper.getMainLooper()).post(launchRunnable);
        }
    }

    private void terminateApplication() {
        try {
            // Allow brief time for debug activity launch
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    public void refreshYouTubeConfiguration() {
        if (downloaderImpl != null && isInitialized.get()) {
            try {
                downloaderImpl.updateYoutubeRestrictedModeCookies(this);
                InfoCache.getInstance().clearCache();
                Log.d(TAG, "YouTube configuration refreshed");
            } catch (Exception e) {
                Log.e(TAG, "Error refreshing YouTube configuration", e);
            }
        } else {
            Log.w(TAG, "Cannot refresh YouTube configuration: downloader not initialized");
        }
    }

    @Nullable
    public DownloaderImpl getDownloader() {
        return downloaderImpl;
    }

    public boolean isApplicationInitialized() {
        return isInitialized.get();
    }

    @NonNull
    public static App getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Application instance not available");
        }
        return instance;
    }

    @Nullable
    public static String retrieveSavedCrashLog(@NonNull Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getString(CRASH_LOG_KEY, null);
    }

    public static void clearSavedCrashLog(@NonNull Context context) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .remove(CRASH_LOG_KEY)
                .remove(CRASH_LOG_KEY + "_time")
                .remove(CRASH_LOG_KEY + "_description")
                .apply();
    }

    @NonNull
    private SharedPreferences getDefaultSharedPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(this);
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        
        if (!isInitialized.get()) {
            return;
        }
        
        final InfoCache cache = InfoCache.getInstance();
        
        switch (level) {
            case TRIM_MEMORY_RUNNING_MODERATE:
                cache.trimCache();
                if (DEBUG) {
                    Log.d(TAG, "Memory trimmed: moderate pressure");
                }
                break;
                
            case TRIM_MEMORY_RUNNING_LOW:
            case TRIM_MEMORY_RUNNING_CRITICAL:
                cache.clearCache();
                if (downloaderImpl != null) {
                    // Clear some cookies to free memory if needed
                    downloaderImpl.clearAllCookies();
                    // Reapply essential cookies
                    applyStoredCookies(downloaderImpl);
                }
                if (DEBUG) {
                    Log.d(TAG, "Memory cleared: high pressure (level " + level + ")");
                }
                break;
                
            case TRIM_MEMORY_UI_HIDDEN:
                cache.trimCache();
                if (DEBUG) {
                    Log.d(TAG, "UI hidden, trimming cache");
                }
                break;
                
            default:
                if (DEBUG) {
                    Log.d(TAG, "Memory trim level: " + level);
                }
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        
        if (isInitialized.get()) {
            InfoCache.getInstance().clearCache();
            if (DEBUG) {
                Log.d(TAG, "Low memory condition: cache cleared");
            }
        }
    }
}