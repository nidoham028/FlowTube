package com.nidoham.opentube.player;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

/**
 * ViewModel that observes ExoPlayer events and exposes LiveData for UI components.
 * Handles periodic updates of playback position and duration.
 * Uses Media3 ExoPlayer APIs.
 */
public class PlayerViewModel extends AndroidViewModel implements Player.Listener {
    private static final String TAG = "PlayerViewModel";
    private static final long UPDATE_INTERVAL_MS = 500L;
    
    private PlayerManager playerManager;
    private ExoPlayer exoPlayer;
    private Handler mainHandler;
    private Runnable updateRunnable;
    private boolean isUpdating = false;
    
    // LiveData for UI observation
    private final MutableLiveData<Boolean> _isPlaying = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> _playbackState = new MutableLiveData<>(Player.STATE_IDLE);
    private final MutableLiveData<Long> _currentPosition = new MutableLiveData<>(0L);
    private final MutableLiveData<Long> _duration = new MutableLiveData<>(0L);
    private final MutableLiveData<Boolean> _isBuffering = new MutableLiveData<>(false);
    private final MutableLiveData<String> _errorMessage = new MutableLiveData<>();
    
    public final LiveData<Boolean> isPlaying = _isPlaying;
    public final LiveData<Integer> playbackState = _playbackState;
    public final LiveData<Long> currentPosition = _currentPosition;
    public final LiveData<Long> duration = _duration;
    public final LiveData<Boolean> isBuffering = _isBuffering;
    public final LiveData<String> errorMessage = _errorMessage;
    
    public PlayerViewModel(@NonNull Application application) {
        super(application);
        initializeViewModel();
    }
    
    /**
     * Initialize the ViewModel components
     */
    private void initializeViewModel() {
        playerManager = PlayerManager.getInstance(getApplication());
        exoPlayer = playerManager.getExoPlayer();
        mainHandler = new Handler(Looper.getMainLooper());
        
        // Add this ViewModel as a listener to ExoPlayer
        exoPlayer.addListener(this);
        
        // Initialize the update runnable
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                updatePlaybackInfo();
                if (isUpdating) {
                    mainHandler.postDelayed(this, UPDATE_INTERVAL_MS);
                }
            }
        };
        
        Log.d(TAG, "PlayerViewModel initialized with Media3");
    }

    // ADDED: Method to expose the ExoPlayer instance to the UI
    public ExoPlayer getExoPlayer() {
        return exoPlayer;
    }
    
    /**
     * Play stream with separate audio and video URLs
     */
    public void playStream(String audioUrl, String videoUrl) {
        playerManager.playStream(audioUrl, videoUrl);
    }
    
    /**
     * Play single media URL
     */
    public void playMedia(String mediaUrl) {
        playerManager.playMedia(mediaUrl);
    }
    
    /**
     * Toggle play/pause
     */
    public void togglePlayPause() {
        if (exoPlayer.isPlaying()) {
            playerManager.pause();
        } else {
            playerManager.play();
        }
    }
    
    /**
     * Pause playback
     */
    public void pause() {
        playerManager.pause();
    }
    
    /**
     * Resume playback
     */
    public void play() {
        playerManager.play();
    }
    
    /**
     * Stop playback
     */
    public void stop() {
        playerManager.stop();
    }
    
    /**
     * Seek to specific position
     */
    public void seekTo(long positionMs) {
        playerManager.seekTo(positionMs);
    }
    
    /**
     * Update playback information (position, duration)
     */
    private void updatePlaybackInfo() {
        if (exoPlayer != null) {
            long currentPos = exoPlayer.getCurrentPosition();
            long totalDuration = exoPlayer.getDuration();
            
            _currentPosition.setValue(currentPos);
            if (totalDuration > 0) {
                _duration.setValue(totalDuration);
            }
        }
    }
    
    /**
     * Start periodic updates
     */
    private void startUpdates() {
        if (!isUpdating) {
            isUpdating = true;
            mainHandler.post(updateRunnable);
            Log.d(TAG, "Started periodic updates");
        }
    }
    
    /**
     * Stop periodic updates
     */
    private void stopUpdates() {
        if (isUpdating) {
            isUpdating = false;
            mainHandler.removeCallbacks(updateRunnable);
            Log.d(TAG, "Stopped periodic updates");
        }
    }
    
    // Player.Listener implementations
    
    @Override
    public void onIsPlayingChanged(boolean isPlaying) {
        _isPlaying.setValue(isPlaying);
        
        if (isPlaying) {
            startUpdates();
        } else {
            stopUpdates();
        }
        
        Log.d(TAG, "Playing state changed: " + isPlaying);
    }
    
    @Override
    public void onPlaybackStateChanged(int playbackState) {
        _playbackState.setValue(playbackState);
        
        boolean buffering = playbackState == Player.STATE_BUFFERING;
        _isBuffering.setValue(buffering);
        
        // Update position and duration when ready
        if (playbackState == Player.STATE_READY) {
            updatePlaybackInfo();
        }
        
        // Stop updates when ended or idle
        if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
            stopUpdates();
        }
        
        Log.d(TAG, "Playback state changed: " + getPlaybackStateString(playbackState));
    }
    
    @Override
    public void onPlayerError(PlaybackException error) {
        String errorMsg = "Playback error: " + error.getMessage();
        _errorMessage.setValue(errorMsg);
        Log.e(TAG, errorMsg, error);
        
        stopUpdates();
    }
    
    /**
     * Get human-readable playback state string for logging
     */
    private String getPlaybackStateString(int state) {
        switch (state) {
            case Player.STATE_IDLE: return "IDLE";
            case Player.STATE_BUFFERING: return "BUFFERING";
            case Player.STATE_READY: return "READY";
            case Player.STATE_ENDED: return "ENDED";
            default: return "UNKNOWN";
        }
    }
    
    /**
     * Clean up resources when ViewModel is destroyed
     */
    @Override
    protected void onCleared() {
        super.onCleared();
        
        // Stop updates
        stopUpdates();
        
        // Remove listener
        if (exoPlayer != null) {
            exoPlayer.removeListener(this);
        }
        
        // Clear handler callbacks
        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
        }
        
        Log.d(TAG, "PlayerViewModel cleared");
    }
}