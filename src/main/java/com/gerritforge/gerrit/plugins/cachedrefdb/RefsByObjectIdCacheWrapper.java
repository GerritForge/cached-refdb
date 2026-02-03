// Copyright (C) 2026 GerritForge, Inc.
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
import java.util.Optional;
import java.util.Set;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;

class RefsByObjectIdCacheWrapper implements RefsByObjectIdCache {
  private static final RefsByObjectIdCache NOOP_CACHE = new NoOpRefsByObjectIdCache();

  private final RefsByObjectIdCache cache;

  @Inject
  RefsByObjectIdCacheWrapper(
      DynamicItem<RefsByObjectIdCache> refsByObjectIdCache,
      NoOpRefsByObjectIdCache noOpRefsByObjectIdCache) {
    this.cache = Optional.ofNullable(refsByObjectIdCache.get()).orElse(NOOP_CACHE);
  }

  @Override
  public Set<Ref> get(ObjectId Id) {
    return cache.get(Id);
  }

  @Override
  public void put(ObjectId id, Ref ref) {
    cache.put(id, ref);
  }

  @Override
  public void evict(String objectId) {
    cache.evict(objectId);
  }

  @VisibleForTesting
  RefsByObjectIdCache cache() {
    return cache;
  }
}
