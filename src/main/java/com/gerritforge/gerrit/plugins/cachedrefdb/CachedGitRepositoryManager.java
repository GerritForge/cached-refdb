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

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.LocalDiskRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.NavigableSet;
import org.eclipse.jgit.lib.Repository;

@Singleton
class CachedGitRepositoryManager implements GitRepositoryManager {
  private final LocalDiskRepositoryManager repoManager;
  private final CachedRefRepository.CachingFactory repoWrapperFactory;

  @Inject
  CachedGitRepositoryManager(
      LocalDiskRepositoryManager repoManager,
      CachedRefRepository.CachingFactory repoWrapperFactory) {
    this.repoManager = repoManager;
    this.repoWrapperFactory = repoWrapperFactory;
  }

  @Override
  public Repository openRepository(Project.NameKey name) throws IOException {
    return repoWrapperFactory.create(name.get(), repoManager.openRepository(name));
  }

  @Override
  public Repository createRepository(Project.NameKey name) throws IOException {
    return repoWrapperFactory.create(name.get(), repoManager.createRepository(name));
  }

  @Override
  public NavigableSet<Project.NameKey> list() {
    return repoManager.list();
  }

  @Override
  public Boolean canPerformGC() {
    return repoManager.canPerformGC();
  }

  @VisibleForTesting
  LocalDiskRepositoryManager getRepoManager() {
    return repoManager;
  }

  @Override
  public Status getRepositoryStatus(Project.NameKey name) {
    return repoManager.getRepositoryStatus(name);
  }
}
