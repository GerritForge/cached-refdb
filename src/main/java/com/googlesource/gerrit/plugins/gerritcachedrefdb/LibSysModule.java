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

package com.googlesource.gerrit.plugins.gerritcachedrefdb;

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
    install(RefByNameGerritCache.module());
    listener().to(RefByNameGerritCacheSetter.class);
    logger.atInfo().log("Sys library loaded");
  }

  @Singleton
  private static class RefByNameGerritCacheSetter implements LifecycleListener {
    private final RefByNameGerritCache refByNameGerritCache;
    private final DynamicItem<RefByNameCache> cacheRef;
    private RegistrationHandle handle;

    @Inject
    RefByNameGerritCacheSetter(
        RefByNameGerritCache refByNameGerritCache, DynamicItem<RefByNameCache> cacheRef) {
      this.refByNameGerritCache = refByNameGerritCache;
      this.cacheRef = cacheRef;
    }

    @Override
    public void start() {
      handle = cacheRef.set(refByNameGerritCache, "gerrit");
      logger.atInfo().log("Gerrit-cache-backed RefDB loaded");
    }

    @Override
    public void stop() {
      if (handle != null) {
        handle.remove();
        logger.atInfo().log("Gerrit-cache-backed RefDB unloaded");
      }
    }
  }
}
