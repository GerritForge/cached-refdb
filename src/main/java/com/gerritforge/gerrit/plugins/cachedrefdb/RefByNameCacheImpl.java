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

import com.google.common.cache.Cache;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import org.eclipse.jgit.internal.storage.memory.TernarySearchTree;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;

@Singleton
class RefByNameCacheImpl implements RefByNameCache {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String REF_BY_NAME = "ref_by_name";
  private static final String REF_NAMES_BY_PROJECT = "ref_names_by_project";

  private static final Object TREE_VALUE = new Object();

  static com.google.inject.Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        cache(REF_BY_NAME, String.class, new TypeLiteral<Optional<Ref>>() {});
        cache(
                REF_NAMES_BY_PROJECT,
                Project.NameKey.class,
                new TypeLiteral<TernarySearchTree<Optional<Ref>>>() {})
            .loader(RefNamesByProjectLoader.class);
      }
    };
  }

  private final Cache<String, Optional<Ref>> refByName;
  private final LoadingCache<Project.NameKey, TernarySearchTree<Optional<Ref>>> refNamesByProject;

  @Inject
  RefByNameCacheImpl(
      @Named(REF_BY_NAME) Cache<String, Optional<Ref>> refByName,
      @Named(REF_NAMES_BY_PROJECT)
          LoadingCache<Project.NameKey, TernarySearchTree<Optional<Ref>>> refNamesByProject) {
    this.refByName = refByName;
    this.refNamesByProject = refNamesByProject;
  }

  static class RefNamesByProjectLoader
      extends CacheLoader<Project.NameKey, TernarySearchTree<Optional<Ref>>> {

    private final LocalDiskRepositoryManager repositoryManager;
    private final Cache<String, Optional<Ref>> refByName;

    @Inject
    RefNamesByProjectLoader(
        LocalDiskRepositoryManager repositoryManager,
        @Named(REF_BY_NAME) Cache<String, Optional<Ref>> refByName) {
      this.repositoryManager = repositoryManager;
      this.refByName = refByName;
    }

    @Override
    public TernarySearchTree<Optional<Ref>> load(Project.NameKey key) throws Exception {

      try (Repository repo = repositoryManager.openRepository(key); ) {
        TernarySearchTree<Optional<Ref>> tree = new TernarySearchTree<>();
        for (Ref ref : repo.getRefDatabase().getRefs()) {
          Optional<Ref> refOpt = Optional.of(ref);
          tree.insert(ref.getName(), refOpt);
          String uniqueName = getUniqueName(key.get(), ref.getName());
          if (!isRefByNameCached(refByName, uniqueName)) {
            refByName.put(uniqueName, refOpt);
          }
        }
        return tree;
      }
    }
  }

  @Override
  public Ref get(String project, String ref, RefDatabase delegate) {
    String key = getUniqueName(project, ref);
    try {
      Optional<Ref> maybeRef =
          refByName.get(
              key, (Callable<Optional<Ref>>) () -> Optional.ofNullable(delegate.exactRef(ref)));
      return maybeRef.orElse(null);
    } catch (ExecutionException e) {
      logger.atWarning().withCause(e).log("Getting ref for [%s, %s] failed.", project, ref);
      return null;
    }
  }

  @Override
  public boolean containsKey(String project, String ref) {
    return isRefByNameCached(refByName, getUniqueName(project, ref));
  }

  @Override
  public List<Ref> allByPrefix(String projectName, String prefix, RefDatabase delegate)
      throws ExecutionException {
    TernarySearchTree<Optional<Ref>> treeRef = refNamesByProject.get(Project.nameKey(projectName));

    return getRefsFromName(projectName, treeRef.getKeysWithPrefix(prefix), delegate);
  }

  @Override
  public List<Ref> all(String projectName, RefDatabase delegate) throws ExecutionException {
    TernarySearchTree<Optional<Ref>> treeRef = refNamesByProject.get(Project.nameKey(projectName));

    return getRefsFromName(projectName, treeRef.getAll().keySet(), delegate);
  }

  private static boolean isRefByNameCached(Cache<String, Optional<Ref>> cache, String uniqueName) {
    return cache.getIfPresent(uniqueName) != null;
  }

  private List<Ref> getRefsFromName(
      String projectName, Iterable<String> refNames, RefDatabase delegate)
      throws ExecutionException {

    List<Ref> result = new ArrayList<>();
    for (String ref : refNames) {
      Optional<Ref> maybeRef =
          refByName.get(
              getUniqueName(projectName, ref),
              (Callable<Optional<Ref>>) () -> Optional.ofNullable(delegate.exactRef(ref)));
      maybeRef.ifPresent(result::add);
    }
    return result;
  }

  @Override
  public void updateRefNamesByProjectCache(String projectName, Ref ref) {
    Project.NameKey projectNameKey = Project.nameKey(projectName);
    try {
      TernarySearchTree<Optional<Ref>> tree = refNamesByProject.get(projectNameKey);
      tree.insert(ref.getName(), Optional.of(ref));
    } catch (ExecutionException e) {
      refNamesByProject.invalidate(projectNameKey);
      logger.atWarning().withCause(e).log(
          "Error when updating entry of %s. Invalidating cache for %s.",
          REF_NAMES_BY_PROJECT, projectNameKey);
    }
  }

  @Override
  public void updateRefNamesByProjectCache(
      String projectName, String refName, RefDatabase delegate) {
    updateRefNamesByProjectCache(projectName, get(projectName, refName, delegate));
  }

  @Override
  public void evictFromRefNamesByProjectCache(String projectName, String refName) {
    Project.NameKey projectNameKey = Project.nameKey(projectName);

    try {
      TernarySearchTree<Optional<Ref>> tree = refNamesByProject.get(projectNameKey);
      tree.delete(refName);
    } catch (ExecutionException e) {
      refNamesByProject.invalidate(projectNameKey);
      logger.atWarning().withCause(e).log(
          "Error when deleting entry from %s. Invalidating cache for %s.",
          REF_NAMES_BY_PROJECT, projectNameKey);
    }
  }

  @Override
  public void put(String project, Ref ref) {
    refByName.put(getUniqueName(project, ref.getName()), Optional.ofNullable(ref));
    updateRefNamesByProjectCache(project, ref);
  }

  @Override
  public void evictRefByNameCache(String identifier, String ref) {
    refByName.invalidate(getUniqueName(identifier, ref));
  }

  private static String getUniqueName(String identifier, String ref) {
    return String.format("%s$%s", identifier, ref);
  }
}
