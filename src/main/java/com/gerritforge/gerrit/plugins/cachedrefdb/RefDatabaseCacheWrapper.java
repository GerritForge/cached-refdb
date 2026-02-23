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

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;

class RefDatabaseCacheWrapper implements RefDatabaseCache {

  private final RefDatabaseCache cache;

  @Inject
  RefDatabaseCacheWrapper(DynamicItem<RefDatabaseCache> refByNameCache) {
    this.cache = refByNameCache.get();
  }

  @Override
  public Ref get(String identifier, String ref, RefDatabase delegate) throws IOException {
    return cache.get(identifier, ref, delegate);
  }

  @Override
  public boolean containsKey(String identifier, String ref, RefDatabase delegate) {
    return cache.containsKey(identifier, ref, delegate);
  }

  @Override
  public void put(String identifier, Ref ref, RefDatabase delegate) throws IOException {
    cache.put(identifier, ref, delegate);
  }

  @Override
  public void evict(String identifier, String ref, RefDatabase delegate) throws ExecutionException {
    cache.evict(identifier, ref, delegate);
  }

  @Override
  public List<Ref> allByPrefixes(String identifier, String[] prefixes, RefDatabase delegate)
      throws ExecutionException {
    return cache.allByPrefixes(identifier, prefixes, delegate);
  }

  @Override
  public List<Ref> all(String identifier, RefDatabase delegate) throws ExecutionException {
    return cache.all(identifier, delegate);
  }

  @Override
  public void renameRef(String identifier, Ref srcRef, Ref destRef, RefDatabase delegate)
      throws ExecutionException {
    cache.renameRef(identifier, srcRef, destRef, delegate);
  }

  @Override
  public void updateRef(String identifier, String refName, RefDatabase delegate)
      throws IOException {
    cache.updateRef(identifier, refName, delegate);
  }

  @Override
  public Set<Ref> getRefsByObjectId(CachedRefRepository repo, ObjectId id, RefDatabase delegate)
      throws ExecutionException {
    return cache.getRefsByObjectId(repo, id, delegate);
  }

  @Override
  public boolean hasFastTipsWithSha1(RefDatabase delegate) {
    return cache.hasFastTipsWithSha1(delegate);
  }

  @VisibleForTesting
  RefDatabaseCache cache() {
    return cache;
  }
}
