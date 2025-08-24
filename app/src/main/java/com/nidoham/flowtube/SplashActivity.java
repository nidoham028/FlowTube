package com.nidoham.flowtube;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityOptionsCompat;
import com.nidoham.flowtube.core.language.AppLanguage;
import com.nidoham.flowtube.databinding.ActivitySplashBinding;

public class SplashActivity extends AppCompatActivity {
    private ActivitySplashBinding binding;
    private static final int SPLASH_DELAY = 3000; // 3 seconds

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Apply language settings before setting content view
        AppLanguage.getInstance(this).initialize();
        
        binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        if(true) {
        	navigateToNextScreen();
        } else {
        	Intent intent = new Intent(SplashActivity.this, PlayerActivity.class);
            startActivity(intent);
        }
    }
	
	@Deprecated
    private void navigateToNextScreen() {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            
            Intent intent = new Intent(SplashActivity.this, MainActivity.class);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Android 14+ (API 34+) - use the new method
                overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0);
                startActivity(intent);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                // Android 4.1+ (API 16+) - use ActivityOptionsCompat
                ActivityOptionsCompat options = ActivityOptionsCompat.makeCustomAnimation(
                    this, 
                    0, // enter animation
                    0  // exit animation
                );
                startActivity(intent, options.toBundle());
            } else {
                // Fallback for very old versions (though your minSdk is probably higher)
                startActivity(intent);
                overridePendingTransition(0, 0);
            }
            
            finish(); // Close splash activity so user can't go back
        }, SPLASH_DELAY);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null; // Prevent memory leaks
    }
}