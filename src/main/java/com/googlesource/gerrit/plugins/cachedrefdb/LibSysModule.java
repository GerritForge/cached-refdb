// Copyright (C) 2021 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.googlesource.gerrit.plugins.cachedrefdb;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eclipse.jgit.lib.Config;

public class LibSysModule extends LifecycleModule {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  public static final String CACHED_REFDB = "cached-refdb";
  public static final String IS_PER_REQUEST_CACHE_KEY = "isPerRequestCache";

  private final Config cfg;

  @Inject
  public LibSysModule(@GerritServerConfig Config cfg) {
    this.cfg = cfg;
  }

  @Override
  protected void configure() {
    if (!isPerRequestCache(cfg)) {
      install(RefByNameCacheImpl.module());
      listener().to(RefByNameCacheSetter.class);
    }
    logger.atInfo().log("Sys library loaded");
  }

  public static boolean isPerRequestCache(Config cfg) {
    return cfg.getBoolean(CACHED_REFDB, IS_PER_REQUEST_CACHE_KEY, false);
  }

  @Singleton
  private static class RefByNameCacheSetter implements LifecycleListener {
    private final RefByNameCacheImpl refByNameCache;
    private final DynamicItem<RefByNameCache> cacheRef;
    private RegistrationHandle handle;

    @Inject
    RefByNameCacheSetter(RefByNameCacheImpl refByNameCache, DynamicItem<RefByNameCache> cacheRef) {
      this.refByNameCache = refByNameCache;
      this.cacheRef = cacheRef;
    }

    @Override
    public void start() {
      handle = cacheRef.set(refByNameCache, "gerrit");
      logger.atInfo().log("Cache-backed RefDB loaded");
    }

    @Override
    public void stop() {
      if (handle != null) {
        handle.remove();
        logger.atInfo().log("Cache-backed RefDB unloaded");
      }
    }
  }
}
