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

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.LocalDiskRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.SortedSet;
import org.eclipse.jgit.lib.Repository;

@Singleton
class GerritCachedGitRepositoryManager implements GitRepositoryManager {
  private final LocalDiskRepositoryManager repoManager;
  private final CachedRefRepository.Factory repoWrapperFactory;

  @Inject
  GerritCachedGitRepositoryManager(
      LocalDiskRepositoryManager repoManager, CachedRefRepository.Factory repoWrapperFactory) {
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
  public SortedSet<Project.NameKey> list() {
    return repoManager.list();
  }

  @VisibleForTesting
  LocalDiskRepositoryManager getRepoManager() {
    return repoManager;
  }
}
