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
import java.util.Optional;
import java.util.concurrent.Callable;
import org.eclipse.jgit.lib.Ref;

class RefByNameCacheWrapper implements RefByNameCache {
  private static final RefByNameCache NOOP_CACHE = new NoOpRefByNameCache();

  private final RefByNameCache cache;

  @Inject
  RefByNameCacheWrapper(DynamicItem<RefByNameCache> refByNameCache) {
    this.cache = Optional.ofNullable(refByNameCache.get()).orElse(NOOP_CACHE);
  }

  @Override
  public Ref computeIfAbsent(
      String identifier, String ref, Callable<? extends Optional<Ref>> loader) {
    return cache.computeIfAbsent(identifier, ref, loader);
  }

  @Override
  public void evict(String identifier, String ref) {
    cache.evict(identifier, ref);
  }

  @Override
  public List<Ref> allByPrefix(String projectName, String prefix) {
    return cache.allByPrefix(projectName, prefix);
  }

  @Override
  public void updateRefsCache(String projectName, Ref ref) throws IOException {
    cache.updateRefsCache(projectName, ref);
  }

  @VisibleForTesting
  RefByNameCache cache() {
    return cache;
  }
}
