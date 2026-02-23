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
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.server.cache.CacheModule;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import org.eclipse.jgit.internal.storage.memory.TernarySearchTree;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;

@Singleton
class RefDatabaseCacheImpl implements RefDatabaseCache {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String REF_NAMES_BY_PROJECT = "ref_names_by_project";
  private static final String REFS_BY_OBJECT_ID = "ref_names_by_object_id";

  static com.google.inject.Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        cache(REF_NAMES_BY_PROJECT, String.class, new TypeLiteral<TernarySearchTree<Ref>>() {});
        cache(REFS_BY_OBJECT_ID, ObjectIdAndProject.class, new TypeLiteral<Set<Ref>>() {});
      }
    };
  }

  private final Cache<String, TernarySearchTree<Ref>> refNamesByProject;
  private final Cache<ObjectIdAndProject, Set<Ref>> refsByObjectId;

  @Inject
  RefDatabaseCacheImpl(
      @Named(REF_NAMES_BY_PROJECT) Cache<String, TernarySearchTree<Ref>> refNamesByProject,
      @Named(REFS_BY_OBJECT_ID) Cache<ObjectIdAndProject, Set<Ref>> refsByObjectId) {
    this.refNamesByProject = refNamesByProject;
    this.refsByObjectId = refsByObjectId;
  }

  static class RefNamesByProjectLoader {

    static TernarySearchTree<Ref> load(RefDatabase refDatabaseDelegate) throws IOException {

      TernarySearchTree<Ref> tree = new TernarySearchTree<>();
      for (Ref ref : refDatabaseDelegate.getRefs()) {
        tree.insert(ref.getName(), ref);
      }
      return tree;
    }
  }

  @Override
  public Ref get(String project, String ref, RefDatabase delegate) {
    try {
      return refNamesByProject.get(project, getLoader(delegate)).get(ref);
    } catch (ExecutionException e) {
      logger.atSevere().withCause(e).log("Getting ref for [%s, %s] failed.", project, ref);
      throw new IllegalStateException(e);
    }
  }

  @Override
  public boolean containsKey(String project, String ref, RefDatabase delegate) {
    try {
      return refNamesByProject.get(project, getLoader(delegate)).contains(ref);
    } catch (ExecutionException e) {
      logger.atSevere().withCause(e).log(
          "Checking ref existence for [%s, %s] failed.", project, ref);
      throw new IllegalStateException(e);
    }
  }

  @Override
  public List<Ref> allByPrefixes(String projectName, String[] prefixes, RefDatabase delegate)
      throws ExecutionException {
    TernarySearchTree<Ref> projectRefs = refNamesByProject.get(projectName, getLoader(delegate));
    AtomicReference<String> lastPrefix = new AtomicReference<>();
    ImmutableList.Builder<Ref> refs = ImmutableList.builder();
    Arrays.stream(prefixes)
        .sorted()
        .filter(prefix -> !isDuplicated(prefix, lastPrefix))
        .map(projectRefs::getValuesWithPrefix)
        .forEach(refs::addAll);
    return refs.build();
  }

  private static Callable<TernarySearchTree<Ref>> getLoader(RefDatabase delegate) {
    return () -> RefNamesByProjectLoader.load(delegate);
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
    return refNamesByProject.get(projectName, getLoader(delegate)).getAllValues();
  }

  public void updateRefInPrefixesByProjectCache(String projectName, Ref ref, RefDatabase delegate)
      throws ExecutionException {
    TernarySearchTree<Ref> tree = refNamesByProject.get(projectName, getLoader(delegate));
    tree.insert(ref.getName(), ref);
  }

  public void updateRefInPrefixesByProjectCache(
      String projectName, String refName, RefDatabase delegate)
      throws IOException, ExecutionException {
    updateRefInPrefixesByProjectCache(projectName, delegate.exactRef(refName), delegate);
  }

  public void deleteRefInPrefixesByProjectCache(
      String projectName, String refName, RefDatabase delegate) throws ExecutionException {
    refNamesByProject.get(projectName, getLoader(delegate)).delete(refName);
  }

  @Override
  public void put(String project, Ref ref, RefDatabase delegate) throws IOException {
    try {
      updateRefInPrefixesByProjectCache(project, ref, delegate);
    } catch (ExecutionException e) {
      throw new IOException(e);
    }
  }

  @Override
  public void renameRef(String project, Ref srcRef, Ref destRef, RefDatabase delegate)
      throws ExecutionException {
    TernarySearchTree<Ref> tree = refNamesByProject.get(project, getLoader(delegate));
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
  public Set<Ref> getRefsByObjectId(CachedRefRepository repo, ObjectId id, RefDatabase delegate)
      throws ExecutionException {
    String projectName = repo.getProjectName();
    return refsByObjectId.get(
        ObjectIdAndProject.create(id, projectName),
        () -> getRefsFromProjectsCache(id, projectName, delegate));
  }

  @Nonnull
  private Set<Ref> getRefsFromProjectsCache(ObjectId id, String projectName, RefDatabase delegate)
      throws ExecutionException {
    return refNamesByProject.get(projectName, getLoader(delegate)).getAllValues().stream()
        .filter(ref -> id.equals(ref.getObjectId()))
        .collect(Collectors.toCollection(ConcurrentHashMap::newKeySet));
  }

  @Override
  public boolean hasFastTipsWithSha1(RefDatabase delegate) {
    return true;
  }

  @Override
  public void removeRefFromObjectIdCache(String projectName, String refName, ObjectId objId) {
    ObjectIdAndProject oip = ObjectIdAndProject.create(objId, projectName);
    Set<Ref> refs = refsByObjectId.getIfPresent(oip);
    if (refs == null) {
      return;
    }
    refs.removeIf(r -> r.getName().equals(refName));
    if (refs.isEmpty()) {
      refsByObjectId.invalidate(oip);
    }
  }

  @Override
  public void addRefToObjectIdCache(String projectName, Ref ref) throws ExecutionException {
    if (ref.getObjectId() == null && ref.getObjectId() == ObjectId.zeroId()) {
      throw new ExecutionException(
          new IOException(
              String.format("Ref %s for project %s is null or empty", ref.getName(), projectName)));
    } else {
      ObjectIdAndProject oip = ObjectIdAndProject.create(ref.getObjectId(), projectName);
      refsByObjectId.get(oip, ConcurrentHashMap::newKeySet).add(ref);
    }
  }

  @Override
  public void evict(String identifier, String refName, RefDatabase delegate)
      throws ExecutionException {
    deleteRefInPrefixesByProjectCache(identifier, refName, delegate);
  }
}
