package com.bfp.alert;

import android.content.Context;

import androidx.annotation.NonNull;

import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory;
import com.bumptech.glide.load.engine.cache.LruResourceCache;
import com.bumptech.glide.module.AppGlideModule;

@GlideModule
public class GlideConfig extends AppGlideModule {

    @Override
    public void applyOptions(@NonNull Context context,
                             @NonNull GlideBuilder builder) {
        // 512MB disk cache for photos
        builder.setDiskCache(
                new InternalCacheDiskCacheFactory(
                        context, 512 * 1024 * 1024));
        // 50MB memory cache
        builder.setMemoryCache(
                new LruResourceCache(50 * 1024 * 1024));
    }
}