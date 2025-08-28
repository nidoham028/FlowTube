package com.nidoham.flowtube;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.nidoham.flowtube.stream.extractor.StreamExtractor;
import com.nidoham.flowtube.stream.until.StreamDownloader.QualityMode;

import org.schabi.newpipe.extractor.stream.StreamInfo;

/**
 * YouTubeExperimentActivity
 * -------------------------
 * Optimized activity class demonstrating comprehensive YouTube content extraction capabilities
 * using the unified StreamExtractor implementation. This enhanced version provides complete
 * extraction functionality including video metadata, audio streams, and video streams through
 * a consolidated interface with improved user experience and robust error handling.
 * 
 * The activity serves as a comprehensive testing environment for validating all extraction
 * capabilities while providing detailed real-time feedback on extraction progress and results.
 */
public class YouTubeExperimentActivity extends AppCompatActivity {

    private static final String TAG = "YouTubeExperimentActivity";

    // UI Components
    private EditText urlInputField;
    private Button extractButton;
    private ProgressBar extractionProgress;
    private TextView videoInfoDisplay;
    private TextView audioUrlDisplay;
    private TextView videoUrlDisplay;
    private TextView statusDisplay;
    
    // Extraction Components
    private StreamExtractor streamExtractor;
    private ExtractionState currentExtractionState;

    /**
     * Enumeration for tracking extraction completion states
     */
    private enum ExtractionState {
        IDLE,
        IN_PROGRESS,
        COMPLETED,
        FAILED
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_youtube_experiment);

        initializeComponents();
        configureUserInterface();
    }

    /**
     * Initialize all components including views and extraction utilities
     */
    private void initializeComponents() {
        initializeViews();
        initializeExtractor();
        setupEventHandlers();
        resetInterfaceState();
    }

    /**
     * Initialize all user interface components and establish view references
     */
    private void initializeViews() {
        urlInputField = findViewById(R.id.editTextYouTubeUrl);
        extractButton = findViewById(R.id.buttonExtract);
        extractionProgress = findViewById(R.id.progressBarExtraction);
        videoInfoDisplay = findViewById(R.id.textViewVideoInfo);
        audioUrlDisplay = findViewById(R.id.textViewAudioUrl);
        videoUrlDisplay = findViewById(R.id.textViewVideoUrl);
        statusDisplay = findViewById(R.id.textViewStatus);
    }

    /**
     * Initialize the StreamExtractor instance with application context
     */
    private void initializeExtractor() {
        streamExtractor = new StreamExtractor(this);
        currentExtractionState = ExtractionState.IDLE;
    }

    /**
     * Configure event handlers for user interface interactions
     */
    private void setupEventHandlers() {
        extractButton.setOnClickListener(this::processExtractionRequest);
    }

    /**
     * Configure initial user interface appearance and state
     */
    private void configureUserInterface() {
        extractionProgress.setVisibility(View.GONE);
        resetDisplayContent();
    }

    /**
     * Process extraction button interaction and validate input
     */
    private void processExtractionRequest(View view) {
        String youtubeUrl = urlInputField.getText().toString().trim();
        
        if (!validateUserInput(youtubeUrl)) {
            return;
        }

        executeComprehensiveExtraction(youtubeUrl);
    }

    /**
     * Validate user input and provide appropriate feedback
     */
    private boolean validateUserInput(@NonNull String youtubeUrl) {
        if (TextUtils.isEmpty(youtubeUrl)) {
            displayUserMessage("Please enter a valid YouTube URL");
            return false;
        }

        if (currentExtractionState == ExtractionState.IN_PROGRESS) {
            displayUserMessage("Extraction already in progress");
            return false;
        }

        return true;
    }

    /**
     * Execute comprehensive extraction process with unified StreamExtractor
     */
    private void executeComprehensiveExtraction(@NonNull String youtubeUrl) {
        updateExtractionState(ExtractionState.IN_PROGRESS);
        updateStatusInformation("Initiating comprehensive stream extraction...");
        resetDisplayContent();

        streamExtractor.extractAllWithQuality(youtubeUrl, QualityMode.USER_PREFERENCE, 
            new StreamExtractor.OnStreamExtractionListener() {
                
                @Override
                public void onVideoReady(@NonNull String videoUrl) {
                    runOnUiThread(() -> {
                        videoUrlDisplay.setText("Video Stream: " + videoUrl);
                        updateStatusInformation("Video stream extracted successfully");
                        Log.d(TAG, "Video URL extracted: " + videoUrl);
                    });
                }

                @Override
                public void onAudioReady(@NonNull String audioUrl) {
                    runOnUiThread(() -> {
                        audioUrlDisplay.setText("Audio Stream: " + audioUrl);
                        updateStatusInformation("Audio stream extracted successfully");
                        Log.d(TAG, "Audio URL extracted: " + audioUrl);
                    });
                }

                @Override
                public void onInformationReady(@NonNull StreamInfo streamInfo) {
                    runOnUiThread(() -> {
                        displayVideoInformation(streamInfo);
                        updateStatusInformation("Video information extracted successfully");
                        Log.d(TAG, "Video information extracted for: " + streamInfo.getName());
                    });
                }

                @Override
                public void onExtractionError(@NonNull Exception error, @NonNull String operationType) {
                    runOnUiThread(() -> {
                        handleExtractionFailure(error, operationType);
                    });
                }
            });
    }

    /**
     * Display comprehensive video information from StreamInfo object
     */
    private void displayVideoInformation(@NonNull StreamInfo streamInfo) {
        StringBuilder infoBuilder = new StringBuilder();
        infoBuilder.append("Title: ").append(streamInfo.getName()).append("\n");
        infoBuilder.append("Uploader: ").append(streamInfo.getUploaderName()).append("\n");
        infoBuilder.append("Duration: ").append(formatDuration(streamInfo.getDuration())).append("\n");
        infoBuilder.append("Views: ").append(streamInfo.getViewCount()).append("\n");
        
        if (streamInfo.getDescription() != null && streamInfo.getDescription().getContent() != null) {
            String description = streamInfo.getDescription().getContent();
            String truncatedDescription = description.length() > 100 ? 
                description.substring(0, 100) + "..." : description;
            infoBuilder.append("Description: ").append(truncatedDescription);
        }

        videoInfoDisplay.setText(infoBuilder.toString());
    }

    /**
     * Format duration from seconds to readable time format
     */
    private String formatDuration(long durationInSeconds) {
        if (durationInSeconds <= 0) {
            return "Unknown";
        }
        
        long hours = durationInSeconds / 3600;
        long minutes = (durationInSeconds % 3600) / 60;
        long seconds = durationInSeconds % 60;
        
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%d:%02d", minutes, seconds);
        }
    }

    /**
     * Handle extraction failures with comprehensive error reporting
     */
    private void handleExtractionFailure(@NonNull Exception error, @NonNull String operationType) {
        updateExtractionState(ExtractionState.FAILED);
        String errorMessage = "Extraction failed (" + operationType + "): " + error.getMessage();
        updateStatusInformation(errorMessage);
        displayUserMessage("Failed to extract content: " + error.getMessage());
        Log.e(TAG, "Extraction error in " + operationType, error);
    }

    /**
     * Update extraction state and corresponding user interface elements
     */
    private void updateExtractionState(@NonNull ExtractionState newState) {
        currentExtractionState = newState;
        boolean isExtracting = (newState == ExtractionState.IN_PROGRESS);
        
        extractionProgress.setVisibility(isExtracting ? View.VISIBLE : View.GONE);
        extractButton.setEnabled(!isExtracting);
        extractButton.setText(isExtracting ? "Extracting..." : "Extract Content");

        if (newState == ExtractionState.COMPLETED || newState == ExtractionState.FAILED) {
            // Extraction process completed, reset to idle after brief delay
            extractButton.postDelayed(() -> {
                if (currentExtractionState != ExtractionState.IN_PROGRESS) {
                    currentExtractionState = ExtractionState.IDLE;
                }
            }, 1000);
        }
    }

    /**
     * Update status display with current extraction progress information
     */
    private void updateStatusInformation(@NonNull String status) {
        statusDisplay.setText("Status: " + status);
    }

    /**
     * Reset all content display fields to their initial state
     */
    private void resetDisplayContent() {
        videoInfoDisplay.setText("Video Information: Not extracted");
        audioUrlDisplay.setText("Audio Stream: Not extracted");
        videoUrlDisplay.setText("Video Stream: Not extracted");
        updateStatusInformation("Ready for comprehensive extraction");
    }

    /**
     * Reset complete interface state including extraction state
     */
    private void resetInterfaceState() {
        resetDisplayContent();
        updateExtractionState(ExtractionState.IDLE);
    }

    /**
     * Display messages to users through Toast notifications
     */
    private void displayUserMessage(@NonNull String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    /**
     * Handle activity lifecycle events and resource management
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        performResourceCleanup();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (currentExtractionState == ExtractionState.IN_PROGRESS) {
            updateExtractionState(ExtractionState.IDLE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (currentExtractionState != ExtractionState.IN_PROGRESS) {
            resetInterfaceState();
        }
    }

    /**
     * Perform comprehensive resource cleanup operations
     */
    private void performResourceCleanup() {
        try {
            if (streamExtractor != null) {
                streamExtractor.cleanup();
                streamExtractor = null;
            }
            currentExtractionState = ExtractionState.IDLE;
        } catch (Exception e) {
            Log.e(TAG, "Error during resource cleanup", e);
        }
    }
}