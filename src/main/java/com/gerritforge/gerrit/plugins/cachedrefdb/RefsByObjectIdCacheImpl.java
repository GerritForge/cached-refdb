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

import com.google.common.cache.LoadingCache;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.server.cache.CacheModule;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;

@Singleton
class RefsByObjectIdCacheImpl implements RefsByObjectIdCache {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String REFS_BY_OBJECT_ID = "refs_by_objectid";

  static com.google.inject.Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        cache(REFS_BY_OBJECT_ID, ObjectId.class, new TypeLiteral<Set<Ref>>() {})
            .loader(RefByObjectIdLoader.class);
      }
    };
  }

  private final LoadingCache<ObjectId, Set<Ref>> refsByObjectId;

  @Inject
  RefsByObjectIdCacheImpl(
      @Named(REFS_BY_OBJECT_ID) LoadingCache<ObjectId, Set<Ref>> refsByObjectId) {
    this.refsByObjectId = refsByObjectId;
  }

  @Override
  public Set<Ref> get(ObjectId id) {
    try {
      return refsByObjectId.get(id);
    } catch (ExecutionException e) {
      logger.atWarning().withCause(e).log("Getting ref object for %s] failed.", id);
      return null;
    }
  }

  @Override
  public void put(ObjectId id, Ref ref) {
    refsByObjectId.put(id, Set.of(ref));
  }

  @Override
  public void evict(String objectId) {
    refsByObjectId.invalidate(objectId);
  }
}
