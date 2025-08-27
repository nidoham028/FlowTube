package com.nidoham.flowtube;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import com.nidoham.flowtube.R;

public class DebugActivity extends Activity {

    private TextView debugText;
    private Button copyButton;
    private Button closeButton;
    private Button restartButton;

    // SharedPreferences crash log key (App.java থেকে match করতে হবে)
    private static final String PREF_CRASH_LOG_KEY = "crash_log";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
            setContentView(R.layout.activity_debug);
            Log.d("DebugActivity", "DebugActivity started successfully");
            
            initializeViews();
            loadErrorMessage();
            setupClickListeners();
            
        } catch (Exception e) {
            Log.e("DebugActivity", "Error in DebugActivity onCreate", e);
            // Fallback: show a simple error dialog
            showFallbackError(e.getMessage());
        }
    }

    private void initializeViews() {
        debugText = findViewById(R.id.debug_text);
        copyButton = findViewById(R.id.btn_copy);
        closeButton = findViewById(R.id.btn_close);
        restartButton = findViewById(R.id.btn_restart); // Add this button to your layout
    }

    private void loadErrorMessage() {
        String errorMsg = null;
        
        try {
            // প্রথমে Intent থেকে error message নেওয়ার চেষ্টা করো
            errorMsg = getIntent().getStringExtra("error_message");
            Log.d("DebugActivity", "Error message from intent: " + (errorMsg != null ? "Found" : "Not found"));
            
            // যদি intent এ error_message না আসে, তাহলে SharedPreferences থেকে পড়ো
            if (errorMsg == null || errorMsg.isEmpty()) {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                errorMsg = prefs.getString(PREF_CRASH_LOG_KEY, null);
                Log.d("DebugActivity", "Error message from prefs: " + (errorMsg != null ? "Found" : "Not found"));
            }
            
            // এখনও যদি না পাওয়া যায়, default message দাও
            if (errorMsg == null || errorMsg.isEmpty()) {
                errorMsg = "No detailed error information available.\n\n" +
                          "The application encountered an unexpected error and had to close.\n" +
                          "Please restart the application.";
            }
            
            // Add timestamp and device info
            String fullErrorMsg = formatErrorMessage(errorMsg);
            debugText.setText(fullErrorMsg);
            
        } catch (Exception e) {
            Log.e("DebugActivity", "Error loading error message", e);
            debugText.setText("Error loading crash information: " + e.getMessage());
        }
    }

    private String formatErrorMessage(String originalError) {
        StringBuilder formatted = new StringBuilder();
        
        // Add header info
        formatted.append("=== SkyMate Crash Report ===\n");
        formatted.append("Time: ").append(new java.util.Date().toString()).append("\n");
        formatted.append("Device: ").append(android.os.Build.MODEL).append("\n");
        formatted.append("Android: ").append(android.os.Build.VERSION.RELEASE).append("\n");
        formatted.append("App Version: ").append(getAppVersion()).append("\n");
        formatted.append("================================\n\n");
        
        // Add the actual error
        formatted.append("Error Details:\n");
        formatted.append(originalError);
        
        return formatted.toString();
    }

    private String getAppVersion() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "Unknown";
        }
    }

    private void setupClickListeners() {
        copyButton.setOnClickListener(v -> {
            try {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("SkyMate Error Log", debugText.getText());
                if (clipboard != null) {
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(this, "Error log copied to clipboard", Toast.LENGTH_SHORT).show();
                    Log.d("DebugActivity", "Error log copied to clipboard");
                } else {
                    Toast.makeText(this, "Clipboard not available", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Log.e("DebugActivity", "Error copying to clipboard", e);
                Toast.makeText(this, "Failed to copy: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        closeButton.setOnClickListener(v -> {
            try {
                clearCrashLog();
                finish();
                Log.d("DebugActivity", "DebugActivity closed by user");
            } catch (Exception e) {
                Log.e("DebugActivity", "Error closing DebugActivity", e);
                finish(); // Force close anyway
            }
        });

        // Add restart functionality
        if (restartButton != null) {
            restartButton.setOnClickListener(v -> {
                try {
                    restartApplication();
                } catch (Exception e) {
                    Log.e("DebugActivity", "Error restarting application", e);
                    Toast.makeText(this, "Failed to restart app", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void clearCrashLog() {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            prefs.edit()
                 .remove(PREF_CRASH_LOG_KEY)
                 .remove(PREF_CRASH_LOG_KEY + "_time")
                 .remove(PREF_CRASH_LOG_KEY + "_description")
                 .apply();
            Log.d("DebugActivity", "Crash log cleared");
        } catch (Exception e) {
            Log.w("DebugActivity", "Failed to clear crash log", e);
        }
    }

    private void restartApplication() {
        try {
            clearCrashLog();
            
            Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
                Log.d("DebugActivity", "Application restarted");
            } else {
                Toast.makeText(this, "Cannot restart application", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e("DebugActivity", "Failed to restart application", e);
            throw e;
        }
    }

    private void showFallbackError(String error) {
        try {
            // Create a minimal error display if the main layout fails
            TextView fallbackText = new TextView(this);
            fallbackText.setText("DebugActivity Error: " + error + "\n\nPlease restart the application manually.");
            fallbackText.setPadding(20, 20, 20, 20);
            setContentView(fallbackText);
        } catch (Exception e) {
            Log.e("DebugActivity", "Even fallback error display failed", e);
        }
    }

    @Override
    protected void onDestroy() {
        try {
            Log.d("DebugActivity", "DebugActivity destroyed");
        } catch (Exception e) {
            // Ignore logging errors during destruction
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        // Handle back button press
        try {
            clearCrashLog();
        } catch (Exception e) {
            Log.w("DebugActivity", "Error clearing crash log on back press", e);
        }
        super.onBackPressed();
    }
}