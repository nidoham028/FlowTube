package com.nidoham.flowtube;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.color.DynamicColors;
import com.nidoham.flowtube.databinding.ActivityMainBinding;

import com.nidoham.opentube.fragments.BaseStateFragment;
import com.nidoham.opentube.fragments.list.*;

/**
 * MainActivity for FlowTube.
 * Handles app initialization, navigation, and search.
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private BottomNavigationView bottomNav;
    private int currentNavigationId = R.id.nav_home;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        int seedColor = ContextCompat.getColor(this, R.color.seed);
        setSystemBarColors(seedColor);

        bottomNav = binding.bottomNav;

        setupSearchButton();
        setupBottomNavigation();
        applyDynamicColors();

        // Load initial fragment only if first launch
        if (savedInstanceState == null) {
            loadNavigationItem(R.id.nav_home);
        }
    }

    /**
     * Setup search button click.
     */
    private void setupSearchButton() {
        binding.searchBtn.setOnClickListener(v -> {
            startActivity(new Intent(this, SearchActivity.class));
        });
    }

    /**
     * Setup bottom navigation listener.
     */
    private void setupBottomNavigation() {
        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId != currentNavigationId) { // Prevent reloading same fragment
                currentNavigationId = itemId;
                loadNavigationItem(itemId);
            }
            return true;
        });
    }

    /**
     * Load fragment based on navigation ID.
     */
    private void loadNavigationItem(int navigationId) {
        switch (navigationId) {
            case R.id.nav_home:
                BaseStateFragment.loadFragment(getSupportFragmentManager(), new HomeFragment(), false, "home");
                break;
            case R.id.nav_community:
                BaseStateFragment.loadFragment(getSupportFragmentManager(), new CommunityFragment(), false, "community");
                break;
            case R.id.nav_library:
                BaseStateFragment.loadFragment(getSupportFragmentManager(), new LibraryFragment(), false, "library");
                break;
            case R.id.nav_subscription:
                BaseStateFragment.loadFragment(getSupportFragmentManager(), new SubscriptionFragment(), false, "subscription");
                break;
            default:
                BaseStateFragment.loadFragment(getSupportFragmentManager(), new HomeFragment(), false, "home");
                break;
        }
    }

    /**
     * Set system bar colors.
     */
    private void setSystemBarColors(int color) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(color);
            getWindow().setNavigationBarColor(color);
        }
    }

    /**
     * Apply Material You dynamic colors (Android 12+).
     */
    private void applyDynamicColors() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            DynamicColors.applyIfAvailable(this);
        }
    }

    /**
     * Handle back navigation with home-first behavior.
     */
    @Override
    public void onBackPressed() {
        finishAffinity();
        super.onBackPressed();
    }

    /**
     * Check if current navigation is Home.
     */
    public boolean isOnHomeNavigation() {
        return currentNavigationId == R.id.nav_home;
    }

    /**
     * Navigate to Home tab.
     */
    public void navigateToHome() {
        bottomNav.setSelectedItemId(R.id.nav_home);
    }

    /**
     * Save current navigation ID on config change.
     */
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("current_navigation_id", currentNavigationId);
    }

    /**
     * Restore current navigation ID after config change.
     */
    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        currentNavigationId = savedInstanceState.getInt("current_navigation_id", R.id.nav_home);
        bottomNav.setSelectedItemId(currentNavigationId);
    }

    @Override
    protected void onDestroy() {
        if (bottomNav != null) {
            bottomNav.setOnItemSelectedListener(null);
        }
        super.onDestroy();
    }
}
