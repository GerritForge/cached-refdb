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

import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.git.LocalDiskRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.Lock;
import org.eclipse.jgit.internal.storage.memory.TernarySearchTree;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;

@Singleton
class RefByNameCacheImpl implements RefByNameCache {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String REF_NAMES_BY_PROJECT = "ref_names_by_project";

  static com.google.inject.Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        cache(REF_NAMES_BY_PROJECT, String.class, new TypeLiteral<TernarySearchTree<Ref>>() {})
            .loader(RefNamesByProjectLoader.class);
      }
    };
  }

  private final LoadingCache<String, TernarySearchTree<Ref>> refNamesByProject;

  @Inject
  RefByNameCacheImpl(
      @Named(REF_NAMES_BY_PROJECT) LoadingCache<String, TernarySearchTree<Ref>> refNamesByProject) {
    this.refNamesByProject = refNamesByProject;
  }

  static class RefNamesByProjectLoader extends CacheLoader<String, TernarySearchTree<Ref>> {

    private final LocalDiskRepositoryManager repositoryManager;

    @Inject
    RefNamesByProjectLoader(LocalDiskRepositoryManager repositoryManager) {
      this.repositoryManager = repositoryManager;
    }

    @Override
    public TernarySearchTree<Ref> load(String project) throws Exception {

      try (Repository repo = repositoryManager.openRepository(Project.nameKey(project)); ) {
        TernarySearchTree<Ref> tree = new TernarySearchTree<>();
        for (Ref ref : repo.getRefDatabase().getRefs()) {
          tree.insert(ref.getName(), ref);
        }
        return tree;
      }
    }
  }

  @Override
  public Ref get(String project, String ref, RefDatabase delegate) {
    try {
      return refNamesByProject.get(project).get(ref);
    } catch (ExecutionException e) {
      logger.atSevere().withCause(e).log("Getting ref for [%s, %s] failed.", project, ref);
      throw new IllegalStateException(e);
    }
  }

  @Override
  public boolean containsKey(String project, String ref) {
    try {
      return refNamesByProject.get(project).contains(ref);
    } catch (ExecutionException e) {
      logger.atSevere().withCause(e).log(
          "Checking ref existence for [%s, %s] failed.", project, ref);
      throw new IllegalStateException(e);
    }
  }

  @Override
  public List<Ref> allByPrefix(String projectName, String prefix, RefDatabase delegate)
      throws ExecutionException {
    return refNamesByProject.get(projectName).getValuesWithPrefix(prefix);
  }

  @Override
  public List<Ref> all(String projectName, RefDatabase delegate) throws ExecutionException {
    return refNamesByProject.get(projectName).getAllValues();
  }

  public void updateRefInPrefixesByProjectCache(String projectName, Ref ref) {
    try {
      TernarySearchTree<Ref> tree = refNamesByProject.get(projectName);
      tree.insert(ref.getName(), ref);
    } catch (ExecutionException e) {
      refNamesByProject.invalidate(projectName);
      logger.atSevere().withCause(e).log(
          "Error when updating entry of %s. Invalidating cache for %s.",
          REF_NAMES_BY_PROJECT, projectName);
      throw new IllegalStateException(e);
    }
  }

  public void updateRefInPrefixesByProjectCache(
      String projectName, String refName, RefDatabase delegate) {
    try {
      updateRefInPrefixesByProjectCache(projectName, delegate.exactRef(refName));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public void deleteRefInPrefixesByProjectCache(String projectName, String refName) {

    try {
      refNamesByProject.get(projectName).delete(refName);
    } catch (ExecutionException e) {
      refNamesByProject.invalidate(projectName);
      logger.atSevere().withCause(e).log(
          "Error when deleting entry from %s. Invalidating cache for %s.",
          REF_NAMES_BY_PROJECT, projectName);
      throw new IllegalStateException(e);
    }
  }

  @Override
  public void put(String project, Ref ref) {
    updateRefInPrefixesByProjectCache(project, ref);
  }

  @Override
  public void renameRef(String project, Ref srcRef, Ref destRef) throws ExecutionException {
    TernarySearchTree<Ref> tree = refNamesByProject.get(project);
    Lock lock = tree.getLock().writeLock();
    lock.lock();
    try {
      tree.delete(srcRef.getName());
      tree.insert(destRef.getName(), destRef);
    } finally {
      lock.unlock();
    }
  }

  @Override
  public void updateRef(String identifier, String refName, RefDatabase delegate) {
    updateRefInPrefixesByProjectCache(identifier, refName, delegate);
  }

  @Override
  public void evict(String identifier, String refName) {
    deleteRefInPrefixesByProjectCache(identifier, refName);
  }

  private static String getUniqueName(String identifier, String ref) {
    return String.format("%s$%s", identifier, ref);
  }
}
