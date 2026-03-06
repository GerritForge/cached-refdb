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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.registration.DynamicItem;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.internal.storage.memory.TernarySearchTree;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class CachedRefRepositoryTest {
  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private final String TEST_TAG_NAME = "test_tag";
  private final String TEST_TAG_REF_NAME = RefNames.REFS_TAGS + TEST_TAG_NAME;
  private static final String MASTER_BRANCH_NAME = "master";
  private final String MASTER_REF_NAME = RefNames.fullName(MASTER_BRANCH_NAME);

  private TestRepository<Repository> tr;
  private CachedRefRepository objectUnderTest;
  private TestRefByNameCacheImpl cache;

  private final String FIRST_FILENAME = "first";
  private RevCommit firstCommit;
  private RevCommit secondCommit;

  @Before
  public void setUp() throws IOException {
    Path repoPath = temporaryFolder.newFolder().toPath();
    Repository repo = new FileRepository(repoPath.toFile());
    repo.create(true);

    // enable reflog for a repository so that references to it could be resolved
    Files.writeString(repoPath.resolve("config"), "[core]\n  logAllRefUpdates = true\n");

    objectUnderTest = createCachedRepository(repo);
    tr = new TestRepository<>(repo);
  }

  @After
  public void tearDown() {
    // both CachedRefRepository and TestRepository call close on the underlying repo hence single
    // close is sufficient
    objectUnderTest.close();
  }

  @Test
  public void shouldResolveFullRefsFromCache() throws Exception {
    initTestRepository();

    assertThat(objectUnderTest.resolve(MASTER_REF_NAME)).isEqualTo(repo().resolve(MASTER_REF_NAME));
    assertThat(objectUnderTest.resolve(TEST_TAG_REF_NAME))
        .isEqualTo(repo().resolve(TEST_TAG_REF_NAME));
    assertThat(cache.cacheCalled).isEqualTo(2);
  }

  @Test
  public void shouldGetExactRefFromCache() throws Exception {
    initTestRepository();
    assertThat(objectUnderTest.exactRef(MASTER_REF_NAME))
        .isEqualTo(repo().exactRef(MASTER_REF_NAME));
    assertThat(objectUnderTest.exactRef(TEST_TAG_REF_NAME))
        .isEqualTo(repo().exactRef(TEST_TAG_REF_NAME));
    assertThat(cache.cacheCalled).isEqualTo(2);
  }

  @Test
  public void shouldNotResolveRefsFromCache() throws Exception {
    initTestRepository();

    assertThat(objectUnderTest.resolve(MASTER_BRANCH_NAME))
        .isEqualTo(repo().resolve(MASTER_BRANCH_NAME));
    assertThat(objectUnderTest.resolve(RefNames.HEAD)).isEqualTo(repo().resolve(RefNames.HEAD));
    assertThat(objectUnderTest.resolve(TEST_TAG_NAME)).isEqualTo(repo().resolve(TEST_TAG_NAME));

    String mastersParent = MASTER_REF_NAME + "^";
    ObjectId resolved = objectUnderTest.resolve(mastersParent);
    assertThat(resolved).isEqualTo(firstCommit);
    assertThat(resolved).isEqualTo(repo().resolve(mastersParent));

    String mastersParentByTilde = MASTER_REF_NAME + "~";
    ObjectId resolvedByTilde = objectUnderTest.resolve(mastersParentByTilde);
    assertThat(resolvedByTilde).isEqualTo(firstCommit);
    assertThat(resolvedByTilde).isEqualTo(repo().resolve(mastersParent));

    String mastersFilename = MASTER_REF_NAME + ":" + FIRST_FILENAME;
    ObjectId resolvedByFilename = objectUnderTest.resolve(mastersFilename);
    assertThat(resolvedByFilename).isEqualTo(repo().resolve(mastersFilename));

    String mastersPreviousRevision = MASTER_REF_NAME + "@{1}";
    ObjectId resolvedByPreviousRevision = objectUnderTest.resolve(mastersPreviousRevision);
    assertThat(resolvedByPreviousRevision).isEqualTo(repo().resolve(mastersPreviousRevision));

    assertThat(cache.cacheCalled).isEqualTo(0);
  }

  @Test
  public void shouldNotResolveSha1sFromCache() throws Exception {
    initTestRepository();

    String secondAbbreviatedName = secondCommit.getName().substring(0, 6);
    assertThat(objectUnderTest.resolve(secondCommit.name()))
        .isEqualTo(repo().resolve(secondCommit.name()));
    assertThat(objectUnderTest.resolve(secondAbbreviatedName))
        .isEqualTo(repo().resolve(secondAbbreviatedName));
    String parentRevString = secondCommit.name() + "^";
    String resolvedName = objectUnderTest.resolve(parentRevString).getName();
    assertThat(resolvedName).isEqualTo(firstCommit.getName());
    assertThat(resolvedName).isEqualTo(repo().resolve(parentRevString).getName());
    String ensureIdIsCommit = secondAbbreviatedName + "(commit)";
    assertThat(objectUnderTest.resolve(ensureIdIsCommit))
        .isEqualTo(repo().resolve(ensureIdIsCommit));

    assertThat(cache.cacheCalled).isEqualTo(0);
  }

  @Test
  public void shouldNotResolveTagAndSha1FromCache() throws Exception {
    RevCommit first = tr.update(MASTER_REF_NAME, tr.commit().add("first", "foo").create());
    tr.update(TEST_TAG_REF_NAME, tr.tag(TEST_TAG_NAME, first));
    RevCommit second =
        tr.update(MASTER_REF_NAME, tr.commit().parent(first).add("second", "foo").create());

    String tagAndSha = TEST_TAG_NAME + "-1-g" + second.getName().substring(0, 6);
    assertThat(objectUnderTest.resolve(tagAndSha)).isEqualTo(repo().resolve(tagAndSha));

    assertThat(cache.cacheCalled).isEqualTo(0);
  }

  private void initTestRepository() throws Exception {
    firstCommit = tr.update(MASTER_REF_NAME, tr.commit().add(FIRST_FILENAME, "foo").create());
    tr.update(TEST_TAG_REF_NAME, tr.tag(TEST_TAG_NAME, firstCommit));
    secondCommit =
        tr.update(MASTER_REF_NAME, tr.commit().parent(firstCommit).add("second", "foo").create());
    assertThat(cache.cacheCalled).isEqualTo(0);
  }

  private Repository repo() {
    return tr.getRepository();
  }

  private CachedRefRepository createCachedRepository(Repository repo) {
    cache = new TestRefByNameCacheImpl(CacheBuilder.newBuilder().build());
    RefDatabaseCacheWrapper wrapper =
        new RefDatabaseCacheWrapper(DynamicItem.itemOf(RefDatabaseCache.class, cache));
    CachedRefDatabase.Factory refDbFactory =
        (repo1, delegate) -> new CachedRefDatabase(wrapper, null, null, null, repo1, delegate);
    return new CachedRefRepository(refDbFactory, null, null, "repo", repo);
  }

  private static class TestRefByNameCacheImpl extends RefDatabaseCacheImpl {
    private int cacheCalled;

    private TestRefByNameCacheImpl(Cache<String, TernarySearchTree<Ref>> refsNamesByPrefix) {
      super(refsNamesByPrefix);
      cacheCalled = 0;
    }

    @Override
    public Ref get(String identifier, String ref, RefDatabase delegate) {
      cacheCalled++;
      return super.get(identifier, ref, delegate);
    }
  }
}
