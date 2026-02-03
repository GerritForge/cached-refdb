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

import com.google.common.cache.CacheLoader;
import com.google.inject.Inject;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;

public class RefByObjectIdLoader extends CacheLoader<ObjectId, Set<Ref>> {
  private final RefByNameCacheWrapper refByNameCache;

  @Inject
  public RefByObjectIdLoader(RefByNameCacheWrapper refByName) {
    this.refByNameCache = refByName;
  }

  @Override
  public Set<Ref> load(ObjectId key) throws Exception {
    return refByNameCache.all().stream()
        .filter(ref -> ref.getObjectId().equals(key))
        .collect(Collectors.toSet());
  }
}
