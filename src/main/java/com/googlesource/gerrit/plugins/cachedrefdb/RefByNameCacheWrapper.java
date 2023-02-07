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

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.server.cache.PerThreadCache;
import java.util.Optional;
import java.util.concurrent.Callable;
import org.eclipse.jgit.lib.Ref;

class RefByNameCacheWrapper implements RefByNameCache {
  protected static final PerThreadCache.Key<RefByNameCache> REF_CACHE_KEY =
      PerThreadCache.Key.create(RefByNameCache.class);
  protected static final RefByNameCache NOOP_CACHE = new NoOpRefByNameCache();

  protected final RefByNameCache cache;

  public RefByNameCacheWrapper() {
    PerThreadCache perThreadCache = PerThreadCache.get();
    if (perThreadCache != null) {
      cache = perThreadCache.get(REF_CACHE_KEY, RefByNameCacheImpl::new);
    } else {
      cache = NOOP_CACHE;
    }
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

  @VisibleForTesting
  RefByNameCache cache() {
    return cache;
  }
}
