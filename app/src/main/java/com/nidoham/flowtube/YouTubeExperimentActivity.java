package com.nidoham.flowtube;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.nidoham.flowtube.tools.YouTubeStreamResolver;
import com.nidoham.flowtube.tools.model.StreamInfo;
import java.util.List;

public class YouTubeExperimentActivity extends AppCompatActivity {
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
                if (url.isEmpty()) {
                    resultText.setText("Please enter a YouTube video URL.");
                    return;
                }
                resultText.setText("Loading...");
                new Thread(() -> {
                    try {
                        List<StreamInfo> streams = resolver.getAvailableStreams(url);
                        StringBuilder builder = new StringBuilder();
                        for (StreamInfo s : streams) {
                            builder.append("Quality: ").append(s.getQuality())
                                    .append("\nFormat: ").append(s.getFormat())
                                    .append("\nURL: ").append(s.getUrl())
                                    .append("\n\n");
                        }
                        runOnUiThread(() -> resultText.setText(builder.length() > 0 ? builder.toString() : "No streams found."));
                    } catch (Exception e) {
                        runOnUiThread(() -> resultText.setText("Failed: " + e.getMessage()));
                    }
                }).start();
            }
        });
    }
}