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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import java.util.Set;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.Ref;
import org.junit.Before;
import org.junit.Test;

public class RefTernarySearchTreeTest {

  private static final ObjectId OID_1 =
      ObjectId.fromString("0000000000000000000000000000000000000001");
  private static final ObjectId OID_2 =
      ObjectId.fromString("0000000000000000000000000000000000000002");

  private RefTernarySearchTree tree;

  @Before
  public void setUp() {
    tree = new RefTernarySearchTree();
  }

  private static Ref ref(String name, ObjectId objectId) {
    return new ObjectIdRef.Unpeeled(Ref.Storage.PACKED, name, objectId);
  }

  @Test
  public void insertMultipleRefsWithSameObjectId() {
    tree.insert("refs/heads/a", ref("refs/heads/a", OID_1));
    tree.insert("refs/heads/b", ref("refs/heads/b", OID_1));
    tree.insert("refs/heads/c", ref("refs/heads/c", OID_1));

    Set<Ref> refs = tree.getByObjectId(OID_1);
    assertThat(refs).hasSize(3);
    assertThat(refs.stream().map(Ref::getName))
        .containsExactly("refs/heads/a", "refs/heads/b", "refs/heads/c");
  }

  @Test
  public void updateRefMovesItBetweenBuckets() {
    tree.insert("refs/heads/a", ref("refs/heads/a", OID_1));
    tree.insert("refs/heads/a", ref("refs/heads/a", OID_2));

    assertThat(tree.getByObjectId(OID_1)).isEmpty();
    Set<Ref> newBucket = tree.getByObjectId(OID_2);
    assertThat(newBucket).hasSize(1);
    assertThat(newBucket.iterator().next().getName()).isEqualTo("refs/heads/a");
  }

  @Test
  public void deleteRefRemovesItFromBucket() {
    tree.insert("refs/heads/a", ref("refs/heads/a", OID_1));
    tree.insert("refs/heads/b", ref("refs/heads/b", OID_1));

    tree.delete("refs/heads/a");

    Set<Ref> refs = tree.getByObjectId(OID_1);
    assertThat(refs).hasSize(1);
    assertThat(refs.iterator().next().getName()).isEqualTo("refs/heads/b");
  }

  @Test
  public void getByObjectIdReturnsEmptyListWhenNoneFound() {
    assertThat(tree.getByObjectId(OID_1)).isEmpty();
  }

  @Test
  public void clearThrowsUnsupportedOperationException() {
    assertThrows(UnsupportedOperationException.class, () -> tree.clear());
  }

  @Test
  public void replaceThrowsUnsupportedOperationException() {
    assertThrows(
        UnsupportedOperationException.class,
        () -> tree.replace(java.util.Collections.emptyList()));
  }

  @Test
  public void reloadThrowsUnsupportedOperationException() {
    assertThrows(
        UnsupportedOperationException.class,
        () -> tree.reload(java.util.Collections.emptyList()));
  }

  @Test
  public void insertMapThrowsUnsupportedOperationException() {
    assertThrows(
        UnsupportedOperationException.class,
        () -> tree.insert(java.util.Collections.emptyMap()));
  }

  @Test
  public void deleteIterableThrowsUnsupportedOperationException() {
    assertThrows(
        UnsupportedOperationException.class,
        () -> tree.delete(java.util.Collections.emptyList()));
  }
}
