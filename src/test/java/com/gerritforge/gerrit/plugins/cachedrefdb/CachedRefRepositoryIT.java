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
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.registration.DynamicItem;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Callable;
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

public class CachedRefRepositoryIT {
  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private TestRepository<Repository> tr;
  private CachedRefRepository objectUnderTest;
  private TestRefByNameCacheImpl cache;

  @Before
  public void setUp() throws IOException {
    Path repoPath = temporaryFolder.newFolder().toPath();
    Repository repo = new FileRepository(repoPath.toFile());
    repo.create(true);

    // enable reflog for a repository so that references to it could be resolved
    Files.write(
        repoPath.resolve("config"),
        "[core]\n  logAllRefUpdates = true\n".getBytes(StandardCharsets.UTF_8));

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
    String master = RefNames.fullName("master");
    RevCommit first = tr.update(master, tr.commit().add("first", "foo").create());
    String tag = "test_tag";
    String fullTag = RefNames.REFS_TAGS + tag;
    tr.update(fullTag, tr.tag(tag, first));
    tr.update(master, tr.commit().parent(first).add("second", "foo").create());

    assertThat(cache.cacheCalled).isEqualTo(0);
    assertThat(objectUnderTest.resolve(master)).isEqualTo(repo().resolve(master));
    assertThat(objectUnderTest.resolve(fullTag)).isEqualTo(repo().resolve(fullTag));
    assertThat(cache.cacheCalled).isEqualTo(2);
  }

  @Test
  public void shouldNotResolveRefsFromCache() throws Exception {
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

    assertThat(cache.cacheCalled).isEqualTo(0);
  }

  @Test
  public void shouldNotResolveSha1sFromCache() throws Exception {
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

    assertThat(cache.cacheCalled).isEqualTo(0);
  }

  @Test
  public void shouldNotResolveTagAndSha1FromCache() throws Exception {
    String master = RefNames.fullName("master");
    RevCommit first = tr.update(master, tr.commit().add("first", "foo").create());
    String tag = "test_tag";
    tr.update(RefNames.REFS_TAGS + tag, tr.tag(tag, first));
    RevCommit second = tr.update(master, tr.commit().parent(first).add("second", "foo").create());

    String tagAndSha = tag + "-1-g" + second.getName().substring(0, 6);
    assertThat(objectUnderTest.resolve(tagAndSha)).isEqualTo(repo().resolve(tagAndSha));

    assertThat(cache.cacheCalled).isEqualTo(0);
  }

  private Repository repo() {
    return tr.getRepository();
  }

  private CacheLoader<RefByNameCacheImpl.RefsByProjectKey, TernarySearchTree<ObjectId>>
      newCacheLoader() {
    return new CacheLoader<>() {

      @Override
      public TernarySearchTree<ObjectId> load(RefByNameCacheImpl.RefsByProjectKey key)
          throws Exception {
        return null;
      }
    };
  }

  private CachedRefRepository createCachedRepository(Repository repo) {
    cache =
        new TestRefByNameCacheImpl(
            CacheBuilder.newBuilder().build(), CacheBuilder.newBuilder().build(newCacheLoader()));
    RefByNameCacheWrapper wrapper =
        new RefByNameCacheWrapper(DynamicItem.itemOf(RefByNameCache.class, cache));
    CachedRefDatabase.Factory refDbFactory =
        new CachedRefDatabase.Factory() {
          @Override
          public CachedRefDatabase create(CachedRefRepository repo, RefDatabase delegate) {
            return new CachedRefDatabase(wrapper, null, null, null, repo, delegate);
          }
        };
    return new CachedRefRepository(refDbFactory, null, null, "repo", repo);
  }

  private static class TestRefByNameCacheImpl extends RefByNameCacheImpl {
    private int cacheCalled;

    private TestRefByNameCacheImpl(
        Cache<String, Optional<Ref>> refByName,
        LoadingCache<RefsByProjectKey, TernarySearchTree<ObjectId>> refsNamesByPrefix) {
      super(refByName, refsNamesByPrefix);
      cacheCalled = 0;
    }

    @Override
    public Ref computeIfAbsent(
        String identifier, String ref, Callable<? extends Optional<Ref>> loader) {
      cacheCalled++;
      return super.computeIfAbsent(identifier, ref, loader);
    }
  }
}
