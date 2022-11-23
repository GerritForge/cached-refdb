// Copyright (C) 2022 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.googlesource.gerrit.plugins.cachedrefdb;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.cache.CacheBuilder;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.registration.DynamicItem;
import java.io.IOException;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class CachedRefRepositoryIT {
  TestRepository<Repository> tr;
  CachedRefRepository objectUnderTest;

  @Before
  public void setUp() throws IOException {
    Repository repo = new InMemoryRepository(new DfsRepositoryDescription("repo"));
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
  public void shouldResolveByRef() throws Exception {
    String master = RefNames.fullName("master");
    RevCommit first = tr.update(master, tr.commit().add("first", "foo").create());
    String tag = "test_tag";
    String fullTag = RefNames.REFS_TAGS + tag;
    tr.update(fullTag, tr.tag(tag, first));
    tr.update(master, tr.commit().parent(first).add("second", "foo").create());

    assertThat(objectUnderTest.resolve(master)).isEqualTo(repo().resolve(master));
    assertThat(objectUnderTest.resolve("master")).isEqualTo(repo().resolve("master"));
    assertThat(objectUnderTest.resolve(RefNames.HEAD)).isEqualTo(repo().resolve(RefNames.HEAD));
    assertThat(objectUnderTest.resolve(tag)).isEqualTo(repo().resolve(tag));
    assertThat(objectUnderTest.resolve(fullTag)).isEqualTo(repo().resolve(fullTag));

    String mastersParent = master + "^";
    ObjectId resolved = objectUnderTest.resolve(mastersParent);
    assertThat(resolved).isEqualTo(first);
    assertThat(resolved).isEqualTo(repo().resolve(mastersParent));
  }

  @Test
  public void shouldResolveBySha1() throws Exception {
    String master = RefNames.fullName("master");
    RevCommit first = tr.update(master, tr.commit().add("first", "foo").create());
    String tag = "test_tag";
    String fullTag = RefNames.REFS_TAGS + tag;
    tr.update(fullTag, tr.tag(tag, first));
    RevCommit second = tr.update(master, tr.commit().parent(first).add("second", "foo").create());

    String secondAbbreviatedName = second.getName().substring(0, 6);
    assertThat(objectUnderTest.resolve(second.name())).isEqualTo(repo().resolve(second.name()));
    assertThat(objectUnderTest.resolve(secondAbbreviatedName))
        .isEqualTo(repo().resolve(secondAbbreviatedName));
    String parentRevString = second.name() + "^";
    String resolvedName = objectUnderTest.resolve(parentRevString).getName();
    assertThat(resolvedName).isEqualTo(first.getName());
    assertThat(resolvedName).isEqualTo(repo().resolve(parentRevString).getName());
    String ensureIdIsCommit = secondAbbreviatedName + "(commit)";
    assertThat(objectUnderTest.resolve(ensureIdIsCommit))
        .isEqualTo(repo().resolve(ensureIdIsCommit));
  }

  @Test
  public void shouldResolveByTagAndSha1() throws Exception {
    String master = RefNames.fullName("master");
    RevCommit first = tr.update(master, tr.commit().add("first", "foo").create());
    String tag = "test_tag";
    tr.update(RefNames.REFS_TAGS + tag, tr.tag(tag, first));
    RevCommit second = tr.update(master, tr.commit().parent(first).add("second", "foo").create());

    String tagAndSha = tag + "-1-g" + second.getName().substring(0, 6);
    assertThat(objectUnderTest.resolve(tagAndSha)).isEqualTo(repo().resolve(tagAndSha));
  }

  private Repository repo() {
    return tr.getRepository();
  }

  private CachedRefRepository createCachedRepository(Repository repo) {
    RefByNameCacheWrapper wrapper =
        new RefByNameCacheWrapper(
            DynamicItem.itemOf(
                RefByNameCache.class, new RefByNameCacheImpl(CacheBuilder.newBuilder().build())));
    CachedRefDatabase.Factory refDbFactory =
        new CachedRefDatabase.Factory() {
          @Override
          public CachedRefDatabase create(CachedRefRepository repo, RefDatabase delegate) {
            return new CachedRefDatabase(wrapper, null, null, null, repo, delegate);
          }
        };
    return new CachedRefRepository(refDbFactory, null, null, "repo", repo);
  }
}
