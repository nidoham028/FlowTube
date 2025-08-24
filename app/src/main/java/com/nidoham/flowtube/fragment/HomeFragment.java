package com.nidoham.flowtube;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import com.nidoham.flowtube.fragment.home.*;
import com.nidoham.flowtube.R;

public class HomeFragment extends Fragment {
    
    private ViewPager2 viewPager;
    private TabLayout tabLayout;
    private HomePagerAdapter pagerAdapter;
    private TabLayoutMediator tabLayoutMediator;
    
    // Tab titles array for text-based tabs
    private final String[] tabTitles = {
        "Feed",        // Feed
        "Trending",    // Trending
        "Music",       // Music
        "Live",        // Live
        "Games"        // Games
    };
    
    public HomeFragment() {
        // Required empty public constructor
    }
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_home, container, false);
        
        // Initialize views
        viewPager = view.findViewById(R.id.content_pager);
        tabLayout = view.findViewById(R.id.tabs);
        
        // Set up ViewPager with adapter
        setupViewPager();
        
        // Connect TabLayout with ViewPager
        setupTabLayout();
            
        return view;
    }
    
    private void setupViewPager() {
        pagerAdapter = new HomePagerAdapter(this);
        viewPager.setAdapter(pagerAdapter);
        viewPager.setUserInputEnabled(false);
    }
    
    private void setupTabLayout() {
        tabLayoutMediator = new TabLayoutMediator(tabLayout, viewPager,
            (tab, position) -> {
                if (position < tabTitles.length) {
                    tab.setText(tabTitles[position]);
                }
            });
        tabLayoutMediator.attach();
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Detach the mediator to prevent memory leaks
        if (tabLayoutMediator != null) {
            tabLayoutMediator.detach();
        }
    }
    
    // Inner class for the ViewPager adapter
    private static class HomePagerAdapter extends FragmentStateAdapter {
        
        public HomePagerAdapter(@NonNull Fragment fragment) {
            super(fragment);
        }
        
        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0:
                    return new FeedFragment();
                case 1:
                    return new TrendingFragment();
                case 2:
                    return new FeedFragment(); // Music (reusing FeedFragment)
                case 3:
                    return new FeedFragment(); // Live (reusing FeedFragment)
                case 4:
                    return new FeedFragment(); // Games (reusing FeedFragment)
                default:
                    return new FeedFragment(); // Default fallback
            }
        }
        
        @Override
        public int getItemCount() {
            return 5; // 5 tabs with titles
        }
    }
}