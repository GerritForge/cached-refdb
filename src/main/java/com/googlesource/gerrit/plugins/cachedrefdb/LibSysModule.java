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
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.lib.Repository;

public class LibSysModule extends LifecycleModule {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Override
  protected void configure() {
    install(RefByNameCacheImpl.module());
    listener().to(RefByNameCacheSetter.class);
    logger.atInfo().log("Sys library loaded");
  }

  @Singleton
  private static class RefByNameCacheSetter implements LifecycleListener {
    private final RefByNameCacheImpl refByNameCache;
    private final DynamicItem<RefByNameCache> cacheRef;
    private final WorkQueue workQueue;
    private final ProjectCache projects;
    private final GitRepositoryManager gitRepoManager;
    private RegistrationHandle handle;

    @Inject
    RefByNameCacheSetter(
        RefByNameCacheImpl refByNameCache,
        DynamicItem<RefByNameCache> cacheRef,
        WorkQueue workQueue,
        ProjectCache projects,
        GitRepositoryManager gitRepoManager) {
      this.refByNameCache = refByNameCache;
      this.cacheRef = cacheRef;
      this.workQueue = workQueue;
      this.projects = projects;
      this.gitRepoManager = gitRepoManager;
    }

    @Override
    public void start() {
      handle = cacheRef.set(refByNameCache, "gerrit");
      logger.atInfo().log("Cache-backed RefDB on steroids loaded");

      // pre-load all refs to cache
      workQueue
          .getDefaultQueue()
          .execute(
              () -> {
                for (Project.NameKey project : projects.all()) {
                  try (Repository repo = gitRepoManager.openRepository(project)) {
                    repo.getRefDatabase().refresh();
                    logger.atInfo().log("Pre-loaded all refs DB for %s project", project);
                  } catch (IOException e) {
                    logger.atSevere().withCause(e).log(
                        "Pre-loading all refs DB failed for %s project", project);
                  }
                }
              });
    }

    @Override
    public void stop() {
      if (handle != null) {
        handle.remove();
        logger.atInfo().log("Cache-backed RefDB on steroids unloaded");
      }
    }
  }
}
