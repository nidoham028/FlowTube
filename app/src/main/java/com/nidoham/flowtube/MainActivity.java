package com.nidoham.flowtube;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.color.DynamicColors;
import com.nidoham.flowtube.databinding.ActivityMainBinding;
import com.nidoham.flowtube.fragment.HomeFragment;

import java.util.ArrayList;
import java.util.List;

/**
 * MainActivity for FlowTube.
 * - Handles navigation safety, crash protection, and fragment lifecycle.
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private ViewPager2 viewPager;
    private BottomNavigationView bottomNav;
    private List<Fragment> fragments;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            binding = ActivityMainBinding.inflate(getLayoutInflater());
            View view = binding.getRoot();
            setContentView(view);

            int seedColor = safeGetColor(R.color.seed);

            // Set status/navigation bar color safely
            setSystemBarColors(seedColor);

            // Initialize views
            viewPager = binding.contentPage;
            bottomNav = binding.bottomNav;
            viewPager.setUserInputEnabled(false);

            // Setup fragments and navigation
            setupFragments();
            setupOnClickListener();

            ViewPagerAdapter adapter = new ViewPagerAdapter(this, fragments);
            viewPager.setAdapter(adapter);

            setupBottomNavigation();

            applyDynamicColors(seedColor);

        } catch (Throwable t) {
            Log.e("MainActivity", "Critical error in onCreate", t);
            showFatalErrorAndExit(t);
        }
    }

    /**
     * Set status bar and navigation bar colors, safely.
     */
    private void setSystemBarColors(int color) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                getWindow().setStatusBarColor(color);
                getWindow().setNavigationBarColor(color);
            }
        } catch (Exception e) {
            Log.w("MainActivity", "Failed to set system bar colors", e);
        }
    }

    /**
     * Get color resource safely.
     */
    private int safeGetColor(int colorResId) {
        try {
            return ContextCompat.getColor(this, colorResId);
        } catch (Exception e) {
            Log.w("MainActivity", "Failed to get color, defaulting to black", e);
            return 0xFF000000;
        }
    }

    /**
     * Setup search button click with protection.
     */
    private void setupOnClickListener() {
        try {
            binding.searchBtn.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(getApplicationContext(), SearchActivity.class));
                } catch (Exception e) {
                    Log.e("MainActivity", "Failed to open SearchActivity", e);
                    showToast("Search unavailable");
                }
            });
        } catch (Exception e) {
            Log.w("MainActivity", "Search button setup failed", e);
        }
    }

    /**
     * Setup fragments safely.
     */
    private void setupFragments() {
        fragments = new ArrayList<>();
        try {
            fragments.add(new HomeFragment());
            fragments.add(new CommunityFragment());
            fragments.add(new LibraryFragment());
            fragments.add(new SubscriptionFragment());
        } catch (Exception e) {
            Log.e("MainActivity", "Error creating fragments", e);
            showToast("Some tabs could not load.");
        }
    }

    /**
     * Setup BottomNavigationView with safety.
     */
    private void setupBottomNavigation() {
        try {
            bottomNav.setOnItemSelectedListener(item -> {
                try {
                    int itemId = item.getItemId();
                    if (itemId == R.id.nav_home) {
                        viewPager.setCurrentItem(0, false);
                        return true;
                    } else if (itemId == R.id.nav_community) {
                        viewPager.setCurrentItem(1, false);
                        return true;
                    } else if (itemId == R.id.nav_library) {
                        viewPager.setCurrentItem(2, false);
                        return true;
                    } else if (itemId == R.id.nav_subscription) {
                        viewPager.setCurrentItem(3, false);
                        return true;
                    }
                } catch (Exception e) {
                    Log.e("MainActivity", "Navigation error", e);
                    showToast("Navigation failed.");
                }
                return false;
            });

            // Sync ViewPager with BottomNavigationView
            viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageSelected(int position) {
                    super.onPageSelected(position);
                    try {
                        // Prevent infinite loop by removing and re-adding listener
                        bottomNav.setOnItemSelectedListener(null);
                        bottomNav.getMenu().getItem(position).setChecked(true);
                        // Restore listener
                        bottomNav.setOnItemSelectedListener(item -> {
                            try {
                                int itemId = item.getItemId();
                                if (itemId == R.id.nav_home) {
                                    viewPager.setCurrentItem(0, false);
                                    return true;
                                } else if (itemId == R.id.nav_community) {
                                    viewPager.setCurrentItem(1, false);
                                    return true;
                                } else if (itemId == R.id.nav_library) {
                                    viewPager.setCurrentItem(2, false);
                                    return true;
                                } else if (itemId == R.id.nav_subscription) {
                                    viewPager.setCurrentItem(3, false);
                                    return true;
                                }
                            } catch (Exception e) {
                                Log.e("MainActivity", "BottomNav error", e);
                                showToast("Navigation failed.");
                            }
                            return false;
                        });
                    } catch (Exception e) {
                        Log.e("MainActivity", "Failed to update BottomNav", e);
                    }
                }
            });
        } catch (Exception e) {
            Log.e("MainActivity", "BottomNavigation setup failed", e);
        }
    }

    /**
     * Apply Material You dynamic colors if available, safely.
     */
    private void applyDynamicColors(int seedColor) {
        try {
            DynamicColors.applyIfAvailable(this);
            setSystemBarColors(seedColor);
        } catch (Exception e) {
            Log.w("MainActivity", "DynamicColors apply failed", e);
        }
    }

    /**
     * Show a toast message safely.
     */
    private void showToast(String msg) {
        try {
            android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.w("MainActivity", "Toast failed", e);
        }
    }

    /**
     * Show fatal error and finish activity.
     */
    private void showFatalErrorAndExit(Throwable t) {
        try {
            showToast("Critical error! App will close.");
        } catch (Exception ignored) {}
        finish();
    }

    /**
     * ViewPager2 Adapter with protection.
     */
    private static class ViewPagerAdapter extends FragmentStateAdapter {
        private final List<Fragment> fragments;

        public ViewPagerAdapter(@NonNull FragmentActivity fa, @NonNull List<Fragment> fragments) {
            super(fa);
            this.fragments = fragments;
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            try {
                return fragments.get(position);
            } catch (Exception e) {
                Log.e("MainActivity", "Fragment create failed for position " + position, e);
                // Return a blank fragment to avoid crash
                return new Fragment();
            }
        }

        @Override
        public int getItemCount() {
            return fragments != null ? fragments.size() : 0;
        }
    }

    /**
     * Back navigation safety:
     * - If not on home, always return to home.
     * - If already home, allow default back.
     */
    @Override
    public void onBackPressed() {
        try {
            if (viewPager != null && viewPager.getCurrentItem() != 0) {
                viewPager.setCurrentItem(0, false);
                if (bottomNav != null)
                    bottomNav.setSelectedItemId(R.id.nav_home);
            } else {
                super.onBackPressed();
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Back navigation error", e);
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        try {
            // Cleanup listeners to avoid leaks
            if (viewPager != null)
                viewPager.setAdapter(null);
            if (bottomNav != null)
                bottomNav.setOnItemSelectedListener(null);
        } catch (Exception e) {
            Log.w("MainActivity", "onDestroy cleanup failed", e);
        }
        super.onDestroy();
    }
}