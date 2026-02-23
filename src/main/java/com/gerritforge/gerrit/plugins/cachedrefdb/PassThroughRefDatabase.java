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

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;

class PassThroughRefDatabase implements RefDatabaseCache {

  public Ref get(String identifier, String ref, RefDatabase delegate) throws IOException {
    return delegate.exactRef(ref);
  }

  @Override
  public boolean containsKey(String identifier, String ref) {
    return false;
  }

  @Override
  public void put(String identifier, Ref ref) {}

  @Override
  public void evict(String identifier, String ref) {
    // do nothing as there is no cache to be evicted
  }

  @Override
  public List<Ref> allByPrefixes(String projectName, String[] prefixes, RefDatabase delegate)
      throws ExecutionException {
    try {
      return delegate.getRefsByPrefix(prefixes);
    } catch (IOException e) {
      throw new ExecutionException(e);
    }
  }

  @Override
  public List<Ref> all(String projectName, RefDatabase delegate) throws ExecutionException {
    try {
      return delegate.getRefs();
    } catch (IOException e) {
      throw new ExecutionException(e);
    }
  }

  @Override
  public void renameRef(String identifier, Ref srcRef, Ref destRef) throws ExecutionException {}

  @Override
  public void updateRef(String identifier, String refName, RefDatabase delete) {}

  @Override
  public Set<Ref> getRefsByObjectId(CachedRefRepository repo, ObjectId id, RefDatabase delegate)
      throws ExecutionException {
    try {
      return delegate.getTipsWithSha1(id);
    } catch (IOException e) {
      throw new ExecutionException(e);
    }
  }

  @Override
  public boolean hasFastTipsWithSha1(RefDatabase delegate) {
    return false;
  }

  @Override
  public void removeRefFromObjectIdCache(String identifier, String refName, ObjectId oldId) {
    // do nothing as there is no cache to update
  }

  @Override
  public void addRefToObjectIdCache(String identifier, String refName, ObjectId newId) {
    // do nothing as there is no cache to update
  }
}
