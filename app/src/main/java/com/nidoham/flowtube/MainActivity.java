package com.nidoham.flowtube;

import androidx.core.content.ContextCompat;
import android.os.Build;
import com.google.android.material.color.DynamicColors;
import com.nidoham.flowtube.databinding.ActivityMainBinding;
import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

  private ActivityMainBinding binding;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    binding = ActivityMainBinding.inflate(getLayoutInflater());
    View view = binding.getRoot();
    setContentView(view);
	
    int seedColor = ContextCompat.getColor(this, R.color.seed);

    // ✅ Set it as navigation bar color
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        getWindow().setNavigationBarColor(seedColor);
    }
  }
}
