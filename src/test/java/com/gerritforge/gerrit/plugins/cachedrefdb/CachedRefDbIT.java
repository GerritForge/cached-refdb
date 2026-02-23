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

import com.google.common.truth.Correspondence;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.projects.BranchInfo;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.LocalDiskRepositoryManager;
import com.google.gerrit.server.git.MultiBaseLocalDiskRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefRename;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.junit.Test;

@UseLocalDisk
@NoHttpd
public class CachedRefDbIT extends AbstractDaemonTest {
  @Inject(optional = true)
  @Named("LocalDiskRepositoryManager")
  private GitRepositoryManager localGitRepositoryManager;

  @Inject private GitRepositoryManager gitRepoManager;

  @Inject private RefDatabaseCacheWrapper refByNameCacheWrapper;

  @Inject protected GerritApi gerritApi;

  @Test
  @GerritConfig(
      name = "gerrit.installDbModule",
      value = "com.gerritforge.gerrit.plugins.cachedrefdb.LibDbModule")
  @GerritConfig(
      name = "gerrit.installModule",
      value = "com.gerritforge.gerrit.plugins.cachedrefdb.LibSysModule")
  public void shouldBeAbleToInstallCachedGitRepoManager() {
    assertThat(gitRepoManager).isInstanceOf(CachedGitRepositoryManager.class);
    assertThat(((CachedGitRepositoryManager) gitRepoManager).getRepoManager().getClass())
        .isEqualTo(LocalDiskRepositoryManager.class);
    assertThat(refByNameCacheWrapper.cache()).isInstanceOf(RefDatabaseCacheImpl.class);
  }

  @Test
  @GerritConfig(
      name = "gerrit.installDbModule",
      value = "com.gerritforge.gerrit.plugins.cachedrefdb.LibDbModule")
  @GerritConfig(
      name = "gerrit.installModule",
      value = "com.gerritforge.gerrit.plugins.cachedrefdb.LibSysModule")
  @GerritConfig(name = "repository.r1.basePath", value = "/tmp/git1")
  public void shouldMultiBaseRepoManagerBeUsedWhenConfigured() {
    assertThat(gitRepoManager).isInstanceOf(CachedGitRepositoryManager.class);
    assertThat(((CachedGitRepositoryManager) gitRepoManager).getRepoManager())
        .isInstanceOf(MultiBaseLocalDiskRepositoryManager.class);
    assertThat(refByNameCacheWrapper.cache()).isInstanceOf(RefDatabaseCacheImpl.class);
  }

  @Test
  @GerritConfig(
      name = "gerrit.installDbModule",
      value = "com.gerritforge.gerrit.plugins.cachedrefdb.LibModule")
  @GerritConfig(
      name = "gerrit.installModule",
      value = "com.gerritforge.gerrit.plugins.cachedrefdb.LibSysModule")
  public void shouldBeAbleToInstallCachedGitRepoManagerAsNamedBinding() {
    assertThat(localGitRepositoryManager).isNotNull();
    assertThat(localGitRepositoryManager).isInstanceOf(CachedGitRepositoryManager.class);
  }

  @Test
  @GerritConfig(
      name = "gerrit.installDbModule",
      value = "com.gerritforge.gerrit.plugins.cachedrefdb.LibDbModule")
  @GerritConfig(
      name = "gerrit.installModule",
      value = "com.gerritforge.gerrit.plugins.cachedrefdb.LibSysModule")
  public void shouldSeeNewlyCreatedBranch() throws Exception {
    String branchName = "refs/heads/branch1";
    BranchNameKey branchKey = BranchNameKey.create(project, branchName);
    assertThat(gitRepoManager.openRepository(project).getRefDatabase().exactRef(branchName))
        .isNull();

    createBranch(branchKey);

    BranchInfo info = gerritApi.projects().name(project.get()).branch(branchName).get();

    Ref branchRef = gitRepoManager.openRepository(project).getRefDatabase().exactRef(branchName);
    assertThat(branchRef).isNotNull();

    assertThat(branchRef.getObjectId().name()).isEqualTo(info.revision);

    List<Ref> refs =
        gitRepoManager.openRepository(project).getRefDatabase().getRefsByPrefix("refs/heads/");
    Optional<Ref> maybeRef = refs.stream().filter(r -> r.getName().equals(branchName)).findFirst();
    assertThat(maybeRef).isPresent();
    assertThat(maybeRef.get().getObjectId().name()).isEqualTo(info.revision);
  }

  @Test
  @GerritConfig(
      name = "gerrit.installDbModule",
      value = "com.gerritforge.gerrit.plugins.cachedrefdb.LibDbModule")
  @GerritConfig(
      name = "gerrit.installModule",
      value = "com.gerritforge.gerrit.plugins.cachedrefdb.LibSysModule")
  public void shouldSeeBranchDeletion() throws Exception {
    String branchName = "refs/heads/branch2";
    BranchNameKey branchKey = BranchNameKey.create(project, branchName);

    createBranch(branchKey);

    assertThat(gitRepoManager.openRepository(project).getRefDatabase().exactRef(branchName))
        .isNotNull();

    deleteBranch(branchName);

    assertThat(gitRepoManager.openRepository(project).getRefDatabase().exactRef(branchName))
        .isNull();
  }

  @Test
  @GerritConfig(
      name = "gerrit.installDbModule",
      value = "com.gerritforge.gerrit.plugins.cachedrefdb.LibDbModule")
  @GerritConfig(
      name = "gerrit.installModule",
      value = "com.gerritforge.gerrit.plugins.cachedrefdb.LibSysModule")
  public void shouldNotListDeletedBranchInPrefixListing() throws Exception {
    String branchName = "refs/heads/branch-delete-prefix";
    BranchNameKey branchKey = BranchNameKey.create(project, branchName);

    createBranch(branchKey);

    assertThat(
            gitRepoManager.openRepository(project).getRefDatabase().getRefsByPrefix("refs/heads/"))
        .comparingElementsUsing(Correspondence.transforming(Ref::getName, "name"))
        .contains(branchName);

    deleteBranch(branchName);

    assertThat(gitRepoManager.openRepository(project).getRefDatabase().exactRef(branchName))
        .isNull();

    assertThat(
            gitRepoManager.openRepository(project).getRefDatabase().getRefsByPrefix("refs/heads/"))
        .comparingElementsUsing(Correspondence.transforming(Ref::getName, "name"))
        .doesNotContain(branchName);
  }

  @Test
  @GerritConfig(
      name = "gerrit.installDbModule",
      value = "com.gerritforge.gerrit.plugins.cachedrefdb.LibDbModule")
  @GerritConfig(
      name = "gerrit.installModule",
      value = "com.gerritforge.gerrit.plugins.cachedrefdb.LibSysModule")
  public void shouldSeeRenamedBranchInPrefixListing() throws Exception {
    String oldName = "refs/heads/branch-old";
    String newName = "refs/heads/branch-new";
    BranchNameKey oldKey = BranchNameKey.create(project, oldName);

    createBranch(oldKey);

    Repository repo = gitRepoManager.openRepository(project);
    assertThat(repo.getRefDatabase().exactRef(oldName)).isNotNull();
    assertThat(repo.getRefDatabase().exactRef(newName)).isNull();

    renameBranch(oldName, newName);

    repo = gitRepoManager.openRepository(project);
    assertThat(repo.getRefDatabase().exactRef(oldName)).isNull();
    assertThat(repo.getRefDatabase().exactRef(newName)).isNotNull();

    assertThat(repo.getRefDatabase().getRefsByPrefix("refs/heads/"))
        .comparingElementsUsing(Correspondence.transforming(Ref::getName, "name"))
        .contains(newName);

    assertThat(repo.getRefDatabase().getRefsByPrefix("refs/heads/"))
        .comparingElementsUsing(Correspondence.transforming(Ref::getName, "name"))
        .doesNotContain(oldName);
  }

  @Test
  @GerritConfig(
      name = "gerrit.installDbModule",
      value = "com.gerritforge.gerrit.plugins.cachedrefdb.LibDbModule")
  @GerritConfig(
      name = "gerrit.installModule",
      value = "com.gerritforge.gerrit.plugins.cachedrefdb.LibSysModule")
  public void shouldReturnTipsWithSha1ForRef() throws Exception {
    String branchName = "refs/heads/branch-sha1-lookup";
    createBranch(BranchNameKey.create(project, branchName));

    try (Repository repo = gitRepoManager.openRepository(project)) {
      Ref ref = repo.getRefDatabase().exactRef(branchName);
      assertThat(ref).isNotNull();

      Set<Ref> tips = repo.getRefDatabase().getTipsWithSha1(ref.getObjectId());
      assertThat(tips)
          .comparingElementsUsing(Correspondence.transforming(Ref::getName, "name"))
          .contains(branchName);
    }
  }

  @Test
  @GerritConfig(
      name = "gerrit.installDbModule",
      value = "com.gerritforge.gerrit.plugins.cachedrefdb.LibDbModule")
  @GerritConfig(
      name = "gerrit.installModule",
      value = "com.gerritforge.gerrit.plugins.cachedrefdb.LibSysModule")
  public void shouldEvictTipsWithSha1CacheOnRefDelete() throws Exception {
    String branchName = "refs/heads/branch-sha1-evict";
    createBranch(BranchNameKey.create(project, branchName));

    ObjectId branchObjectId;
    try (Repository repo = gitRepoManager.openRepository(project)) {
      Ref ref = repo.getRefDatabase().exactRef(branchName);
      branchObjectId = ref.getObjectId();
      // Populate the cache
      repo.getRefDatabase().getTipsWithSha1(branchObjectId);
    }

    // Delete through the cached layer — triggers eviction of the objectId entry
    deleteBranch(branchName);

    // Deleted branch must no longer appear in subsequent lookups
    try (Repository repo = gitRepoManager.openRepository(project)) {
      assertThat(repo.getRefDatabase().getTipsWithSha1(branchObjectId))
          .comparingElementsUsing(Correspondence.transforming(Ref::getName, "name"))
          .doesNotContain(branchName);
    }
  }

  private void renameBranch(String oldName, String newName) throws Exception {
    try (Repository repo = gitRepoManager.openRepository(project)) {
      RefRename renameRef = repo.renameRef(oldName, newName);
      assertThat(renameRef).isNotNull();
      assertThat(renameRef.rename()).isEqualTo(RefUpdate.Result.RENAMED);
    }
  }

  private void deleteBranch(String branchName) throws Exception {
    try (Repository repo = gitRepoManager.openRepository(project)) {
      RefUpdate update = repo.updateRef(branchName);
      update.setForceUpdate(true);
      assertThat(update.delete()).isEqualTo(RefUpdate.Result.FORCED);
    }
  }
}
