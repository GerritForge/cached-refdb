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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;

/**
 * A {@link TernarySearchTree} specialised for {@link Ref} values, keyed by ref name, with the
 * ability to look up refs by the {@link ObjectId} they point to.
 *
 * @since 6.5
 */
public class RefTernarySearchTree extends TernarySearchTree<Ref> {

  private final Map<ObjectId, Set<Ref>> byObjectId = new HashMap<>();

  /**
   * Insert a ref. If the key already exists the old value is replaced and the secondary index is
   * updated accordingly.
   *
   * @param key ref name
   * @param ref the ref
   * @return number of key-value pairs after the operation
   */
  @Override
  public int insert(String key, Ref ref) {
    getLock().writeLock().lock();
    try {
      Ref old = get(key);
      int result = super.insert(key, ref);
      if (old != null && old.getObjectId() != null) {
        removeFromBucket(old.getObjectId(), key);
      }
      if (ref.getObjectId() != null) {
        addToBucket(ref.getObjectId(), ref);
      }
      // TODO: handle symbolic refs (ref.getObjectId() == null, e.g. HEAD)
      return result;
    } finally {
      getLock().writeLock().unlock();
    }
  }

  /**
   * Delete a ref by key. The secondary index is updated accordingly.
   *
   * @param key ref name
   * @return number of key-value pairs after the operation
   */
  @Override
  public int delete(String key) {
    getLock().writeLock().lock();
    try {
      Ref old = get(key);
      int result = super.delete(key);
      if (old != null && old.getObjectId() != null) {
        removeFromBucket(old.getObjectId(), key);
      }
      return result;
    } finally {
      getLock().writeLock().unlock();
    }
  }

  /**
   * Look up all refs pointing at the given {@link ObjectId}.
   *
   * @param objectId the object id to look up
   * @return unmodifiable set of refs pointing at {@code objectId}, never {@code null}
   */
  public Set<Ref> getByObjectId(ObjectId objectId) {
    getLock().readLock().lock();
    try {
      Set<Ref> refs = byObjectId.get(objectId);
      if (refs == null) {
        return Collections.emptySet();
      }
      return Collections.unmodifiableSet(new HashSet<>(refs));
    } finally {
      getLock().readLock().unlock();
    }
  }

  @Override
  public int replace(Iterable<Entry<String, Ref>> loader) {
    throw new UnsupportedOperationException(
        "replace(Iterable) is not supported; use insert(String, Ref) instead");
  }

  @Override
  public int reload(Iterable<Entry<String, Ref>> loader) {
    throw new UnsupportedOperationException(
        "reload(Iterable) is not supported; use insert(String, Ref) instead");
  }

  @Override
  public int insert(Map<String, Ref> map) {
    throw new UnsupportedOperationException(
        "insert(Map) is not supported; use insert(String, Ref) instead");
  }

  @Override
  public int delete(Iterable<String> delete) {
    throw new UnsupportedOperationException(
        "delete(Iterable) is not supported; use delete(String) instead");
  }

  @Override
  public void clear() {
    throw new UnsupportedOperationException(
        "clear() is not supported on RefTernarySearchTree");
  }

  private void addToBucket(ObjectId objectId, Ref ref) {
    byObjectId.computeIfAbsent(objectId, k -> new HashSet<>()).add(ref);
  }

  private void removeFromBucket(ObjectId objectId, String refName) {
    Set<Ref> refs = byObjectId.get(objectId);
    if (refs == null) {
      return;
    }
    refs.removeIf(r -> r.getName().equals(refName));
    if (refs.isEmpty()) {
      byObjectId.remove(objectId);
    }
  }
}
