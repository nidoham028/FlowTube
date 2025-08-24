package com.nidoham.flowtube;

import android.app.Application;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.preference.PreferenceManager;
import com.nidoham.flowtube.core.language.LanguageManager;
import com.nidoham.skymate.DownloaderImpl;
import com.nidoham.flowtube.core.language.AppLanguage;
import org.schabi.newpipe.error.ReCaptchaActivity;
import com.nidoham.strivo.Localization.Localizations;
import com.nidoham.strivo.settings.ApplicationSettings;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.util.InfoCache;
import org.schabi.newpipe.util.image.PicassoHelper;

/**
 * Main application class for SkyMate providing centralized initialization and configuration
 * management with comprehensive error handling and Bangladesh-specific localization support.
 * 
 * This class handles critical application lifecycle events including startup initialization,
 * crash management, and memory optimization while ensuring optimal performance for
 * Bangladesh users through hardcoded Bangla language configuration.
 */
public class App extends Application {

    public static final boolean DEBUG = BuildConfig.DEBUG;
    private static final String TAG = "SkymateApp";
    private static final String CRASH_LOG_KEY = "crash_log";
    private static final String CRASH_TIME_SUFFIX = "_time";
    private static final String RECAPTCHA_COOKIES_KEY = "recaptcha_cookies_key";
    
    // Application state constants
    private static final int INITIALIZATION_SUCCESS = 0;
    private static final int INITIALIZATION_FAILURE = -1;
    
    // Hardcoded Bangladesh configuration
    private static final String BANGLADESH_LANGUAGE_CODE = "bn";

    private static App applicationInstance;
    private DownloaderImpl downloaderImplementation;
    private boolean isInitializationComplete = false;

    /**
     * Primary application initialization method executed during application startup.
     * Establishes core components including network management, localization, and
     * error handling systems with specific optimization for Bangladesh users.
     */
    @Override
    public void onCreate() {
        super.onCreate();
        applicationInstance = this;
        initializeAppLanguage();

        Log.i(TAG, "Initializing SkyMate application for Bangladesh market");

        // Configure global exception handling before any other operations
        configureGlobalExceptionHandling();

        // Initialize core application components with comprehensive error handling
        final int initializationResult = initializeCoreComponents();
        
        if (initializationResult == INITIALIZATION_SUCCESS) {
            isInitializationComplete = true;
            Log.i(TAG, "SkyMate application initialization completed successfully");
        } else {
            Log.e(TAG, "SkyMate application initialization failed with critical errors");
            handleInitializationFailure();
        }
    }
    
    private void initializeAppLanguage() {
        AppLanguage.getInstance(this).initialize();
    }

    /**
     * Establishes comprehensive global exception handling to ensure graceful application
     * failure management and detailed crash reporting for debugging purposes.
     */
    private void configureGlobalExceptionHandling() {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            Log.e(TAG, "Uncaught exception detected in thread: " + thread.getName(), throwable);
            handleApplicationCrash(thread, throwable);
        });
        
        Log.d(TAG, "Global exception handling configured successfully");
    }

    /**
     * Initializes all essential application components including settings management,
     * image processing, language configuration, and network components.
     * 
     * @return INITIALIZATION_SUCCESS if all components initialize successfully,
     *         INITIALIZATION_FAILURE otherwise
     */
    private int initializeCoreComponents() {
        try {
            // Initialize application settings infrastructure
            ApplicationSettings.getInstance(this);
            Log.d(TAG, "Application settings initialized");

            // Configure image processing components
            PicassoHelper.init(this);
            Log.d(TAG, "Image processing components initialized");

            // Establish Bangladesh-specific language configuration
            final boolean languageInitialized = initializeBangladeshLanguageSettings();
            if (!languageInitialized) {
                Log.w(TAG, "Language initialization encountered issues but continuing");
            }

            // Initialize network and extraction components
            initializeNetworkComponents();
            
            // Apply Bangladesh-specific localization settings
            configureBangladeshLocalization();

            return INITIALIZATION_SUCCESS;
            
        } catch (Exception criticalError) {
            Log.e(TAG, "Critical error during core component initialization", criticalError);
            return INITIALIZATION_FAILURE;
        }
    }

    /**
     * Configures language management specifically for Bangladesh users with hardcoded
     * Bangla language settings to ensure consistent user experience.
     * 
     * @return true if language initialization completes successfully, false otherwise
     */
    private boolean initializeBangladeshLanguageSettings() {
        try {
            final LanguageManager languageManager = new LanguageManager(this);
            languageManager.initialize();
            languageManager.setLanguage(BANGLADESH_LANGUAGE_CODE);
            
            Log.i(TAG, "Bangladesh language settings configured with Bangla as primary language");
            return true;
            
        } catch (Exception languageError) {
            Log.e(TAG, "Error configuring Bangladesh language settings", languageError);
            return false;
        }
    }

    /**
     * Initializes network components including the NewPipe extractor system and
     * downloader implementation with Bangladesh-specific configuration.
     * 
     * @throws ExtractionException if network component initialization fails
     */
    private void initializeNetworkComponents() throws ExtractionException {
        try {
            downloaderImplementation = DownloaderImpl.init(null);
            configureCookieManagement(downloaderImplementation);
            NewPipe.init(downloaderImplementation);
            InfoCache.getInstance().clearCache();
            
            Log.d(TAG, "Network components initialized with Bangladesh configuration");
            
        } catch (Exception networkError) {
            throw new ExtractionException("Failed to initialize network components for Bangladesh users", networkError);
        }
    }

    /**
     * Configures cookie management including reCAPTCHA and YouTube restricted mode
     * cookies with appropriate error handling for missing resources.
     * 
     * @param downloader The downloader instance to configure with cookies
     */
    private void configureCookieManagement(final DownloaderImpl downloader) {
        final SharedPreferences applicationPreferences = getApplicationPreferences();
        
        // Configure reCAPTCHA cookies with fallback handling
        configureReCaptchaCookies(downloader, applicationPreferences);
        
        // Configure YouTube restricted mode settings
        configureYouTubeRestrictedMode(downloader);
    }

    /**
     * Configures reCAPTCHA cookie settings from stored preferences with comprehensive
     * error handling for missing or corrupted cookie data.
     * 
     * @param downloader The downloader instance to configure
     * @param preferences Shared preferences containing cookie data
     */
    private void configureReCaptchaCookies(final DownloaderImpl downloader, final SharedPreferences preferences) {
        try {
            final String cookieResourceKey = getString(R.string.recaptcha_cookies_key);
            final String storedCookieData = preferences.getString(cookieResourceKey, null);
            
            if (storedCookieData != null && !storedCookieData.trim().isEmpty()) {
                downloader.setCookie(ReCaptchaActivity.RECAPTCHA_COOKIES_KEY, storedCookieData);
                Log.d(TAG, "reCAPTCHA cookies configured successfully");
            } else {
                Log.d(TAG, "No stored reCAPTCHA cookies found, proceeding with default configuration");
            }
            
        } catch (Exception cookieError) {
            Log.w(TAG, "reCAPTCHA cookie configuration failed, using fallback settings", cookieError);
        }
    }

    /**
     * Configures YouTube restricted mode settings with error handling to ensure
     * graceful degradation if configuration fails.
     * 
     * @param downloader The downloader instance to configure
     */
    private void configureYouTubeRestrictedMode(final DownloaderImpl downloader) {
        try {
            downloader.updateYoutubeRestrictedModeCookies(this);
            Log.d(TAG, "YouTube restricted mode configured for Bangladesh users");
            
        } catch (Exception restrictedModeError) {
            Log.w(TAG, "YouTube restricted mode configuration failed, using default settings", restrictedModeError);
        }
    }

    /**
     * Applies Bangladesh-specific localization settings throughout the application
     * with error handling to ensure graceful degradation.
     */
    private void configureBangladeshLocalization() {
        try {
            Localizations.applySettingsLocale(this);
            Log.d(TAG, "Bangladesh localization settings applied successfully");
            
        } catch (Exception localizationError) {
            Log.w(TAG, "Bangladesh localization configuration encountered errors", localizationError);
        }
    }

    /**
     * Handles critical initialization failures by attempting graceful recovery
     * or controlled application termination with appropriate logging.
     */
    private void handleInitializationFailure() {
        Log.e(TAG, "Application initialization failed critically, attempting recovery procedures");
        
        // Attempt to save failure state for debugging purposes
        saveCriticalFailureLog("Application initialization failed during startup");
        
        // Consider implementing recovery mechanisms or safe mode here
        // For now, we continue execution but mark initialization as incomplete
    }

    /**
     * Comprehensive application crash handler that logs crash details, saves crash
     * information for debugging, and attempts to launch debug activity before termination.
     * 
     * @param crashThread The thread where the crash occurred
     * @param crashThrowable The exception that caused the crash
     */
    private void handleApplicationCrash(final Thread crashThread, final Throwable crashThrowable) {
        final String detailedCrashLog = Log.getStackTraceString(crashThrowable);
        
        Log.e(TAG, String.format("Application crashed in thread: %s", crashThread.getName()), crashThrowable);
        
        // Save crash information for post-mortem analysis
        saveCrashLogData(detailedCrashLog);
        
        // Attempt to launch debug interface for user feedback
        launchDebugInterface(detailedCrashLog);
        
        // Terminate application process after cleanup
        terminateApplicationProcess();
    }

    /**
     * Launches debug activity safely with comprehensive error handling and thread
     * management to ensure proper execution context.
     * 
     * @param crashLogData The crash log information to pass to debug activity
     */
    private void launchDebugInterface(final String crashLogData) {
        final Intent debugIntent = createDebugIntent(crashLogData);
        
        final Runnable debugLauncher = () -> {
            try {
                startActivity(debugIntent);
                Log.d(TAG, "Debug activity launched successfully");
                
            } catch (Exception debugLaunchError) {
                Log.e(TAG, "Failed to launch debug activity", debugLaunchError);
            }
        };

        executeOnMainThread(debugLauncher);
    }

    /**
     * Creates properly configured intent for debug activity with crash information
     * and appropriate flags for emergency launch scenarios.
     * 
     * @param crashLogData The crash log information to include in the intent
     * @return Configured intent for debug activity launch
     */
    private Intent createDebugIntent(final String crashLogData) {
        return new Intent(this, DebugActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | 
                         Intent.FLAG_ACTIVITY_CLEAR_TOP | 
                         Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra("error_message", crashLogData)
                .putExtra("crash_time", System.currentTimeMillis())
                .putExtra("app_version", BuildConfig.VERSION_NAME)
                .putExtra("bangladesh_config", true);
    }

    /**
     * Ensures runnable execution on the main UI thread with proper looper handling
     * for thread-safe UI operations during crash scenarios.
     * 
     * @param runnable The runnable to execute on the main thread
     */
    private void executeOnMainThread(final Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            new Handler(Looper.getMainLooper()).post(runnable);
        }
    }

    /**
     * Persists crash log information to shared preferences for debugging and
     * analysis purposes with timestamp and version information.
     * 
     * @param crashLogData The detailed crash log information to save
     */
    private void saveCrashLogData(final String crashLogData) {
        try {
            getApplicationPreferences()
                    .edit()
                    .putString(CRASH_LOG_KEY, crashLogData)
                    .putLong(CRASH_LOG_KEY + CRASH_TIME_SUFFIX, System.currentTimeMillis())
                    .putString(CRASH_LOG_KEY + "_version", BuildConfig.VERSION_NAME)
                    .apply();
                    
            Log.d(TAG, "Crash log data saved successfully");
            
        } catch (Exception saveError) {
            Log.w(TAG, "Failed to save crash log data", saveError);
        }
    }

    /**
     * Saves critical failure information for debugging purposes when initialization
     * fails before normal crash handling can be established.
     * 
     * @param failureMessage Description of the critical failure
     */
    private void saveCriticalFailureLog(final String failureMessage) {
        try {
            getApplicationPreferences()
                    .edit()
                    .putString("critical_failure", failureMessage)
                    .putLong("critical_failure_time", System.currentTimeMillis())
                    .apply();
                    
        } catch (Exception saveError) {
            Log.w(TAG, "Failed to save critical failure log", saveError);
        }
    }

    /**
     * Terminates the application process after ensuring all cleanup operations
     * have completed and crash information has been saved.
     */
    private void terminateApplicationProcess() {
        Log.i(TAG, "Terminating application process after crash handling completion");
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    /**
     * Updates YouTube restriction settings and clears relevant caches to ensure
     * settings take effect immediately for Bangladesh users.
     */
    public void updateYouTubeRestrictionSettings() {
        if (downloaderImplementation != null && isInitializationComplete) {
            try {
                downloaderImplementation.updateYoutubeRestrictedModeCookies(this);
                InfoCache.getInstance().clearCache();
                
                Log.d(TAG, "YouTube restriction settings updated for Bangladesh users");
                
            } catch (Exception updateError) {
                Log.e(TAG, "Error updating YouTube restriction settings", updateError);
            }
        } else {
            Log.w(TAG, "Cannot update YouTube restrictions: downloader not initialized or initialization incomplete");
        }
    }

    /**
     * Retrieves the configured downloader implementation instance.
     * 
     * @return The downloader implementation or null if not initialized
     */
    public DownloaderImpl getDownloaderInstance() {
        return downloaderImplementation;
    }

    /**
     * Provides access to the application singleton instance with null safety.
     * 
     * @return The application instance, creating one if necessary
     */
    public static App getApplicationInstance() {
        return applicationInstance != null ? applicationInstance : new App();
    }

    /**
     * Retrieves saved crash log information from application preferences.
     * 
     * @param applicationContext The application context for preference access
     * @return The saved crash log or null if no crash log exists
     */
    public static String retrieveSavedCrashLog(final Application applicationContext) {
        return PreferenceManager.getDefaultSharedPreferences(applicationContext)
                .getString(CRASH_LOG_KEY, null);
    }

    /**
     * Clears all saved crash log information from application preferences.
     * 
     * @param applicationContext The application context for preference access
     */
    public static void clearSavedCrashInformation(final Application applicationContext) {
        PreferenceManager.getDefaultSharedPreferences(applicationContext)
                .edit()
                .remove(CRASH_LOG_KEY)
                .remove(CRASH_LOG_KEY + CRASH_TIME_SUFFIX)
                .remove(CRASH_LOG_KEY + "_version")
                .remove("critical_failure")
                .remove("critical_failure_time")
                .apply();
    }

    /**
     * Retrieves the default shared preferences instance for the application.
     * 
     * @return SharedPreferences instance for application-wide settings
     */
    private SharedPreferences getApplicationPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(this);
    }

    /**
     * Handles system memory pressure by implementing intelligent cache management
     * strategies based on the severity of memory constraints.
     * 
     * @param memoryLevel The system memory pressure level indicator
     */
    @Override
    public void onTrimMemory(final int memoryLevel) {
        super.onTrimMemory(memoryLevel);
        
        final InfoCache cacheManager = InfoCache.getInstance();
        
        switch (memoryLevel) {
            case TRIM_MEMORY_RUNNING_MODERATE:
                cacheManager.trimCache();
                Log.d(TAG, "Cache trimmed due to moderate memory pressure");
                break;
                
            case TRIM_MEMORY_RUNNING_LOW:
            case TRIM_MEMORY_RUNNING_CRITICAL:
                cacheManager.clearCache();
                Log.d(TAG, "Cache cleared due to critical memory pressure");
                break;
                
            default:
                if (DEBUG) {
                    Log.d(TAG, String.format("Memory trim level %d handled with default behavior", memoryLevel));
                }
                break;
        }
    }

    /**
     * Provides information about the current initialization state for debugging purposes.
     * 
     * @return true if initialization completed successfully, false otherwise
     */
    public boolean isApplicationInitializationComplete() {
        return isInitializationComplete;
    }
}