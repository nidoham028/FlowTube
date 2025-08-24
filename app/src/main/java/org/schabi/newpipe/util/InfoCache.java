/*
 * Copyright 2017 Mauricio Colli <mauriciocolli@outlook.com>
 * InfoCache.java is part of NewPipe
 *
 * License: GPL-3.0+
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.schabi.newpipe.util;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.LruCache;

import com.nidoham.flowtube.App;
import org.schabi.newpipe.extractor.Info;
import org.schabi.newpipe.extractor.ServiceList;

import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.schabi.newpipe.service.ServiceHelper;

public final class InfoCache {
    private final String TAG = getClass().getSimpleName();
    private static final boolean DEBUG = App.DEBUG;

    private static final InfoCache INSTANCE = new InfoCache();
    private static final int MAX_ITEMS_ON_CACHE = 60;
    private static final int TRIM_CACHE_TO = 30;

    private static final LruCache<String, CacheData> LRU_CACHE = new LruCache<>(MAX_ITEMS_ON_CACHE);
    
    // Enhanced metrics for performance monitoring
    private static final AtomicLong cacheHits = new AtomicLong(0);
    private static final AtomicLong cacheMisses = new AtomicLong(0);
    private static final AtomicLong restrictionClears = new AtomicLong(0);
    
    // Track restriction states for intelligent cache management
    private static final ConcurrentHashMap<Integer, Boolean> serviceRestrictionStates = new ConcurrentHashMap<>();

    private InfoCache() {
        // Private constructor for singleton pattern
    }

    /**
     * Enhanced type enumeration with better categorization for restrictions
     */
    public enum Type {
        STREAM(true),           // Content that may be restricted
        CHANNEL(true),          // Channels may have restricted content
        CHANNEL_TAB(true),      // Channel tabs may contain restricted content
        COMMENTS(false),        // Comments generally not affected by restrictions
        PLAYLIST(true),         // Playlists may contain restricted content
        KIOSK(true),           // Kiosks may show different content based on restrictions
        SEARCH(true);          // Search results affected by restrictions

        private final boolean affectedByRestrictions;

        Type(boolean affectedByRestrictions) {
            this.affectedByRestrictions = affectedByRestrictions;
        }

        public boolean isAffectedByRestrictions() {
            return affectedByRestrictions;
        }
    }

    public static InfoCache getInstance() {
        return INSTANCE;
    }

    @NonNull
    private static String keyOf(final int serviceId,
                                @NonNull final String url,
                                @NonNull final Type cacheType,
                                final boolean restrictedMode) {
        // Include restriction state in cache key for proper separation
        return serviceId + ":" + cacheType.ordinal() + ":" + (restrictedMode ? "1" : "0") + ":" + url.hashCode();
    }

    @NonNull
    private static String keyOf(final int serviceId,
                                @NonNull final String url,
                                @NonNull final Type cacheType) {
        // Default method for backward compatibility
        final boolean currentRestrictionState = getCurrentRestrictionState(serviceId);
        return keyOf(serviceId, url, cacheType, currentRestrictionState);
    }

    private static boolean getCurrentRestrictionState(final int serviceId) {
        // For YouTube service, check current restriction state
        if (serviceId == ServiceList.YouTube.getServiceId()) {
            return serviceRestrictionStates.getOrDefault(serviceId, false);
        }
        return false;
    }

    private static void removeStaleCache() {
        int removedCount = 0;
        for (final Map.Entry<String, CacheData> entry : InfoCache.LRU_CACHE.snapshot().entrySet()) {
            final CacheData data = entry.getValue();
            if (data != null && data.isExpired()) {
                InfoCache.LRU_CACHE.remove(entry.getKey());
                removedCount++;
            }
        }
        
        if (DEBUG && removedCount > 0) {
            Log.d(InfoCache.class.getSimpleName(), "Removed " + removedCount + " expired cache entries");
        }
    }

    @Nullable
    private static Info getInfo(@NonNull final String key) {
        final CacheData data = InfoCache.LRU_CACHE.get(key);
        if (data == null) {
            cacheMisses.incrementAndGet();
            return null;
        }

        if (data.isExpired()) {
            InfoCache.LRU_CACHE.remove(key);
            cacheMisses.incrementAndGet();
            return null;
        }

        cacheHits.incrementAndGet();
        return data.info;
    }

    @Nullable
    public Info getFromKey(final int serviceId,
                           @NonNull final String url,
                           @NonNull final Type cacheType) {
        if (DEBUG) {
            Log.d(TAG, "getFromKey() called with serviceId=" + serviceId + ", url=" + url + ", type=" + cacheType);
        }
        
        synchronized (LRU_CACHE) {
            return getInfo(keyOf(serviceId, url, cacheType));
        }
    }

    @Nullable
    public Info getFromKey(final int serviceId,
                           @NonNull final String url,
                           @NonNull final Type cacheType,
                           final boolean restrictedMode) {
        if (DEBUG) {
            Log.d(TAG, "getFromKey() called with restrictions: serviceId=" + serviceId + 
                  ", restrictedMode=" + restrictedMode + ", type=" + cacheType);
        }
        
        synchronized (LRU_CACHE) {
            return getInfo(keyOf(serviceId, url, cacheType, restrictedMode));
        }
    }

    public void putInfo(final int serviceId,
                        @NonNull final String url,
                        @NonNull final Info info,
                        @NonNull final Type cacheType) {
        putInfo(serviceId, url, info, cacheType, getCurrentRestrictionState(serviceId));
    }

    public void putInfo(final int serviceId,
                        @NonNull final String url,
                        @NonNull final Info info,
                        @NonNull final Type cacheType,
                        final boolean restrictedMode) {
        if (DEBUG) {
            Log.d(TAG, "putInfo() called with info=" + info.getName() + 
                  ", restrictedMode=" + restrictedMode + ", type=" + cacheType);
        }

        final long expirationMillis = ServiceHelper.getCacheExpirationMillis(info.getServiceId());
        synchronized (LRU_CACHE) {
            final CacheData data = new CacheData(info, expirationMillis, restrictedMode, cacheType);
            LRU_CACHE.put(keyOf(serviceId, url, cacheType, restrictedMode), data);
        }
    }

    public void removeInfo(final int serviceId,
                           @NonNull final String url,
                           @NonNull final Type cacheType) {
        if (DEBUG) {
            Log.d(TAG, "removeInfo() called with serviceId=" + serviceId + ", url=" + url);
        }
        
        synchronized (LRU_CACHE) {
            // Remove both restricted and unrestricted versions
            LRU_CACHE.remove(keyOf(serviceId, url, cacheType, true));
            LRU_CACHE.remove(keyOf(serviceId, url, cacheType, false));
        }
    }

    /**
     * Updates restriction state for a service and clears affected cache entries
     */
    public void updateServiceRestrictionState(final int serviceId, final boolean restrictedMode) {
        final Boolean previousState = serviceRestrictionStates.put(serviceId, restrictedMode);
        
        if (DEBUG) {
            Log.d(TAG, "updateServiceRestrictionState() serviceId=" + serviceId + 
                  ", restrictedMode=" + restrictedMode + ", previousState=" + previousState);
        }

        // Only clear cache if restriction state actually changed
        if (previousState == null || previousState != restrictedMode) {
            clearServiceRestrictedContent(serviceId);
            restrictionClears.incrementAndGet();
        }
    }

    /**
     * Clears cache entries for a specific service that are affected by restrictions
     */
    private void clearServiceRestrictedContent(final int serviceId) {
        synchronized (LRU_CACHE) {
            final Set<String> keysToRemove = new HashSet<>();
            
            for (final Map.Entry<String, CacheData> entry : LRU_CACHE.snapshot().entrySet()) {
                final String key = entry.getKey();
                final CacheData data = entry.getValue();
                
                if (data != null && data.serviceId == serviceId && data.cacheType.isAffectedByRestrictions()) {
                    keysToRemove.add(key);
                }
            }
            
            for (final String key : keysToRemove) {
                LRU_CACHE.remove(key);
            }
            
            if (DEBUG && !keysToRemove.isEmpty()) {
                Log.d(TAG, "Cleared " + keysToRemove.size() + 
                      " restriction-affected cache entries for serviceId=" + serviceId);
            }
        }
    }

    /**
     * Clears cache entries for a specific service
     */
    public void clearServiceCache(final int serviceId) {
        if (DEBUG) {
            Log.d(TAG, "clearServiceCache() called for serviceId=" + serviceId);
        }
        
        synchronized (LRU_CACHE) {
            final Set<String> keysToRemove = new HashSet<>();
            
            for (final Map.Entry<String, CacheData> entry : LRU_CACHE.snapshot().entrySet()) {
                final CacheData data = entry.getValue();
                if (data != null && data.serviceId == serviceId) {
                    keysToRemove.add(entry.getKey());
                }
            }
            
            for (final String key : keysToRemove) {
                LRU_CACHE.remove(key);
            }
            
            if (DEBUG) {
                Log.d(TAG, "Cleared " + keysToRemove.size() + " cache entries for serviceId=" + serviceId);
            }
        }
    }

    public void clearCache() {
        if (DEBUG) {
            Log.d(TAG, "clearCache() called");
        }
        synchronized (LRU_CACHE) {
            LRU_CACHE.evictAll();
        }
        
        // Reset metrics
        cacheHits.set(0);
        cacheMisses.set(0);
        restrictionClears.set(0);
    }

    public void trimCache() {
        if (DEBUG) {
            Log.d(TAG, "trimCache() called");
        }
        synchronized (LRU_CACHE) {
            removeStaleCache();
            LRU_CACHE.trimToSize(TRIM_CACHE_TO);
        }
    }

    public long getSize() {
        synchronized (LRU_CACHE) {
            return LRU_CACHE.size();
        }
    }

    /**
     * Enhanced cache statistics for monitoring and optimization
     */
    public CacheStats getCacheStats() {
        synchronized (LRU_CACHE) {
            final long hits = cacheHits.get();
            final long misses = cacheMisses.get();
            final long total = hits + misses;
            final double hitRate = total > 0 ? (double) hits / total : 0.0;
            
            return new CacheStats(hits, misses, hitRate, LRU_CACHE.size(), restrictionClears.get());
        }
    }

    /**
     * Validates cache integrity and removes corrupted entries
     */
    public void validateAndCleanCache() {
        synchronized (LRU_CACHE) {
            final Set<String> corruptedKeys = new HashSet<>();
            
            for (final Map.Entry<String, CacheData> entry : LRU_CACHE.snapshot().entrySet()) {
                final CacheData data = entry.getValue();
                if (data == null || data.info == null) {
                    corruptedKeys.add(entry.getKey());
                }
            }
            
            for (final String key : corruptedKeys) {
                LRU_CACHE.remove(key);
            }
            
            if (DEBUG && !corruptedKeys.isEmpty()) {
                Log.w(TAG, "Removed " + corruptedKeys.size() + " corrupted cache entries");
            }
        }
    }

    /**
     * Enhanced cache data structure with additional metadata
     */
    private static final class CacheData {
        private final long expireTimestamp;
        private final Info info;
        private final boolean restrictedMode;
        private final Type cacheType;
        private final int serviceId;
        private final long creationTime;

        private CacheData(@NonNull final Info info, 
                         final long timeoutMillis, 
                         final boolean restrictedMode,
                         @NonNull final Type cacheType) {
            this.creationTime = System.currentTimeMillis();
            this.expireTimestamp = this.creationTime + timeoutMillis;
            this.info = info;
            this.restrictedMode = restrictedMode;
            this.cacheType = cacheType;
            this.serviceId = info.getServiceId();
        }

        private boolean isExpired() {
            return System.currentTimeMillis() > expireTimestamp;
        }

        public long getAge() {
            return System.currentTimeMillis() - creationTime;
        }

        public boolean isRestrictedMode() {
            return restrictedMode;
        }

        public Type getCacheType() {
            return cacheType;
        }
    }

    /**
     * Cache statistics data structure for monitoring
     */
    public static final class CacheStats {
        public final long hits;
        public final long misses;
        public final double hitRate;
        public final long size;
        public final long restrictionClears;

        private CacheStats(long hits, long misses, double hitRate, long size, long restrictionClears) {
            this.hits = hits;
            this.misses = misses;
            this.hitRate = hitRate;
            this.size = size;
            this.restrictionClears = restrictionClears;
        }

        @Override
        public String toString() {
            return String.format("CacheStats{hits=%d, misses=%d, hitRate=%.2f%%, size=%d, restrictionClears=%d}", 
                               hits, misses, hitRate * 100, size, restrictionClears);
        }
    }
}