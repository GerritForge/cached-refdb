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
import com.google.common.collect.Streams;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.git.LocalDiskRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.eclipse.jgit.internal.storage.memory.TernarySearchTree;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

@Singleton
class RefByNameCacheImpl implements RefByNameCache {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String REF_BY_NAME = "ref_by_name";
  private static final String REFS_NAMES_BY_PROJECT = "refs_names_by_project";

  private static final Object TREE_VALUE = new Object();

  static com.google.inject.Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        cache(REF_BY_NAME, String.class, new TypeLiteral<Optional<Ref>>() {})
            .loader(RefByNameLoader.class);
        cache(
                REFS_NAMES_BY_PROJECT,
                RefsByProjectKey.class,
                new TypeLiteral<AtomicReference<TernarySearchTree<Object>>>() {})
            .loader(RefsPrefixLoader.class);
      }
    };
  }

  private final LoadingCache<String, Optional<Ref>> refByName;

  public static class RefByNameLoader extends CacheLoader<String, Optional<Ref>> {

    private final LocalDiskRepositoryManager repositoryManager;

    @Inject
    public RefByNameLoader(LocalDiskRepositoryManager repositoryManager) {
      this.repositoryManager = repositoryManager;
    }

    @Override
    public Optional<Ref> load(String key) throws Exception {
      String[] projectNameAndRef = parseKey(key);
      if (projectNameAndRef.length != 2) {
        throw new IllegalArgumentException("Invalid cache key: " + key);
      }
      try (Repository repo =
          repositoryManager.openRepository(Project.nameKey(projectNameAndRef[0]))) {
        Ref ref = repo.getRefDatabase().exactRef(projectNameAndRef[1]);
        return Optional.ofNullable(ref);
      }
    }
  }

  public static class RefsPrefixLoader
      extends CacheLoader<RefsByProjectKey, AtomicReference<TernarySearchTree<Object>>> {

    private final LocalDiskRepositoryManager repositoryManager;
    private final LoadingCache<String, Optional<Ref>> refByName;

    @Inject
    public RefsPrefixLoader(
        LocalDiskRepositoryManager repositoryManager,
        @Named(REF_BY_NAME) LoadingCache<String, Optional<Ref>> refByName) {
      this.repositoryManager = repositoryManager;
      this.refByName = refByName;
    }

    public AtomicReference<TernarySearchTree<Object>> load(RefsByProjectKey key) throws Exception {

      try (Repository repo = repositoryManager.openRepository(key.projectNameKey); ) {
        TernarySearchTree<Object> tree = new TernarySearchTree<>();
        List<Ref> allRefs = repo.getRefDatabase().getRefs();
        for (Ref ref : allRefs) {
          tree.insert(ref.getName(), TREE_VALUE);
          refByName.put(
              getUniqueName(key.projectNameKey.get(), ref.getName()), Optional.ofNullable(ref));
        }
        return new AtomicReference<>(tree);
      }
    }
  }

  public record RefsByProjectKey(Project.NameKey projectNameKey) {}

  private final LoadingCache<RefsByProjectKey, AtomicReference<TernarySearchTree<Object>>>
      refsPrefixByProject;

  @Inject
  RefByNameCacheImpl(
      @Named(REF_BY_NAME) LoadingCache<String, Optional<Ref>> refByName,
      @Named(REFS_NAMES_BY_PROJECT)
          LoadingCache<RefsByProjectKey, AtomicReference<TernarySearchTree<Object>>>
              refsPrefixByProject) {
    this.refByName = refByName;
    this.refsPrefixByProject = refsPrefixByProject;
  }

  @Override
  public Ref get(String project, String ref) {
    try {
      Optional<Ref> maybeRef = refByName.get(getUniqueName(project, ref));
      return maybeRef.orElse(null);
    } catch (ExecutionException e) {
      logger.atWarning().withCause(e).log("Getting ref for [%s, %s] failed.", project, ref);
      return null;
    }
  }

  @Override
  public void evictRefByNameCache(String identifier, String ref) {
    refByName.invalidate(getUniqueName(identifier, ref));
  }

  @Override
  public List<Ref> allByPrefix(String projectName, String prefix) throws ExecutionException {
    AtomicReference<TernarySearchTree<Object>> treeRef =
        refsPrefixByProject.get(new RefsByProjectKey(Project.nameKey(projectName)));

    return Streams.stream(treeRef.get().getKeysWithPrefix(prefix))
        .map(ref -> getMaybeRef(projectName, ref))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(Collectors.toList());
  }

  private Optional<Ref> getMaybeRef(String projectName, String refString) {
    try {
      return refByName.get(getUniqueName(projectName, refString));
    } catch (ExecutionException e) {
      logger.atWarning().withCause(e).log(
          "Failed to lookup ref %s in project %s", refString, projectName);
    }
    return Optional.empty();
  }

  @Override
  public void updateRefsPrefixCache(String projectName, String refName) {
    RefsByProjectKey refsByProjectKey = new RefsByProjectKey(Project.nameKey(projectName));
    try {
      AtomicReference<TernarySearchTree<Object>> treeRef =
          refsPrefixByProject.get(refsByProjectKey);

      treeRef.getAndUpdate(
          oldTree -> {
            oldTree.insert(refName, TREE_VALUE);
            return oldTree;
          });
    } catch (ExecutionException e) {
      logger.atWarning().log(
          "Error when updating entry of %s. Cannot load key %s of cache ",
          REFS_NAMES_BY_PROJECT, refsByProjectKey);
    }
  }

  @Override
  public void deleteFromRefsPrefixCache(String identifier, String refName) {
    RefsByProjectKey refsByProjectKey = new RefsByProjectKey(Project.nameKey(identifier));
    try {
      AtomicReference<TernarySearchTree<Object>> treeRef =
          refsPrefixByProject.get(refsByProjectKey);
      treeRef.getAndUpdate(
          oldTree -> {
            oldTree.delete(refName);
            return oldTree;
          });
    } catch (ExecutionException e) {
      logger.atWarning().log(
          "Error when deleting entry from %s. Cannot load key %s of cache ",
          REFS_NAMES_BY_PROJECT, refsByProjectKey);
    }
  }

  @Override
  public void put(String project, Ref ref) throws IOException {
    refByName.put(getUniqueName(project, ref.getName()), Optional.of(ref));
    updateRefsPrefixCache(project, ref.getName());
  }

  private static String getUniqueName(String identifier, String ref) {
    return String.format("%s$%s", identifier, ref);
  }

  private static String[] parseKey(String key) {
    return key.split("\\$", 2);
  }
}
