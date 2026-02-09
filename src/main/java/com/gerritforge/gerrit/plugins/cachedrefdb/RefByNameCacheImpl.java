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
import java.util.concurrent.atomic.AtomicReference;
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
                RefsByProjectKey.class,
                new TypeLiteral<AtomicReference<TernarySearchTree<Object>>>() {})
            .loader(RefNamesByProjectLoader.class);
      }
    };
  }

  private final Cache<String, Optional<Ref>> refByName;
  private final LoadingCache<RefsByProjectKey, AtomicReference<TernarySearchTree<Object>>>
      refNamesByProject;

  @Inject
  RefByNameCacheImpl(
      @Named(REF_BY_NAME) Cache<String, Optional<Ref>> refByName,
      @Named(REF_NAMES_BY_PROJECT)
          LoadingCache<RefsByProjectKey, AtomicReference<TernarySearchTree<Object>>>
              refNamesByProject) {
    this.refByName = refByName;
    this.refNamesByProject = refNamesByProject;
  }

  static class RefNamesByProjectLoader
      extends CacheLoader<RefsByProjectKey, AtomicReference<TernarySearchTree<Object>>> {

    private final LocalDiskRepositoryManager repositoryManager;
    private final Cache<String, Optional<Ref>> refByName;

    @Inject
    RefNamesByProjectLoader(
        LocalDiskRepositoryManager repositoryManager,
        @Named(REF_BY_NAME) Cache<String, Optional<Ref>> refByName) {
      this.repositoryManager = repositoryManager;
      this.refByName = refByName;
    }

    public AtomicReference<TernarySearchTree<Object>> load(RefsByProjectKey key) throws Exception {

      try (Repository repo = repositoryManager.openRepository(key.projectNameKey); ) {
        TernarySearchTree<Object> tree = new TernarySearchTree<>();
        List<Ref> allRefs = repo.getRefDatabase().getRefs();
        for (Ref ref : allRefs) {
          tree.insert(ref.getName(), TREE_VALUE);
          String projectName = key.projectNameKey.get();
          if (refByName.getIfPresent(getUniqueName(projectName, ref.getName())) != null) {
            refByName.put(getUniqueName(projectName, ref.getName()), Optional.ofNullable(ref));
          }
        }
        return new AtomicReference<>(tree);
      }
    }
  }

  record RefsByProjectKey(Project.NameKey projectNameKey) {}

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
    return refByName.getIfPresent(getUniqueName(project, ref)) != null;
  }

  @Override
  public List<Ref> allByPrefix(String projectName, String prefix, RefDatabase delegate)
      throws ExecutionException {
    AtomicReference<TernarySearchTree<Object>> treeRef =
        refNamesByProject.get(new RefsByProjectKey(Project.nameKey(projectName)));

    return getRefsFromName(projectName, treeRef.get().getKeysWithPrefix(prefix), delegate);
  }

  @Override
  public List<Ref> all(String projectName, RefDatabase delegate) throws ExecutionException {
    AtomicReference<TernarySearchTree<Object>> treeRef =
        refNamesByProject.get(new RefsByProjectKey(Project.nameKey(projectName)));

    return getRefsFromName(projectName, treeRef.get().getAll().keySet(), delegate);
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
  public void updateRefNamesByProjectCache(String projectName, String refName) {
    RefsByProjectKey refsByProjectKey = new RefsByProjectKey(Project.nameKey(projectName));
    try {
      AtomicReference<TernarySearchTree<Object>> treeRef = refNamesByProject.get(refsByProjectKey);

      treeRef.getAndUpdate(
          oldTree -> {
            oldTree.insert(refName, TREE_VALUE);
            return oldTree;
          });
    } catch (ExecutionException e) {
      refNamesByProject.invalidate(refsByProjectKey);
      logger.atWarning().withCause(e).log(
          "Error when updating entry of %s. Invalidating cache for %s.",
          REF_NAMES_BY_PROJECT, refsByProjectKey);
    }
  }

  @Override
  public void evictFromRefNamesByProjectCache(String identifier, String refName) {
    RefsByProjectKey refsByProjectKey = new RefsByProjectKey(Project.nameKey(identifier));

    try {
      AtomicReference<TernarySearchTree<Object>> treeRef = refNamesByProject.get(refsByProjectKey);
      treeRef.getAndUpdate(
          oldTree -> {
            oldTree.delete(refName);
            return oldTree;
          });
    } catch (ExecutionException e) {
      refNamesByProject.invalidate(refsByProjectKey);
      logger.atWarning().withCause(e).log(
          "Error when deleting entry from %s. Invalidating cache for %s.",
          REF_NAMES_BY_PROJECT, refsByProjectKey);
    }
  }

  @Override
  public void put(String project, Ref ref) {
    refByName.put(getUniqueName(project, ref.getName()), Optional.ofNullable(ref));
    updateRefNamesByProjectCache(project, ref.getName());
  }

  @Override
  public void evictRefByNameCache(String identifier, String ref) {
    refByName.invalidate(getUniqueName(identifier, ref));
  }

  private static String getUniqueName(String identifier, String ref) {
    return String.format("%s$%s", identifier, ref);
  }
}
