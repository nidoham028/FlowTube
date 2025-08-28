package com.nidoham.flowtube;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.nidoham.flowtube.stream.AudioStreamDownloader;

public class YouTubeExperimentActivity extends AppCompatActivity {
    private static final String TAG = "YouTubeExperiment";

    private EditText inputUrl;
    private Button fetchButtonLow;
    private Button fetchButtonHigh;
    private TextView resultText;
    private AudioStreamDownloader downloader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_youtube_experiment);

        inputUrl = findViewById(R.id.input_url);
        fetchButtonLow = findViewById(R.id.fetch_button_low);   // লো কোয়ালিটি
        fetchButtonHigh = findViewById(R.id.fetch_button_high); // হাই কোয়ালিটি
        resultText = findViewById(R.id.result_text);

        downloader = new AudioStreamDownloader(this);

        // ✅ Data Saver (Low Quality)
        fetchButtonLow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String url = inputUrl.getText().toString().trim();
                if (!isValidYouTubeUrl(url)) return;

                fetchButtonLow.setEnabled(false);
                resultText.setText("Retrieving Data Saver Audio link...");

                downloader.loadDataSaverAudio(url, new AudioStreamDownloader.OnAudioExtractListener() {
                    @Override
                    public void onSuccess(String audioUrl, int bitrate, String format) {
                        runOnUiThread(() -> {
                            resultText.setText("Data Saver (Low) Audio Link:\n" + audioUrl +
                                    "\nBitrate: " + bitrate + " kbps\nFormat: " + format);
                            fetchButtonLow.setEnabled(true);
                        });
                    }

                    @Override
                    public void onError(Exception e) {
                        Log.e(TAG, "Low quality extraction failed", e);
                        runOnUiThread(() -> {
                            resultText.setText("Low quality extraction failed: " + e.getMessage());
                            fetchButtonLow.setEnabled(true);
                        });
                    }
                });
            }
        });

        // ✅ High Quality
        fetchButtonHigh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String url = inputUrl.getText().toString().trim();
                if (!isValidYouTubeUrl(url)) return;

                fetchButtonHigh.setEnabled(false);
                resultText.setText("Retrieving High Quality Audio link...");

                downloader.loadHighQualityAudio(url, new AudioStreamDownloader.OnAudioExtractListener() {
                    @Override
                    public void onSuccess(String audioUrl, int bitrate, String format) {
                        runOnUiThread(() -> {
                            resultText.setText("High Quality Audio Link:\n" + audioUrl +
                                    "\nBitrate: " + bitrate + " kbps\nFormat: " + format);
                            fetchButtonHigh.setEnabled(true);
                        });
                    }

                    @Override
                    public void onError(Exception e) {
                        Log.e(TAG, "High quality extraction failed", e);
                        runOnUiThread(() -> {
                            resultText.setText("High quality extraction failed: " + e.getMessage());
                            fetchButtonHigh.setEnabled(true);
                        });
                    }
                });
            }
        });
    }

    private boolean isValidYouTubeUrl(String url) {
        if (url == null || url.isEmpty() ||
                !(url.contains("youtube.com") || url.contains("youtu.be"))) {
            resultText.setText("Please enter a valid YouTube video URL.");
            Toast.makeText(this, "Please enter a valid YouTube video URL.", Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }
}