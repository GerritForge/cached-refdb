package com.gerritforge.gerrit.plugins.cachedrefdb;

import com.google.common.cache.CacheLoader;
import com.google.inject.Inject;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;

import java.util.Set;
import java.util.stream.Collectors;


public class RefByObjectIdLoader extends CacheLoader<ObjectId, Set<Ref>> {
  private final RefByNameCacheWrapper refByNameCache;

  @Inject
  public RefByObjectIdLoader(RefByNameCacheWrapper refByName) {
    this.refByNameCache = refByName;
  }

  @Override
  public Set<Ref> load(ObjectId key) throws Exception {
    return refByNameCache.all().stream()
        .filter(ref -> ref.getObjectId().equals(key)).collect(Collectors.toSet());
  }
}
