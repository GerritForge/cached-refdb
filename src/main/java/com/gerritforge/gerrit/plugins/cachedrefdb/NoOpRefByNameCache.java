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
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.git.LocalDiskRepositoryManager;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

class NoOpRefByNameCache implements RefByNameCache {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private final LocalDiskRepositoryManager repoManager;

  @Inject
  NoOpRefByNameCache(LocalDiskRepositoryManager repoManager) {
    this.repoManager = repoManager;
  }

  @Override
  public Ref get(String identifier, String ref) {
    try (Repository repo = repoManager.openRepository(Project.nameKey(identifier))) {
      return repo.getRefDatabase().exactRef(ref);
    } catch (IOException e) {
      logger.atWarning().withCause(e).log(
          "Failed to resolve ref %s for project %s", ref, identifier);
      return null;
    }
  }

  @Override
  public void put(String identifier, Ref ref) {}

  @Override
  public void evictRefByNameCache(String identifier, String ref) {
    // do nothing as there is no cache to be evicted
  }

  @Override
  public List<Ref> allByPrefix(String projectName, String prefix) throws ExecutionException {
    try (Repository repo = repoManager.openRepository(Project.nameKey(projectName))) {
      return repo.getRefDatabase().getRefsByPrefix(prefix);
    } catch (IOException e) {
      logger.atWarning().withCause(e).log(
          "Failed to read refs for project %s and prefix %s", projectName, prefix);
      return List.of();
    }
  }

  @Override
  public void updateRefsPrefixCache(String projectName, String refName) {
    // do nothing as there is no cache to update
  }

  @Override
  public void evictFromRefNamesByProjectCache(String identifier, String refName) {
    // do nothing as there is no cache to delete
  }
}
