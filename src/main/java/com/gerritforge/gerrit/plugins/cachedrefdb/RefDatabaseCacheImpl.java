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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.git.LocalDiskRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import org.eclipse.jgit.internal.storage.memory.TernarySearchTree;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

@Singleton
class RefDatabaseCacheImpl implements RefDatabaseCache {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String REF_NAMES_BY_PROJECT = "ref_names_by_project";
  private static final String REFS_BY_OBJECT_ID = "ref_names_by_object_id";

  static com.google.inject.Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        cache(REF_NAMES_BY_PROJECT, String.class, new TypeLiteral<TernarySearchTree<Ref>>() {})
            .loader(RefNamesByProjectLoader.class);
        cache(REFS_BY_OBJECT_ID, String.class, new TypeLiteral<Set<Ref>>() {});
      }
    };
  }

  private final LoadingCache<String, TernarySearchTree<Ref>> refNamesByProject;
  private final Cache<String, Set<Ref>> refsByObjectId;

  @Inject
  RefDatabaseCacheImpl(
      @Named(REF_NAMES_BY_PROJECT) LoadingCache<String, TernarySearchTree<Ref>> refNamesByProject,
      @Named(REFS_BY_OBJECT_ID) Cache<String, Set<Ref>> refsByObjectId) {
    this.refNamesByProject = refNamesByProject;
    this.refsByObjectId = refsByObjectId;
  }

  static class RefNamesByProjectLoader extends CacheLoader<String, TernarySearchTree<Ref>> {

    private final LocalDiskRepositoryManager repositoryManager;
    private final Cache<String, Set<Ref>> refsByObjectId;

    @Inject
    RefNamesByProjectLoader(LocalDiskRepositoryManager repositoryManager,
                            @Named(REFS_BY_OBJECT_ID) Cache<String, Set<Ref>> refsByObjectId) {
      this.repositoryManager = repositoryManager;
      this.refsByObjectId = refsByObjectId;
    }

    @Override
    public TernarySearchTree<Ref> load(String project) throws Exception {

      try (Repository repo = repositoryManager.openRepository(Project.nameKey(project)); ) {
        TernarySearchTree<Ref> tree = new TernarySearchTree<>();
        Map<String, ImmutableSet.Builder<Ref>> byObjectId = new HashMap<>();
        for (Ref ref : repo.getRefDatabase().getRefs()) {
          tree.insert(ref.getName(), ref);
          ObjectId oid = ref.getObjectId();
          if (oid != null) {
            byObjectId
                .computeIfAbsent(getUniqueName(project, oid.name()), k -> ImmutableSet.builder())
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
  public List<Ref> allByPrefixes(String projectName, String[] prefixes, RefDatabase delegate)
      throws ExecutionException {
    TernarySearchTree<Ref> projectRefs = refNamesByProject.get(projectName);
    AtomicReference<String> lastPrefix = new AtomicReference<>();
    ImmutableList.Builder<Ref> refs = ImmutableList.builder();
    Arrays.stream(prefixes)
        .sorted()
        .filter(prefix -> !isDuplicated(prefix, lastPrefix))
        .map(projectRefs::getValuesWithPrefix)
        .forEach(refs::addAll);
    return refs.build();
  }

  private static boolean isDuplicated(String prefix, AtomicReference<String> lastPrefix) {
    if (lastPrefix.get() != null && prefix.contains(lastPrefix.get())) {
      return true;
    } else {
      lastPrefix.set(prefix);
      return false;
    }
  }

  @Override
  public List<Ref> all(String projectName, RefDatabase delegate) throws ExecutionException {
    return refNamesByProject.get(projectName).getAllValues();
  }

  public void updateRefInPrefixesByProjectCache(String projectName, Ref ref)
      throws ExecutionException {
    TernarySearchTree<Ref> tree = refNamesByProject.get(projectName);
    tree.insert(ref.getName(), ref);
  }

  public void updateRefInPrefixesByProjectCache(
      String projectName, String refName, RefDatabase delegate)
      throws IOException, ExecutionException {
    updateRefInPrefixesByProjectCache(projectName, delegate.exactRef(refName));
  }

  public void deleteRefInPrefixesByProjectCache(String projectName, String refName)
      throws ExecutionException {
    refNamesByProject.get(projectName).delete(refName);
  }

  @Override
  public void put(String project, Ref ref) throws IOException {
    try {
      updateRefInPrefixesByProjectCache(project, ref);
    } catch (ExecutionException e) {
      throw new IOException(e);
    }
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
  public void updateRef(String identifier, String refName, RefDatabase delegate)
      throws IOException {
    try {
      updateRefInPrefixesByProjectCache(identifier, refName, delegate);
    } catch (ExecutionException e) {
      throw new IOException(e);
    }
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

  @Override
  public void evict(String identifier, String refName) throws ExecutionException {
    deleteRefInPrefixesByProjectCache(identifier, refName);
  }

  private static String getUniqueName(String identifier, String ref) {
    return String.format("%s$%s", identifier, ref);
  }
}
