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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.server.cache.PerThreadCache;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import javax.annotation.Nonnull;
import org.eclipse.jgit.lib.Ref;

public class PerThreadRefByNameCache implements RefByNameCache {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  protected static final PerThreadCache.Key<PerThreadRefByNameCache> REF_CACHE_KEY =
      PerThreadCache.Key.create(PerThreadRefByNameCache.class);

  private Map<String, Optional<Ref>> refByName;

  PerThreadRefByNameCache() {
    refByName = new HashMap<>();
  }

  public static PerThreadRefByNameCache get(@Nonnull PerThreadCache perThreadCache) {
    return perThreadCache.get(REF_CACHE_KEY, PerThreadRefByNameCache::new);
  }

  @Override
  public Ref computeIfAbsent(
      String identifier, String refName, Callable<? extends Optional<Ref>> loader) {
    String uniqueRefName = getUniqueName(identifier, refName);
    return refByName
        .computeIfAbsent(
            uniqueRefName,
            s -> {
              try {
                return loader.call();
              } catch (Exception e) {
                logger.atWarning().withCause(e).log("Getting ref for [%s] failed.", uniqueRefName);
                return Optional.empty();
              }
            })
        .orElse(null);
  }

  @Override
  public void evict(String identifier, String ref) {
    refByName.remove(getUniqueName(identifier, ref));
  }

  private static String getUniqueName(String identifier, String ref) {
    return String.format("%s$%s", identifier, ref);
  }
}
