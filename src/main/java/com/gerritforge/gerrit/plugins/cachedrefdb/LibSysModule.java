// Copyright (C) 2025 GerritForge, Inc.
//
// Licensed under the BSL 1.1 (the "License");
// you may not use this file except in compliance with the License.
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.gerritforge.gerrit.plugins.cachedrefdb;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.inject.Inject;
import com.google.inject.Singleton;

public class LibSysModule extends LifecycleModule {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Override
  protected void configure() {
    install(RefDatabaseCacheImpl.module());
    listener().to(RefByNameCacheSetter.class);
    logger.atInfo().log("Sys library loaded");
  }

  @Singleton
  private static class RefByNameCacheSetter implements LifecycleListener {
    private final RefDatabaseCacheImpl refByNameCache;
    private final DynamicItem<RefDatabaseCache> cacheRef;
    private RegistrationHandle handle;

    @Inject
    RefByNameCacheSetter(
        RefDatabaseCacheImpl refByNameCache, DynamicItem<RefDatabaseCache> cacheRef) {
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
