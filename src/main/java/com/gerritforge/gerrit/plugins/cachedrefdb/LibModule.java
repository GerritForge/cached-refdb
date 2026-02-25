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

import static com.google.inject.Scopes.SINGLETON;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.LocalDiskRepositoryManager;
import com.google.inject.name.Names;

public class LibModule extends LifecycleModule {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Override
  protected void configure() {
    bind(GitRepositoryManager.class)
        .annotatedWith(Names.named(LocalDiskRepositoryManager.class.getSimpleName()))
        .to(CachedGitRepositoryManager.class);

    DynamicItem.itemOf(binder(), RefDatabaseCache.class);
    DynamicItem.bind(binder(), RefDatabaseCache.class).to(PassThroughRefDatabase.class).in(SINGLETON);

    factory(RefUpdateWithCacheUpdate.Factory.class);
    factory(RefRenameWithCacheUpdate.Factory.class);
    factory(BatchRefUpdateWithCacheUpdate.Factory.class);
    factory(CachedRefDatabase.Factory.class);
    factory(CachedRefRepository.Factory.class);

    logger.atInfo().log("Ref repository and db library loaded");
  }
}
