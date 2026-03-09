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
  public void insertUpdatesSecondaryIndex() {
    tree.insert("refs/heads/a", ref("refs/heads/a", OID_1));
    tree.insert("refs/heads/b", ref("refs/heads/b", OID_1));

    // Verify tree contains both refs
    assertThat(tree.get("refs/heads/a")).isNotNull();
    assertThat(tree.get("refs/heads/b")).isNotNull();
    assertThat(tree.size()).isEqualTo(2);
  }

  @Test
  public void updateRefChangesObjectIdInIndex() {
    tree.insert("refs/heads/a", ref("refs/heads/a", OID_1));
    tree.insert("refs/heads/a", ref("refs/heads/a", OID_2));

    // Tree should still contain exactly one entry with the new objectId
    assertThat(tree.size()).isEqualTo(1);
    assertThat(tree.get("refs/heads/a").getObjectId()).isEqualTo(OID_2);
  }

  @Test
  public void deleteRefRemovesFromTree() {
    tree.insert("refs/heads/a", ref("refs/heads/a", OID_1));
    tree.insert("refs/heads/b", ref("refs/heads/b", OID_1));

    tree.delete("refs/heads/a");

    assertThat(tree.contains("refs/heads/a")).isFalse();
    assertThat(tree.contains("refs/heads/b")).isTrue();
    assertThat(tree.size()).isEqualTo(1);
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
