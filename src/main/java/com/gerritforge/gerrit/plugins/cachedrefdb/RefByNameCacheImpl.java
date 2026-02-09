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

import com.google.common.cache.Cache;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.server.cache.CacheModule;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;

@Singleton
class RefByNameCacheImpl implements RefByNameCache {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String REF_BY_NAME = "ref_by_name";

  static com.google.inject.Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        cache(REF_BY_NAME, String.class, new TypeLiteral<Optional<Ref>>() {});
      }
    };
  }

  private final Cache<String, Optional<Ref>> refByName;

  @Inject
  RefByNameCacheImpl(@Named(REF_BY_NAME) Cache<String, Optional<Ref>> refByName) {
    this.refByName = refByName;
  }


  @Override
  public Ref get(String project, String ref, RefDatabase delegate) {
    String key = getUniqueName(project, ref);
    try {
      Optional<Ref> maybeRef =
          refByName.get(
              key,
              (Callable<Optional<Ref>>)
                  () -> Optional.ofNullable(delegate.exactRef(ref)));
      return maybeRef.orElse(null);
    } catch (ExecutionException e) {
      logger.atWarning().withCause(e).log("Getting ref for [%s, %s] failed.", project, ref);
      return null;
    }
  }

  @Override
  public void put(String project, Ref ref) {
    refByName.put(getUniqueName(project, ref.getName()), Optional.of(ref));
  }

  @Override
  public void evict(String identifier, String ref) {
    refByName.invalidate(getUniqueName(identifier, ref));
  }

  private static String getUniqueName(String identifier, String ref) {
    return String.format("%s$%s", identifier, ref);
  }
}
