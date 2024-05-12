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

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.LocalDiskRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Map;
import java.util.NavigableSet;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;

@Singleton
class CachedGitRepositoryManager implements GitRepositoryManager {
  private final LocalDiskRepositoryManager repoManager;
  private final CachedRefRepository.Factory repoWrapperFactory;
  private final Map<Project.NameKey, Repository> repos;

  @Inject
  CachedGitRepositoryManager(
      LocalDiskRepositoryManager repoManager, CachedRefRepository.Factory repoWrapperFactory) {
    this.repoManager = repoManager;
    this.repoWrapperFactory = repoWrapperFactory;
    this.repos = new ConcurrentHashMap<>();
  }

  @Override
  public Repository openRepository(Project.NameKey name) throws IOException {
    return repos.computeIfAbsent(name, (n) ->
    {
	    try {
		    return repoWrapperFactory.create(n.get(), repoManager.openRepository(n));
	    } catch (RepositoryNotFoundException e) {
		    throw new RuntimeException(e);
	    }
    });
  }

  @Override
  public Repository createRepository(Project.NameKey name) throws IOException {
    return repos.computeIfAbsent(name, (n) -> {
	    try {
		    return repoWrapperFactory.create(name.get(), repoManager.createRepository(name));
	    } catch (IOException e) {
		    throw new RuntimeException(e);
	    }
    });
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
