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

  private static final String refA = "refs/heads/a";
  private static final String refB = "refs/heads/b";

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
    tree.insert(refA, ref(refA, OID_1));
    tree.insert(refB, ref(refB, OID_2));

    // Verify tree contains both refs
    assertThat(tree.get(refA)).isNotNull();
    assertThat(tree.get(refB)).isNotNull();
    assertThat(tree.size()).isEqualTo(2);

    assertThat(tree.getByObjectId(OID_1).stream().map(Ref::getName))
        .containsExactly(refA);
    assertThat(tree.getByObjectId(OID_2).stream().map(Ref::getName))
        .containsExactly(refB);
  }

  @Test
  public void insertMultipleRefsWithSameOIDCorrectlyUpdatesIndex() {
    tree.insert(refA, ref(refA, OID_1));
    tree.insert(refB, ref(refB, OID_1));

    // Verify tree contains both refs
    assertThat(tree.get(refA)).isNotNull();
    assertThat(tree.get(refB)).isNotNull();
    assertThat(tree.size()).isEqualTo(2);

    assertThat(tree.getByObjectId(OID_1).stream().map(Ref::getName))
        .containsExactly(refA, refB);
  }

  @Test
  public void updateRefMovesItBetweenObjectIdBuckets() {
    tree.insert(refA, ref(refA, OID_1));
    tree.insert(refA, ref(refA, OID_2));

    assertThat(tree.getByObjectId(OID_1)).isEmpty();
    assertThat(tree.getByObjectId(OID_2).stream().map(Ref::getName)).containsExactly(refA);
  }

  @Test
  public void deleteRefRemovesFromTree() {
    tree.insert(refA, ref(refA, OID_1));
    tree.insert(refB, ref(refB, OID_1));

    tree.delete(refA);

    assertThat(tree.contains(refA)).isFalse();
    assertThat(tree.contains(refB)).isTrue();
    assertThat(tree.size()).isEqualTo(1);
    assertThat(tree.getByObjectId(OID_1).stream().map(Ref::getName)).containsExactly(refB);
  }
}
