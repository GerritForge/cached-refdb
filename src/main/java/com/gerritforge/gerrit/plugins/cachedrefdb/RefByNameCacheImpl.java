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
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.server.cache.CacheModule;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;

@Singleton
class RefByNameCacheImpl implements RefByNameCache {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String REF_BY_NAME = "ref_by_name";
  private static final String REFS_BY_OBJECT_ID = "refs_by_object_id";

  static com.google.inject.Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        cache(REF_BY_NAME, String.class, new TypeLiteral<Optional<Ref>>() {});
        cache(REFS_BY_OBJECT_ID, String.class, new TypeLiteral<SetMultimap<ObjectId, Ref>>() {});
      }
    };
  }

  private final Cache<String, Optional<Ref>> refByName;
  private final Cache<String, SetMultimap<ObjectId, Ref>> refsByObjectId;
  private final SetMultimap<String, ObjectId> objectIdsByRef;

  @Inject
  RefByNameCacheImpl(
      @Named(REF_BY_NAME) Cache<String, Optional<Ref>> refByName,
      @Named(REFS_BY_OBJECT_ID) Cache<String, SetMultimap<ObjectId, Ref>> refsByObjectId) {
    this.refByName = refByName;
    this.refsByObjectId = refsByObjectId;
    this.objectIdsByRef = newSetObjectIdByRefNameMultimap();
  }

  @Override
  public Ref computeIfAbsent(
      String identifier, String ref, Callable<? extends Optional<Ref>> loader) {
    String uniqueRefName = getUniqueName(identifier, ref);
    Callable<Optional<Ref>> refsByObjectIdLoader =
        () -> {
          Optional<Ref> foundRef = loader.call();
          foundRef.ifPresent(
              r -> {
                SetMultimap<ObjectId, Ref> refsByObjectIdForIdentifier = refsByObjectId(identifier);
                refsByObjectIdForIdentifier.put(r.getObjectId(), r);
              });
          return foundRef;
        };
    try {
      return refByName.get(uniqueRefName, refsByObjectIdLoader).orElse(null);
    } catch (ExecutionException e) {
      logger.atWarning().withCause(e).log("Getting ref for [%s] failed.", uniqueRefName);
      return null;
    }
  }

  @Override
  public void evict(String identifier, String ref) {
    refByName.invalidate(getUniqueName(identifier, ref));
  }

  @Override
  public List<Ref> all(String identifier) {
    String prefix = prefix(identifier);
    return existingRefs()
        .filter(e -> e.getKey().startsWith(prefix))
        .map(Map.Entry::getValue)
        .map(Optional::get)
        .collect(Collectors.toList());
  }

  @Override
  public boolean hasRefs(String identifier) {
    String prefix = prefix(identifier);
    return existingRefs().anyMatch(e -> e.getKey().startsWith(prefix));
  }

  @Override
  public SetMultimap<ObjectId, Ref> refsByObjectId(String identifier) {
    try {
      return refsByObjectId.get(identifier, RefByNameCacheImpl::newSetRefsByObjectIdMultimap);
    } catch (ExecutionException e) {
      logger.atWarning().withCause(e).log("Getting refs by object id failed for [%s]", identifier);
      return null;
    }
  }

  private static SetMultimap<ObjectId, Ref> newSetRefsByObjectIdMultimap() {
    return MultimapBuilder.hashKeys().hashSetValues().build();
  }

  private static SetMultimap<String, ObjectId> newSetObjectIdByRefNameMultimap() {
    return MultimapBuilder.hashKeys().hashSetValues().build();
  }

  private Stream<Entry<String, Optional<Ref>>> existingRefs() {
    return refByName.asMap().entrySet().stream().filter(e -> e.getValue().isPresent());
  }

  private static String getUniqueName(String identifier, String ref) {
    return String.format("%s$%s", identifier, ref);
  }

  private static String prefix(String identifier) {
    return identifier + "$";
  }
}
