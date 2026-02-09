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
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;

class RefByNameCacheWrapper implements RefByNameCache {

  private final RefByNameCache cache;

  @Inject
  RefByNameCacheWrapper(DynamicItem<RefByNameCache> refByNameCache) {
    this.cache = refByNameCache.get();
  }

  @Override
  public Ref get(String identifier, String ref, RefDatabase delegate) {
    return cache.get(identifier, ref, delegate);
  }

  @Override
  public boolean containsKey(String identifier, String ref) {
    return cache.containsKey(identifier, ref);
  }

  @Override
  public void put(String identifier, Ref ref) {
    cache.put(identifier, ref);
  }

  @Override
  public void evict(String identifier, String ref) {
    cache.evict(identifier, ref);
  }

  @VisibleForTesting
  RefByNameCache cache() {
    return cache;
  }
}
