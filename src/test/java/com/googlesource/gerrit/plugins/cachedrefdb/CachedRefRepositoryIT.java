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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.gerrit.entities.RefNames;
import com.google.gerrit.server.cache.PerThreadCache;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

public class CachedRefRepositoryIT {
  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private TestRepository<Repository> tr;
  private RefByNameCacheWrapper cacheWrapper;
  private Repository repo;

  @Before
  public void setUp() throws IOException {
    Path repoPath = temporaryFolder.newFolder().toPath();
    repo = new FileRepository(repoPath.toFile());
    repo.create(true);

    // enable reflog for a repository so that references to it could be resolved
    Files.write(
        repoPath.resolve("config"),
        "[core]\n  logAllRefUpdates = true\n".getBytes(StandardCharsets.UTF_8));

    tr = new TestRepository<>(repo);
  }

  @Test
  public void shouldResolveFullRefsFromCache() throws Exception {
    try (PerThreadCache ignored = PerThreadCache.create();
        CachedRefRepository objectUnderTest = createCachedRepository(repo)) {
      String master = RefNames.fullName("master");
      RevCommit first = tr.update(master, tr.commit().add("first", "foo").create());
      String tag = "test_tag";
      String fullTag = RefNames.REFS_TAGS + tag;
      tr.update(fullTag, tr.tag(tag, first));
      tr.update(master, tr.commit().parent(first).add("second", "foo").create());

      verify(cacheWrapper, times(0)).computeIfAbsent(any(), any(), any());
      assertThat(objectUnderTest.resolve(master)).isEqualTo(repo().resolve(master));
      assertThat(objectUnderTest.resolve(fullTag)).isEqualTo(repo().resolve(fullTag));
      verify(cacheWrapper, times(2)).computeIfAbsent(any(), any(), any());
    }
  }

  @Test
  public void shouldNotResolveRefsFromCache() throws Exception {
    try (PerThreadCache ignored = PerThreadCache.create();
        CachedRefRepository objectUnderTest = createCachedRepository(repo)) {
      String master = RefNames.fullName("master");
      String filename = "first";
      RevCommit first = tr.update(master, tr.commit().add(filename, "foo").create());
      String tag = "test_tag";
      String fullTag = RefNames.REFS_TAGS + tag;
      tr.update(fullTag, tr.tag(tag, first));
      tr.update(master, tr.commit().parent(first).add("second", "foo").create());

      assertThat(objectUnderTest.resolve("master")).isEqualTo(repo().resolve("master"));
      assertThat(objectUnderTest.resolve(RefNames.HEAD)).isEqualTo(repo().resolve(RefNames.HEAD));
      assertThat(objectUnderTest.resolve(tag)).isEqualTo(repo().resolve(tag));

      String mastersParent = master + "^";
      ObjectId resolved = objectUnderTest.resolve(mastersParent);
      assertThat(resolved).isEqualTo(first);
      assertThat(resolved).isEqualTo(repo().resolve(mastersParent));

      String mastersParentByTilde = master + "~";
      ObjectId resolvedByTilde = objectUnderTest.resolve(mastersParentByTilde);
      assertThat(resolvedByTilde).isEqualTo(first);
      assertThat(resolvedByTilde).isEqualTo(repo().resolve(mastersParent));

      String mastersFilename = master + ":" + filename;
      ObjectId resolvedByFilename = objectUnderTest.resolve(mastersFilename);
      assertThat(resolvedByFilename).isEqualTo(repo().resolve(mastersFilename));

      String mastersPreviousRevision = master + "@{1}";
      ObjectId resolvedByPreviousRevision = objectUnderTest.resolve(mastersPreviousRevision);
      assertThat(resolvedByPreviousRevision).isEqualTo(repo().resolve(mastersPreviousRevision));

      verify(cacheWrapper, times(0)).computeIfAbsent(any(), any(), any());
    }
  }

  @Test
  public void shouldNotResolveSha1sFromCache() throws Exception {
    try (PerThreadCache ignored = PerThreadCache.create();
        CachedRefRepository objectUnderTest = createCachedRepository(repo)) {
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

      verify(cacheWrapper, times(0)).computeIfAbsent(any(), any(), any());
    }
  }

  @Test
  public void shouldNotResolveTagAndSha1FromCache() throws Exception {
    try (PerThreadCache ignored = PerThreadCache.create();
        CachedRefRepository objectUnderTest = createCachedRepository(repo)) {
      String master = RefNames.fullName("master");
      RevCommit first = tr.update(master, tr.commit().add("first", "foo").create());
      String tag = "test_tag";
      tr.update(RefNames.REFS_TAGS + tag, tr.tag(tag, first));
      RevCommit second = tr.update(master, tr.commit().parent(first).add("second", "foo").create());

      String tagAndSha = tag + "-1-g" + second.getName().substring(0, 6);
      assertThat(objectUnderTest.resolve(tagAndSha)).isEqualTo(repo().resolve(tagAndSha));

      verify(cacheWrapper, times(0)).computeIfAbsent(any(), any(), any());
    }
  }

  private Repository repo() {
    return tr.getRepository();
  }

  private CachedRefRepository createCachedRepository(Repository repo) {
    cacheWrapper = Mockito.spy(new RefByNameCacheWrapper());
    CachedRefDatabase.Factory refDbFactory =
        new CachedRefDatabase.Factory() {
          @Override
          public CachedRefDatabase create(CachedRefRepository repo, RefDatabase delegate) {
            return new CachedRefDatabase(cacheWrapper, null, null, null, repo, delegate);
          }
        };
    return new CachedRefRepository(refDbFactory, null, null, "repo", repo);
  }
}
