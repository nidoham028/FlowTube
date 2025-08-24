package com.nidoham.flowtube.data;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Optimized trending content retriever for Bangladesh-specific categories.
 * This class provides thread-safe access to trending content categories
 * with improved performance and maintainability. Returns single trending
 * items instead of arrays for simplified usage.
 * 
 * @author System
 * @version 3.0
 * @since 1.0
 */
public final class TrendingContentManager {
    
    // Using ConcurrentHashMap for thread safety and better performance
    private static final Map<String, List<String>> TRENDING_CATEGORIES = new ConcurrentHashMap<>();
    
    // Pre-computed array for getAllCategories to avoid repeated array creation
    private static final String[] CATEGORY_KEYS_CACHE;
    
    // Thread-safe random instance for better performance
    private static final ThreadLocalRandom RANDOM = ThreadLocalRandom.current();
    
    static {
        // Initialize with immutable lists for better memory efficiency and thread safety
        TRENDING_CATEGORIES.put("songs", Collections.unmodifiableList(Arrays.asList(
                "(new Bangladeshi songs this week) | (latest Indian songs today) | (new Bangladeshi music 2025) | (latest Indian music 2025)"
        )));
        
        // Cache the category keys array to avoid repeated creation
        CATEGORY_KEYS_CACHE = TRENDING_CATEGORIES.keySet().toArray(new String[0]);
    }
    
    // Private constructor to prevent instantiation
    private TrendingContentManager() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
    
    /**
     * Retrieves a random trending content item for the specified category.
     * 
     * @param category The category name (case-insensitive)
     * @return A random trending content item for the category, or null if category not found or empty
     * @throws IllegalArgumentException if category is null
     */
    public static String getTrending(String category) {
        if (category == null) {
            throw new IllegalArgumentException("Category cannot be null");
        }
        
        String normalizedCategory = category.toLowerCase().trim();
        List<String> trends = TRENDING_CATEGORIES.get(normalizedCategory);
        
        if (trends == null || trends.isEmpty()) {
            return null;
        }
        
        return trends.get(RANDOM.nextInt(trends.size()));
    }
    
    /**
     * Retrieves the first trending content item for the specified category.
     * 
     * @param category The category name (case-insensitive)
     * @return The first trending content item for the category, or null if category not found or empty
     * @throws IllegalArgumentException if category is null
     */
    public static String getFirstTrending(String category) {
        if (category == null) {
            throw new IllegalArgumentException("Category cannot be null");
        }
        
        String normalizedCategory = category.toLowerCase().trim();
        List<String> trends = TRENDING_CATEGORIES.get(normalizedCategory);
        
        if (trends == null || trends.isEmpty()) {
            return null;
        }
        
        return trends.get(0);
    }
    
    /**
     * Retrieves a specific trending content item by index for the specified category.
     * 
     * @param category The category name (case-insensitive)
     * @param index The index of the item to retrieve (0-based)
     * @return The trending content item at the specified index, or null if category/index not found
     * @throws IllegalArgumentException if category is null or index is negative
     */
    public static String getTrendingByIndex(String category, int index) {
        if (category == null) {
            throw new IllegalArgumentException("Category cannot be null");
        }
        if (index < 0) {
            throw new IllegalArgumentException("Index cannot be negative");
        }
        
        String normalizedCategory = category.toLowerCase().trim();
        List<String> trends = TRENDING_CATEGORIES.get(normalizedCategory);
        
        if (trends == null || index >= trends.size()) {
            return null;
        }
        
        return trends.get(index);
    }
    
    /**
     * Retrieves all available category names.
     * 
     * @return Array of all category names
     */
    public static String[] getAllCategories() {
        // Return a copy to prevent external modification
        return Arrays.copyOf(CATEGORY_KEYS_CACHE, CATEGORY_KEYS_CACHE.length);
    }
    
    /**
     * Checks if a category exists in the trending categories.
     * 
     * @param category The category name to check (case-insensitive)
     * @return true if the category exists, false otherwise
     */
    public static boolean hasCategory(String category) {
        return category != null && TRENDING_CATEGORIES.containsKey(category.toLowerCase().trim());
    }
    
    /**
     * Gets the count of trending items for a specific category.
     * 
     * @param category The category name (case-insensitive)
     * @return Number of trending items in the category, or 0 if category not found
     */
    public static int getCategorySize(String category) {
        if (category == null) {
            return 0;
        }
        
        List<String> trends = TRENDING_CATEGORIES.get(category.toLowerCase().trim());
        return trends != null ? trends.size() : 0;
    }
    
    /**
     * Gets the total number of available categories.
     * 
     * @return Total number of categories
     */
    public static int getCategoryCount() {
        return TRENDING_CATEGORIES.size();
    }
    
    /**
     * Retrieves a random trending item from any available category.
     * 
     * @return A random trending item from any category, or null if no categories exist
     */
    public static String getRandomTrendingFromAnyCategory() {
        if (TRENDING_CATEGORIES.isEmpty()) {
            return null;
        }
        
        String[] categories = CATEGORY_KEYS_CACHE;
        String randomCategory = categories[RANDOM.nextInt(categories.length)];
        return getTrending(randomCategory);
    }
    
    /**
     * Retrieves all trending items for a specific category as a single concatenated string.
     * 
     * @param category The category name (case-insensitive)
     * @param delimiter The delimiter to use between items (default: ", ")
     * @return Concatenated string of all trending items, or null if category not found
     */
    public static String getAllTrendingAsString(String category, String delimiter) {
        if (category == null) {
            return null;
        }
        
        String normalizedCategory = category.toLowerCase().trim();
        List<String> trends = TRENDING_CATEGORIES.get(normalizedCategory);
        
        if (trends == null || trends.isEmpty()) {
            return null;
        }
        
        return String.join(delimiter != null ? delimiter : ", ", trends);
    }
    
    /**
     * Retrieves all trending items for a specific category as a single concatenated string
     * using comma-space as delimiter.
     * 
     * @param category The category name (case-insensitive)
     * @return Comma-separated string of all trending items, or null if category not found
     */
    public static String getAllTrendingAsString(String category) {
        return getAllTrendingAsString(category, ", ");
    }
}