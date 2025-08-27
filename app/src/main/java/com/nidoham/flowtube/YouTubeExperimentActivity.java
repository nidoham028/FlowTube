package com.nidoham.flowtube;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.nidoham.flowtube.tools.YouTubeStreamResolver;

public class YouTubeExperimentActivity extends AppCompatActivity {
    private static final String TAG = "YouTubeExperiment";

    private EditText inputUrl;
    private Button fetchButton;
    private TextView resultText;
    private YouTubeStreamResolver resolver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_youtube_experiment);

        inputUrl = findViewById(R.id.input_url);
        fetchButton = findViewById(R.id.fetch_button);
        resultText = findViewById(R.id.result_text);

        resolver = new YouTubeStreamResolver();

        fetchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String url = inputUrl.getText().toString().trim();
                if (url == null || url.isEmpty() ||
                        !(url.contains("youtube.com") || url.contains("youtu.be"))) {
                    resultText.setText("Please enter a valid YouTube video URL.");
                    Toast.makeText(YouTubeExperimentActivity.this, "Please enter a valid YouTube video URL.", Toast.LENGTH_LONG).show();
                    return;
                }

                fetchButton.setEnabled(false);
                resultText.setText("Retrieving WiFi optimized link...");
                resolver.getWiFiOptimizedDirectLink(url, new YouTubeStreamResolver.DirectLinkCallback() {
                    @Override
                    public void onSuccess(String directUrl, com.nidoham.flowtube.tools.model.StreamInfo streamInfo) {
                        runOnUiThread(() -> {
                            resultText.setText("WiFi Optimized Direct Link:\n" + directUrl);
                            fetchButton.setEnabled(true);
                        });
                    }

                    @Override
                    public void onError(String error, Exception exception) {
                        Log.e(TAG, "WiFi optimized extraction failed", exception);
                        runOnUiThread(() -> {
                            resultText.setText("WiFi optimized extraction failed: " + error);
                            fetchButton.setEnabled(true);
                        });
                    }
                });
            }
        });
    }
}