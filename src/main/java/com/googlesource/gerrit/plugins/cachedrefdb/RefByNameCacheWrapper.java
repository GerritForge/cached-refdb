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
import com.google.common.collect.ListMultimap;
import com.google.common.collect.SetMultimap;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import org.eclipse.jgit.lib.ObjectId;
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
  public List<Ref> all(String identifier) {
    return cache.all(identifier);
  }

  @Override
  public boolean hasRefs(String identifier) {
    return cache.hasRefs(identifier);
  }

  @Override
  public SetMultimap<ObjectId, Ref> refsByObjectId(String identifier) {
    return cache.refsByObjectId(identifier);
  }

  @VisibleForTesting
  RefByNameCache cache() {
    return cache;
  }
}
