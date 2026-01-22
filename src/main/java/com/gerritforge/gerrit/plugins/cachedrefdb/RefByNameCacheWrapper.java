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
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.eclipse.jgit.lib.Ref;

class RefByNameCacheWrapper implements RefByNameCache {

  private final RefByNameCache cache;

  @Inject
  RefByNameCacheWrapper(DynamicItem<RefByNameCache> refByNameCache) {
    this.cache = refByNameCache.get();
  }

  @Override
  public Ref get(String identifier, String ref) {
    return cache.get(identifier, ref);
  }

  @Override
  public void put(String identifier, Ref ref) {
    cache.put(identifier, ref);
  }

  @Override
  public void evictRefByNameCache(String identifier, String ref) {
    cache.evictRefByNameCache(identifier, ref);
  }

  @Override
  public List<Ref> allByPrefix(String projectName, String prefix) throws ExecutionException {
    return cache.allByPrefix(projectName, prefix);
  }

  @Override
  public void updateRefNamesByProjectCache(String projectName, String refName) {
    cache.updateRefNamesByProjectCache(projectName, refName);
  }

  @Override
  public void evictFromRefNamesByProjectCache(String projectName, String refName) {
    cache.evictFromRefNamesByProjectCache(projectName, refName);
  }

  @VisibleForTesting
  RefByNameCache cache() {
    return cache;
  }
}
