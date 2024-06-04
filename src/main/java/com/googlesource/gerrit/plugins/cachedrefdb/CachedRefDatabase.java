// Copyright (C) 2021 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.googlesource.gerrit.plugins.cachedrefdb;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import com.google.common.flogger.FluentLogger;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.RefRename;
import org.eclipse.jgit.lib.RefUpdate;

class CachedRefDatabase extends RefDatabase {
  interface Factory {
    CachedRefDatabase create(CachedRefRepository repo, RefDatabase delegate);
  }

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final RefByNameCacheWrapper refsCache;
  private final BatchRefUpdateWithCacheUpdate.Factory batchUpdateFactory;
  private final RefUpdateWithCacheUpdate.Factory updateFactory;
  private final RefRenameWithCacheUpdate.Factory renameFactory;
  private final RefDatabase delegate;
  private final CachedRefRepository repo;
  /** Contains all refs. */
  private SetMultimap<ObjectId, Ref> refsByObjectId;

  @Inject
  CachedRefDatabase(
      RefByNameCacheWrapper refsCache,
      BatchRefUpdateWithCacheUpdate.Factory batchUpdateFactory,
      RefUpdateWithCacheUpdate.Factory updateFactory,
      RefRenameWithCacheUpdate.Factory renameFactory,
      @Assisted CachedRefRepository repo,
      @Assisted RefDatabase delegate) {
    this.refsCache = refsCache;
    this.batchUpdateFactory = batchUpdateFactory;
    this.updateFactory = updateFactory;
    this.renameFactory = renameFactory;
    this.delegate = delegate;
    this.repo = repo;
  }

  @Override
  public void create() throws IOException {
    delegate.create();
  }

  @Override
  public void close() {
    delegate.close();
  }

  @Override
  public boolean isNameConflicting(String name) throws IOException {
    return delegate.isNameConflicting(name);
  }

  @Override
  public RefUpdate newUpdate(String name, boolean detach) throws IOException {
    return updateFactory.create(this, repo, delegate.newUpdate(name, detach));
  }

  @Override
  public RefRename newRename(String fromName, String toName) throws IOException {
    return renameFactory.create(
        repo,
        delegate.newRename(fromName, toName),
        newUpdate(fromName, false),
        newUpdate(toName, false));
  }

  @Override
  public Ref exactRef(String name) throws IOException {
    lazilyInitRefMaps();
    return refsCache.computeIfAbsent(
        repo.getProjectName(),
        name,
        () -> {
          Optional<Ref> ref = Optional.ofNullable(delegate.exactRef(name));
          ref.ifPresent(r -> refsByObjectId.put(r.getObjectId(), r));
          return ref;
        });
  }

  @Deprecated
  @Override
  public Map<String, Ref> getRefs(String prefix) throws IOException {
    List<Ref> found = getRefsByPrefix(prefix);
    return found.stream().collect(toMap(Ref::getName, Function.identity()));
  }

  @Override
  public List<Ref> getAdditionalRefs() throws IOException {
    return delegate.getAdditionalRefs();
  }

  @Override
  public Ref peel(Ref ref) throws IOException {
    return delegate.peel(ref);
  }

  @Override
  public boolean hasVersioning() {
    return delegate.hasVersioning();
  }

  @Override
  public Collection<String> getConflictingNames(String name) throws IOException {
    return delegate.getConflictingNames(name);
  }

  @Override
  public BatchRefUpdate newBatchUpdate() {
    return batchUpdateFactory.create(repo, delegate.newBatchUpdate());
  }

  @Override
  public boolean performsAtomicTransactions() {
    return delegate.performsAtomicTransactions();
  }

  @Override
  public Map<String, Ref> exactRef(String... refs) throws IOException {
    Set<String> exactRefs = new HashSet<>(Arrays.asList(refs));
    Map<String, Ref> foundRefs =
        getAllRefs().stream()
            .filter(ref -> exactRefs.contains(ref.getName()))
            .collect(toMap(Ref::getName, Function.identity()));
    return foundRefs;
  }

  @Override
  public Ref firstExactRef(String... refs) throws IOException {
    Set<String> exactRefs = Set.of(refs);
    Optional<Ref> found =
        getAllRefs().stream().filter(ref -> exactRefs.contains(ref.getName())).findFirst();
    return found.orElse(null);
  }

  @Override
  public List<Ref> getRefs() throws IOException {
    return getAllRefs();
  }

  @Override
  public List<Ref> getRefsByPrefix(String prefix) throws IOException {
    List<Ref> refs = getAllRefs();
    return RefDatabase.ALL.equals(prefix)
        ? refs
        : refs.stream().filter(r -> r.getName().startsWith(prefix)).collect(toList());
  }

  @Override
  public List<Ref> getRefsByPrefix(String... prefixes) throws IOException {
    List<Ref> refs = getAllRefs();
    List<String> prefixesToCheck = List.of(prefixes);
    List<Ref> foundRefs =
        refs.stream()
            .filter(
                r -> prefixesToCheck.stream().anyMatch(prefix -> r.getName().startsWith(prefix)))
            .collect(toList());
    return foundRefs;
  }

  @Override
  public Set<Ref> getTipsWithSha1(ObjectId id) throws IOException {
    lazilyInitRefMaps();
    return new HashSet<>(refsByObjectId.get(id));
  }

  @Override
  public boolean hasFastTipsWithSha1() throws IOException {
    return delegate.hasFastTipsWithSha1();
  }

  @Override
  public boolean hasRefs() throws IOException {
    return refsCache.hasRefs(repo.getProjectName()) || delegate.hasRefs();
  }

  @Override
  public void refresh() {
    delegate.refresh();
    getAllRefsFromDelegate();
  }

  @CanIgnoreReturnValue
  private List<Ref> getAllRefsFromDelegate() {
    logger.atInfo().log(
        "Getting all refs from underlying ref DB for %s repository", repo.getProjectName());
    try {
      List<Ref> allRefs = delegate.getRefs();
      for (Ref ref : allRefs) {
        refsCache.computeIfAbsent(
            repo.getProjectName(),
            ref.getName(),
            () -> {
              refsByObjectId.put(ref.getObjectId(), ref);
              return Optional.of(ref);
            });
      }
      return allRefs;
    } catch (IOException e) {
      return Collections.emptyList();
    }
  }

  private List<Ref> getAllRefs() {
    List<Ref> refs = refsCache.all(repo.getProjectName());
    if (refs.isEmpty()) {
      refs = getAllRefsFromDelegate();
    }
    return refs;
  }

  private void lazilyInitRefMaps() {
    if (refsByObjectId != null) {
      return;
    }

    refsByObjectId = MultimapBuilder.hashKeys().hashSetValues().build();
    List<Ref> allRefs = getAllRefs();
    for (Ref ref : allRefs) {
      ObjectId objectId = ref.getObjectId();
      if (objectId != null) {
        refsByObjectId.put(objectId, ref);
      }
    }
  }
}
