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
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.LocalDiskRepositoryManager;
import com.google.gerrit.server.git.MultiBaseLocalDiskRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.eclipse.jgit.lib.Ref;
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

  @Inject private RefByNameCacheWrapper refByNameCacheWrapper;

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
    assertThat(refByNameCacheWrapper.cache()).isInstanceOf(RefByNameCacheImpl.class);
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
    assertThat(refByNameCacheWrapper.cache()).isInstanceOf(RefByNameCacheImpl.class);
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

    assertThat(gitRepoManager.openRepository(project).getRefDatabase().exactRef(branchName))
        .isNotNull();

    assertThat(
            gitRepoManager.openRepository(project).getRefDatabase().getRefsByPrefix("refs/heads/"))
        .comparingElementsUsing(Correspondence.transforming(Ref::getName, "name"))
        .contains(branchName);
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

  private void deleteBranch(String branchName) throws Exception {
    try (Repository repo = gitRepoManager.openRepository(project)) {
      org.eclipse.jgit.lib.RefUpdate update = repo.updateRef(branchName);
      update.setForceUpdate(true);
      assertThat(update.delete()).isEqualTo(RefUpdate.Result.FORCED);
    }
  }
}
