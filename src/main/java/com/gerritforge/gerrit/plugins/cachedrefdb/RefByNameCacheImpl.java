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

import com.google.common.base.Splitter;
import com.google.common.cache.Cache;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.server.cache.CacheModule;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;
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
  private static final String REFS_NAMES_BY_PREFIX = "refs_names_by_prefix";

  static com.google.inject.Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        cache(REF_BY_NAME, RefsByNameKey.class, new TypeLiteral<Optional<Ref>>() {});
        cache(REFS_BY_OBJECT_ID, RefsByObjectIdKey.class, new TypeLiteral<Set<Ref>>() {});
        cache(REFS_NAMES_BY_PREFIX, RefsByPrefixKey.class, new TypeLiteral<Set<String>>() {});
      }
    };
  }

  public record RefsByObjectIdKey(Project.NameKey projectNameKey, ObjectId objectId) {}

  public record RefsByNameKey(Project.NameKey projectNameKey, String refName) {}

  public record RefsByPrefixKey(Project.NameKey projectNameKey, String prefix) {}

  private final Cache<RefsByNameKey, Optional<Ref>> refByName;
  private final Cache<RefsByObjectIdKey, Set<Ref>> refsByObjectIdCache;
  private final Cache<RefsByPrefixKey, Set<String>> refsNamesByPrefix;

  @Inject
  RefByNameCacheImpl(
      @Named(REF_BY_NAME) Cache<RefsByNameKey, Optional<Ref>> refByName,
      @Named(REFS_BY_OBJECT_ID) Cache<RefsByObjectIdKey, Set<Ref>> refsByObjectIdCache,
      @Named(REFS_NAMES_BY_PREFIX) Cache<RefsByPrefixKey, Set<String>> refsNamesByPrefix) {
    this.refByName = refByName;
    this.refsByObjectIdCache = refsByObjectIdCache;
    this.refsNamesByPrefix = refsNamesByPrefix;
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
  public List<Ref> allByPrefix(String projectName, String prefix) throws IOException {
    try {
      Set<String> refNames =
          refsNamesByPrefix.get(
              new RefsByPrefixKey(CachedProjectsNames.nameKey(projectName), prefix),
              ConcurrentHashMap::newKeySet);
      return refNames.stream()
          .map(
              refName ->
                  Optional.ofNullable(
                          refByName.getIfPresent(
                              new RefsByNameKey(CachedProjectsNames.nameKey(projectName), refName)))
                      .orElse(Optional.empty()))
          .filter(Optional::isPresent)
          .map(Optional::get)
          .toList();
    } catch (ExecutionException e) {
      throw new IOException(e);
    }
  }

  @Override
  public boolean hasRefs(String identifier) {
    return existingRefs().anyMatch(e -> e.getKey().projectNameKey.get().equals(identifier));
  }

  @Override
  public void updateRefsCache(String projectName, Ref ref) throws IOException {
    ObjectId oid = ref.getObjectId();
    String refName = ref.getName();
    checkNotNull(oid);

    try {
      updateRefsByObjectIdCache(projectName, ref, oid);
      updateRefsPrefixesCache(projectName, refName);
    } catch (ExecutionException e) {
      throw new IOException(e);
    }
  }

  private void updateRefsPrefixesCache(String projectName, String refName)
      throws ExecutionException {
    if (!refName.startsWith(RefNames.REFS)) {
      return;
    }

    StringBuilder fullPrefix = new StringBuilder(RefNames.REFS);
    Project.NameKey projectNameKey = CachedProjectsNames.nameKey(projectName);
    String refPrefixSubstring = refName.substring(RefNames.REFS.length(), refName.lastIndexOf('/'));
    for (String refPrefix : Splitter.on('/').split(refPrefixSubstring)) {
      fullPrefix.append(refPrefix);
      fullPrefix.append('/');

      Set<String> existingRefNames =
          refsNamesByPrefix.get(
              new RefsByPrefixKey(projectNameKey, fullPrefix.toString()),
              ConcurrentHashMap::newKeySet);
      existingRefNames.add(refName);
    }
  }

  private void updateRefsByObjectIdCache(String projectName, Ref ref, ObjectId oid)
      throws ExecutionException {
    RefsByObjectIdKey key = new RefsByObjectIdKey(CachedProjectsNames.nameKey(projectName), oid);

    Set<Ref> existing = refsByObjectIdCache.get(key, ConcurrentHashMap::newKeySet);
    existing.add(ref);
  }

  private Stream<Entry<RefsByNameKey, Optional<Ref>>> existingRefs() {
    return refByName.asMap().entrySet().stream().filter(e -> e.getValue().isPresent());
  }

  @Override
  public Set<Ref> getRefsForObjectId(
      String projectName, ObjectId objectId, Callable<? extends Set<Ref>> loader)
      throws ExecutionException {
    RefsByObjectIdKey key =
        new RefsByObjectIdKey(CachedProjectsNames.nameKey(projectName), objectId);
    return refsByObjectIdCache.get(key, loader);
  }
}
