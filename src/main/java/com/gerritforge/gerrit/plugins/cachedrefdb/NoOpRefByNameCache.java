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

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.flogger.FluentLogger;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;

class NoOpRefByNameCache implements RefByNameCache {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public Ref get(String identifier, String ref, RefDatabase delegate) {
    try {
      return delegate.exactRef(ref);
    } catch (IOException e) {
      logger.atWarning().withCause(e).log(
          "Failed to resolve ref %s for project %s", ref, identifier);
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public boolean containsKey(String identifier, String ref) {
    return false;
  }

  @Override
  public void put(String identifier, Ref ref) {}

  @Override
  public void evictRefByNameCache(String identifier, String ref) {
    // do nothing as there is no cache to be evicted
  }

  @Override
  public List<Ref> allByPrefix(String projectName, String prefix, RefDatabase delegate)
      throws ExecutionException {
    try {
      return delegate.getRefsByPrefix(prefix);
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
  public void updateRefInPrefixesByProjectCache(String projectName, Ref ref) {
    // do nothing as there is no cache to update
  }

  @Override
  public void updateRefInPrefixesByProjectCache(
      String projectName, String refName, RefDatabase delegate) {
    // do nothing as there is no cache to update
  }

  @Override
  public void deleteRefInPrefixesByProjectCache(String identifier, String refName) {
    // do nothing as there is no cache to delete
  }

  @Override
  public Set<String> getRefNamesByObjectId(String identifier, ObjectId id, RefDatabase delegate)
      throws IOException {
    return delegate.getTipsWithSha1(id).stream().map(Ref::getName).collect(toImmutableSet());
  }

  @Override
  public boolean hasFastTipsWithSha1(RefDatabase delegate) throws IOException {
    return delegate.hasFastTipsWithSha1();
  }

  @Override
  public void evictObjectIdCache(String identifier, ObjectId id) {
    // do nothing as there is no cache to evict
  }
}
