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

import static com.google.common.base.Preconditions.checkNotNull;

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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.eclipse.jgit.internal.storage.memory.TernarySearchTree;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

@Singleton
class RefByNameCacheImpl implements RefByNameCache {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String REF_BY_NAME = "ref_by_name";
  private static final String REFS_NAMES_BY_PROJECT = "refs_names_by_project";

  static com.google.inject.Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        cache(REF_BY_NAME, String.class, new TypeLiteral<Optional<Ref>>() {})
            .loader(RefByNameLoader.class);
        cache(
                REFS_NAMES_BY_PROJECT,
                RefsByProjectKey.class,
                new TypeLiteral<AtomicReference<TernarySearchTree<ObjectId>>>() {})
            .loader(RefsByProjectLoader.class);
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

  public static class RefsByProjectLoader
      extends CacheLoader<RefsByProjectKey, AtomicReference<TernarySearchTree<ObjectId>>> {

    private final LocalDiskRepositoryManager repositoryManager;
    private final LoadingCache<String, Optional<Ref>> refByName;

    @Inject
    public RefsByProjectLoader(
        LocalDiskRepositoryManager repositoryManager,
        @Named(REF_BY_NAME) LoadingCache<String, Optional<Ref>> refByName) {
      this.repositoryManager = repositoryManager;
      this.refByName = refByName;
    }

    public AtomicReference<TernarySearchTree<ObjectId>> load(RefsByProjectKey key)
        throws Exception {

      try (Repository repo = repositoryManager.openRepository(key.projectNameKey); ) {
        TernarySearchTree<ObjectId> tree = new TernarySearchTree<>();
        List<Ref> allRefs = repo.getRefDatabase().getRefs();
        for (Ref ref : allRefs) {
          tree.insert(ref.getName(), ref.getObjectId());
          refByName.put(
              getUniqueName(key.projectNameKey.get(), ref.getName()), Optional.ofNullable(ref));
        }
        return new AtomicReference<>(tree);
      } catch (IOException e) {
        logger.atWarning().log("Cannot open repository %s", key.projectNameKey);
      }
      return new AtomicReference<>(new TernarySearchTree<>());
    }
  }

  public record RefsByProjectKey(Project.NameKey projectNameKey) {}

  private final LoadingCache<RefsByProjectKey, AtomicReference<TernarySearchTree<ObjectId>>>
      refsNamesByProject;

  @Inject
  RefByNameCacheImpl(
      @Named(REF_BY_NAME) LoadingCache<String, Optional<Ref>> refByName,
      @Named(REFS_NAMES_BY_PROJECT)
          LoadingCache<RefsByProjectKey, AtomicReference<TernarySearchTree<ObjectId>>>
              refsNamesByProject) {
    this.refByName = refByName;
    this.refsNamesByProject = refsNamesByProject;
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
  public void evict(String identifier, String ref) {
    if (ref == null) {
      logger.atWarning().log("Cannot evict null ref for project %s", identifier);
      return;
    }
    refByName.invalidate(getUniqueName(identifier, ref));
    RefsByProjectKey refsByProjectKey = new RefsByProjectKey(Project.nameKey(identifier));
    AtomicReference<TernarySearchTree<ObjectId>> treeRef =
        refsNamesByProject.getIfPresent(refsByProjectKey);
    if (treeRef != null) {
      treeRef.getAndUpdate(
          oldTree -> {
            oldTree.delete(ref);
            return oldTree;
          });
    }
  }

  @Override
  public List<Ref> allByPrefix(String projectName, String prefix) {

    List<Ref> refsList = List.of();
    try {
      AtomicReference<TernarySearchTree<ObjectId>> treeRef =
          refsNamesByProject.get(new RefsByProjectKey(Project.nameKey(projectName)));

      refsList =
          Streams.stream(treeRef.get().getKeysWithPrefix(prefix))
              .map(ref -> getMaybeRef(projectName, ref))
              .filter(Optional::isPresent)
              .map(Optional::get)
              .collect(Collectors.toList());
    } catch (ExecutionException e) {
      logger.atWarning().withCause(e).log(
          "Failed to load all refs by prefix for project %s", projectName);
    }
    return refsList;
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
  public void updateRefsCache(String projectName, Ref ref) throws IOException {
    ObjectId oid = ref.getObjectId();
    String refName = ref.getName();
    checkNotNull(oid);

    try {
      updateRefsPrefixesCache(projectName, refName, oid);
    } catch (ExecutionException e) {
      throw new IOException(e);
    }
  }

  @Override
  public List<Ref> all() {
    return refByName.asMap().values().stream()
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(Collectors.toList());
  }

  private void updateRefsPrefixesCache(String projectName, String refName, ObjectId oid)
      throws ExecutionException {

    RefsByProjectKey refsByProjectKey = new RefsByProjectKey(Project.nameKey(projectName));
    AtomicReference<TernarySearchTree<ObjectId>> treeRef =
        refsNamesByProject.getIfPresent(refsByProjectKey);

    AtomicReference<TernarySearchTree<ObjectId>> targetTreeRef =
        treeRef != null ? treeRef : new AtomicReference<>(new TernarySearchTree<>());

    targetTreeRef.getAndUpdate(
        oldTree -> {
          oldTree.insert(refName, oid);
          return oldTree;
        });

    refsNamesByProject.put(refsByProjectKey, targetTreeRef);
  }

  @Override
  public void put(String project, Ref ref) {
    refByName.put(getUniqueName(project, ref.getName()), Optional.ofNullable(ref));
  }

  static String getUniqueName(String identifier, String ref) {
    return String.format("%s$%s", identifier, ref);
  }

  private static String[] parseKey(String key) {
    return key.split("\\$", 2);
  }
}
