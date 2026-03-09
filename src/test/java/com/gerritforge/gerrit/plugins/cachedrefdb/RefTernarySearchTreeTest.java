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
import org.eclipse.jgit.lib.SymbolicRef;
import org.junit.Before;
import org.junit.Test;

public class RefTernarySearchTreeTest {

  private static final ObjectId OID_1 =
      ObjectId.fromString("0000000000000000000000000000000000000001");
  private static final ObjectId OID_2 =
      ObjectId.fromString("0000000000000000000000000000000000000002");

  private static final String REF_A = "refs/heads/a";
  private static final String REF_B = "refs/heads/b";
  private static final String TAG_V1 = "refs/tags/v1.0";

  private RefTernarySearchTree tree;

  @Before
  public void setUp() {
    tree = new RefTernarySearchTree();
  }

  private static Ref ref(String name, ObjectId objectId) {
    return new ObjectIdRef.Unpeeled(Ref.Storage.PACKED, name, objectId);
  }

  private static Ref peeledTagRef(String name, ObjectId tagObjectId, ObjectId commitObjectId) {
    return new ObjectIdRef.PeeledTag(Ref.Storage.PACKED, name, tagObjectId, commitObjectId);
  }

  private static Ref symbolicRef(String name, String targetName, ObjectId targetOid) {
    return new SymbolicRef(
        name, new ObjectIdRef.Unpeeled(Ref.Storage.LOOSE, targetName, targetOid));
  }

  @Test
  public void insertUpdatesSecondaryIndex() {
    tree.insert(REF_A, ref(REF_A, OID_1));
    tree.insert(REF_B, ref(REF_B, OID_2));

    // Verify tree contains both refs
    assertThat(tree.get(REF_A)).isNotNull();
    assertThat(tree.get(REF_B)).isNotNull();
    assertThat(tree.size()).isEqualTo(2);

    assertThat(tree.getByObjectId(OID_1).stream().map(Ref::getName)).containsExactly(REF_A);
    assertThat(tree.getByObjectId(OID_2).stream().map(Ref::getName)).containsExactly(REF_B);
  }

  @Test
  public void insertMultipleRefsWithSameOIDCorrectlyUpdatesIndex() {
    tree.insert(REF_A, ref(REF_A, OID_1));
    tree.insert(REF_B, ref(REF_B, OID_1));

    // Verify tree contains both refs
    assertThat(tree.get(REF_A)).isNotNull();
    assertThat(tree.get(REF_B)).isNotNull();
    assertThat(tree.size()).isEqualTo(2);

    assertThat(tree.getByObjectId(OID_1).stream().map(Ref::getName)).containsExactly(REF_A, REF_B);
  }

  @Test
  public void updateRefMovesItBetweenObjectIdBuckets() {
    tree.insert(REF_A, ref(REF_A, OID_1));
    tree.insert(REF_A, ref(REF_A, OID_2));

    assertThat(tree.getByObjectId(OID_1)).isEmpty();
    assertThat(tree.getByObjectId(OID_2).stream().map(Ref::getName)).containsExactly(REF_A);
    assertThat(tree.getSize().get()).isEqualTo(1);
  }

  @Test
  public void symbolicRefIsStoredAndIndexedByTargetObjectId() {
    tree.insert(REF_A, symbolicRef(REF_A, REF_B, OID_1));

    assertThat(tree.get(REF_A)).isNotNull();
    assertThat(tree.get(REF_A).isSymbolic()).isTrue();
    assertThat(tree.getByObjectId(OID_1).stream().map(Ref::getName)).containsExactly(REF_A);
  }

  @Test
  public void refWithNullObjectIdIsStoredButNotIndexed() {
    tree.insert(REF_A, ref(REF_A, null));

    assertThat(tree.get(REF_A)).isNotNull();
    assertThat(tree.getByObjectId(OID_1)).isEmpty();
  }

  @Test
  public void annotatedTagIsIndexedByPeeledCommitObjectId() {
    // OID_1 = tag object SHA, OID_2 = commit SHA the tag points to
    tree.insert(TAG_V1, peeledTagRef(TAG_V1, OID_1, OID_2));

    assertThat(tree.getByObjectId(OID_2).stream().map(Ref::getName)).containsExactly(TAG_V1);

    tree.delete(TAG_V1);

    assertThat(tree.getByObjectId(OID_1)).isEmpty();
    assertThat(tree.getByObjectId(OID_2)).isEmpty();
  }

  @Test
  public void deleteRefRemovesFromTree() {
    tree.insert(REF_A, ref(REF_A, OID_1));
    tree.insert(REF_B, ref(REF_B, OID_1));

    tree.delete(REF_A);

    assertThat(tree.contains(REF_A)).isFalse();
    assertThat(tree.contains(REF_B)).isTrue();
    assertThat(tree.size()).isEqualTo(1);
    assertThat(tree.getByObjectId(OID_1).stream().map(Ref::getName)).containsExactly(REF_B);
  }
}
