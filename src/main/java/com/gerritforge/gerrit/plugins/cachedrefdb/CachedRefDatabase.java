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

import com.google.common.flogger.FluentLogger;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.RefRename;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.ReflogReader;

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

  RefDatabase getDelegate() {
    return delegate;
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
    return refsCache.get(repo.getProjectName(), name, delegate);
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
    return batchUpdateFactory.create(repo, delegate.newBatchUpdate(), delegate);
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
    List<Ref> allRefs = delegate.getRefs();
    for (Ref ref : allRefs) {
      if (!refsCache.containsKey(repo.getProjectName(), ref.getName())) {
        refsCache.put(repo.getProjectName(), ref);
      }
    }
    return allRefs;
  }

  @Override
  public ReflogReader getReflogReader(Ref ref) throws IOException {
    return delegate.getReflogReader(ref);
  }

  @Override
  public List<Ref> getRefsByPrefix(String prefix) throws IOException {
    try {
      return RefDatabase.ALL.equals(prefix)
          ? refsCache.all(repo.getProjectName(), delegate)
          : refsCache.allByPrefix(repo.getProjectName(), prefix, delegate);
    } catch (ExecutionException e) {
      logger.atWarning().withCause(e).log(
          "Cannot load refs from cache for project %s, prefix %s", repo.getProjectName(), prefix);
      return delegate.getRefsByPrefix(prefix);
    }
  }

  @Override
  public List<Ref> getRefsByPrefix(String... prefixes) throws IOException {
    Set<String> uniquePrefixes = new LinkedHashSet<>();

    for (String p : prefixes) {
      if (p != null && !p.isBlank()) {
        uniquePrefixes.add(p);
      }
    }

    List<Ref> result = new ArrayList<>();
    for (String p : uniquePrefixes) {
      result.addAll(getRefsByPrefix(p));
    }
    return result;
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
