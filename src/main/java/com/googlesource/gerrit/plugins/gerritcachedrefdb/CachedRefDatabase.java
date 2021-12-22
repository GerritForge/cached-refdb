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

package com.googlesource.gerrit.plugins.gerritcachedrefdb;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

  private final RefByNameCacheWrapper refsCache;
  private final BatchRefUpdateWithCacheUpdate.Factory batchUpdateFactory;
  private final RefUpdateWithCacheUpdate.Factory updateFactory;
  private final RefRenameWithCacheUpdate.Factory renameFactory;
  private final RefDatabase delegate;
  private final CachedRefRepository repo;

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
    return refsCache.computeIfAbsent(
        repo.getProjectName(), name, () -> Optional.ofNullable(delegate.exactRef(name)));
  }

  @Deprecated
  @Override
  public Map<String, Ref> getRefs(String prefix) throws IOException {
    return delegate.getRefs(prefix);
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
    return delegate.exactRef(refs);
  }

  @Override
  public Ref firstExactRef(String... refs) throws IOException {
    return delegate.firstExactRef(refs);
  }

  @Override
  public List<Ref> getRefs() throws IOException {
    return delegate.getRefs();
  }

  @Override
  public List<Ref> getRefsByPrefix(String prefix) throws IOException {
    return delegate.getRefsByPrefix(prefix);
  }

  @Override
  public List<Ref> getRefsByPrefix(String... prefixes) throws IOException {
    return delegate.getRefsByPrefix(prefixes);
  }

  @Override
  public Set<Ref> getTipsWithSha1(ObjectId id) throws IOException {
    return delegate.getTipsWithSha1(id);
  }

  @Override
  public boolean hasFastTipsWithSha1() throws IOException {
    return delegate.hasFastTipsWithSha1();
  }

  @Override
  public boolean hasRefs() throws IOException {
    return delegate.hasRefs();
  }

  @Override
  public void refresh() {
    delegate.refresh();
  }
}
