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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;

/**
 * A {@link TernarySearchTree} specialised for {@link Ref} values, keyed by ref name, with the
 * ability to look up refs by the {@link ObjectId} they point to.
 *
 */
public class RefTernarySearchTree extends TernarySearchTree<Ref> {

  private final SetMultimap<ObjectId, Ref> byObjectId = HashMultimap.create();

  /**
   * Insert a ref. If the key already exists the old value is replaced and the secondary index is
   * updated accordingly.
   *
   * @param refName ref name
   * @param ref the ref
   * @return number of key-value pairs after the operation
   */
  @Override
  public int insert(String refName, Ref ref) {
    getLock().writeLock().lock();
    try {
      validateValue(ref);
      Ref old = get(refName);
      if (old == null) {
        getSize().incrementAndGet();
      }
      setRoot(super.insert(getRoot(), refName, ref, 0));
      removeFromIndex(old, refName);
      addToIndex(ref.getObjectId(), ref);
      return size();
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
      if (old != null) {
        getSize().decrementAndGet();
        setRoot(super.insert(getRoot(), key, null, 0));
      }
      removeFromIndex(old, key);
      return getSize().get();
    } finally {
      getLock().writeLock().unlock();
    }
  }

  /**
   * Look up all refs pointing at the given {@link ObjectId}.
   *
   * @param objectId the object id to look up
   * @return unmodifiable set of refs pointing at {@code objectId},
   * returns an empty set if no refs are associated with the object id.
   */
  public Set<Ref> getByObjectId(ObjectId objectId) {
    getLock().readLock().lock();
    try {
      return ImmutableSet.copyOf(byObjectId.get(objectId));
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

  private void addToIndex(ObjectId objectId, Ref ref) {
    if (objectId != null) {
      byObjectId.put(objectId, ref);
    }
  }

  private void removeFromIndex(Ref old, String refName) {
    if (old != null && old.getObjectId() != null) {
      byObjectId.get(old.getObjectId()).removeIf(r -> r.getName().equals(refName));
    }
  }
}
