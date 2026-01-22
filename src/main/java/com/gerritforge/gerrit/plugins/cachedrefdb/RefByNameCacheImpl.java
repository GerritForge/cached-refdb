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

import com.google.common.cache.Cache;
import com.google.common.collect.Streams;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.cache.CacheModule;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.eclipse.jgit.internal.storage.memory.TernarySearchTree;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;

@Singleton
class RefByNameCacheImpl implements RefByNameCache {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String REF_BY_NAME = "ref_by_name";
  private static final String REFS_NAMES_BY_PROJECT = "refs_names_by_project";

  static com.google.inject.Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        cache(REF_BY_NAME, String.class, new TypeLiteral<Optional<Ref>>() {});
        cache(
            REFS_NAMES_BY_PROJECT,
            RefsByProjectKey.class,
            new TypeLiteral<TernarySearchTree<ObjectId>>() {});
      }
    };
  }

  public record RefsByProjectKey(Project.NameKey projectNameKey) {}

  private final Cache<RefsByProjectKey, TernarySearchTree<ObjectId>> refsNamesByProject;
  private final Cache<String, Optional<Ref>> refByName;

  @Inject
  RefByNameCacheImpl(
      @Named(REF_BY_NAME) Cache<String, Optional<Ref>> refByName,
      @Named(REFS_NAMES_BY_PROJECT)
          Cache<RefsByProjectKey, TernarySearchTree<ObjectId>> refsNamesByProject) {
    this.refByName = refByName;
    this.refsNamesByProject = refsNamesByProject;
  }

  @Override
  public Ref computeIfAbsent(
      String identifier, String ref, Callable<? extends Optional<Ref>> loader) {
    String uniqueRefName = getUniqueName(identifier, ref);
    try {
      return refByName.get(uniqueRefName, loader).orElse(null);
    } catch (ExecutionException e) {
      logger.atWarning().withCause(e).log("Getting ref for [%s] failed.", uniqueRefName);
      return null;
    }
  }

  @Override
  public void evict(String identifier, String ref) {
    refByName.invalidate(getUniqueName(identifier, ref));
    // TODO This operation is not atomic, Do we actually need to do this?
    // TODO What if ref is null? Is it a possible case?
    RefsByProjectKey refsByProjectKey = new RefsByProjectKey(Project.nameKey(identifier));
    TernarySearchTree<ObjectId> tree = refsNamesByProject.getIfPresent(refsByProjectKey);
    if (tree != null) {
      tree.delete(ref);
      refsNamesByProject.put(refsByProjectKey, tree);
    }
  }

  @Override
  public List<Ref> allByPrefix(String projectName, String prefix, RefDatabase refDatabase) {

    List<Ref> refsList = List.of();
    try {
      TernarySearchTree<ObjectId> tree =
          refsNamesByProject.get(
              new RefsByProjectKey(Project.nameKey(projectName)),
              () -> loadFromRepository(refDatabase));

      refsList =
          Streams.stream(tree.getKeysWithPrefix(prefix))
              .map(this::getMaybeRef)
              .filter(Optional::isPresent)
              .map(Optional::get)
              .collect(Collectors.toList());
    } catch (ExecutionException e) {
      // TODO what to do here?
    }
    return refsList;
  }

  private Optional<Ref> getMaybeRef(String refString) {
    return Optional.ofNullable(refByName.getIfPresent(refString)).orElse(Optional.empty());
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

  private void updateRefsPrefixesCache(String projectName, String refName, ObjectId oid)
      throws ExecutionException {

    RefsByProjectKey refsByProjectKey = new RefsByProjectKey(Project.nameKey(projectName));
    TernarySearchTree<ObjectId> tree = refsNamesByProject.getIfPresent(refsByProjectKey);

    TernarySearchTree<ObjectId> targetTree = tree != null ? tree : new TernarySearchTree<>();

    targetTree.insert(refName, oid);

    refsNamesByProject.put(refsByProjectKey, targetTree);
  }

  private static String getUniqueName(String identifier, String ref) {
    return String.format("%s$%s", identifier, ref);
  }

  private TernarySearchTree<ObjectId> loadFromRepository(RefDatabase refDatabase) {

    try {
      TernarySearchTree<ObjectId> tree = new TernarySearchTree<>();
      List<Ref> allRefs = refDatabase.getRefs();
      for (Ref ref : allRefs) {
        tree.insert(ref.getName(), ref.getObjectId());
      }
      return tree;
    } catch (IOException e) {
      // TODO Silently fails. Is it correct?
    }
    // TODO is it correct to return an empty tree ?
    return new TernarySearchTree<>();
  }
}
