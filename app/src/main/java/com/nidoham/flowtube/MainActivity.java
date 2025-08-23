package com.nidoham.flowtube;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import android.os.Build;
import com.google.android.material.color.DynamicColors;
import com.nidoham.flowtube.databinding.ActivityMainBinding;
import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private ViewPager2 viewPager;
    private BottomNavigationView bottomNav;
    private List<Fragment> fragments;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        View view = binding.getRoot();
        setContentView(view);
        
        int seedColor = ContextCompat.getColor(this, R.color.seed);

        // ✅ Set it as status bar color
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(seedColor);
            getWindow().setNavigationBarColor(seedColor);
        }

        // Initialize views
        viewPager = binding.contentPage;
        bottomNav = binding.bottomNav;

        // Disable swipe gesture if you want only bottom nav clicks
        viewPager.setUserInputEnabled(false);

        // Setup fragments
        setupFragments();
        
        // Setup ViewPager adapter
        ViewPagerAdapter adapter = new ViewPagerAdapter(this, fragments);
        viewPager.setAdapter(adapter);

        // Setup bottom navigation listener
        setupBottomNavigation();

        // Apply dynamic colors if available
        applyDynamicColors();
    }

    private void setupFragments() {
        fragments = new ArrayList<>();
        // Add your fragments in the order of navigation items
        fragments.add(new HomeFragment());
        fragments.add(new CommunityFragment());
        fragments.add(new LibraryFragment());
        fragments.add(new SubscriptionFragment());
    }

    private void setupBottomNavigation() {
        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            
            switch (itemId) {
                case R.id.nav_home:
                    viewPager.setCurrentItem(0, false);
                    return true;
                case R.id.nav_community:
                    viewPager.setCurrentItem(1, false);
                    return true;
                case R.id.nav_library:
                    viewPager.setCurrentItem(2, false);
                    return true;
                case R.id.nav_subscription:
                    viewPager.setCurrentItem(3, false);
                    return true;
            }
            return false;
        });

        // Sync ViewPager with BottomNavigationView
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                // Prevent infinite loop by removing and re-adding listener
                bottomNav.setOnItemSelectedListener(null);
                bottomNav.getMenu().getItem(position).setChecked(true);
                bottomNav.setOnItemSelectedListener(item -> {
                    int itemId = item.getItemId();
                    switch (itemId) {
                        case R.id.nav_home:
                            viewPager.setCurrentItem(0, false);
                            return true;
                        case R.id.nav_community:
                            viewPager.setCurrentItem(1, false);
                            return true;
                        case R.id.nav_library:
                            viewPager.setCurrentItem(2, false);
                            return true;
                        case R.id.nav_subscription:
                            viewPager.setCurrentItem(3, false);
                            return true;
                    }
                    return false;
                });
            }
        });
    }

    private void applyDynamicColors() {
        // Apply Material You dynamic colors if available
        try {
            DynamicColors.applyIfAvailable(this);
            
            // After applying dynamic colors, you might want to update the status bar
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                int seedColor = ContextCompat.getColor(this, R.color.seed);
                getWindow().setStatusBarColor(seedColor);
            }
        } catch (Exception e) {
            // Handle exception if DynamicColors is not available
            e.printStackTrace();
        }
    }

    // ViewPager2 Adapter
    private static class ViewPagerAdapter extends FragmentStateAdapter {
        private final List<Fragment> fragments;

        public ViewPagerAdapter(FragmentActivity fa, List<Fragment> fragments) {
            super(fa);
            this.fragments = fragments;
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            return fragments.get(position);
        }

        @Override
        public int getItemCount() {
            return fragments.size();
        }
    }

    @Override
    public void onBackPressed() {
        // If not on home screen, go to home screen
        if (viewPager.getCurrentItem() != 0) {
            viewPager.setCurrentItem(0, false);
            bottomNav.setSelectedItemId(R.id.nav_home);
        } else {
            super.onBackPressed();
        }
    }
}