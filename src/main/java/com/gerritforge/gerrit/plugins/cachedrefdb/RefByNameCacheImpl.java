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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.cache.Cache;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.cache.CacheModule;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;

@Singleton
class RefByNameCacheImpl implements RefByNameCache {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String REF_BY_NAME = "ref_by_name";
  public static final String REFS_BY_OBJECT_ID = "refs_by_object_id";

  static com.google.inject.Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        cache(REF_BY_NAME, RefsByNameKey.class, new TypeLiteral<Optional<Ref>>() {});
        cache(REFS_BY_OBJECT_ID, RefsByObjectIdKey.class, new TypeLiteral<Set<Ref>>() {});
      }
    };
  }

  public record RefsByObjectIdKey(Project.NameKey projectNameKey, ObjectId objectId) {}

  public record RefsByNameKey(Project.NameKey projectNameKey, String refName) {}

  private final Cache<RefsByNameKey, Optional<Ref>> refByName;
  private final Cache<RefsByObjectIdKey, Set<Ref>> refsByObjectIdCache;

  @Inject
  RefByNameCacheImpl(
      @Named(REF_BY_NAME) Cache<RefsByNameKey, Optional<Ref>> refByName,
      @Named(REFS_BY_OBJECT_ID) Cache<RefsByObjectIdKey, Set<Ref>> refsByObjectIdCache) {
    this.refByName = refByName;
    this.refsByObjectIdCache = refsByObjectIdCache;
  }

  @Override
  public Ref computeIfAbsent(
      String projectName, String ref, Callable<? extends Optional<Ref>> loader) {
    RefsByNameKey uniqueRefName = new RefsByNameKey(CachedProjectsNames.nameKey(projectName), ref);
    try {
      return refByName.get(uniqueRefName, loader).orElse(null);
    } catch (ExecutionException e) {
      logger.atWarning().withCause(e).log("Getting ref for [%s] failed.", uniqueRefName);
      return null;
    }
  }

  @Override
  public void evict(String projectName, String ref) {
    refByName.invalidate(new RefsByNameKey(CachedProjectsNames.nameKey(projectName), ref));
  }

  @Override
  public List<Ref> all(String projectName) {
    return existingRefs()
        .filter(e -> e.getKey().projectNameKey.get().equals(projectName))
        .map(Map.Entry::getValue)
        .map(Optional::get)
        .collect(Collectors.toList());
  }

  @Override
  public boolean hasRefs(String identifier) {
    return existingRefs().anyMatch(e -> e.getKey().projectNameKey.get().equals(identifier));
  }

  @Override
  public void updateRefsByObjectIdCacheIfNeeded(String projectName, Ref ref) throws IOException {
    ObjectId oid = ref.getObjectId();
    checkNotNull(oid);

    try {
      RefsByObjectIdKey key = new RefsByObjectIdKey(CachedProjectsNames.nameKey(projectName), oid);

      Set<Ref> existing = refsByObjectIdCache.get(key, ConcurrentHashMap::newKeySet);
      existing.add(ref);
    } catch (ExecutionException e) {
      throw new IOException(e);
    }
  }

  private Stream<Entry<RefsByNameKey, Optional<Ref>>> existingRefs() {
    return refByName.asMap().entrySet().stream().filter(e -> e.getValue().isPresent());
  }

  @Override
  public Set<Ref> getRefsForObjectId(
      String projectName, ObjectId objectId, Callable<? extends Set<Ref>> loader)
      throws ExecutionException {
    RefsByObjectIdKey key = new RefsByObjectIdKey(CachedProjectsNames.nameKey(projectName), objectId);
    return refsByObjectIdCache.get(key, loader);
  }
}
