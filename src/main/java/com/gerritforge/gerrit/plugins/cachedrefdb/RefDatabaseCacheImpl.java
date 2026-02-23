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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;

@Singleton
class RefDatabaseCacheImpl implements RefDatabaseCache {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String REF_NAMES_BY_PROJECT = "ref_names_by_project";

  static com.google.inject.Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        cache(REF_NAMES_BY_PROJECT, String.class, new TypeLiteral<RefTernarySearchTree>() {});
      }
    };
  }

  private final Cache<String, RefTernarySearchTree> refNamesByProject;

  @Inject
  RefDatabaseCacheImpl(
      @Named(REF_NAMES_BY_PROJECT) Cache<String, RefTernarySearchTree> refNamesByProject) {
    this.refNamesByProject = refNamesByProject;
  }

  static class RefNamesByProjectLoader {

    static RefTernarySearchTree load(RefDatabase refDatabaseDelegate) throws IOException {

      RefTernarySearchTree tree = new RefTernarySearchTree();
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
    RefTernarySearchTree projectRefs = refNamesByProject.get(projectName, getLoader(delegate));
    AtomicReference<String> lastPrefix = new AtomicReference<>();
    ImmutableList.Builder<Ref> refs = ImmutableList.builder();
    Arrays.stream(prefixes)
        .sorted()
        .filter(prefix -> !isDuplicated(prefix, lastPrefix))
        .map(projectRefs::getValuesWithPrefix)
        .forEach(refs::addAll);
    return refs.build();
  }

  private static Callable<RefTernarySearchTree> getLoader(RefDatabase delegate) {
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
    RefTernarySearchTree tree = refNamesByProject.get(projectName, getLoader(delegate));
    tree.insert(ref.getName(), ref);
  }

  public void updateRefInPrefixesByProjectCache(
      String projectName, String refName, RefDatabase delegate)
      throws IOException, ExecutionException {
    updateRefInPrefixesByProjectCache(projectName, delegate.exactRef(refName), delegate);
  }

  public void deleteRefInPrefixesByProjectCache(
      String projectName, String refName, RefDatabase delegate) throws ExecutionException {
    refNamesByProject.get(projectName, getLoader(delegate)).deleteAndReturnOld(refName);
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
    RefTernarySearchTree tree = refNamesByProject.get(project, getLoader(delegate));
    Lock lock = tree.getLock().writeLock();
    lock.lock();
    try {
      tree.deleteAndReturnOld(srcRef.getName());
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
  public Set<Ref> getRefsByObjectId(String projectName, ObjectId id, RefDatabase delegate)
      throws ExecutionException {
    return refNamesByProject.get(projectName, getLoader(delegate)).getByObjectId(id);
  }

  @Override
  public boolean hasFastTipsWithSha1(RefDatabase delegate) {
    return true;
  }

  @Override
  public void evict(String identifier, String refName, RefDatabase delegate)
      throws ExecutionException {
    deleteRefInPrefixesByProjectCache(identifier, refName, delegate);
  }
}
