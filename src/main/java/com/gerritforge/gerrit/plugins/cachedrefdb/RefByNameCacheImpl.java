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

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.cache.Cache;
import com.google.common.collect.ImmutableSet;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import org.eclipse.jgit.internal.storage.memory.TernarySearchTree;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;

@Singleton
class RefByNameCacheImpl implements RefByNameCache {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String REF_BY_NAME = "ref_by_name";
  private static final String REF_NAMES_BY_PROJECT = "ref_names_by_project";
  private static final String REFS_BY_OBJECT_ID = "ref_names_by_object_id";

  static com.google.inject.Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        cache(REF_BY_NAME, String.class, new TypeLiteral<Optional<Ref>>() {});
        cache(
                REF_NAMES_BY_PROJECT,
                Project.NameKey.class,
                new TypeLiteral<TernarySearchTree<Ref>>() {})
            .loader(RefNamesByProjectLoader.class);
        cache(REFS_BY_OBJECT_ID, String.class, new TypeLiteral<Set<Ref>>() {});
      }
    };
  }

  private final Cache<String, Optional<Ref>> refByName;
  private final LoadingCache<Project.NameKey, TernarySearchTree<Ref>> refNamesByProject;
  private final Cache<String, Set<Ref>> refsByObjectId;

  @Inject
  RefByNameCacheImpl(
      @Named(REF_BY_NAME) Cache<String, Optional<Ref>> refByName,
      @Named(REF_NAMES_BY_PROJECT)
          LoadingCache<Project.NameKey, TernarySearchTree<Ref>> refNamesByProject,
      @Named(REFS_BY_OBJECT_ID) Cache<String, Set<Ref>> refsByObjectId) {
    this.refByName = refByName;
    this.refNamesByProject = refNamesByProject;
    this.refsByObjectId = refsByObjectId;
  }

  static class RefNamesByProjectLoader
      extends CacheLoader<Project.NameKey, TernarySearchTree<Ref>> {

    private final LocalDiskRepositoryManager repositoryManager;
    private final Cache<String, Optional<Ref>> refByName;
    private final Cache<String, Set<Ref>> refsByObjectId;

    @Inject
    RefNamesByProjectLoader(
        LocalDiskRepositoryManager repositoryManager,
        @Named(REF_BY_NAME) Cache<String, Optional<Ref>> refByName,
        @Named(REFS_BY_OBJECT_ID) Cache<String, Set<Ref>> refsByObjectId) {
      this.repositoryManager = repositoryManager;
      this.refByName = refByName;
      this.refsByObjectId = refsByObjectId;
    }

    @Override
    public TernarySearchTree<Ref> load(Project.NameKey key) throws Exception {
      try (Repository repo = repositoryManager.openRepository(key)) {
        TernarySearchTree<Ref> tree = new TernarySearchTree<>();
        Map<String, ImmutableSet.Builder<Ref>> byObjectId = new HashMap<>();
        for (Ref ref : repo.getRefDatabase().getRefs()) {
          tree.insert(ref.getName(), ref);
          String uniqueName = getUniqueName(key.get(), ref.getName());
          if (!isRefByNameCached(refByName, uniqueName)) {
            refByName.put(uniqueName, Optional.of(ref));
          }
          ObjectId oid = ref.getObjectId();
          if (oid != null) {
            byObjectId
                .computeIfAbsent(getUniqueName(key.get(), oid.name()), k -> ImmutableSet.builder())
                .add(ref);
          }
        }
        byObjectId.forEach(
            (oidKey, builder) -> {
              if (refsByObjectId.getIfPresent(oidKey) == null) {
                refsByObjectId.put(oidKey, builder.build());
              }
            });
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
    return refNamesByProject.get(Project.nameKey(projectName)).getValuesWithPrefix(prefix);
  }

  @Override
  public List<Ref> all(String projectName, RefDatabase delegate) throws ExecutionException {
    return refNamesByProject.get(Project.nameKey(projectName)).getAllValues();
  }

  private static boolean isRefByNameCached(Cache<String, Optional<Ref>> cache, String uniqueName) {
    return cache.getIfPresent(uniqueName) != null;
  }

  @Override
  public void updateRefInPrefixesByProjectCache(String projectName, Ref ref) {
    Project.NameKey projectNameKey = Project.nameKey(projectName);
    try {
      TernarySearchTree<Ref> tree = refNamesByProject.get(projectNameKey);
      tree.insert(ref.getName(), ref);
    } catch (ExecutionException e) {
      refNamesByProject.invalidate(projectNameKey);
      logger.atWarning().withCause(e).log(
          "Error when updating entry of %s. Invalidating cache for %s.",
          REF_NAMES_BY_PROJECT, projectNameKey);
    }
  }

  @Override
  public void updateRefInPrefixesByProjectCache(
      String projectName, String refName, RefDatabase delegate) {
    updateRefInPrefixesByProjectCache(projectName, get(projectName, refName, delegate));
  }

  @Override
  public void deleteRefInPrefixesByProjectCache(String projectName, String refName) {
    Project.NameKey projectNameKey = Project.nameKey(projectName);

    try {
      refNamesByProject.get(projectNameKey).delete(refName);
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
    updateRefInPrefixesByProjectCache(project, ref);
  }

  @Override
  public void evictRefByNameCache(String identifier, String ref) {
    refByName.invalidate(getUniqueName(identifier, ref));
  }

  @Override
  public Set<Ref> getRefsByObjectId(String project, ObjectId id, RefDatabase delegate) {
    String key = getUniqueName(project, id.name());
    try {
      return refsByObjectId.get(
          key, () -> delegate.getTipsWithSha1(id).stream().collect(toImmutableSet()));
    } catch (ExecutionException e) {
      logger.atWarning().withCause(e).log(
          "Getting refs for [%s, %s] failed.", project, id.name());
      return null;
    }
  }

  @Override
  public boolean hasFastTipsWithSha1(RefDatabase delegate) throws IOException {
    return true;
  }

  @Override
  public void evictObjectIdCache(String identifier, ObjectId id) {
    if (id != null && !id.equals(ObjectId.zeroId())) {
      refsByObjectId.invalidate(getUniqueName(identifier, id.name()));
    }
  }

  private static String getUniqueName(String identifier, String ref) {
    return String.format("%s$%s", identifier, ref);
  }
}
